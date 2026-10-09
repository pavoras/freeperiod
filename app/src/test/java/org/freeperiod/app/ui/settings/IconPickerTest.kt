package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpIcons
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.CustomCategory
import org.freeperiod.engine.backup.BackupData
import org.freeperiod.engine.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class IconPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun setup() { compose.activity.setTheme(R.style.Theme_FreePeriod) }

    @Test fun storedKeysKeepResolvingAndPickerKeysAreUnique() {
        val stored = listOf("tag", "sex", "discharge", "note", "today", "cramps", "headache", "backache", "bloating", "breast",
            "acne", "fatigue", "nausea", "cravings", "sleep", "digestion", "anxious", "irritable", "sad", "energetic", "warmth",
            "night", "focus", "joint", "tags", "history", "settings")
        assertTrue(stored.filterNot { it in FpIcons.byKey }.toString(), stored.all { it in FpIcons.byKey })
        assertEquals(FpIcons.itemKeys.size, FpIcons.itemKeys.toSet().size)
        assertTrue(FpIcons.itemKeys.size >= 100)
    }

    @Test fun pickingAnIconByItsNameSelectsIt() {
        var selected by mutableStateOf("tag")
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
            IconPicker(selected, { selected = it }) } } } }
        compose.onNodeWithContentDescription("Hot water bottle").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("hot_water_bottle", selected)
        compose.onNodeWithContentDescription("Hot water bottle").assertIsSelected()
        compose.onNodeWithContentDescription("Tag").assertIsNotSelected()
    }

    @Test fun newCategorySavesItsIcon() {
        var saved: Triple<String, String, CustomCategory?>? = null
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { CompositionLocalProvider(LocalInlineEditors provides true) {
            DayEntrySettingsScreen(data, LocalDate.of(2026, 4, 12), {}, { _, _, _ -> }, {}, { name, icon, existing -> saved = Triple(name, icon, existing) },
                {}, { _, _, _, _ -> })
        } } } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add category"))
        compose.onNodeWithText("Add category").performClick()
        compose.onNodeWithText("Category name").performTextInput("Sport")
        compose.onNodeWithContentDescription("Dumbbell").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(Triple("Sport", "dumbbell", null), saved)
    }

    @Test fun picker_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { CompositionLocalProvider(LocalInlineEditors provides true) {
            AddItemDialog({}, { _, _ -> })
        } } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/day-settings/iconPicker_enLight.png")
    }

    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun picker_deDark() {
        compose.setContent { FreePeriodTheme(darkTheme = true) { Surface { IconPicker("hot_water_bottle", {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/day-settings/iconPicker_deDark.png")
    }
}
