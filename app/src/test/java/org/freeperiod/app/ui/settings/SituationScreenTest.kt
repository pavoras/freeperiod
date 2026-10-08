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
    @Test fun methodOffersReminderWithoutEnablingIt() {
        val situation = mutableStateOf(Situation())
        var draft: Reminder? = null
        compose.setContent { FreePeriodTheme {
            SituationScreen(situation.value, LocalDate.of(2026, 4, 12), { situation.value = it }, {}, { draft = it })
        } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Mini-pill"))
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
