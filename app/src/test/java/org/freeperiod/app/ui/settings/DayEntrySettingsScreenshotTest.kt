package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class DayEntrySettingsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val today = LocalDate.of(2026, 4, 12)
    @Before fun setup() { compose.activity.setTheme(R.style.Theme_FreePeriod); Locale.setDefault(Locale.US) }
    @After fun reset() { Locale.setDefault(originalLocale) }
    @Test fun main_enLight() {
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { DayEntrySettingsScreen(data, today, {}, { _, _, _ -> }, {}, { _, _, _ -> }, {}, { _, _, _, _ -> }) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/day-settings/main_enLight.png")
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun main_deLargeDark() {
        Locale.setDefault(Locale.GERMANY)
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                FreePeriodTheme(darkTheme = true) { Surface { DayEntrySettingsScreen(data, today, {}, { _, _, _ -> }, {}, { _, _, _ -> }, {}, { _, _, _, _ -> }) } }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/day-settings/main_deLargeDark.png")
    }
    @Test fun addItem_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { CompositionLocalProvider(LocalInlineEditors provides true) { AddItemDialog({}, { _, _ -> }) } } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/day-settings/addItem_enLight.png")
    }
}
