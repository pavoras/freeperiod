package org.freeperiod.app.ui.today

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.ui.components.FpNavBar
import org.freeperiod.app.ui.theme.FreePeriodTheme
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

    @Test fun regular_enLight() = capture("regular_enLight", "regular")
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun regular_deDark() = capture("regular_deDark", "regular", Locale.GERMANY, dark = true)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun regular_deLarge() = capture("regular_deLarge", "regular", Locale.GERMANY, dark = true, fontScale = 1.5f)
    @Test fun regular_enLarge() = capture("regular_enLarge", "regular", fontScale = 1.5f)
    @Test fun ongoingDay3_enLight() = capture("ongoingDay3_enLight", "ongoing")
    @Test fun rangePassed_enLight() = capture("rangePassed_enLight", "rangePassed")
    @Test fun scheduledBreak_enLight() = capture("scheduledBreak_enLight", "scheduledBreak")
    @Test fun menopause_enLight() = capture("menopause_enLight", "menopause")
    @Test fun empty_enLight() = capture("empty_enLight", "empty")

    @Test fun calendarPredicted_enLight() {
        Locale.setDefault(Locale.US)
        val state = todayFixture("regular", clock())
        compose.setContent { FreePeriodTheme(darkTheme = false) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("calendar")) {
                    MonthCalendar(state.month, state.days, {}, {}, locale = Locale.US)
                    TodayLegend(Modifier.padding(vertical = 8.dp))
                }
            }
        } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("28 April 2026, predicted period day").assertExists()
        compose.onNodeWithTag("calendar").captureRoboImage("src/test/screenshots/today/calendarPredicted_enLight.png")
    }

    private fun capture(name: String, scenario: String, locale: Locale = Locale.US, dark: Boolean = false, fontScale: Float = 1f) {
        Locale.setDefault(locale)
        val state = todayFixture(scenario, clock())
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(locale)); this.fontScale = fontScale }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FreePeriodTheme(darkTheme = dark) {
                    Scaffold(bottomBar = { FpNavBar("today", {}) }) { padding ->
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
