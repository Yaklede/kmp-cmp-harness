package dev.harness.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.harness.ui.App
import dev.harness.core.MockScenario

fun main(args: Array<String>) = application {
    val scenario = args.firstOrNull { it.startsWith("--scenario=") }?.substringAfter("=")?.let(MockScenario::valueOf) ?: MockScenario.Success
    Window(onCloseRequest = ::exitApplication, title = "Harness · Mock payment",
        state = rememberWindowState(width = 420.dp, height = 820.dp)) { App(scenario = scenario, gallery = "--gallery" in args) }
}
