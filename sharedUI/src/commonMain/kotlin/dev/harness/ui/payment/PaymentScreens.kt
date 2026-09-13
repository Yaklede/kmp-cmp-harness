package dev.harness.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.harness.core.*
import dev.harness.ui.design.*

fun formatWon(amount: Long): String = amount.toString().reversed().chunked(3).joinToString(",").reversed() + "원"

@Composable
fun ContractDetail(state: PaymentState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("납부할 내역을\n확인해 주세요", style = MaterialTheme.typography.h4)
        HarnessCard {
            Text(state.contract.title, color = Tokens.muted)
            Spacer(Modifier.height(12.dp))
            HarnessAmount(formatWon(state.contract.amountWon), Modifier.testTag("payment.amount"))
            Spacer(Modifier.height(20.dp))
            HorizontalRule()
            HarnessListRow("받는 분", state.contract.recipient)
            HarnessListRow("계약", "샘플 주택 · 101호")
        }
        Text("다음 화면에서 결제수단과 금액을 확인한 뒤 직접 결제를 요청할 수 있어요.", color = Tokens.muted)
    }
}

@Composable
fun PaymentConfirm(state: PaymentState, onIntent: (PaymentIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("이 내용으로\n결제할까요?", style = MaterialTheme.typography.h4)
        HarnessCard {
            Text("결제 금액", color = Tokens.muted)
            Spacer(Modifier.height(12.dp))
            HarnessAmount(formatWon(state.contract.amountWon), Modifier.testTag("payment.amount"))
            Spacer(Modifier.height(16.dp))
            HorizontalRule()
            HarnessListRow("받는 분", state.contract.recipient, Modifier.testTag("payment.recipient"))
            HarnessListRow("결제수단", state.contract.paymentMethod, Modifier.testTag("payment.method"))
        }
        HarnessTextField(state.memo, { onIntent(PaymentIntent.EditMemo(it)) }, "메모 (선택, 최대 120자)",
            modifier = Modifier.testTag("payment.memo"), enabled = state.canEdit)
        if (state.phase == PaymentPhase.Submitting) HarnessLoading("결제를 요청하고 있어요. 잠시 기다려 주세요.")
    }
}

@Composable
fun PaymentResult(state: PaymentState, modifier: Modifier = Modifier) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        when (state.phase) {
            PaymentPhase.Succeeded -> {
                Text("결제가\n완료됐어요", style = MaterialTheme.typography.h4, modifier = Modifier.testTag("payment.success"))
                Text("샘플 납부 내역을 확인해 주세요.", color = Tokens.muted)
            }
            PaymentPhase.FailedKnown -> HarnessErrorState(state.message ?: "결제수단을 확인해 주세요.")
            else -> {
                Text("결제 결과를\n확인하고 있어요", style = MaterialTheme.typography.h4, modifier = Modifier.testTag("payment.unknown"))
                Text(state.message ?: "결제 결과를 조회해 주세요.")
            }
        }
        HarnessCard {
            HarnessAmount(formatWon(state.contract.amountWon), Modifier.testTag("payment.amount"))
            HarnessListRow("받는 분", state.contract.recipient)
            HarnessListRow("결제수단", state.contract.paymentMethod)
            if (state.memo.isNotBlank()) HarnessListRow("메모", state.memo)
            if (state.receiptId != null) Text("샘플 영수증이 발급됐어요.", color = Tokens.primary)
        }
    }
}

@Composable
private fun HorizontalRule() { Divider(color = Tokens.border) }

@Composable
fun PaymentFooter(state: PaymentState, onIntent: (PaymentIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.screen == Screen.ContractDetail -> HarnessButton("결제 내용 확인", { onIntent(PaymentIntent.OpenConfirmation) }, Modifier.testTag("contract.continue"))
            state.screen == Screen.PaymentConfirm -> HarnessButton(
                if (state.phase == PaymentPhase.Submitting) "결제 요청 중" else "${formatWon(state.contract.amountWon)} 결제하기",
                { onIntent(PaymentIntent.Submit) }, Modifier.testTag("payment.submit"), enabled = state.canSubmit,
                loading = state.phase == PaymentPhase.Submitting)
            state.phase == PaymentPhase.AwaitingConfirmation -> HarnessButton("결제 결과 조회", { onIntent(PaymentIntent.CheckStatus) },
                Modifier.testTag("payment.lookup"), loading = state.checkingStatus)
            state.phase == PaymentPhase.FailedKnown -> HarnessButton("입력 내용 확인", { onIntent(PaymentIntent.Back) }, Modifier.testTag("payment.review"))
            state.phase == PaymentPhase.Succeeded -> Text("납부가 완료되어 다시 결제할 수 없어요.", color = Tokens.muted)
        }
    }
}
