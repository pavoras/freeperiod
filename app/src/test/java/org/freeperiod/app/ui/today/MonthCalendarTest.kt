package org.freeperiod.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class MonthCalendarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun independentFlagsAreDescribedAndDayIsClickable() {
        val date = LocalDate.of(2026, 4, 12)
        var selected: LocalDate? = null
        compose.setContent {
            FreePeriodTheme {
                MonthCalendar(YearMonth.from(date), mapOf(date to DayMarks(true, true, true, true, higherChance = true)),
                    {}, { selected = it }, locale = Locale.US)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("12 April 2026, period day, period likely, higher chance of pregnancy, calendar estimate, logged, today")
            .assertHasClickAction().performClick()
        assertEquals(date, selected)
    }

    @Test fun jumpScrollsBackToToday() {
        val today = LocalDate.of(2026, 4, 12)
        val days = calendarMonths(YearMonth.from(today)).flatMap { month -> (1..month.lengthOfMonth()).map { month.atDay(it) } }
            .associateWith { DayMarks(false, false, false, it == today) }
        var jump by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { FreePeriodTheme { MonthCalendar(YearMonth.from(today).minusMonths(5), days, {}, {}, locale = Locale.US, jump = jump) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("12 April 2026, today").assertDoesNotExist()
        jump = 1
        compose.waitForIdle()
        compose.onNodeWithContentDescription("12 April 2026, today").assertIsDisplayed()
    }

    @Test fun usWeekStartsOnSunday() = assertFirstWeekday(Locale.US, "Sunday", "Monday")
    @Test fun germanWeekStartsOnMonday() = assertFirstWeekday(Locale.GERMANY, "Montag", "Dienstag")

    @OptIn(ExperimentalTestApi::class)
    @Test fun scrollingChangesMonth() {
        val month = YearMonth.of(2026, 4)
        val selected = mutableListOf<YearMonth>()
        compose.setContent {
            FreePeriodTheme { MonthCalendar(month, emptyMap(), { selected.add(it) }, {}, locale = Locale.US) }
        }
        compose.waitForIdle()
        val calendar = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        calendar.performCustomAccessibilityActionWithLabel("Previous month")
        compose.waitForIdle()
        compose.onNodeWithText("March 2026").assertIsDisplayed()
        calendar.performCustomAccessibilityActionWithLabel("Next month")
        compose.waitForIdle()
        assertEquals(listOf(month, month.minusMonths(1), month), selected)
    }

    private fun assertFirstWeekday(locale: Locale, first: String, second: String) {
        compose.setContent {
            FreePeriodTheme { MonthCalendar(YearMonth.of(2026, 4), emptyMap(), {}, {}, locale = locale) }
        }
        compose.waitForIdle()
        val left = compose.onNodeWithContentDescription(first).fetchSemanticsNode().boundsInRoot.left
        val next = compose.onNodeWithContentDescription(second).fetchSemanticsNode().boundsInRoot.left
        assertTrue(left < next)
    }
}
