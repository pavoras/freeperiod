package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FertilityTest {
    private val centre = LocalDate.of(2026, 4, 23)
    private val range = PredictionState.Range(centre.minusDays(1), centre.plusDays(1), Basis.HISTORY, 3, 5, 10)
    private val enabled = Situation(fertileWindowEnabled = true)

    @Test fun threeDayPredictionSpansEightDays() {
        val window = requireNotNull(fertileWindow(range, enabled))
        assertEquals(LocalDate.of(2026, 4, 3)..LocalDate.of(2026, 4, 10), window)
        assertEquals(8L, java.time.temporal.ChronoUnit.DAYS.between(window.start, window.endInclusive) + 1)
    }

    @Test fun wideRangeStaysAtMostEightDaysAroundTheMiddle() {
        // Owner's phone: next period likely Nov 1–7 gave 12 days; now the middle (Nov 4) decides.
        val wide = range.copy(earliest = LocalDate.of(2026, 11, 1), latest = LocalDate.of(2026, 11, 7))
        assertEquals(LocalDate.of(2026, 10, 15)..LocalDate.of(2026, 10, 22), fertileWindow(wide, enabled))
    }

    @Test fun singleDayPredictionSpansSixDays() {
        val window = requireNotNull(fertileWindow(range.copy(earliest = centre, latest = centre), enabled))
        assertEquals(centre.minusDays(19)..centre.minusDays(14), window)
        assertEquals(6L, java.time.temporal.ChronoUnit.DAYS.between(window.start, window.endInclusive) + 1)
    }

    @Test fun enabledByDefaultAndCanBeDisabled() {
        assertTrue(Situation().fertileWindowEnabled)
        assertNotNull(fertileWindow(range, Situation()))
        assertNull(fertileWindow(range, Situation(fertileWindowEnabled = false)))
    }

    @Test fun noFertileWindowWhenVaries() {
        assertNull(fertileWindow(PredictionState.Varies(25, 60, 4, 10), enabled))
        assertNull(fertileWindow(PredictionState.Paused, enabled))
        assertNull(fertileWindow(PredictionState.RangePassed(centre, 3, 33), enabled))
    }

    @Test fun hormonalMethodsDisallowFertileWindow() {
        for (phase in LifePhase.entries) for (method in Method.entries) for (on in listOf(false, true)) {
            val allowed = on && phase in setOf(LifePhase.REGULAR, LifePhase.TRYING_TO_CONCEIVE, LifePhase.PERIMENOPAUSE) &&
                method in setOf(Method.NONE, Method.IUD_COPPER, Method.CONDOM, Method.OTHER)
            val situation = Situation(phase = phase, method = method, fertileWindowEnabled = on)
            assertEquals("$phase/$method/$on", allowed, situation.fertileWindowAllowed())
            assertEquals("$phase/$method/$on", allowed, fertileWindow(range, situation) != null)
        }
    }
}
