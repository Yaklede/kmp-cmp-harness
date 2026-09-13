package dev.harness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.harness.core.*
import dev.harness.ui.design.*
import dev.harness.ui.payment.*
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.TimeSource

@Composable
fun App(modifier: Modifier = Modifier, scenario: MockScenario = MockScenario.Success, gallery: Boolean = false,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> }) {
    val scope = rememberCoroutineScope()
    val store = remember(scenario) {
        val started = TimeSource.Monotonic.markNow()
        PaymentStore(MockPaymentRepository(scenario), scope,
            { "sample-${Random.nextLong().toULong().toString(16)}" }, { started.elapsedNow().inWholeMilliseconds })
    }
    DisposableEffect(store) { onDispose { store.close() } }
    HarnessTheme {
        Surface(modifier.fillMaxSize(), color = Tokens.background) {
            if (gallery) ComponentGallery(Modifier.safeDrawingPadding())
            else {
                val state by store.state.collectAsState()
                val onIntent: (PaymentIntent) -> Unit = { scope.launch { store.dispatch(it) } }
                backHandler(state.screen != Screen.ContractDetail && state.phase != PaymentPhase.Succeeded) { onIntent(PaymentIntent.Back) }
                PaymentApp(state, onIntent)
            }
        }
    }
}

@Composable
fun PaymentApp(state: PaymentState, onIntent: (PaymentIntent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            HarnessAppBar(when (state.screen) {
                Screen.ContractDetail -> "계약 상세"
                Screen.PaymentConfirm -> "결제 확인"
                Screen.PaymentResult -> "결제 결과"
            }, if ((state.screen == Screen.PaymentConfirm && state.canEdit) || state.phase == PaymentPhase.FailedKnown)
                ({ onIntent(PaymentIntent.Back) }) else null)
            Text("MOCK DEMO · 실제 금액이 청구되지 않아요", style = MaterialTheme.typography.body2, color = Tokens.muted)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 28.dp)) {
                when (state.screen) {
                    Screen.ContractDetail -> ContractDetail(state)
                    Screen.PaymentConfirm -> PaymentConfirm(state, onIntent)
                    Screen.PaymentResult -> PaymentResult(state)
                }
            }
            PaymentFooter(state, onIntent, Modifier.padding(vertical = 16.dp))
        }
    }
}
