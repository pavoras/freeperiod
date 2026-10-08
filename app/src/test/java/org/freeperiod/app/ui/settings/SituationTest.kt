package org.freeperiod.app.ui.settings

import java.time.LocalDate
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

class SituationTest {
    @Test fun presetOfferedPerMethod() {
        val today = LocalDate.of(2026, 4, 12)
        for (method in Method.entries) {
            val preset = methodReminderPreset(method, today)
            when (method) {
                Method.NONE, Method.CONDOM, Method.OTHER -> assertNull(preset)
                Method.PILL_COMBINED, Method.PILL_PROGESTIN -> assertEquals(Recurrence.Daily, preset!!.recurrence)
                Method.RING -> assertEquals(Recurrence.EveryNDays(1, today), preset!!.recurrence)
                Method.PATCH -> assertEquals(Recurrence.EveryNDays(1, today), preset!!.recurrence)
                Method.INJECTION -> assertEquals(Recurrence.EveryNDays(1, today), preset!!.recurrence)
                else -> assertEquals(Recurrence.Once(today), preset!!.recurrence)
            }
            if (preset != null) assertFalse(preset.enabled)
        }
    }
}
