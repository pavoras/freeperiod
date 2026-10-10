package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SummaryTest {
    private val day = LocalDate.of(2026, 3, 1)

    @Test fun periodsTouchingTheRangeWithTheirLengthAndDaysToTheNextStart() {
        val periods = listOf(Period(3, day.plusDays(26), null), Period(1, day.minusDays(30), day.minusDays(26)),
            Period(2, day.minusDays(2), day.plusDays(2)))
        val facts = summary(periods, emptyList(), day, day.plusDays(27))
        assertEquals(listOf(SummaryPeriod(day.minusDays(2), day.plusDays(2), 5, 28), SummaryPeriod(day.plusDays(26), null, null, null)),
            facts.periods)
        assertEquals(listOf(0L, 1, 2, 26, 27).map { day.plusDays(it) }, facts.days.map { it.date })
        assertTrue(facts.days.all { it.periodDay })
    }

    @Test fun loggedDaysInRangeOnlyAndEmptyEntriesLeftOut() {
        val logs = listOf(DayLog(day.plusDays(5), mood = Mood.GOOD), DayLog(day.plusDays(6)), DayLog(day.minusDays(1), mood = Mood.LOW))
        val facts = summary(emptyList(), logs, day, day.plusDays(10))
        assertEquals(listOf(SummaryDay(day.plusDays(5), false, logs[0])), facts.days)
        assertTrue(facts.periods.isEmpty())
    }
}
