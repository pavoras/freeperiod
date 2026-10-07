package org.freeperiod.app.lock

import org.freeperiod.app.ui.settings.SettingsScreenshotFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class LockScreenshotTest : SettingsScreenshotFixture() {
    @Test fun locked_enLight() = capture("lock/locked_enLight", "Unlock to open FreePeriod.") { LockedScreen({}) }
}
