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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class HistoryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
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
}
