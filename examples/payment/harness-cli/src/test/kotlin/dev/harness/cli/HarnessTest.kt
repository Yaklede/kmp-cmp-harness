package dev.harness.cli

import dev.harness.cli.contract.*
import dev.harness.cli.runner.*
import dev.harness.core.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class HarnessTest {
    private val root = Path.of("..").toAbsolutePath().normalize()
    private val contract get() = json.decodeFromString<UxContract>(root.resolve("contracts/payment-confirm.json").readText())
    private fun observation(name: String) = json.decodeFromString<UiObservation>(root.resolve("fixtures/ui/$name.json").readText())

    @Test fun validContractAndObservationHaveNoFindings() {
        assertTrue(ContractValidator.validate(contract).isEmpty())
        assertTrue(UxInspector.inspect(contract, observation("valid-confirm")).isEmpty())
    }

    @Test fun seededDefectsAreDetectedWithEvidenceAndNoPolicyAutoFix() {
        val findings = UxInspector.inspect(contract, observation("keyboard-obscured"))
        assertTrue(findings.map { it.ruleId }.containsAll(listOf("INFORMATION-CLIPPED", "TOUCH-TARGET", "ACTION-REACHABLE",
            "PAYMENT-UNKNOWN-NO-RESUBMIT", "PAYMENT-LOOKUP-MISSING", "LABEL-IN-NAME")))
        assertTrue(findings.all { it.evidence.isNotEmpty() && it.acceptanceTest.isNotBlank() })
        assertTrue(findings.filter { it.changeClass in setOf(ChangeClass.C, ChangeClass.D) }.none { it.automaticFixAllowed })
    }

    @Test fun heldOutOverlapAndMissingInformationAreDetected() {
        val valid = observation("valid-confirm")
        val submit = valid.nodes.last()
        val altered = valid.copy(nodes = valid.nodes.filter { it.id != "recipient" } + submit.copy(id = "secondary"))
        val rules = UxInspector.inspect(contract, altered).map { it.ruleId }
        assertTrue("TOUCH-OVERLAP" in rules)
        assertTrue("INFORMATION-MISSING" in rules)
    }

    @Test fun invalidBoundsAndDuplicateNodesFailClosed() {
        val valid = observation("valid-confirm")
        assertEquals("OBSERVATION-INVALID", UxInspector.inspect(contract, valid.copy(nodes = valid.nodes + valid.nodes.first())).single().ruleId)
        assertEquals("OBSERVATION-INVALID", UxInspector.inspect(contract, valid.copy(viewport = valid.viewport.copy(width = -1.0))).single().ruleId)
    }

    @Test fun touchSizeUsesLogicalPlatformUnits() {
        val valid = observation("valid-confirm")
        val small = valid.copy(nodes = valid.nodes.map { if (it.interactive) it.copy(bounds = it.bounds.copy(height = 44.0)) else it })
        assertTrue(UxInspector.inspect(contract, small).any { it.ruleId == "TOUCH-TARGET" })
        assertTrue(UxInspector.inspect(contract, small.copy(platform = Platform.Ios)).isEmpty())
    }

    @Test fun unresolvedPolicyAndUnsafeRetryDoNotValidate() {
        assertTrue(ContractValidator.validate(contract.copy(allowResubmitWhenUnknown = true)).any { it.verdict == Verdict.FAIL })
        assertEquals(Verdict.REVIEW, ContractValidator.validate(contract.copy(unresolvedPolicies = listOf("provider retry"))).single().verdict)
    }

    @Test fun absentOrUnsupportedGatesNeverPass() {
        assertEquals(Verdict.NOT_RUN, Gates.overall(emptyList()))
        val l0 = GateResult("L0.behavior", Verdict.PASS, listOf("state.json"), "executed")
        assertEquals(Verdict.NOT_RUN, Gates.overall(listOf(l0)))
        assertEquals(Verdict.UNSUPPORTED, Gates.overall(listOf(l0.copy(verdict = Verdict.UNSUPPORTED))))
        assertEquals(Verdict.FAIL, Gates.overall(listOf(l0.copy(evidence = emptyList()))))
        assertFailsWith<IllegalArgumentException> { Gates.complete(listOf(l0, l0)) }
        assertEquals(Verdict.PASS, Gates.overall(Gates.required.map { l0.copy(id = it) }))
    }

    @Test fun everyPaymentFixtureChecksBothStoreAndServer() = runTest {
        for (file in listOf("payment-success", "payment-response-lost", "payment-declined", "payment-offline")) {
            val fixture = json.decodeFromString<ScenarioFixture>(root.resolve("fixtures/$file.json").readText())
            val result = ScenarioRunner().run(fixture)
            assertEquals(Verdict.PASS, result.gate.verdict, "$file: ${result.gate.detail}")
            assertEquals(1, result.submitCalls)
            assertEquals(Verdict.NOT_RUN, Gates.overall(listOf(result.gate)))
        }
    }

    @Test fun wrongExpectedOutcomeProducesFailure() = runTest {
        val fixture = ScenarioFixture("incorrect", MockScenario.Declined, PaymentPhase.Succeeded, false, 1)
        assertEquals(Verdict.FAIL, ScenarioRunner().run(fixture).gate.verdict)
    }

    @Test fun inputHashIncludesUncommittedFilesAndArtifactsAreSelfContained() = runTest {
        val temp = Files.createTempDirectory("harness-evidence-test")
        try {
            git(temp, "init", "-q")
            temp.resolve(".gitignore").writeText("artifacts/\n")
            temp.resolve("source.txt").writeText("before")
            git(temp, "add", ".")
            git(temp, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "fixture")
            val initial = sourceTreeHash(temp)
            temp.resolve("source.txt").writeText("after")
            assertNotEquals(initial, sourceTreeHash(temp))
            temp.resolve("new.txt").writeText("new")
            val stable = sourceTreeHash(temp)
            val bytes = root.resolve("fixtures/payment-success.json").readBytes()
            val result = ScenarioRunner().run(json.decodeFromString(bytes.decodeToString()))
            val dir = writeRun(temp, root.resolve("contracts/payment-confirm.json").readBytes(), bytes, result, stable)
            val manifest = json.decodeFromString<RunManifest>(dir.resolve("manifest.json").readText())
            assertEquals(Verdict.NOT_RUN, manifest.overall)
            assertEquals(stable, manifest.sourceTreeSha256)
            assertEquals(sha256(bytes), manifest.fixtureSha256)
            assertTrue(dir.resolve("events.jsonl").readLines().all { it.startsWith("{") && it.endsWith("}") })
            assertEquals(stable, sourceTreeHash(temp))
            temp.resolve("source.txt").writeText("changed again")
            assertFailsWith<IllegalStateException> { writeRun(temp, byteArrayOf(), bytes, result, stable) }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
