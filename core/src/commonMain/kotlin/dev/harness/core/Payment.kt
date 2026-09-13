package dev.harness.core

import kotlinx.serialization.Serializable

@Serializable
data class Contract(
    val id: String = "contract-demo-001",
    val title: String = "9월 월세",
    val recipient: String = "샘플 임대인",
    val amountWon: Long = 500_000,
    val paymentMethod: String = "테스트 카드 · 1234",
) {
    init { require(id.isNotBlank()); require(amountWon > 0) }
}

@Serializable enum class Screen { ContractDetail, PaymentConfirm, PaymentResult }
@Serializable enum class PaymentPhase { Ready, Submitting, Succeeded, FailedKnown, AwaitingConfirmation }

@Serializable
data class PaymentState(
    val contract: Contract = Contract(),
    val screen: Screen = Screen.ContractDetail,
    val phase: PaymentPhase = PaymentPhase.Ready,
    val memo: String = "",
    val operationId: String? = null,
    val receiptId: String? = null,
    val message: String? = null,
    val checkingStatus: Boolean = false,
    val revision: Long = 0,
) {
    val canSubmit: Boolean get() = screen == Screen.PaymentConfirm &&
        phase in setOf(PaymentPhase.Ready, PaymentPhase.FailedKnown)
    val canEdit: Boolean get() = phase in setOf(PaymentPhase.Ready, PaymentPhase.FailedKnown)

    /** An interrupted request is unresolved, never implicitly safe to submit again. */
    fun restored(): PaymentState = when (phase) {
        PaymentPhase.Submitting, PaymentPhase.AwaitingConfirmation -> copy(
            phase = PaymentPhase.AwaitingConfirmation, screen = Screen.PaymentResult,
            checkingStatus = false, message = "결제 결과를 확인해 주세요.",
        )
        else -> copy(checkingStatus = false)
    }
}

sealed interface PaymentIntent {
    data object OpenConfirmation : PaymentIntent
    data object Back : PaymentIntent
    data class EditMemo(val value: String) : PaymentIntent
    data object Submit : PaymentIntent
    data object CheckStatus : PaymentIntent
}

sealed interface PaymentAction {
    data class User(val intent: PaymentIntent, val newOperationId: String? = null) : PaymentAction
    data class Completed(val operationId: String, val result: PaymentOutcome, val fromLookup: Boolean) : PaymentAction
}

@Serializable
sealed interface PaymentOutcome {
    @Serializable data class Approved(val receiptId: String) : PaymentOutcome
    @Serializable data class Declined(val reason: String) : PaymentOutcome
    @Serializable data object Unknown : PaymentOutcome
}

@Serializable
data class PaymentRequest(val operationId: String, val contract: Contract, val memo: String)

sealed interface PaymentEffect {
    data class Submit(val request: PaymentRequest) : PaymentEffect
    data class Lookup(val operationId: String) : PaymentEffect
}

data class Transition(val state: PaymentState, val effect: PaymentEffect? = null)

/** Pure transitions. Platform input and headless commands reach the same reducer. */
object PaymentReducer {
    fun reduce(state: PaymentState, action: PaymentAction): Transition {
        val next = when (action) {
            is PaymentAction.User -> user(state, action)
            is PaymentAction.Completed -> completed(state, action)
        }
        return if (next.state == state) next else next.copy(state = next.state.copy(revision = state.revision + 1))
    }

    private fun user(s: PaymentState, a: PaymentAction.User): Transition = when (val intent = a.intent) {
        PaymentIntent.OpenConfirmation -> if (s.screen == Screen.ContractDetail && s.canEdit)
            Transition(s.copy(screen = Screen.PaymentConfirm)) else Transition(s)
        PaymentIntent.Back -> when {
            s.screen == Screen.PaymentConfirm && s.canEdit -> Transition(s.copy(screen = Screen.ContractDetail))
            s.screen == Screen.PaymentResult && s.phase == PaymentPhase.FailedKnown -> Transition(s.copy(screen = Screen.PaymentConfirm))
            else -> Transition(s)
        }
        is PaymentIntent.EditMemo -> if (s.screen == Screen.PaymentConfirm && s.canEdit)
            Transition(s.copy(memo = intent.value.take(120))) else Transition(s)
        PaymentIntent.Submit -> if (s.canSubmit && !a.newOperationId.isNullOrBlank() && a.newOperationId != s.operationId) {
            val request = PaymentRequest(a.newOperationId, s.contract, s.memo)
            Transition(s.copy(phase = PaymentPhase.Submitting, operationId = request.operationId,
                message = null, receiptId = null), PaymentEffect.Submit(request))
        } else Transition(s)
        PaymentIntent.CheckStatus -> if (s.phase == PaymentPhase.AwaitingConfirmation && !s.checkingStatus && s.operationId != null)
            Transition(s.copy(checkingStatus = true), PaymentEffect.Lookup(s.operationId)) else Transition(s)
    }

    private fun completed(s: PaymentState, a: PaymentAction.Completed): Transition {
        if (s.operationId != a.operationId) return Transition(s)
        if (a.fromLookup && (s.phase != PaymentPhase.AwaitingConfirmation || !s.checkingStatus)) return Transition(s)
        if (!a.fromLookup && s.phase != PaymentPhase.Submitting) return Transition(s)
        val base = s.copy(screen = Screen.PaymentResult, checkingStatus = false)
        return Transition(when (val result = a.result) {
            is PaymentOutcome.Approved -> base.copy(phase = PaymentPhase.Succeeded, receiptId = result.receiptId, message = "Mock 결제가 완료됐어요.")
            is PaymentOutcome.Declined -> base.copy(phase = PaymentPhase.FailedKnown, message = result.reason)
            PaymentOutcome.Unknown -> base.copy(phase = PaymentPhase.AwaitingConfirmation,
                message = "아직 결제 결과를 확인하지 못했어요. 다시 결제하지 말고 결과를 조회해 주세요.")
        })
    }
}
