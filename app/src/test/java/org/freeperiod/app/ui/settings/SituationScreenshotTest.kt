package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
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
    @Test fun situation_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { SituationScreen(Situation(), today, {}, {}, {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/situation/situation_enLight.png")
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun pillRhythm_deLight() {
        Locale.setDefault(Locale.GERMANY)
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { PillRhythmDialog(PillSchedule(today, 21, 7), today, {}, {}) } } }
        compose.waitForIdle()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/situation/pillRhythm_deLight.png")
    }
}
