package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class ChoiceTypeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }

    private fun editor(field: String, label: String, onChoice: (String, Boolean, String) -> Unit = { _, _, _ -> }) {
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false), overrides = listOf(UiOverride("category:$field", false, -1)))
        compose.setContent { FreePeriodTheme { CompositionLocalProvider(LocalInlineEditors provides true) {
            DayEntrySettingsScreen(data, LocalDate.of(2026, 4, 12), {}, { _, _, _ -> }, {}, {}, {},
                { _, _, _, _ -> }, onChoice = onChoice)
        } } }
        compose.waitForIdle()
        compose.onNodeWithText(label).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Rename $label"))
        compose.onNodeWithText("Rename $label").performClick()
        compose.waitForIdle()
    }

    @Test fun dischargeEditorDefaultsToSeveralAndSavesOneWithoutCount() {
        var saved: Triple<String, Boolean, String>? = null
        editor("discharge", "Discharge") { field, one, name -> saved = Triple(field, one, name) }
        compose.onNodeWithText("Pick several").assertIsSelected()
        compose.onNodeWithText("Count").assertDoesNotExist()
        compose.onNodeWithText("Pick one").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(Triple("discharge", true, "Discharge"), saved)
    }

    @Test fun sexEditorDefaultsToOneAndSavesSeveralWithoutCount() {
        var saved: Boolean? = null
        editor("sex", "Sex") { _, one, _ -> saved = one }
        compose.onNodeWithText("Pick one").assertIsSelected()
        compose.onNodeWithText("Count").assertDoesNotExist()
        compose.onNodeWithText("Pick several").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(false, saved)
    }

    @Test fun moodEditorHasNoChoiceType() {
        editor("mood", "Mood")
        compose.onNodeWithText("Pick one").assertDoesNotExist()
        compose.onNodeWithText("Pick several").assertDoesNotExist()
        compose.onNodeWithText("Count").assertDoesNotExist()
    }
}
