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
    @Test fun charts_enLight() = captureLevels("charts_enLight", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-night-xxhdpi")
    fun charts_deDark() = captureLevels("charts_deDark", Locale.GERMANY, dark = true)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun charts_deLight() = captureLevels("charts_deLight", Locale.GERMANY)
    @Test fun chartsYear_enLight() = captureLevels("chartsYear_enLight", Locale.US, range = "Year")
    @Test fun chartsWeek_enLight() = captureLevels("chartsWeek_enLight", Locale.US, range = "Week")
    private fun captureLevels(name: String, locale: Locale, dark: Boolean = false, range: String? = null) {
        Locale.setDefault(locale)
        val today = clock()
        val moods = listOf(Mood.GOOD, Mood.OKAY, Mood.LOW, Mood.LOW, Mood.OKAY, Mood.GREAT, Mood.GOOD)
        val logs = (0 until 360).filter { it % 9 != 4 }.map { back ->
            val date = today.minusDays(back.toLong())
            val cycleDay = back % 28
            DayLog(date, mood = moods[back % moods.size], pain = if (cycleDay in 20..24) Pain.entries[(back % 4)] else null,
                flow = if (cycleDay in 20..24) FlowLevel.entries[1 + back % 4] else if (back % 5 == 0) FlowLevel.NONE else null,
                appetite = Appetite.entries[(back / 3) % 3])
        }
        val periods = (0 until 13).map { Period(it + 1L, today.minusDays(24L + 28 * it), today.minusDays(20L + 28 * it)) }.reversed()
        // Appetite starts hidden; turned on, it gets its chart.
        val state = historyState(BackupData(periods = periods, dayLogs = logs, tags = emptyList(), settings = BackupSettings(null, false),
            overrides = listOf(UiOverride("category:appetite", false, 4))), today)
        compose.setContent { FreePeriodTheme(darkTheme = dark) { Surface { HistoryScreen(state, { _, _ -> }) } } }
        compose.waitForIdle()
        range?.let {
            compose.onNodeWithTag("history-list").performScrollToNode(hasText(it))
            compose.onNodeWithText(it).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(if (locale.language == "de") "Appetit" else "Appetite"))
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
