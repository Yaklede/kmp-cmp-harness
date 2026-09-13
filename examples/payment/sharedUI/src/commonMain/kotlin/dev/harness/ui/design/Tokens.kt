package dev.harness.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Tokens {
    val background = Color(0xFFF5F5EF)
    val ink = Color(0xFF172C28)
    val primary = Color(0xFF176A56)
    val muted = Color(0xFF536660)
    val border = Color(0xFFD7DFD9)
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val touch = 48.dp
}

@Composable
fun HarnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = lightColors(primary = Tokens.primary, onPrimary = Color.White,
            background = Tokens.background, surface = Color.White, onSurface = Tokens.ink,
            onBackground = Tokens.ink, error = Color(0xFFAC3028)),
        typography = Typography(
            h3 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
            h4 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 28.sp),
            body1 = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            body2 = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp)),
        content = content,
    )
}
