package org.freeperiod.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.lock.LockedScreen
import org.freeperiod.app.ui.components.FpNavBar
import org.freeperiod.app.ui.day.DayEntryActions
import org.freeperiod.app.ui.day.DayEntrySheet
import org.freeperiod.app.ui.day.dayEntryState
import org.freeperiod.app.ui.history.HistoryScreen
import org.freeperiod.app.ui.history.historyState
import org.freeperiod.app.ui.onboarding.OnboardingActions
import org.freeperiod.app.ui.onboarding.OnboardingScreen
import org.freeperiod.app.ui.onboarding.OnboardingUiState
import org.freeperiod.app.ui.settings.*
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.app.ui.today.TodayScreen
import org.freeperiod.app.ui.today.todayFixture
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData
import org.freeperiod.engine.backup.BackupSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Add one test per language: its resource qualifier in @Config (so dialogs and sheets, which open their own window,
 * are translated too) and tour("BCP-47 tag"). Record/compare is controlled by Roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class LocaleTourScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }

    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun ptBR() = tour("pt-BR")

    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun es() = tour("es-ES")

    private enum class Screen { TODAY, RING, DAY, HISTORY, SETTINGS, IMPORT, ONBOARDING, LOCK, DISCLAIMER }

    private fun tour(languageTag: String) {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val context = compose.activity
        val today = clock()
        // Same dates and state builders as Today/DayEntry/HistoryScreenshotTest.
        val periods = listOf("2025-11-10", "2025-12-08", "2026-01-05", "2026-02-02", "2026-02-16", "2026-03-16")
            .mapIndexed { index, start -> LocalDate.parse(start).let { Period(index + 1L, it, it.plusDays(4)) } }
        val logs = listOf(
            DayLog(today, flow = FlowLevel.LIGHT, mood = Mood.GOOD, pain = Pain.MILD, symptoms = setOf(Symptom.CRAMPS)),
            DayLog(today.minusDays(1), flow = FlowLevel.HEAVY, mood = Mood.LOW, pain = Pain.MODERATE,
                symptoms = setOf(Symptom.FATIGUE, Symptom.HEADACHE, Symptom.BLOATING)),
            DayLog(today.minusDays(2), flow = FlowLevel.MEDIUM, mood = Mood.OKAY),
            DayLog(today.minusDays(9), mood = Mood.GREAT, discharge = Discharge.CREAMY))
        val data = BackupData(periods = periods, dayLogs = logs, tags = emptyList(), settings = BackupSettings(null, false))
        val history = historyState(data, today)
        val day = dayEntryState(data.copy(periods = listOf(Period(1, today.minusDays(2), null))), today, today)
        var screen by mutableStateOf(Screen.TODAY)
        compose.setContent {
            FreePeriodTheme(darkTheme = false) {
                Surface {
                    // Dispose remembered page/dialog/scroll state when moving to a new screen.
                    key(screen) {
                        when (screen) {
                            Screen.TODAY, Screen.RING, Screen.DISCLAIMER -> {
                                val state = todayFixture(if (screen == Screen.DISCLAIMER) "fertile" else "regular", today)
                                    .copy(ring = screen == Screen.RING)
                                Scaffold(bottomBar = { FpNavBar("today", {}) }) { padding ->
                                    TodayScreen(state, {}, {}, {}, {}, {}, {}, Modifier.padding(padding))
                                }
                            }
                            Screen.DAY -> DayEntrySheet(day, DayEntryActions(), {}, {})
                            Screen.HISTORY -> HistoryScreen(history, { _, _ -> })
                            Screen.SETTINGS -> SettingsScreen(SettingsUiState(28, loading = false, lockCanEnable = true), SettingsActions())
                            Screen.IMPORT -> ImportScreen(ImportUiState(periods = periods.takeLast(3)), {}, {}, {}, {}, {})
                            Screen.ONBOARDING -> OnboardingScreen(OnboardingUiState(today = today), OnboardingActions())
                            Screen.LOCK -> LockedScreen({})
                        }
                    }
                }
            }
        }

        fun capture(name: String, title: Int, dialog: Boolean = false) {
            compose.waitForIdle()
            if (dialog) compose.onNode(isDialog()).assertExists() else compose.onNodeWithText(context.getString(title)).assertIsDisplayed()
            val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
            node.captureRoboImage("src/test/screenshots/locale/${name}_$languageTag.png")
        }
        fun show(next: Screen) { compose.runOnIdle { screen = next }; compose.waitForIdle() }

        capture("today", R.string.nav_settings)
        show(Screen.RING)
        capture("cycle_ring", R.string.nav_settings)
        show(Screen.DAY)
        capture("day_entry", R.string.entry_mood, dialog = true)
        show(Screen.HISTORY)
        capture("history_overview", R.string.history_tab_overview)
        compose.onNodeWithText(context.getString(R.string.history_tab_entries)).performClick()
        capture("history_entries", R.string.history_tab_entries)
        show(Screen.SETTINGS)
        capture("settings", R.string.nav_settings)
        show(Screen.IMPORT)
        capture("import", R.string.import_csv)
        show(Screen.ONBOARDING)
        capture("onboarding", R.string.onboarding_welcome)
        show(Screen.LOCK)
        capture("lock", R.string.locked_message)
        show(Screen.DISCLAIMER)
        compose.onNodeWithContentDescription(context.getString(R.string.pregnancy_chance_info)).performClick()
        capture("medical_disclaimer", R.string.medical_disclaimer_title, dialog = true)
    }
}
