package org.freeperiod.app.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale
import org.freeperiod.app.support.SupportProduct
import org.freeperiod.app.support.SupportState
import org.freeperiod.app.support.supportState
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
    private val offer = SupportState(listOf(SupportProduct("support_small", "€4.99"), SupportProduct("support_large", "€9.99")))

    @Test fun support_enLight() = capture("settings/support_enLight", "Support FreePeriod") { SupportScreen(offer, {}, {}) }

    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun supportThanks_deDark() = capture("settings/supportThanks_deDark", "FreePeriod unterstützen", Locale.GERMANY, dark = true) {
        SupportScreen(offer.copy(supported = true), {}, {})
    }

    @Test fun buyingPassesTheProductAndThanksHideTheButtons() {
        var bought: String? = null
        var state by androidx.compose.runtime.mutableStateOf(offer)
        compose.setContent { org.freeperiod.app.ui.theme.FreePeriodTheme { SupportScreen(state, {}, { bought = it }) } }
        compose.onNodeWithText("Support with €9.99").performClick()
        assertEquals("support_large", bought)
        state = offer.copy(supported = true)
        compose.onNodeWithText("Thank you for supporting FreePeriod!").assertExists()
        compose.onAllNodesWithText("Support with", substring = true).assertCountEquals(0)
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
