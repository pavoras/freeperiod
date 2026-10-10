package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import com.github.takahirom.roborazzi.captureRoboImage
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SituationScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val today = LocalDate.of(2026, 4, 12)
    @Before fun setup() { compose.activity.setTheme(R.style.Theme_FreePeriod); Locale.setDefault(Locale.US) }
    @After fun reset() { Locale.setDefault(originalLocale) }
    @Test fun situationFertile_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { SituationScreen(Situation(), today, {}, {}, {}) } } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasContentDescription("Show days with a higher chance of pregnancy"))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Show days with a higher chance of pregnancy").assertIsOn()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/situationFertile_enLight.png")
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun alsoTrackMenopause_deLight() {
        Locale.setDefault(Locale.GERMANY)
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface {
            SituationScreen(Situation(phase = LifePhase.MENOPAUSE, painDiary = true), today, {}, {}, {}, TrackingExtras(medication = true))
        } } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Medikamente"))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Schmerztagebuch").assertIsOn()
        compose.onRoot().captureRoboImage("src/test/screenshots/situation/alsoTrackMenopause_deLight.png")
    }
    @Test fun situation_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { SituationScreen(Situation(), today, {}, {}, {}) } } }
        compose.waitForIdle()
        compose.onNodeWithText("Regular tracking").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onRoot().captureRoboImage("src/test/screenshots/situation/situation_enLight.png")
    }
    @Test fun methodPicker_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { SituationScreen(Situation(), today, {}, {}, {}) } } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Method"))
        compose.onNodeWithText("Method").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("None") and hasAnyAncestor(isDialog())).assertIsSelected()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/situation/methodPicker_enLight.png")
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun situation_deLargeDark() {
        Locale.setDefault(Locale.GERMANY)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                FreePeriodTheme(darkTheme = true) { Surface { SituationScreen(Situation(), today, {}, {}, {}) } }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/situation/situation_deLargeDark.png")
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun pillRhythm_deLight() {
        Locale.setDefault(Locale.GERMANY)
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { CompositionLocalProvider(LocalInlineEditors provides true) { PillRhythmDialog(PillSchedule(today, 21, 7), today, {}, {}) } } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/situation/pillRhythm_deLight.png")
    }
}
