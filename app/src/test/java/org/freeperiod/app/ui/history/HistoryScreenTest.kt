package org.freeperiod.app.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class HistoryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    @Test fun diaryShowsAllSexAndDischargeValuesInItemOrderIncludingOwnItems() {
        val today = LocalDate.of(2026, 4, 12)
        val data = BackupData(periods = emptyList(), settings = BackupSettings(null, false),
            dayLogs = listOf(DayLog(today, sex = setOf(Sex.UNPROTECTED, Sex.PROTECTED),
                discharge = setOf(Discharge.CREAMY, Discharge.UNUSUAL), tagIds = setOf(7))),
            tags = listOf(Tag(7, "Own", archived = true, categoryId = 1)),
            customCategories = listOf(CustomCategory(1, "Discharge", "builtin:discharge", 0, false, singleChoice = true)),
            overrides = listOf(UiOverride("tag:7", false, -2), UiOverride("item:discharge:UNUSUAL", true, -1)))
        compose.setContent { FreePeriodTheme { HistoryScreen(historyState(data, today), { _, _ -> }) } }
        compose.waitForIdle()
        compose.onNodeWithText("Entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Discharge: Own, Unusual, Creamy").assertIsDisplayed()
        compose.onNodeWithText("Sex: Protected, Unprotected").assertIsDisplayed()
    }
    @Test fun hiddenAndArchivedSymptomsUseNamesAndItemOrder() {
        val today = LocalDate.of(2026, 4, 12)
        val state = HistoryUiState(today, loading = false, symptomCounts = mapOf(Symptom.CRAMPS to 1),
            ownSymptomCounts = mapOf(1L to 2), tags = listOf(Tag(1, "Own note", archived = true, categoryId = 1, iconKey = "leaf")),
            customCategories = listOf(CustomCategory(1, "Own symptoms", "builtin:symptoms", 0, false)),
            overrides = listOf(UiOverride("category:symptoms", true, 0, "My notes", "leaf"),
                UiOverride("item:symptoms:CRAMPS", true, 1, "My cramps", "calm"), UiOverride("tag:1", true, 0)))
        compose.setContent { FreePeriodTheme { HistoryScreen(state, { _, _ -> }) } }
        compose.waitForIdle()
        compose.onNodeWithText("My notes").assertIsDisplayed()
        val own = compose.onNodeWithText("Own note: 2 days").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val builtIn = compose.onNodeWithText("My cramps: 1 day").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(own.top < builtIn.top)
    }
    @Test fun barTapOpensSheet() {
        val today = LocalDate.of(2026, 4, 12)
        val state = historyState(BackupData(periods = listOf(Period(1, today.minusDays(35), today.minusDays(31)),
            Period(2, today.minusDays(7), today.minusDays(3))), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false)), today)
        compose.setContent { FreePeriodTheme { HistoryScreen(state, { _, _ -> }) } }
        compose.waitForIdle()
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("cycle-chart"))
        compose.onNodeWithTag("history-bar-1").performScrollTo().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cycle details").assertExists()
        compose.onNodeWithText("28-day cycle").assertExists()
        compose.onNodeWithText("Use for predictions").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cycle details").assertDoesNotExist()
    }

    @Test fun entriesTabListsDaysAndOpensTheTappedOne() {
        val today = LocalDate.of(2026, 4, 12)
        val note = "A long walk by the river, then tea with a friend. ".repeat(6).trim()
        val state = historyState(BackupData(periods = listOf(Period(1, today.minusDays(3), today.minusDays(1))),
            dayLogs = listOf(DayLog(today.minusDays(2), mood = Mood.GOOD, flow = FlowLevel.MEDIUM, pain = Pain.MILD,
                symptoms = setOf(Symptom.CRAMPS), note = note), DayLog(today.minusDays(40), note = "Quiet day")),
            tags = emptyList(), settings = BackupSettings(null, false)), today)
        val opened = mutableListOf<LocalDate>()
        compose.setContent { FreePeriodTheme { HistoryScreen(state, { _, _ -> }, onOpenDay = { opened += it }) } }
        compose.onNodeWithText("Entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("April 2026").assertIsDisplayed()
        compose.onNodeWithText("Flow: Medium").assertIsDisplayed()
        compose.onNodeWithText("Pain: Mild").assertIsDisplayed()
        compose.onNodeWithText("Cramps").assertIsDisplayed()
        compose.onNodeWithTag("diary-list").performScrollToNode(hasText("Quiet day"))
        compose.onNodeWithText("March 2026").assertIsDisplayed()
        compose.onNodeWithText("Quiet day").performClick()
        Assert.assertEquals(listOf(today.minusDays(40)), opened)
    }
}
