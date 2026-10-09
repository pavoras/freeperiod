package org.freeperiod.app.ui.day

import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.app.R
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class DayEntrySheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun setHostTheme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    private val today = LocalDate.of(2026, 4, 12)

    @Test fun customCategoryShowsInEntry() {
        val category = CustomCategory(5, "Movement", "energetic", -1, false)
        var selected: Long? = null
        compose.setContent { FreePeriodTheme {
            DayEntrySheet(DayEntryUiState(today, today, tags = listOf(Tag(7, "Walk", categoryId = 5, iconKey = "energetic")),
                loading = false, customCategories = listOf(category)), DayEntryActions(tag = { selected = it }), {}, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Movement").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Walk").performClick()
        assertEquals(7L, selected)
    }

    @Test fun ttcOffersOvulationTestAndRegularDoesNot() {
        val state = mutableStateOf(DayEntryUiState(today, today, loading = false,
            situation = Situation(phase = LifePhase.TRYING_TO_CONCEIVE),
            overrides = listOf(UiOverride("category:ovulation_test", false, -1))))
        var selected: OvulationTest? = null
        compose.setContent { FreePeriodTheme {
            DayEntrySheet(state.value, DayEntryActions(ovulationTest = { selected = it }), {}, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Positive").performClick()
        assertEquals(OvulationTest.POSITIVE, selected)
        state.value = state.value.copy(situation = Situation(), overrides = emptyList())
        compose.waitForIdle()
        compose.onNodeWithText("Ovulation test").assertDoesNotExist()
    }

    @Test fun futureChipsAreDisabledAndOverlapIsText() {
        val state = DayEntryUiState(today.plusDays(1), today, loading = false, error = DayEntryError.OVERLAP)
        compose.setContent { FreePeriodTheme { DayEntrySheet(state, DayEntryActions(), {}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Light").assertIsNotEnabled()
        compose.onNodeWithText("This overlaps another period.").assertExists()
    }

    @Test fun flowChipHasLabelAndCallsAction() {
        var selected: FlowLevel? = null
        compose.setContent {
            FreePeriodTheme {
                DayEntrySheet(DayEntryUiState(today, today, loading = false),
                    DayEntryActions(flow = { selected = it }), {}, {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Light").performClick()
        assertEquals(FlowLevel.LIGHT, selected)
    }

    @Test fun noteAutosavesAndPreviews() {
        var saved = ""
        compose.setContent { FreePeriodTheme {
            DayEntrySheet(DayEntryUiState(today, today, loading = false),
                DayEntryActions(note = { saved = it }), {}, {})
        } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Note"))
        compose.onNodeWithText("Note").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("entry-note").performTextInput("A calm day with time outside and friends")
        compose.waitForIdle()
        assertEquals("A calm day with time outside and friends", saved)
        compose.onNodeWithText("Note").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("entry-note").assertDoesNotExist()
        compose.onNodeWithText("A calm day with time outside and").assertExists()
    }

    @Test fun openingTheNoteFocusesItAndScrollsToTheEnd() {
        compose.setContent { FreePeriodTheme {
            DayEntrySheet(DayEntryUiState(today, today, loading = false), DayEntryActions(), {}, {})
        } }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Note"))
        compose.onNodeWithText("Note").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("entry-note").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Clear day").assertIsDisplayed()
    }

    @Test fun selectedMoodHasSelectedSemanticsAndCanBeCleared() {
        val state = mutableStateOf(DayEntryUiState(today, today, log = DayLog(today, mood = Mood.GOOD), loading = false))
        compose.setContent { FreePeriodTheme {
            DayEntrySheet(state.value, DayEntryActions(mood = {
                state.value = state.value.copy(log = state.value.log.copy(mood = it))
            }), {}, {})
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Good").assertIsSelected().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Good").assertIsNotSelected()
    }

    @Test fun clearSnackbarUndoRestoresFullLog() {
        val log = DayLog(today, mood = Mood.GOOD, note = "Test", tagIds = setOf(1))
        val state = mutableStateOf(DayEntryUiState(today, today, log, listOf(Tag(1, "Travel")), loading = false))
        val events = Channel<DayEntryEvent>(Channel.UNLIMITED)
        val flow = events.receiveAsFlow()
        compose.setContent {
            FreePeriodTheme {
                DayEntrySheet(state.value, DayEntryActions(clear = {
                    state.value = state.value.copy(log = DayLog(today))
                    events.trySend(DayEntryEvent.Cleared(log))
                }, undo = { state.value = state.value.copy(log = it) }), {}, {}, flow)
            }
        }
        compose.waitForIdle()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText("Clear day"))
        compose.onNodeWithText("Clear day").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(log, state.value.log)
    }
}
