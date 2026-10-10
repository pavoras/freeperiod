package org.freeperiod.app.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }
    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }
    @Test fun chart_enLight() = capture("chart_enLight", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun chart_deDark() = capture("chart_deDark", Locale.GERMANY, dark = true)
    @Test fun sheet_enLight() = capture("sheet_enLight", Locale.US, sheet = true)
    @Test fun empty_enLight() = capture("empty_enLight", Locale.US, empty = true)
    @Test fun menopause_enLight() = capture("menopause_enLight", Locale.US, menopause = true)
    @Test fun moodAndPain_enLight() = captureLevels("moodAndPain_enLight", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-night-xxhdpi")
    fun moodAndPain_deDark() = captureLevels("moodAndPain_deDark", Locale.GERMANY, dark = true)
    private fun captureLevels(name: String, locale: Locale, dark: Boolean = false) {
        Locale.setDefault(locale)
        val today = clock()
        val moods = listOf(Mood.GOOD, Mood.OKAY, Mood.LOW, Mood.LOW, Mood.OKAY, Mood.GREAT, Mood.GOOD)
        val logs = (0 until 50).filter { it % 9 != 4 }.map { back ->
            val date = today.minusDays(back.toLong())
            DayLog(date, mood = moods[back % moods.size], pain = if (back % 28 in 20..24) Pain.entries[(back % 4)] else null)
        }
        val periods = listOf(Period(1, today.minusDays(52), today.minusDays(48)), Period(2, today.minusDays(24), today.minusDays(20)))
        val state = historyState(BackupData(periods = periods, dayLogs = logs, tags = emptyList(), settings = BackupSettings(null, false)), today)
        compose.setContent { FreePeriodTheme(darkTheme = dark) { Surface { HistoryScreen(state, { _, _ -> }) } } }
        compose.waitForIdle()
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(if (locale.language == "de") "Schmerzen" else "Pain"))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/history/$name.png")
    }
    private fun capture(name: String, locale: Locale, dark: Boolean = false, empty: Boolean = false, sheet: Boolean = false, menopause: Boolean = false) {
        Locale.setDefault(locale)
        val starts = listOf("2025-11-10", "2025-12-08", "2026-01-05", "2026-02-02", "2026-02-16", "2026-03-16")
        val periods = if (empty) emptyList() else starts.mapIndexed { index, start ->
            val date = LocalDate.parse(start); Period(index + 1L, date, date.plusDays(4))
        }
        val logs = if (empty) emptyList() else listOf(
            DayLog(LocalDate.of(2026, 2, 3), symptoms = setOf(Symptom.CRAMPS, Symptom.HEADACHE)),
            DayLog(LocalDate.of(2026, 3, 17), symptoms = setOf(Symptom.CRAMPS)),
            DayLog(clock().minusDays(2), symptoms = setOf(Symptom.HOT_FLUSHES, Symptom.NIGHT_SWEATS)),
            DayLog(clock().minusDays(1), symptoms = setOf(Symptom.HOT_FLUSHES, Symptom.BRAIN_FOG)))
        val state = historyState(BackupData(periods = periods, dayLogs = logs, tags = emptyList(), settings = BackupSettings(null, false),
            situation = Situation(phase = if (menopause) LifePhase.MENOPAUSE else LifePhase.REGULAR)), clock())
        compose.setContent { FreePeriodTheme(darkTheme = dark) { Surface { HistoryScreen(state, { _, _ -> }) } } }
        compose.waitForIdle()
        if (sheet) {
            compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("cycle-chart"))
            compose.onNodeWithTag("history-bar-4").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/history/$name.png")
        } else compose.onRoot().captureRoboImage("src/test/screenshots/history/$name.png")
    }
}
