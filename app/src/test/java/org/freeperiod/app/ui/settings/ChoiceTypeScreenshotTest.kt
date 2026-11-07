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
import org.junit.After
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
    private val originalLocale = Locale.getDefault()
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }
    private val today = LocalDate.of(2026, 4, 12)

    @Test fun dischargeEditor_enLight() = editor("enLight", "en-US")

    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun dischargeEditor_ptBRLight() = editor("ptBRLight", "pt-BR")
    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun dischargeSeveral_ptBRLight() = several("ptBRLight", "pt-BR")
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun dischargeEditor_esLight() = editor("esLight", "es-ES")
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun dischargeSeveral_esLight() = several("esLight", "es-ES")
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun dischargeEditor_idLight() = editor("idLight", "id-ID")
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun dischargeSeveral_idLight() = several("idLight", "id-ID")
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun dischargeEditor_trLight() = editor("trLight", "tr-TR")
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun dischargeSeveral_trLight() = several("trLight", "tr-TR")
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun dischargeEditor_frLight() = editor("frLight", "fr-FR")
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun dischargeSeveral_frLight() = several("frLight", "fr-FR")

    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun categoryTypes_ptBRLight() = categoryTypes("ptBRLight", "pt-BR")
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun categoryTypes_esLight() = categoryTypes("esLight", "es-ES")
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun categoryTypes_idLight() = categoryTypes("idLight", "id-ID")
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun categoryTypes_trLight() = categoryTypes("trLight", "tr-TR")
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun categoryTypes_frLight() = categoryTypes("frLight", "fr-FR")

    // All three choices share a narrow row in the custom-category editor.
    private fun categoryTypes(suffix: String, tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        val context = compose.activity
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false))
        compose.setContent { FreePeriodTheme(darkTheme = false) { CompositionLocalProvider(LocalInlineEditors provides true) {
            DayEntrySettingsScreen(data, today, {}, { _, _, _ -> }, {}, {}, {}, { _, _, _, _ -> })
        } } }
        compose.waitForIdle()
        val add = context.getString(R.string.add_category)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(add))
        compose.onNodeWithText(add).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.pick_several)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.pick_one)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.category_count)).assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/categoryTypes_$suffix.png")
    }

    private fun editor(suffix: String, tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        val context = compose.activity
        val discharge = context.getString(R.string.entry_discharge)
        val rename = context.getString(R.string.rename_entry_description, discharge)
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false), overrides = listOf(UiOverride("category:discharge", false, -1)))
        compose.setContent { FreePeriodTheme(darkTheme = false) { CompositionLocalProvider(LocalInlineEditors provides true) {
            DayEntrySettingsScreen(data, today, {}, { _, _, _ -> }, {}, {}, {}, { _, _, _, _ -> })
        } } }
        compose.waitForIdle()
        compose.onNodeWithText(discharge).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(rename))
        compose.onNodeWithText(rename).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.pick_several)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.pick_one)).assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/dischargeEditor_$suffix.png")
    }

    @Test fun dischargeSeveral_enLight() = several("enLight", "en-US")

    private fun several(suffix: String, tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        val context = compose.activity
        val state = DayEntryUiState(today, today, loading = false,
            log = DayLog(today, discharge = setOf(Discharge.CREAMY, Discharge.UNUSUAL)),
            overrides = listOf(UiOverride("category:discharge", false, -1)))
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { DayEntrySheet(state, DayEntryActions(), {}, {}) } } }
        compose.waitForIdle()
        compose.onAllNodesWithText(context.getString(R.string.entry_discharge)).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.discharge_unusual)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.discharge_creamy)).assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/settings/dischargeSeveral_$suffix.png")
    }
}
