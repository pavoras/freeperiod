package org.freeperiod.app.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.app.ui.theme.LocalDaylight
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class IconScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun launcher_light() = capture(false)
    @Test fun launcher_dark() = capture(true)

    private fun capture(dark: Boolean) {
        compose.activity.setTheme(R.style.Theme_FreePeriod)
        val icon = compose.activity.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        compose.setContent {
            FreePeriodTheme(darkTheme = dark) {
                val bitmap = remember {
                    Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also {
                        icon.setBounds(0, 0, 192, 192)
                        icon.draw(Canvas(it))
                    }.asImageBitmap()
                }
                Box(Modifier.testTag("launcher").background(LocalDaylight.current.background).padding(16.dp)) {
                    Image(bitmap, stringResource(R.string.app_name), Modifier.size(with(LocalDensity.current) { 192.toDp() }))
                }
            }
        }
        compose.waitForIdle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) assertNotNull(icon.monochrome)
        compose.onNodeWithTag("launcher").captureRoboImage("src/test/screenshots/settings/launcher_${if (dark) "dark" else "light"}.png")
    }
}
