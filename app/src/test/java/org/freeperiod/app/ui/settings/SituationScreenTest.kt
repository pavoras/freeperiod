package org.freeperiod.app.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import java.time.LocalDate
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SituationScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun estimateSwitchCanBeReenabledAndIsHiddenForIncompatibleSituations() {
        val situation = mutableStateOf(Situation())
        val label = "Show days with a higher chance of pregnancy"
        compose.setContent { FreePeriodTheme {
            SituationScreen(situation.value, LocalDate.of(2026, 4, 12), { situation.value = it }, {}, {})
        } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasContentDescription(label))
        compose.onNodeWithContentDescription(label).assertIsOn().performClick()
        compose.waitForIdle()
        assertFalse(situation.value.fertileWindowEnabled)
        compose.onNodeWithContentDescription(label).assertIsOff().performClick()
        compose.waitForIdle()
        assertTrue(situation.value.fertileWindowEnabled)
        compose.runOnIdle { situation.value = situation.value.copy(method = Method.IUD_HORMONAL) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(label).assertDoesNotExist()
        compose.runOnIdle { situation.value = situation.value.copy(method = Method.NONE, phase = LifePhase.PREGNANT) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(label).assertDoesNotExist()
    }

    @Test fun methodOffersReminderWithoutEnablingIt() {
        val situation = mutableStateOf(Situation())
        var draft: Reminder? = null
        compose.setContent { FreePeriodTheme {
            SituationScreen(situation.value, LocalDate.of(2026, 4, 12), { situation.value = it }, {}, { draft = it })
        } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Method"))
        compose.onNodeWithText("Method").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mini-pill").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add a method reminder?").assertExists()
        assertNull(draft)
        compose.onNodeWithText("Add reminder").performClick()
        compose.waitForIdle()
        assertFalse(requireNotNull(draft).enabled)
        assertEquals(Recurrence.Daily, draft!!.recurrence)
    }
}
