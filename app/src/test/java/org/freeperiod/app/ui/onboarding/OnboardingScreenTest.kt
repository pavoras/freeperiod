package org.freeperiod.app.ui.onboarding

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 4, 12)
    @Test fun welcomeShowsMedicalDisclaimerWithoutOpeningALink() {
        compose.setContent { FreePeriodTheme { OnboardingScreen(OnboardingUiState(today = today), OnboardingActions()) } }
        compose.waitForIdle()
        compose.onNodeWithText("Not a medical device").assertIsDisplayed()
        compose.onNodeWithText("These calendar estimates", substring = true).assertIsDisplayed()
    }
    @Test fun dragAcrossMonths() {
        val state = mutableStateOf(OnboardingUiState(page = 3, today = today))
        compose.setContent { FreePeriodTheme {
            OnboardingScreen(state.value, OnboardingActions(range = { from, to ->
                val start = minOf(from, to)
                val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= maxOf(from, to) }.toSet()
                state.value = state.value.copy(selectedDays = state.value.selectedDays + days)
            }))
        } }
        compose.waitForIdle()
        val calendar = compose.onNodeWithTag("past-periods-calendar")
        calendar.performScrollToIndex(8)
        compose.waitForIdle()
        // Keep the last March week and first April week inside the viewport together.
        val shift = calendar.fetchSemanticsNode().boundsInRoot.height / 4
        calendar.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, shift) }
        compose.waitForIdle()
        val origin = calendar.fetchSemanticsNode().boundsInRoot.topLeft
        val from = compose.onNodeWithTag("picker-day-2026-03-29").fetchSemanticsNode().boundsInRoot.center - origin
        val to = compose.onNodeWithTag("picker-day-2026-04-03").fetchSemanticsNode().boundsInRoot.center - origin
        // Dispatch the down before advancing the coroutine timeout. Event timestamps alone
        // do not run the long-press detector before the move in Robolectric.
        compose.mainClock.autoAdvance = false
        calendar.performTouchInput { down(from) }
        compose.mainClock.advanceTimeBy(700)
        calendar.performTouchInput { moveTo(to, delayMillis = 100); up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(6, state.value.selectedDays.size)
        assertEquals(LocalDate.of(2026, 3, 29), state.value.draftPeriods.single().start)
        assertEquals(LocalDate.of(2026, 4, 3), state.value.draftPeriods.single().end)
    }
    @Test fun importActionOnPastPeriodsStep() {
        var imported = false
        compose.setContent { FreePeriodTheme {
            OnboardingScreen(OnboardingUiState(page = 3, today = today), OnboardingActions(importPeriods = { imported = true }))
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Import from another app").performClick()
        assertTrue(imported)
    }
    @Test fun unknownStartsUncheckedAndLengthCanBeSkipped() {
        var skipped = false
        compose.setContent { FreePeriodTheme {
            OnboardingScreen(OnboardingUiState(page = 4, today = today), OnboardingActions(skip = { skipped = true }))
        } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("I don't know").assertIsOff()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Skip").performClick()
        assertTrue(skipped)
    }
}
