package org.freeperiod.app.ui.onboarding

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
        calendar.performScrollToIndex(4)
        compose.waitForIdle()
        val origin = calendar.fetchSemanticsNode().boundsInRoot.topLeft
        val from = compose.onNodeWithTag("picker-day-2026-03-29").fetchSemanticsNode().boundsInRoot.center - origin
        val to = compose.onNodeWithTag("picker-day-2026-04-03").fetchSemanticsNode().boundsInRoot.center - origin
        calendar.performTouchInput { down(from); advanceEventTime(700); moveTo(to); up() }
        compose.waitForIdle()
        assertEquals(6, state.value.selectedDays.size)
        assertEquals(LocalDate.of(2026, 3, 29), state.value.draftPeriods.single().start)
        assertEquals(LocalDate.of(2026, 4, 3), state.value.draftPeriods.single().end)
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
