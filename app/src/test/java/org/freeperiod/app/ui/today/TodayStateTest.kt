package org.freeperiod.app.ui.today

import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.Assert.*
import org.junit.Test

class TodayStateTest {
    private val today = LocalDate.of(2026, 4, 12)
    @Test fun ongoingFixtureSharesDatesAcrossCardTimelineAndCalendar() {
        val state = todayFixture("ongoing", today)
        val timeline = requireNotNull(state.timeline)
        assertEquals(3, state.periodDay)
        assertEquals(today.minusDays(1), state.periodEndForToday)
        assertEquals(LocalDate.of(2026, 4, 10)..today, timeline.recordedPeriod)
        assertEquals(LocalDate.of(2026, 4, 13)..LocalDate.of(2026, 4, 14), timeline.expectedPeriodRest)
        assertEquals(LocalDate.of(2026, 5, 7)..LocalDate.of(2026, 5, 9), timeline.bracket)
        assertEquals(LocalDate.of(2026, 5, 9), timeline.axisEnd)
        assertTrue(state.days.getValue(today).period)
    }

    @Test fun regularFixtureMatchesDraftDates() {
        val state = todayFixture("regular", today)
        assertEquals(12, state.cycleDay)
        assertEquals(LocalDate.of(2026, 4, 28)..LocalDate.of(2026, 4, 30), state.timeline!!.bracket)
        assertTrue(state.days.getValue(today).logged)
        assertFalse(state.days.getValue(today).period)
    }

    @Test fun scheduledBreakUsesPackAxis() {
        val state = todayFixture("scheduledBreak", today)
        assertEquals(12, (state.prediction as PredictionState.ScheduledBreak).packDay)
        assertEquals(BracketKind.SCHEDULED_BREAK, state.timeline!!.bracketKind)
        assertEquals(LocalDate.of(2026, 4, 28), state.timeline.axisEnd)
        assertTrue(state.days.getValue(LocalDate.of(2026, 4, 22)).predicted)
    }

    @Test fun menopauseShowsCompleteMonthsWithoutTimelineOrPredictions() {
        val state = todayFixture("menopause", today)
        assertEquals(7, (state.prediction as PredictionState.Menopause).fullMonthsSinceLastEnd)
        assertNull(state.timeline)
        assertTrue(state.days.values.none { it.predicted })
    }

    @Test fun noFertilityMarksAreAddedEvenWhenStoredPreferenceIsEnabled() {
        val start = LocalDate.of(2026, 4, 1)
        val periods = (0..3).map { i -> start.minusDays(i * 28L).let { Period(i + 1L, it, it.plusDays(4)) } }
        val data = BackupData(periods = periods, dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
        val regular = todayState(data, today)
        val optedIn = todayState(data, today, situation = Situation(fertileWindowEnabled = true))
        assertEquals(regular.days, optedIn.days)
    }
}
