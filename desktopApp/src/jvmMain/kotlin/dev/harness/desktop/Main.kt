package dev.harness.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.harness.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Harness · Mock payment",
        state = rememberWindowState(width = 420.dp, height = 820.dp)) { App() }
}
