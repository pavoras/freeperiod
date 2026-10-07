package org.freeperiod.app.ui.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class PrivacyScreenshotTest : SettingsScreenshotFixture() {
    @Test fun privacy_enLight() = capture("privacy/privacy_enLight", "Privacy policy") { PrivacyScreen({}, {}) }
}
