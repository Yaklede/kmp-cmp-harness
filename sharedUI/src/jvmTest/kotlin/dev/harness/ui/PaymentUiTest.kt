package dev.harness.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.harness.core.*
import dev.harness.ui.design.HarnessTheme
import dev.harness.ui.design.Tokens
import org.junit.Rule
import org.junit.Test
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class PaymentUiTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String) {
        val image = rule.onRoot().captureToImage()
        File("build/qa/$name.png").apply { parentFile.mkdirs() }
            .writeBytes(requireNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()).bytes)
    }

    @Test fun visibleButtonsDriveUnknownOutcomeToLookupAndSuccess() {
        rule.setContent { Box(Modifier.requiredSize(390.dp, 800.dp)) { App(scenario = MockScenario.ResponseLost) } }
        rule.onNodeWithTag("contract.continue").performClick()
        rule.onNodeWithTag("payment.memo").performScrollTo().performTextInput("retained memo")
        capture("desktop-confirm")
        rule.onNodeWithTag("payment.submit").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("payment.unknown").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("payment.submit").assertDoesNotExist()
        rule.onNodeWithTag("payment.lookup").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("payment.success").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("retained memo").assertExists()
        capture("desktop-success")
    }

    @Test fun smallViewportAndLargeTextDoNotClipAmountAndKeepCtaVisible() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.requiredSize(360.dp, 640.dp)) {
                    HarnessTheme { Surface(color = Tokens.background) { PaymentApp(PaymentState(screen = Screen.PaymentConfirm), {}) } }
                }
            }
        }
        rule.onNodeWithTag("payment.submit").assertIsDisplayed()
        val amount = rule.onNodeWithTag("payment.amount").performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        amount.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        assertEquals(1, layouts.single().lineCount)
        val ctaLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("payment.submit").assertTextContains("500,000원 결제하기")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(ctaLayouts) }
        assertFalse(ctaLayouts.single().hasVisualOverflow)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        capture("desktop-large-text")
    }

    @Test fun declinedPaymentReturnsToPreservedInput() {
        rule.setContent { Box(Modifier.requiredSize(390.dp, 800.dp)) { App(scenario = MockScenario.Declined) } }
        rule.onNodeWithTag("contract.continue").performClick()
        rule.onNodeWithTag("payment.memo").performScrollTo().performTextInput("keep me")
        rule.onNodeWithTag("payment.submit").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("payment.review").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("payment.review").performClick()
        rule.onNodeWithTag("payment.memo").performScrollTo().assertTextContains("keep me")
        rule.onNodeWithTag("payment.submit").assertIsEnabled()
    }
}
