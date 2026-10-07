package org.freeperiod.app.ui.today

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.freeperiod.app.ui.nav.NavigationIcons
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
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
class TodayScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()
    private val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }

    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test fun empty_enLight() = capture("empty_enLight", "empty", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun empty_deDark() = capture("empty_deDark", "empty", Locale.GERMANY, dark = true)
    @Test fun empty_enLarge() = capture("empty_enLarge", "empty", Locale.US, fontScale = 1.5f)
    @Test fun regular_enLight() = capture("regular_enLight", "regular", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun regular_deDark() = capture("regular_deDark", "regular", Locale.GERMANY, dark = true)
    @Test fun regular_enLarge() = capture("regular_enLarge", "regular", Locale.US, fontScale = 1.5f)
    @Test fun rangePassed_enLight() = capture("rangePassed_enLight", "rangePassed", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun rangePassed_deDark() = capture("rangePassed_deDark", "rangePassed", Locale.GERMANY, dark = true)
    @Test fun rangePassed_enLarge() = capture("rangePassed_enLarge", "rangePassed", Locale.US, fontScale = 1.5f)
    @Test fun ongoing_enLight() = capture("ongoing_enLight", "ongoing", Locale.US)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun ongoing_deDark() = capture("ongoing_deDark", "ongoing", Locale.GERMANY, dark = true)
    @Test fun ongoing_enLarge() = capture("ongoing_enLarge", "ongoing", Locale.US, fontScale = 1.5f)

    private fun capture(name: String, scenario: String, locale: Locale, dark: Boolean = false, fontScale: Float = 1f) {
        Locale.setDefault(locale)
        val today = clock()
        val periods = when (scenario) {
            "empty" -> emptyList()
            "ongoing" -> listOf(Period(1, today.minusDays(10), null))
            else -> {
                val latest = if (scenario == "rangePassed") today.minusDays(35) else today.minusDays(11)
                (0..3).map { index ->
                    val start = latest.minusDays((3 - index) * 28L)
                    Period(index + 1L, start, start.plusDays(4))
                }
            }
        }
        val prediction = predict(periods, PredictionSettings(), today)
        val predicted = predictedDays(prediction)
        val month = YearMonth.from(today)
        val logged = if (scenario == "empty") emptySet() else setOf(today.minusDays(2), today)
        val state = TodayUiState(today = today, prediction = prediction,
            cycleDay = PeriodRules.cycleDay(periods, today),
            endQuestion = PeriodRules.endQuestion(periods, today),
            ongoingPeriodId = periods.lastOrNull { it.end == null }?.id,
            days = (1..month.lengthOfMonth()).associate { number ->
                val date = month.atDay(number)
                date to DayMarks(PeriodRules.periodOn(periods, date, today) != null,
                    predicted?.contains(date) == true, date in logged, date == today)
            }, loading = false)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(locale))
                this.fontScale = fontScale
            }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density, fontScale)) {
                FreePeriodTheme(darkTheme = dark) {
                    Scaffold(bottomBar = {
                        NavigationBar {
                            listOf(org.freeperiod.app.R.string.nav_today to NavigationIcons.Today,
                                org.freeperiod.app.R.string.nav_history to NavigationIcons.History,
                                org.freeperiod.app.R.string.nav_settings to NavigationIcons.Settings).forEachIndexed { index, (label, icon) ->
                                NavigationBarItem(selected = index == 0, onClick = {}, icon = { Icon(icon, null) },
                                    label = { Text(androidx.compose.ui.res.stringResource(label)) })
                            }
                        }
                    }) { padding ->
                        TodayScreen(state, {}, {}, {}, {}, {}, {}, Modifier.padding(padding))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(if (locale.language == "de") "Einstellungen" else "Settings").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/today/$name.png")
    }
}
