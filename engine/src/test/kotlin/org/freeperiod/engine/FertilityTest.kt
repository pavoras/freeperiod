package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FertilityTest {
    private val centre = LocalDate.of(2026, 4, 23)
    private val range = PredictionState.Range(centre.minusDays(1), centre.plusDays(1), Basis.HISTORY, 3, 5, 10)
    private val enabled = Situation(fertileWindowEnabled = true)

    @Test fun fertileWindowFromCentre() {
        assertEquals(LocalDate.of(2026, 4, 4)..LocalDate.of(2026, 4, 10), fertileWindow(range, enabled))
        assertNull(fertileWindow(range, Situation()))
    }

    @Test fun noFertileWindowWhenVaries() {
        assertNull(fertileWindow(PredictionState.Varies(25, 60, 4, 10), enabled))
        assertNull(fertileWindow(PredictionState.Paused, enabled))
        assertNull(fertileWindow(PredictionState.RangePassed(centre, 3, 33), enabled))
    }

    @Test fun hormonalMethodsDisallowFertileWindow() {
        Method.entries.forEach { method ->
            val allowed = method in setOf(Method.NONE, Method.IUD_COPPER, Method.CONDOM, Method.OTHER)
            assertEquals(method.name, allowed, fertileWindow(range, enabled.copy(method = method)) != null)
        }
        LifePhase.entries.forEach { phase ->
            val allowed = phase in setOf(LifePhase.REGULAR, LifePhase.TRYING_TO_CONCEIVE, LifePhase.PERIMENOPAUSE)
            assertEquals(phase.name, allowed, fertileWindow(range, enabled.copy(phase = phase)) != null)
        }
    }
}
