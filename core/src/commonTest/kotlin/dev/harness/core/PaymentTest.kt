package dev.harness.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentTest {
    private fun TestScope.store(repository: PaymentRepository, initial: PaymentState = PaymentState(), timeout: Long = 10_000): PaymentStore {
        var id = 0
        return PaymentStore(repository, backgroundScope, { "op-${++id}" }, { testScheduler.currentTime }, initial, timeout)
    }

    @Test fun successRequiresConfirmationAndPreservesTheRequest() = runTest {
        val repository = MockPaymentRepository(latencyMillis = 5)
        val store = store(repository)
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        store.dispatch(PaymentIntent.OpenConfirmation)
        store.dispatch(PaymentIntent.EditMemo("9월 납부"))
        val receipt = store.dispatch(PaymentIntent.Submit)
        assertTrue(receipt.accepted)
        assertEquals(PaymentPhase.Submitting, store.state.value.phase)
        val done = store.state.first { it.phase == PaymentPhase.Succeeded }
        assertEquals("9월 납부", done.memo)
        assertEquals("receipt-op-1", done.receiptId)
        assertEquals(1, repository.approvedCount)
        store.close()
    }

    @Test fun concurrentClicksProduceOneSubmission() = runTest {
        val repository = MockPaymentRepository(latencyMillis = 100)
        val store = store(repository)
        store.dispatch(PaymentIntent.OpenConfirmation)
        val receipts = List(30) { async { store.dispatch(PaymentIntent.Submit) } }.awaitAll()
        assertEquals(1, receipts.count { it.accepted })
        store.state.first { it.phase == PaymentPhase.Succeeded }
        assertEquals(1, repository.submitCalls)
        assertEquals(1, repository.approvedCount)
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        store.close()
    }

    @Test fun lostResponseIsResolvedByLookupWithoutResubmission() = runTest {
        val repository = MockPaymentRepository(MockScenario.ResponseLost, 5)
        val store = store(repository)
        store.dispatch(PaymentIntent.OpenConfirmation)
        store.dispatch(PaymentIntent.Submit)
        store.state.first { it.phase == PaymentPhase.AwaitingConfirmation }
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        assertFalse(store.dispatch(PaymentIntent.Back).accepted)
        assertTrue(store.dispatch(PaymentIntent.CheckStatus).accepted)
        assertFalse(store.dispatch(PaymentIntent.CheckStatus).accepted)
        store.state.first { it.phase == PaymentPhase.Succeeded }
        assertEquals(1, repository.submitCalls)
        assertEquals(1, repository.lookupCalls)
        store.close()
    }

    @Test fun knownFailureRetainsInputAndRequiresExplicitNewAttempt() = runTest {
        val repository = MockPaymentRepository(MockScenario.Declined, 1)
        val store = store(repository)
        store.dispatch(PaymentIntent.OpenConfirmation)
        store.dispatch(PaymentIntent.EditMemo("keep this"))
        store.dispatch(PaymentIntent.Submit)
        store.state.first { it.phase == PaymentPhase.FailedKnown }
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        store.dispatch(PaymentIntent.Back)
        assertEquals("keep this", store.state.value.memo)
        store.dispatch(PaymentIntent.Submit)
        store.state.first { it.phase == PaymentPhase.FailedKnown }
        assertEquals("op-2", store.state.value.operationId)
        assertEquals(2, repository.submitCalls)
        store.close()
    }

    @Test fun offlineRemainsUnresolvedAfterLookup() = runTest {
        val repository = MockPaymentRepository(MockScenario.Offline, 1)
        val store = store(repository)
        store.dispatch(PaymentIntent.OpenConfirmation)
        store.dispatch(PaymentIntent.Submit)
        store.state.first { it.phase == PaymentPhase.AwaitingConfirmation }
        store.dispatch(PaymentIntent.CheckStatus)
        store.state.first { !it.checkingStatus }
        assertEquals(PaymentPhase.AwaitingConfirmation, store.state.value.phase)
        assertEquals(0, repository.approvedCount)
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        store.close()
    }

    @Test fun timeoutAfterServerCommitCanStillResolveToSuccess() = runTest {
        val repository = MockPaymentRepository(MockScenario.Slow, 20)
        val store = store(repository, timeout = 50)
        store.dispatch(PaymentIntent.OpenConfirmation)
        store.dispatch(PaymentIntent.Submit)
        store.state.first { it.phase == PaymentPhase.AwaitingConfirmation }
        assertEquals(1, repository.approvedCount)
        store.dispatch(PaymentIntent.CheckStatus)
        store.state.first { it.phase == PaymentPhase.Succeeded }
        assertEquals(1, repository.submitCalls)
        store.close()
    }

    @Test fun restoredInFlightRequestUsesTheOriginalOperation() = runTest {
        val repository = MockPaymentRepository(MockScenario.ResponseLost, 1)
        repository.submit(PaymentRequest("existing", Contract(), "saved"))
        val snapshot = PaymentState(screen = Screen.PaymentConfirm, phase = PaymentPhase.Submitting,
            operationId = "existing", memo = "saved")
        val decoded = Json.decodeFromString<PaymentState>(Json.encodeToString(snapshot))
        val store = store(repository, decoded)
        assertEquals(PaymentPhase.AwaitingConfirmation, store.state.value.phase)
        assertEquals(Screen.PaymentResult, store.state.value.screen)
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        store.dispatch(PaymentIntent.CheckStatus)
        store.state.first { it.phase == PaymentPhase.Succeeded }
        assertEquals("existing", store.state.value.operationId)
        assertEquals(1, repository.submitCalls)
        store.close()
    }

    @Test fun staleAndUnsolicitedCompletionsCannotChangeState() {
        val s = PaymentState(phase = PaymentPhase.Submitting, operationId = "current")
        assertEquals(s, PaymentReducer.reduce(s, PaymentAction.Completed("old", PaymentOutcome.Approved("r"), false)).state)
        assertEquals(s, PaymentReducer.reduce(s, PaymentAction.Completed("current", PaymentOutcome.Approved("r"), true)).state)
        val ready = PaymentState(operationId = "current")
        assertEquals(ready, PaymentReducer.reduce(ready, PaymentAction.Completed("current", PaymentOutcome.Approved("r"), false)).state)
    }

    @Test fun repositoryUsesIdempotencyAndRejectsDifferentPayload() = runTest {
        val repository = MockPaymentRepository(latencyMillis = 0)
        val request = PaymentRequest("same", Contract(), "memo")
        assertEquals(repository.submit(request), repository.submit(request))
        assertEquals(1, repository.approvedCount)
        assertFailsWith<IllegalArgumentException> { repository.submit(request.copy(memo = "different")) }
    }

    @Test fun transportExceptionsDoNotBecomeKnownFailures() = runTest {
        val repository = object : PaymentRepository {
            override suspend fun submit(request: PaymentRequest): PaymentOutcome = error("transport lost")
            override suspend fun lookup(operationId: String): PaymentOutcome = PaymentOutcome.Unknown
        }
        val store = store(repository)
        store.dispatch(PaymentIntent.OpenConfirmation)
        store.dispatch(PaymentIntent.Submit)
        store.state.first { it.phase == PaymentPhase.AwaitingConfirmation }
        assertFalse(store.dispatch(PaymentIntent.Submit).accepted)
        store.close()
    }
}
