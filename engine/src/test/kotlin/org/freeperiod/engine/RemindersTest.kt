package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RemindersTest {
    private val earliest = LocalDate.of(2026, 4, 22)
    private val state = PredictionState.Range(earliest, earliest.plusDays(2), Basis.HISTORY, 3, 5, 26)

    @Test fun reminderFiresTwoDaysBefore() {
        assertTrue(periodReminderDue(state, 1, 2, earliest.minusDays(2), null))
        assertFalse(periodReminderDue(state, 1, 2, earliest.minusDays(3), null))
        assertFalse(periodReminderDue(state, 1, 2, earliest.plusDays(1), null))
    }

    @Test fun reminderNotRepeatedForSameRange() {
        assertFalse(periodReminderDue(state, 1, 2, earliest.minusDays(2), 1))
        assertTrue(periodReminderDue(state, 2, 2, earliest.minusDays(2), 1))
        assertFalse(periodReminderDue(state, null, 2, earliest.minusDays(2), null))
    }

    @Test fun reminderCatchesUpIfMissedDay() {
        assertTrue(periodReminderDue(state, 1, 2, earliest.minusDays(1), null))
        assertTrue(periodReminderDue(state, 1, 2, earliest, null))
    }

    @Test fun noReminderWhenPausedOrVaries() {
        listOf(PredictionState.Paused, PredictionState.Varies(25, 60, 4, 26), PredictionState.NoData,
            PredictionState.NeedMoreData(26), PredictionState.RangePassed(earliest, 1, 26)).forEach {
            assertFalse(periodReminderDue(it, 1, 2, earliest.minusDays(2), null))
        }
    }

    @Test fun zeroDaysBeforeFiresOnlyAtEarliest() {
        assertFalse(periodReminderDue(state, 1, 0, earliest.minusDays(1), null))
        assertTrue(periodReminderDue(state, 1, 0, earliest, null))
        assertFalse(periodReminderDue(state, 1, -1, earliest, null))
    }
}
