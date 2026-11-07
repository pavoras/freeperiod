package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.day.DayEntryActions
import org.freeperiod.app.ui.day.DayEntrySheet
import org.freeperiod.app.ui.day.DayEntryUiState
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pick one / Pick several for discharge: the switch in the editor and two values on one day. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class ChoiceTypeScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod); Locale.setDefault(Locale.US) }
    private val today = LocalDate.of(2026, 4, 12)

    @Test fun dischargeEditor_enLight() {
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false), overrides = listOf(UiOverride("category:discharge", false, -1)))
        compose.setContent { FreePeriodTheme(darkTheme = false) { CompositionLocalProvider(LocalInlineEditors provides true) {
            DayEntrySettingsScreen(data, today, {}, { _, _, _ -> }, {}, {}, {}, { _, _, _, _ -> })
        } } }
        compose.waitForIdle()
        compose.onNodeWithText("Discharge").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Rename Discharge"))
        compose.onNodeWithText("Rename Discharge").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pick several").assertIsSelected()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/dischargeEditor_enLight.png")
    }

    @Test fun dischargeSeveral_enLight() {
        val state = DayEntryUiState(today, today, loading = false,
            log = DayLog(today, discharge = setOf(Discharge.CREAMY, Discharge.UNUSUAL)),
            overrides = listOf(UiOverride("category:discharge", false, -1)))
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { DayEntrySheet(state, DayEntryActions(), {}, {}) } } }
        compose.waitForIdle()
        compose.onAllNodesWithText("Discharge").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Unusual").assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/settings/dischargeSeveral_enLight.png")
    }
}
