package org.freeperiod.app.ui.onboarding

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.ui.settings.SettingsScreenshotFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class OnboardingScreenshotTest : SettingsScreenshotFixture() {
    @Test fun page1_enLight() = capture("onboarding/page1_enLight", "Welcome to FreePeriod.") {
        OnboardingScreen(OnboardingUiState(today = clock()), OnboardingActions())
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun page2_deDark() = capture("onboarding/page2_deDark", "Dein Zyklus", Locale.GERMANY, dark = true) {
        OnboardingScreen(OnboardingUiState(page = 1, today = clock(), start = LocalDate.of(2026, 4, 3),
            hasEnded = true, end = LocalDate.of(2026, 4, 7), unknown = false, typicalLength = "28"), OnboardingActions())
    }
    @Test fun page3_enLight() = capture("onboarding/page3_enLight", "Reminders") {
        OnboardingScreen(OnboardingUiState(page = 2, today = clock()), OnboardingActions())
    }
}
