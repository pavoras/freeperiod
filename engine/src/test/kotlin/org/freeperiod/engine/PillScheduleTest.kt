package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PillScheduleTest {
    private fun d(value: String) = LocalDate.parse("2026-$value")
    private val pill = PillSchedule(d("01-01"), 21, 7)

    @Test fun breakDaysAcrossPacks() {
        assertEquals(d("01-22")..d("01-28"), pill.breakDaysAround(d("01-01")))
        assertEquals(d("01-22")..d("01-28"), pill.breakDaysAround(d("01-28")))
        assertEquals(d("02-19")..d("02-25"), pill.breakDaysAround(d("01-29")))
        assertEquals(1, pill.packDay(d("01-29")))
        assertEquals(28, pill.packDay(d("01-28")))
    }

    @Test fun continuousHasNoBreak() {
        assertNull(pill.copy(breakDays = 0).breakDaysAround(d("01-23")))
    }

    @Test fun futurePackStartHasNoBreakYet() {
        assertNull(pill.breakDaysAround(d("01-01").minusDays(1)))
        assertNull(pill.packDay(d("01-01").minusDays(1)))
    }

    @Test fun validatesRanges() {
        listOf(0, 366).forEach { n -> assertThrows(IllegalArgumentException::class.java) { pill.copy(activeDays = n) } }
        listOf(-1, 31).forEach { n -> assertThrows(IllegalArgumentException::class.java) { pill.copy(breakDays = n) } }
        PillSchedule(d("01-01"), 1, 0)
        PillSchedule(d("01-01"), 365, 30)
    }
}
