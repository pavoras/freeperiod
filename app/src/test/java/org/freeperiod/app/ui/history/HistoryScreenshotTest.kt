package org.freeperiod.app.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.Accent
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
    fun chartsSage_deLight() = captureLevels("chartsSage_deLight", Locale.GERMANY, accent = Accent.SAGE)
    @Test fun chartsSage_enLight() = captureLevels("chartsSage_enLight", Locale.US, accent = Accent.SAGE)
    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun chartsSage_ptBRLight() = captureLevels("chartsSage_ptBRLight", Locale.forLanguageTag("pt-BR"), accent = Accent.SAGE)
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun chartsSage_esLight() = captureLevels("chartsSage_esLight", Locale.forLanguageTag("es-ES"), accent = Accent.SAGE)
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun chartsSage_idLight() = captureLevels("chartsSage_idLight", Locale.forLanguageTag("id-ID"), accent = Accent.SAGE)
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun chartsSage_trLight() = captureLevels("chartsSage_trLight", Locale.forLanguageTag("tr-TR"), accent = Accent.SAGE)
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun chartsSage_frLight() = captureLevels("chartsSage_frLight", Locale.forLanguageTag("fr-FR"), accent = Accent.SAGE)
    @Test fun chartsYear_enLight() = captureLevels("chartsYear_enLight", Locale.US, range = "Year")
    @Test fun chartsWeek_enLight() = captureLevels("chartsWeek_enLight", Locale.US, range = "Week")
    @Test fun diary_enLight() = captureDiary("diary_enLight", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-night-xxhdpi")
    fun diary_deDark() = captureDiary("diary_deDark", Locale.GERMANY, dark = true)
    @Test fun diaryEmpty_enLight() = captureDiary("diaryEmpty_enLight", Locale.US, empty = true)
    /** Needs real text measuring (native graphics) to know that a note is longer than three lines. */
    @Test fun diaryLongNoteOpensWithMore() {
        val today = clock()
        val state = historyState(BackupData(periods = emptyList(), dayLogs = listOf(DayLog(today,
            note = "A long walk by the river, then tea with a friend. ".repeat(6).trim())), tags = emptyList(),
            settings = BackupSettings(null, false)), today)
        compose.setContent { FreePeriodTheme { Surface { HistoryScreen(state, { _, _ -> }) } } }
        compose.onNodeWithText("Entries").performClick()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Less").assertIsDisplayed()
    }
    private fun captureDiary(name: String, locale: Locale, dark: Boolean = false, empty: Boolean = false) {
        Locale.setDefault(locale)
        val today = clock()
        val de = locale.language == "de"
        val walk = Tag(1, if (de) "Spaziergang" else "Long walk", iconKey = "leaf")
        val logs = if (empty) emptyList() else listOf(
            DayLog(today, mood = Mood.GOOD, flow = FlowLevel.LIGHT, pain = Pain.MILD, symptoms = setOf(Symptom.CRAMPS),
                note = if (de) "Ruhiger Morgen, am Nachmittag Krämpfe. Wärmflasche hat geholfen, früh ins Bett."
                    else "Slow morning, cramps in the afternoon. The hot water bottle helped, early night."),
            DayLog(today.minusDays(1), mood = Mood.LOW, flow = FlowLevel.HEAVY, pain = Pain.MODERATE,
                symptoms = setOf(Symptom.FATIGUE, Symptom.HEADACHE, Symptom.BLOATING)),
            DayLog(today.minusDays(2), mood = Mood.OKAY, flow = FlowLevel.MEDIUM, tagIds = setOf(1),
                note = (if (de) "Lange Runde durch den Park mit Mia. Danach Pasta gekocht und einen Film geschaut. "
                    else "Long loop through the park with Mia. Cooked pasta afterwards and watched a film. ").repeat(3).trim()),
            DayLog(today.minusDays(9), mood = Mood.GREAT, discharge = setOf(Discharge.CREAMY), tagIds = setOf(1)),
            DayLog(today.minusDays(14), note = if (de) "Neue Laufschuhe gekauft." else "Bought new running shoes."))
        val periods = listOf(Period(1, today.minusDays(2), null))
        val state = historyState(BackupData(periods = periods, dayLogs = logs, tags = listOf(walk), settings = BackupSettings(null, false)), today)
        compose.setContent { FreePeriodTheme(darkTheme = dark) { Surface { HistoryScreen(state, { _, _ -> }) } } }
        compose.onNodeWithText(if (de) "Einträge" else "Entries").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/history/$name.png")
    }
    private fun captureLevels(name: String, locale: Locale, dark: Boolean = false, range: String? = null, accent: Accent = Accent.CORAL) {
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
        compose.setContent { FreePeriodTheme(darkTheme = dark, accent = accent) { Surface { HistoryScreen(state, { _, _ -> }) } } }
        compose.waitForIdle()
        range?.let {
            compose.onNodeWithTag("history-list").performScrollToNode(hasText(it))
            compose.onNodeWithText(it).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(compose.activity.getString(R.string.entry_appetite)))
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
