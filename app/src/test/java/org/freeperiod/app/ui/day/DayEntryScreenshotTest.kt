package org.freeperiod.app.ui.day

import androidx.compose.material3.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.app.R
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class DayEntryScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun setHostTheme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    private val originalLocale = Locale.getDefault()
    private val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test fun filled_enLight() = capture("filled_enLight", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun filled_deDark() = capture("filled_deDark", Locale.GERMANY, dark = true)
    @Test fun empty_enLarge() = capture("empty_enLarge", Locale.US, empty = true, fontScale = 1.5f)

    private fun capture(name: String, locale: Locale, dark: Boolean = false, empty: Boolean = false, fontScale: Float = 1f) {
        Locale.setDefault(locale)
        val today = clock()
        val log = DayLog(today, FlowLevel.MEDIUM, Mood.GOOD, setOf(Symptom.CRAMPS, Symptom.FATIGUE),
            Pain.MILD, Sex.NONE, Discharge.CREAMY, if (locale.language == "de") "Ein ruhiger Tag." else "A quiet day.", setOf(1))
        val data = BackupData(periods = if (empty) emptyList() else listOf(Period(1, today.minusDays(2), null)),
            dayLogs = if (empty) emptyList() else listOf(log),
            tags = if (empty) emptyList() else listOf(Tag(1, if (locale.language == "de") "Bewegung" else "Exercise")),
            settings = BackupSettings(null, false))
        val state = dayEntryState(data, today, today)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FreePeriodTheme(darkTheme = dark) {
                    Surface { DayEntrySheet(state, DayEntryActions(), {}, {}) }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(if (locale.language == "de") "Tageseintrag" else "Day entry").assertExists()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/day/$name.png")
    }
}
