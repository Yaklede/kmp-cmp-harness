package dev.harness.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable enum class MockScenario { Success, Declined, ResponseLost, Offline, Slow }

/** In-memory fake server, not a claim about any real provider's idempotency. */
class MockPaymentRepository(
    private val scenario: MockScenario = MockScenario.Success,
    private val latencyMillis: Long = 600,
) : PaymentRepository {
    private val mutex = Mutex()
    private val ledger = mutableMapOf<String, Pair<PaymentRequest, PaymentOutcome>>()
    var submitCalls: Int = 0
        private set
    var lookupCalls: Int = 0
        private set
    val approvedCount: Int get() = ledger.values.count { it.second is PaymentOutcome.Approved }

    override suspend fun submit(request: PaymentRequest): PaymentOutcome {
        val outcome = mutex.withLock {
            submitCalls++
            ledger[request.operationId]?.let {
                require(it.first == request) { "Operation ID reused with a different request" }
                return@withLock it.second
            }
            if (scenario == MockScenario.Offline) return@withLock PaymentOutcome.Unknown
            val result = if (scenario == MockScenario.Declined) PaymentOutcome.Declined("테스트 카드 승인이 거절됐어요. 입력 내용은 보관했어요.")
                else PaymentOutcome.Approved("receipt-${request.operationId}")
            ledger[request.operationId] = request to result
            result
        }
        delay(if (scenario == MockScenario.Slow) latencyMillis * 10 else latencyMillis)
        return if (scenario == MockScenario.ResponseLost) PaymentOutcome.Unknown else outcome
    }

    override suspend fun lookup(operationId: String): PaymentOutcome {
        delay(latencyMillis)
        return mutex.withLock {
            lookupCalls++
            ledger[operationId]?.second ?: PaymentOutcome.Unknown
        }
    }
}
