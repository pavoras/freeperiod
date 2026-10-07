package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    private val date = LocalDate.of(2026, 3, 1)

    @Test fun emptyDayHasNoLoggedFields() {
        assertTrue(DayLog(date).isEmpty())
        val logs = listOf(
            DayLog(date, flow = FlowLevel.NONE), DayLog(date, mood = Mood.OKAY),
            DayLog(date, symptoms = setOf(Symptom.CRAMPS)), DayLog(date, pain = Pain.NONE),
            DayLog(date, sex = Sex.NONE), DayLog(date, discharge = Discharge.NONE),
            DayLog(date, note = "A note"), DayLog(date, tagIds = setOf(1)),
        )
        logs.forEach { assertFalse(it.isEmpty()) }
    }

    @Test fun emptyNoteDoesNotMakeADayLogged() {
        assertTrue(DayLog(date, note = "").isEmpty())
        assertFalse(DayLog(date, note = " ").isEmpty())
    }
}

