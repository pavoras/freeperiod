package org.freeperiod.app.ui.today

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.Period
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()
    private val initial = todayFixture("ongoing")
    private val before = Period(4, LocalDate.of(2026, 4, 10), null)
    private val receipt = PeriodEndReceipt(before, before.copy(end = LocalDate.of(2026, 4, 11)))

    @Test fun endingSavesImmediatelyThenUndoUsesTheSavedReceipt() {
        val state = mutableStateOf(initial)
        var ended: LocalDate? = null
        var undone: PeriodEndReceipt? = null
        compose.setContent { FreePeriodTheme {
            TodayScreen(state.value, {}, {
                ended = it
                state.value = state.value.copy(ongoingPeriodId = null, periodEndForToday = null, endSaved = receipt)
            }, {}, {}, {}, {}, onUndoEnd = { undone = it })
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Period ended").performClick()
        compose.waitForIdle()
        assertEquals(receipt.after.end, ended)
        compose.onNodeWithText("Saved: last bleeding day 11 April").assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(receipt, undone)
    }

    @Test fun endingAgainAfterUndoShowsANewSnackbar() {
        val state = mutableStateOf(initial.copy(endSaved = receipt))
        compose.setContent { FreePeriodTheme {
            TodayScreen(state.value, {}, {}, {}, {}, {}, {}, onUndoEnd = {
                state.value = state.value.copy(endSaved = null)
            })
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        compose.runOnIdle { state.value = state.value.copy(endSaved = receipt) }
        compose.waitForIdle()
        compose.onNodeWithText("Undo").assertExists()
    }

    @Test fun editOpensTheSavedBleedingDay() {
        var selected: LocalDate? = null
        compose.setContent { FreePeriodTheme {
            TodayScreen(initial.copy(endSaved = receipt), {}, {}, { selected = it }, {}, {}, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Edit").performClick()
        compose.waitForIdle()
        assertEquals(receipt.after.end, selected)
    }

    @Test fun aSuccessfulStartProducesExactlyOneHaptic() {
        val state = mutableStateOf(todayFixture("regular"))
        var haptics = 0
        val feedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics++ }
        }
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides feedback) {
            FreePeriodTheme {
                TodayScreen(state.value, { state.value = state.value.copy(startedPeriodId = 42) }, {}, {}, {}, {}, {})
            }
        } }
        compose.waitForIdle()
        assertEquals(0, haptics)
        compose.onNodeWithText("Period started").performClick()
        compose.waitForIdle()
        assertEquals(1, haptics)
        compose.runOnIdle { state.value = state.value.copy(writing = true) }
        compose.waitForIdle()
        assertEquals(1, haptics)
    }
}
