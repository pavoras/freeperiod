package org.freeperiod.app.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.freeperiod.app.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class AppearanceScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun choosingSwatchUpdatesSelectionAndCallsSetting() {
        var chosen = Accent.CORAL
        compose.setContent { FreePeriodTheme { AppearanceScreen(Accent.CORAL, { chosen = it }, {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Coral").assertIsSelected()
        compose.onNodeWithText("Ocean").performClick()
        compose.waitForIdle()
        assertEquals(Accent.OCEAN, chosen)
        compose.onNodeWithText("Ocean").assertIsSelected()
        compose.onNodeWithText("Coral").assertIsNotSelected()
    }
}
