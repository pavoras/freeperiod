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
    @Test fun welcome_enLight() = capture("onboarding/welcome_enLight", "Welcome to FreePeriod.") {
        OnboardingScreen(OnboardingUiState(today = clock()), OnboardingActions())
    }
    @Test fun phase_enLight() = capture("onboarding/phase_enLight", "Life phase") {
        OnboardingScreen(OnboardingUiState(page = 1, today = clock()), OnboardingActions())
    }
    @Test fun pastPeriods_enLight() = capture("onboarding/pastPeriods_enLight", "Past periods") {
        OnboardingScreen(pastPeriods(), OnboardingActions())
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun pastPeriods_deDark() = capture("onboarding/pastPeriods_deDark", "Bisherige Perioden", Locale.GERMANY, dark = true) {
        OnboardingScreen(pastPeriods(), OnboardingActions())
    }
    @Test fun reminders_enLight() = capture("onboarding/reminders_enLight", "Reminders") {
        OnboardingScreen(OnboardingUiState(page = 5, today = clock()), OnboardingActions())
    }
    private fun pastPeriods(): OnboardingUiState {
        val first = LocalDate.of(2026, 3, 29)
        return OnboardingUiState(page = 3, today = clock(), selectedDays = generateSequence(first) { it.plusDays(1) }.take(6).toSet())
    }
}
