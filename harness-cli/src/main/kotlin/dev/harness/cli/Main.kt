package dev.harness.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import dev.harness.cli.contract.*
import dev.harness.cli.runner.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.io.path.*

private class Harness : CliktCommand(name = "harness") {
    override fun run() = Unit
}

private class Validate : CliktCommand(name = "validate") {
    private val contract by option(help = "UX contract JSON").default("contracts/payment-confirm.json")
    override fun run() {
        val findings = ContractValidator.validate(json.decodeFromString<UxContract>(Path.of(contract).readText()))
        echo(json.encodeToString(findings))
        if (findings.any { it.verdict == Verdict.FAIL }) throw ProgramResult(1)
        if (findings.any { it.verdict == Verdict.REVIEW }) throw ProgramResult(2)
    }
}

private class Inspect : CliktCommand(name = "inspect-ui") {
    private val contract by option().default("contracts/payment-confirm.json")
    private val observation by option(help = "Normalized observation JSON; no native driver implied").required()
    override fun run() {
        val spec = json.decodeFromString<UxContract>(Path.of(contract).readText())
        val input = json.decodeFromString<UiObservation>(Path.of(observation).readText())
        val findings = ContractValidator.validate(spec) + UxInspector.inspect(spec, input)
        echo("Observation source: ${input.source}. This command does not award native gates.")
        echo(json.encodeToString(findings))
        if (findings.any { it.verdict == Verdict.FAIL }) throw ProgramResult(1)
        if (findings.any { it.verdict == Verdict.REVIEW }) throw ProgramResult(2)
    }
}

private class Scenario : CliktCommand(name = "scenario") {
    private val fixture by option().default("fixtures/payment-response-lost.json")
    private val contract by option().default("contracts/payment-confirm.json")
    private val requireAllGates by option(help = "Exit 2 when native/visual gates are missing").flag()
    override fun run() {
        val root = Path.of(git(Path.of("."), "rev-parse", "--show-toplevel").trim())
        val contractBytes = Path.of(contract).readBytes()
        val fixtureBytes = Path.of(fixture).readBytes()
        val spec = json.decodeFromString<UxContract>(contractBytes.decodeToString())
        val findings = ContractValidator.validate(spec)
        if (findings.isNotEmpty()) {
            echo(json.encodeToString(findings))
            throw ProgramResult(if (findings.any { it.verdict == Verdict.FAIL }) 1 else 2)
        }
        val input = json.decodeFromString<ScenarioFixture>(fixtureBytes.decodeToString())
        val sourceHash = sourceTreeHash(root)
        val result = runBlocking { ScenarioRunner().run(input) }
        val directory = writeRun(root, contractBytes, fixtureBytes, result, sourceHash)
        echo("L0.behavior=${result.gate.verdict}; overall=${Gates.overall(listOf(result.gate))}\n$directory/report.md")
        if (result.gate.verdict != Verdict.PASS) throw ProgramResult(1)
        if (requireAllGates && Gates.overall(listOf(result.gate)) != Verdict.PASS) throw ProgramResult(2)
    }
}

fun main(args: Array<String>) = Harness().subcommands(Validate(), Inspect(), Scenario()).main(args)
