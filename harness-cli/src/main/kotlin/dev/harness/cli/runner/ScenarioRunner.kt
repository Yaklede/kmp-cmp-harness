package dev.harness.cli.runner

import dev.harness.cli.contract.Verdict
import dev.harness.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable
data class ScenarioFixture(
    val id: String,
    val scenario: MockScenario,
    val expectedPhase: PaymentPhase,
    val lookupAfterUnknown: Boolean,
    val rapidClicks: Int,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{1,80}"))) { "Invalid fixture ID" }
        require(rapidClicks in 1..100) { "rapidClicks must be between 1 and 100" }
        require(expectedPhase in setOf(PaymentPhase.Succeeded, PaymentPhase.FailedKnown, PaymentPhase.AwaitingConfirmation))
    }
}

@Serializable
data class ScenarioResult(
    val state: PaymentState,
    val events: List<PaymentEvent>,
    val submitCalls: Int,
    val lookupCalls: Int,
    val approvedCount: Int,
    val acceptedSubmissions: Int,
    val gate: GateResult,
)

class ScenarioRunner {
    suspend fun run(fixture: ScenarioFixture): ScenarioResult = coroutineScope {
        val repository = MockPaymentRepository(fixture.scenario, latencyMillis = 10)
        var id = 0
        var tick = 0L
        val store = PaymentStore(repository, this, { "${fixture.id}-${++id}" }, { tick++ })
        var accepted = 0
        var failure: String? = null
        try {
            withTimeout(5_000) {
                store.dispatch(PaymentIntent.OpenConfirmation)
                store.dispatch(PaymentIntent.EditMemo("fixture memo"))
                accepted = List(fixture.rapidClicks) { async { store.dispatch(PaymentIntent.Submit) } }
                    .awaitAll().count { it.accepted }
                store.state.first { it.phase != PaymentPhase.Submitting }
                if (store.state.value.phase == PaymentPhase.AwaitingConfirmation && fixture.lookupAfterUnknown) {
                    check(!store.dispatch(PaymentIntent.Submit).accepted) { "Unresolved payment accepted resubmission" }
                    store.dispatch(PaymentIntent.CheckStatus)
                    store.state.first { !it.checkingStatus }
                }
                check(store.state.value.phase == fixture.expectedPhase) { "Expected ${fixture.expectedPhase}, got ${store.state.value.phase}" }
                check(repository.submitCalls == 1 && accepted == 1) { "Duplicate submit accepted" }
                check(store.state.value.memo == "fixture memo") { "Input was lost" }
                check(store.state.value.contract.amountWon == 500_000L) { "Amount changed" }
                val expectedApprovals = if (fixture.scenario in setOf(MockScenario.Success, MockScenario.ResponseLost, MockScenario.Slow)) 1 else 0
                check(repository.approvedCount == expectedApprovals) { "Mock server outcome mismatch" }
            }
        } catch (timeout: TimeoutCancellationException) {
            failure = "Scenario timeout"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message ?: "Scenario failed"
        } finally {
            store.close()
        }
        ScenarioResult(store.state.value, store.events.value, repository.submitCalls, repository.lookupCalls,
            repository.approvedCount, accepted, GateResult("L0.behavior", if (failure == null) Verdict.PASS else Verdict.FAIL,
                listOf("state.json", "events.jsonl", "mock-server.json"), failure ?: "Shared store and fake server agree with fixture"))
    }
}
