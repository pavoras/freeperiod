package org.freeperiod.app.ui.settings

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

abstract class SettingsScreenshotFixture {
    @get:Rule val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()
    protected val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }
    protected fun capture(path: String, title: String, locale: Locale = Locale.US,
        dark: Boolean = false, content: @Composable () -> Unit) {
        Locale.setDefault(locale)
        compose.setContent { FreePeriodTheme(darkTheme = dark) { Surface { content() } } }
        compose.waitForIdle()
        compose.onNodeWithText(title).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/$path.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SettingsScreenshotTest : SettingsScreenshotFixture() {
    @Test fun main_enLight() = capture("settings/main_enLight", "Settings") {
        SettingsScreen(SettingsUiState(28, loading = false, lockCanEnable = true), SettingsActions())
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun main_deDark() = capture("settings/main_deDark", "Einstellungen", Locale.GERMANY, dark = true) {
        SettingsScreen(SettingsUiState(28, loading = false, lockCanEnable = true), SettingsActions())
    }
}
