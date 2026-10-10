package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SummaryScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    @Before fun setup() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    @After fun reset() { Locale.setDefault(originalLocale) }

    @Test fun summary_enLight() {
        Locale.setDefault(Locale.US)
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { SummaryScreen(false, null, false, { _, _ -> }, {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/summary_enLight.png")
    }

    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-night-xxhdpi")
    fun summary_deDark() {
        Locale.setDefault(Locale.GERMANY)
        compose.setContent { FreePeriodTheme(darkTheme = true) { Surface { SummaryScreen(false, R.string.summary_saved, false, { _, _ -> }, {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/summary_deDark.png")
    }
}
