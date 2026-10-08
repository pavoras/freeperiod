package org.freeperiod.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.day.*
import org.freeperiod.app.ui.settings.AppearanceScreen
import org.freeperiod.engine.*
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
class AccentScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val clock: () -> LocalDate = { LocalDate.of(2026, 4, 12) }
    @Before fun setup() { Locale.setDefault(Locale.US); compose.activity.setTheme(R.style.Theme_FreePeriod) }
    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test fun settingsAppearance_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) {
            AppearanceScreen(Accent.CORAL, {}, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Accent colour").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/settingsAppearance_enLight.png")
    }

    @Test fun dayEntry_coral_enLight() = day(Accent.CORAL)
    @Test fun dayEntry_plum_enLight() = day(Accent.PLUM)
    @Test fun dayEntry_sage_enLight() = day(Accent.SAGE)
    @Test fun dayEntry_ocean_enLight() = day(Accent.OCEAN)
    @Test fun dayEntry_ochre_enLight() = day(Accent.OCHRE)
    @Test fun dayEntry_ink_enLight() = day(Accent.INK)

    private fun day(accent: Accent) {
        val today = clock()
        val state = DayEntryUiState(today, today, log = DayLog(today, flow = FlowLevel.NONE,
            mood = Mood.GOOD, pain = Pain.MILD, symptoms = setOf(Symptom.CRAMPS, Symptom.BLOATING),
            sex = Sex.NONE, discharge = Discharge.CREAMY, note = "A quiet afternoon outside.", tagIds = setOf(1)),
            tags = listOf(Tag(1, "Walk")), loading = false)
        compose.setContent { FreePeriodTheme(darkTheme = false, accent = accent) {
            Surface { DayEntrySheet(state, DayEntryActions(), {}, {}) }
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Good").assertIsSelected()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/day/dayEntry_${accent.name.lowercase(Locale.ROOT)}_enLight.png")
    }
}
