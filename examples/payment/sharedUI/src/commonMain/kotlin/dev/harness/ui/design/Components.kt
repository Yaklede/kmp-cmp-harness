package dev.harness.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keep the monetary value together at narrow widths; accessibility scaling still applies. */
@Composable
fun HarnessAmount(value: String, modifier: Modifier = Modifier) {
    BasicText(value, modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.h3.copy(color = MaterialTheme.colors.onSurface), maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp, stepSize = 1.sp))
}

@Composable
fun HarnessButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false) {
    Button(onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            if (loading) stateDescription = "처리 중"
        }, contentPadding = PaddingValues(16.dp), elevation = ButtonDefaults.elevation(0.dp)) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(12.dp))
        }
        Text(label)
    }
}

@Composable
fun HarnessTextField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, error: String? = null) {
    Column {
        OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled,
            label = { Text(label) }, isError = error != null, modifier = modifier.fillMaxWidth().heightIn(min = Tokens.touch))
        if (error != null) Text(error, color = MaterialTheme.colors.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
fun HarnessListRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
        Text(label, color = Tokens.muted, style = MaterialTheme.typography.body2)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.body1)
    }
}

@Composable
fun HarnessCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), elevation = 0.dp, border = BorderStroke(1.dp, Tokens.border)) {
        Column(Modifier.padding(Tokens.large), content = content)
    }
}

@Composable
fun HarnessAppBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Tokens.touch).widthIn(min = Tokens.touch).testTag("navigation.back")) { Text("뒤로") }
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

@Composable
fun HarnessLoading(label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(label)
    }
}

@Composable
fun HarnessEmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Tokens.muted)
    }
}

@Composable
fun HarnessErrorState(message: String, modifier: Modifier = Modifier) {
    HarnessCard(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("결제를 완료하지 못했어요", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.error)
        Spacer(Modifier.height(8.dp))
        Text(message)
    }
}
