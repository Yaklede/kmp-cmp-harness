package dev.harness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                Text("계약 상세", style = MaterialTheme.typography.h4)
                Spacer(Modifier.height(24.dp))
                Text("9월 월세 · 샘플 계약")
                Text("500,000원", style = MaterialTheme.typography.h3)
                Text("실제 결제 없이 UX를 검증하는 Mock 앱입니다.")
            }
        }
    }
}
