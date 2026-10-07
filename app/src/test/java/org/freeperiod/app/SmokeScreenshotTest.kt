package org.freeperiod.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class SmokeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun placeholder() {
        compose.setContent { FreePeriodTheme { Placeholder() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/placeholder.png")
    }
}
