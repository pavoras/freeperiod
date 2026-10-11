package org.freeperiod.app.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale
import org.freeperiod.app.support.SupportProduct
import org.freeperiod.app.support.SupportState
import org.freeperiod.app.support.supportState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SupportScreenshotTest : SettingsScreenshotFixture() {
    // Play formats the price per country; the owner's products are 5 € and 10 €.
    private val offer = SupportState(listOf(SupportProduct("support_small", "€5.00"), SupportProduct("support_large", "€10.00")))
    private val offerDe = SupportState(listOf(SupportProduct("support_small", "5,00 €"), SupportProduct("support_large", "10,00 €")))

    @Test fun support_enLight() = capture("settings/support_enLight", "Support FreePeriod") { SupportScreen(offer, {}, {}) }

    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun supportThanks_deDark() = capture("settings/supportThanks_deDark", "FreePeriod unterstützen", Locale.GERMANY, dark = true) {
        SupportScreen(offerDe.copy(supported = true), {}, {})
    }

    @Test fun buyingPassesTheProductAndSupportersCanSupportAgain() {
        var bought: String? = null
        var state by androidx.compose.runtime.mutableStateOf(offer)
        compose.setContent { org.freeperiod.app.ui.theme.FreePeriodTheme { SupportScreen(state, {}, { bought = it }) } }
        compose.onNodeWithText("Support with €10.00").performClick()
        assertEquals("support_large", bought)
        state = offer.copy(supported = true)
        compose.onNodeWithText("Thank you for supporting FreePeriod!").assertExists()
        compose.onNodeWithText("Support with €5.00").performClick()
        assertEquals("support_small", bought)
        state = offer.copy(pending = true)
        compose.onAllNodesWithText("Support with", substring = true).assertCountEquals(0)
    }

    @Test fun aPurchaseCelebratesOnceButReopeningThePageDoesNot() {
        var state by androidx.compose.runtime.mutableStateOf(offer.copy(supported = true, celebration = 1))
        var shown by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { org.freeperiod.app.ui.theme.FreePeriodTheme { if (shown) SupportScreen(state, {}, {}) } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithTag("thank-you-hearts").assertCountEquals(0)
        set { state = state.copy(celebration = 2) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("thank-you-hearts").assertExists()
        compose.mainClock.advanceTimeBy(3000)
        compose.onAllNodesWithTag("thank-you-hearts").assertCountEquals(0)
    }

    /** A state change on the UI thread, applied right away while the test clock is paused. */
    private fun set(change: () -> Unit) = compose.runOnUiThread {
        change()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    @Test fun thankYouHearts_enLight() {
        var state by androidx.compose.runtime.mutableStateOf(offer)
        compose.setContent { org.freeperiod.app.ui.theme.FreePeriodTheme { androidx.compose.material3.Surface { SupportScreen(state, {}, {}) } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        set { state = offer.copy(supported = true, celebration = 1) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/thankYouHearts_enLight.png")
    }

    @Test fun theSettingsRowAppearsOnlyWithSomethingToShow() {
        var state by androidx.compose.runtime.mutableStateOf(SupportState())
        compose.setContent { org.freeperiod.app.ui.theme.FreePeriodTheme {
            SettingsScreen(SettingsUiState(28, loading = false), SettingsActions(), support = state)
        } }
        compose.onAllNodesWithText("Support FreePeriod").assertCountEquals(0)
        state = offer
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Support FreePeriod"))
        compose.onNodeWithText("Support FreePeriod").assertExists()
    }

    @Test fun storeProductsKeepTheirOrderAndUnknownOnesAreLeftOut() {
        assertEquals(listOf("support_small", "support_large"),
            supportState(mapOf("support_large" to "9", "support_small" to "4", "other" to "1"), false, false).products.map { it.id })
        assertFalse(supportState(emptyMap(), supported = true, pending = true).pending)
        assertFalse(SupportState().shown)
    }
}
