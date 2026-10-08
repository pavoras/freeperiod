package org.freeperiod.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecurrenceTest {
    private fun d(value: String) = LocalDate.parse(value)
    private val anchor = d("2026-01-31")

    @Test fun dailyInclusiveAndExclusive() {
        assertEquals(anchor, Recurrence.Daily.nextDate(anchor, true))
        assertEquals(anchor.plusDays(1), Recurrence.Daily.nextDate(anchor, false))
    }

    @Test fun everyThreeDaysAndFutureAnchor() {
        val recurrence = Recurrence.EveryNDays(3, anchor)
        assertEquals(anchor, recurrence.nextDate(anchor.minusDays(10), false))
        assertEquals(anchor.plusDays(3), recurrence.nextDate(anchor, false))
        assertEquals(anchor.plusDays(3), recurrence.nextDate(anchor.plusDays(3), true))
        assertEquals(anchor.plusDays(6), recurrence.nextDate(anchor.plusDays(3), false))
    }

    @Test fun weeklyMonday() {
        val recurrence = Recurrence.Weekly(DayOfWeek.MONDAY)
        assertEquals(d("2026-02-02"), recurrence.nextDate(anchor, true))
        assertEquals(d("2026-02-09"), recurrence.nextDate(d("2026-02-02"), false))
        assertEquals(d("2026-02-02"), recurrence.nextDate(d("2026-02-02"), true))
    }

    @Test fun monthlyDay31ClampsWithoutDrift() {
        val recurrence = Recurrence.MonthlyOnDay(31)
        assertEquals(d("2026-02-28"), recurrence.nextDate(anchor, false))
        assertEquals(d("2024-02-29"), recurrence.nextDate(d("2024-02-01"), true))
        assertEquals(d("2026-03-31"), recurrence.nextDate(d("2026-02-28"), false))
    }

    @Test fun everyThreeMonthsKeepsAnchor() {
        val recurrence = Recurrence.EveryNMonths(3, anchor)
        assertEquals(anchor, recurrence.nextDate(anchor.minusDays(1), false))
        assertEquals(d("2026-04-30"), recurrence.nextDate(anchor, false))
        assertEquals(d("2026-04-30"), recurrence.nextDate(d("2026-04-30"), true))
        assertEquals(d("2026-07-31"), recurrence.nextDate(d("2026-04-30"), false))
        assertEquals(d("2026-10-31"), recurrence.nextDate(d("2026-07-31"), false))
        assertEquals(d("2050-01-31"), recurrence.nextDate(d("2049-12-31"), false))
    }

    @Test fun oncePastReturnsNull() {
        val recurrence = Recurrence.Once(anchor)
        assertEquals(anchor, recurrence.nextDate(anchor, true))
        assertEquals(anchor, recurrence.nextDate(anchor.minusDays(1), false))
        assertNull(recurrence.nextDate(anchor, false))
    }

    @Test fun validatesBounds() {
        listOf(0, 1000).forEach { n ->
            assertThrows(IllegalArgumentException::class.java) { Recurrence.EveryNDays(n, anchor) }
            assertThrows(IllegalArgumentException::class.java) { Recurrence.EveryNMonths(n, anchor) }
        }
        listOf(0, 32).forEach { n -> assertThrows(IllegalArgumentException::class.java) { Recurrence.MonthlyOnDay(n) } }
        Recurrence.EveryNDays(999, anchor)
        Recurrence.EveryNMonths(999, anchor)
    }
}
