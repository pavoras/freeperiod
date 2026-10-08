package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StatsTest {
    private fun hintCycles(vararg lengths: Int): List<Cycle> {
        val dates = lengths.runningFold(LocalDate.of(2026, 1, 1)) { date, length -> date.plusDays(length.toLong()) }
        return PeriodRules.cycles(dates.mapIndexed { index, date -> Period(index + 1L, date, date.plusDays(4)) })
    }

    @Test fun longCycleHintBoundary44Vs45AndAutoExcludedCandidate() {
        assertEquals(emptySet<Long>(), longCycleHints(hintCycles(28, 28, 28, 44)))
        assertEquals(setOf(4L), longCycleHints(hintCycles(28, 28, 28, 45)))
        assertEquals(setOf(4L), longCycleHints(hintCycles(28, 28, 28, 95)))
        assertEquals(emptySet<Long>(), longCycleHints(hintCycles(28, 28, 95)))
    }

    @Test fun longCycleBaselineUsesOnlyTwelveEligiblePriorCycles() {
        val cycles = hintCycles(*IntArray(15) { 60 }, *IntArray(12) { 28 }, 45)
        assertEquals(setOf(28L), longCycleHints(cycles.reversed()))
        val excluded = hintCycles(28, 28, 28, 95).map { if (it.length == 95) it.copy(ineligibleReason = IneligibleReason.EXCLUDED_BY_USER) else it }
        assertTrue(longCycleHints(excluded).isEmpty())
    }

    @Test fun medianHandlesOddEvenAndUnsortedValues() {
        assertEquals(5.0, Stats.median(listOf(7, 4, 5)), 0.0)
        assertEquals(4.5, Stats.median(listOf(5, 4)), 0.0)
        assertEquals(Int.MAX_VALUE.toDouble(), Stats.median(listOf(Int.MAX_VALUE, Int.MAX_VALUE)), 0.0)
    }

    @Test fun madPreservesFractionalDeviations() {
        assertEquals(2.0, Stats.mad(listOf(26, 30, 35, 28)), 0.0)
        assertEquals(0.5, Stats.mad(listOf(28, 29)), 0.0)
    }

    @Test(expected = IllegalArgumentException::class) fun emptyMedianIsRejected() {
        Stats.median(emptyList())
    }

    @Test(expected = IllegalArgumentException::class) fun emptyMadIsRejected() {
        Stats.mad(emptyList())
    }

    @Test fun symptomCountsInRange() {
        val from = LocalDate.of(2026, 1, 1)
        val to = LocalDate.of(2026, 3, 26)
        val logs = listOf(
            DayLog(from.minusDays(1), symptoms = setOf(Symptom.CRAMPS)),
            DayLog(from, symptoms = setOf(Symptom.CRAMPS, Symptom.HEADACHE)),
            DayLog(to.minusDays(1), symptoms = setOf(Symptom.CRAMPS)),
            DayLog(to, symptoms = setOf(Symptom.HEADACHE)),
        )
        assertEquals(mapOf(Symptom.CRAMPS to 2, Symptom.HEADACHE to 1), Stats.symptomCounts(logs, from, to))
        assertTrue(Stats.symptomCounts(logs, to, to).isEmpty())
    }
}

