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
import org.freeperiod.app.ui.theme.Accent
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
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun ongoingDay3_deDark() = capture("ongoingDay3_deDark", "ongoing", Locale.GERMANY, dark = true)
    @Test fun rangePassed_enLight() = capture("rangePassed_enLight", "rangePassed")
    @Test fun scheduledBreak_enLight() = capture("scheduledBreak_enLight", "scheduledBreak")
    @Test fun menopause_enLight() = capture("menopause_enLight", "menopause")
    @Test fun empty_enLight() = capture("empty_enLight", "empty")
    @Test fun fertile_enLight() = capture("fertile_enLight", "fertile")
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun fertile_deDark() = capture("fertile_deDark", "fertile", Locale.GERMANY, dark = true)
    @Test fun disclaimer_enLight() = capture("disclaimer_enLight", "fertile", disclaimer = true)
    @Test fun ring_enLight() = capture("ring_enLight", "regular", ring = true)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun ring_deDark() = capture("ring_deDark", "fertile", Locale.GERMANY, dark = true, ring = true)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun ring_deLarge() = capture("ring_deLarge", "ongoing", Locale.GERMANY, fontScale = 1.5f, ring = true)
    @Test fun ringScheduledBreak_enLight() = capture("ringScheduledBreak_enLight", "scheduledBreak", ring = true)
    @Test fun ringMenopause_enLight() = capture("ringMenopause_enLight", "menopause", ring = true)
    // Store screenshots: light Coral next to dark Ink, the ring in light Plum, in both languages.
    @Test fun regularInk_enDark() = capture("regularInk_enDark", "regular", dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun regularInk_deDark() = capture("regularInk_deDark", "regular", Locale.GERMANY, dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun regular_deLight() = capture("regular_deLight", "regular", Locale.GERMANY)
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun ringPlum_deLight() = capture("ringPlum_deLight", "regular", Locale.GERMANY, ring = true, accent = Accent.PLUM)
    @Test fun ringPlum_enLight() = capture("ringPlum_enLight", "regular", ring = true, accent = Accent.PLUM)

    // Localized store references; filenames match tools/store-boards.py.
    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun regular_ptBRLight() = capture("regular_ptBRLight", "regular", Locale.forLanguageTag("pt-BR"))
    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun regularInk_ptBRDark() = capture("regularInk_ptBRDark", "regular", Locale.forLanguageTag("pt-BR"), dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun ringPlum_ptBRLight() = capture("ringPlum_ptBRLight", "regular", Locale.forLanguageTag("pt-BR"), ring = true, accent = Accent.PLUM)
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun regular_esLight() = capture("regular_esLight", "regular", Locale.forLanguageTag("es-ES"))
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun regularInk_esDark() = capture("regularInk_esDark", "regular", Locale.forLanguageTag("es-ES"), dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "es-rES-w360dp-h800dp-xxhdpi")
    fun ringPlum_esLight() = capture("ringPlum_esLight", "regular", Locale.forLanguageTag("es-ES"), ring = true, accent = Accent.PLUM)
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun regular_idLight() = capture("regular_idLight", "regular", Locale.forLanguageTag("id-ID"))
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun regularInk_idDark() = capture("regularInk_idDark", "regular", Locale.forLanguageTag("id-ID"), dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "in-rID-w360dp-h800dp-xxhdpi")
    fun ringPlum_idLight() = capture("ringPlum_idLight", "regular", Locale.forLanguageTag("id-ID"), ring = true, accent = Accent.PLUM)
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun regular_trLight() = capture("regular_trLight", "regular", Locale.forLanguageTag("tr-TR"))
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun regularInk_trDark() = capture("regularInk_trDark", "regular", Locale.forLanguageTag("tr-TR"), dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "tr-rTR-w360dp-h800dp-xxhdpi")
    fun ringPlum_trLight() = capture("ringPlum_trLight", "regular", Locale.forLanguageTag("tr-TR"), ring = true, accent = Accent.PLUM)
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun regular_frLight() = capture("regular_frLight", "regular", Locale.forLanguageTag("fr-FR"))
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun regularInk_frDark() = capture("regularInk_frDark", "regular", Locale.forLanguageTag("fr-FR"), dark = true, accent = Accent.INK)
    @Test @Config(sdk = [35], qualifiers = "fr-rFR-w360dp-h800dp-xxhdpi")
    fun ringPlum_frLight() = capture("ringPlum_frLight", "regular", Locale.forLanguageTag("fr-FR"), ring = true, accent = Accent.PLUM)
    @Test @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-xxhdpi")
    fun ring_ptBRLarge() = capture("ring_ptBRLarge", "regular", Locale.forLanguageTag("pt-BR"), fontScale = 1.5f, ring = true)

    // The round of 2026-11-10: folded card, entering a past period, the home question, the year view and the tips.
    @Test fun folded_enLight() = capture("folded_enLight", "fertile") { it.copy(cardFolded = true) }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun picking_deLight() = capture("picking_deLight", "regular", Locale.GERMANY) { it.copy(pick = RangePick(LocalDate.of(2026, 4, 8))) }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun question_deLight() = capture("question_deLight", "regular", Locale.GERMANY) {
        it.copy(question = HomeQuestion.PERIOD_STARTED, questionPeriodId = 1) }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun year_deDark() = capture("year_deDark", "fertile", Locale.GERMANY, dark = true) { it.copy(yearView = true) }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun tips_deLight() = capture("tips_deLight", "regular", Locale.GERMANY) { it.copy(showTips = true) }

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
        compose.onNodeWithContentDescription("28 April 2026, period possible").assertExists()
        compose.onNodeWithContentDescription("29 April 2026, period likely").assertExists()
        compose.onNodeWithTag("calendar").captureRoboImage("src/test/screenshots/today/calendarPredicted_enLight.png")
    }

    private fun capture(name: String, scenario: String, locale: Locale = Locale.US, dark: Boolean = false,
        fontScale: Float = 1f, disclaimer: Boolean = false, ring: Boolean = false, accent: Accent = Accent.CORAL,
        change: (TodayUiState) -> TodayUiState = { it }) {
        Locale.setDefault(locale)
        val state = change(todayFixture(scenario, clock()).copy(ring = ring))
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(locale)); this.fontScale = fontScale }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FreePeriodTheme(darkTheme = dark, accent = accent) {
                    Scaffold(bottomBar = { FpNavBar("today", {}) }) { padding ->
                        TodayScreen(state, {}, {}, {}, {}, {}, {}, Modifier.padding(padding))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(org.robolectric.RuntimeEnvironment.getApplication()
            .getString(org.freeperiod.app.R.string.nav_settings)).assertExists()
        if (disclaimer) {
            compose.onNodeWithContentDescription("About this calendar estimate").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Not a medical device").assertIsDisplayed()
            compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/today/$name.png")
            return
        }
        if (state.showTips) {
            compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/today/$name.png")
            return
        }
        if (scenario == "fertile" && !ring && !state.yearView) compose.onNodeWithContentDescription(
            if (locale.language == "de") "9 April 2026, erhöhte Chance auf Schwangerschaft, Kalenderschätzung"
            else "9 April 2026, higher chance of pregnancy, calendar estimate").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/today/$name.png")
    }
}
