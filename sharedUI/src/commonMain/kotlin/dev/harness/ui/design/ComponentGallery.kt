package dev.harness.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf("") }
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HarnessAppBar("컴포넌트 갤러리", null)
        Text("샘플 토큰과 8개 공통 컴포넌트 · 승인 baseline 아님")
        HarnessButton("기본 버튼 · 긴 한국어 문구도 여러 줄로 표시합니다", {})
        HarnessButton("비활성 버튼", {}, enabled = false)
        HarnessButton("처리 중 버튼", {}, loading = true)
        HarnessTextField(value, { value = it }, "입력", error = "입력값을 확인해 주세요")
        HarnessCard { HarnessListRow("리스트 행", "긴 한국어 값과 큰 글씨를 위한 세로 배치") }
        HarnessLoading("로딩 중이에요")
        HarnessEmptyState("아직 내역이 없어요", "내역이 생기면 이곳에 표시돼요.")
        HarnessErrorState("입력 내용은 보관했어요. 연결 상태를 확인해 주세요.")
    }
}
