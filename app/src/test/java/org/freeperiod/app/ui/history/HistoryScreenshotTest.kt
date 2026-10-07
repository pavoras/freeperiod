package org.freeperiod.app.ui.history

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()
    private val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test fun withCycles_enLight() = capture("withCycles_enLight", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun withCycles_deDark() = capture("withCycles_deDark", Locale.GERMANY, dark = true)
    @Test fun empty_enLight() = capture("empty_enLight", Locale.US, empty = true)

    private fun capture(name: String, locale: Locale, dark: Boolean = false, empty: Boolean = false) {
        Locale.setDefault(locale)
        val starts = listOf("2026-01-03", "2026-01-31", "2026-02-28", "2026-03-14")
        val periods = if (empty) emptyList() else starts.mapIndexed { index, start ->
            val date = LocalDate.parse(start)
            Period(index + 1L, date, date.plusDays(4))
        }
        val logs = if (empty) emptyList() else listOf(
            DayLog(LocalDate.of(2026, 2, 2), symptoms = setOf(Symptom.CRAMPS, Symptom.HEADACHE)),
            DayLog(LocalDate.of(2026, 3, 1), symptoms = setOf(Symptom.CRAMPS)))
        val state = historyState(BackupData(periods = periods, dayLogs = logs, tags = emptyList(),
            settings = BackupSettings(null, false)), clock())
        compose.setContent {
            FreePeriodTheme(darkTheme = dark) { Surface { HistoryScreen(state, { _, _ -> }) } }
        }
        compose.waitForIdle()
        compose.onNodeWithText(if (locale.language == "de") "Verlauf" else "History").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/history/$name.png")
    }
}
