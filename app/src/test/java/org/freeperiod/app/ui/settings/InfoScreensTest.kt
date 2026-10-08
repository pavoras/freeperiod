package org.freeperiod.app.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class InfoScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun privacyStartsWithTheFullMedicalDisclaimer() {
        compose.setContent { FreePeriodTheme { PrivacyScreen({}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Not a medical device").assertIsDisplayed()
        compose.onNodeWithText("FreePeriod. is not a medical device.", substring = true).assertIsDisplayed()
        val disclaimer = compose.onNodeWithText("Not a medical device").fetchSemanticsNode().boundsInRoot
        val storage = compose.onNodeWithText("Your periods, day entries", substring = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(disclaimer.top < storage.top)
    }
}
