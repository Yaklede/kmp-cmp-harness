package dev.harness.cli.runner

import dev.harness.cli.contract.Verdict
import kotlinx.serialization.Serializable

@Serializable data class GateResult(val id: String, val verdict: Verdict, val evidence: List<String>, val detail: String)

object Gates {
    val required = listOf("L0.behavior", "L1.desktop", "L2.android", "L2.ios")

    fun complete(results: List<GateResult>): List<GateResult> {
        require(results.map { it.id }.distinct().size == results.size) { "Duplicate gate results" }
        require(results.all { it.id in required }) { "Unknown gate ID" }
        return required.map { id ->
            val result = results.find { it.id == id }
            when {
                result == null -> GateResult(id, Verdict.NOT_RUN, emptyList(), "Driver has not run for this source and fixture")
                result.verdict == Verdict.PASS && result.evidence.isEmpty() ->
                    result.copy(verdict = Verdict.FAIL, detail = "PASS without evidence is invalid")
                else -> result
            }
        }
    }

    fun overall(results: List<GateResult>): Verdict {
        val verdicts = complete(results).map { it.verdict }
        return listOf(Verdict.FAIL, Verdict.REVIEW, Verdict.UNSUPPORTED, Verdict.NOT_RUN, Verdict.PASS).first { it in verdicts }
    }
}
