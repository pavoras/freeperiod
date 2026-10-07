package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PeriodRulesTest {
    private fun d(value: String) = LocalDate.parse("2026-$value")
    private fun p(id: Long, start: String, end: String? = null, use: CycleUse = CycleUse.AUTO) =
        Period(id, d(start), end?.let(::d), use)
    private fun today(periods: List<Period>) = periods.maxOf { it.start }.plusDays(10)

    @Test fun overlapIsRejected() {
        val periods = listOf(p(1, "03-01", "03-05"))
        assertEquals(PeriodError.OVERLAP, PeriodRules.validate(periods, p(2, "03-04", "03-08"), today(periods)))
        assertEquals(PeriodError.OVERLAP, PeriodRules.validate(periods, p(2, "03-05", "03-08"), today(periods)))
    }

    @Test fun overlapAfterMoveIsRejected() {
        val periods = listOf(p(1, "03-01", "03-05"), p(2, "03-29", "04-02"))
        assertEquals(PeriodError.OVERLAP, PeriodRules.validate(periods, p(1, "03-01", "03-30"), today(periods)))
        assertNull(PeriodRules.validate(periods, p(1, "03-01", "03-06"), today(periods)))
    }

    @Test fun ongoingPeriodBlocksLaterStart() {
        assertEquals(PeriodError.OVERLAP, PeriodRules.validate(listOf(p(1, "03-01")), p(2, "03-03"), d("03-04")))
    }

    @Test fun endBeforeStart() {
        assertEquals(PeriodError.END_BEFORE_START, PeriodRules.validate(emptyList(), p(1, "03-05", "03-04"), d("03-15")))
        assertEquals(PeriodError.START_IN_FUTURE, PeriodRules.validate(emptyList(), p(1, "03-16"), d("03-15")))
    }

    @Test fun endInFuture() {
        assertEquals(PeriodError.END_IN_FUTURE, PeriodRules.validate(emptyList(), p(1, "03-05", "03-16"), d("03-15")))
    }

    @Test fun cyclesAreStartToStart() {
        val periods = listOf(p(3, "02-28"), p(1, "01-01", "01-05"), p(2, "01-29", "02-02"))
        val cycles = PeriodRules.cycles(periods)
        assertEquals(listOf(28, 30), cycles.map { it.length })
        assertEquals(listOf(1L, 2L), cycles.map { it.startPeriodId })
        assertEquals(listOf(5, 5), cycles.map { it.periodLength })
        assertTrue(cycles.all { it.eligible && it.ineligibleReason == null })
    }

    @Test fun cycleFlags() {
        val cases = listOf(
            Triple(14, CycleUse.AUTO, IneligibleReason.TOO_SHORT),
            Triple(91, CycleUse.AUTO, IneligibleReason.TOO_LONG),
            Triple(28, CycleUse.EXCLUDE, IneligibleReason.EXCLUDED_BY_USER),
            Triple(15, CycleUse.AUTO, null), Triple(90, CycleUse.AUTO, null),
            Triple(14, CycleUse.INCLUDE, null), Triple(91, CycleUse.INCLUDE, null),
        )
        cases.forEach { (length, use, reason) ->
            val start = d("01-01")
            val cycle = PeriodRules.cycles(listOf(Period(1, start, start.plusDays(4), use), Period(2, start.plusDays(length.toLong()), null))).single()
            assertEquals(reason, cycle.ineligibleReason)
            assertEquals(reason == null, cycle.eligible)
        }
    }

    @Test fun onlyOneOngoingAndItIsLatest() {
        val periods = listOf(p(1, "03-01", "03-05"), p(2, "04-01"))
        assertEquals(PeriodError.OVERLAP, PeriodRules.validate(periods, p(3, "02-01"), today(periods)))
        assertNull(PeriodRules.validate(periods, p(3, "02-01", "02-05"), today(periods)))
    }

    @Test fun cycleDayCountsFromLatestStart() {
        assertEquals(12, PeriodRules.cycleDay(listOf(p(1, "03-01")), d("03-12")))
        assertNull(PeriodRules.cycleDay(emptyList(), d("03-12")))
        assertEquals(12, PeriodRules.cycleDay(listOf(p(2, "04-01"), p(1, "03-01", "03-05")), d("03-12")))
    }

    @Test fun typicalPeriodLengthIgnoresOngoing() {
        val periods = listOf(p(1, "01-01", "01-04"), p(2, "01-29", "02-02"), p(3, "02-28", "03-06"), p(4, "03-28"))
        assertEquals(5, PeriodRules.typicalPeriodLength(periods))
        assertEquals(5, PeriodRules.typicalPeriodLength(emptyList()))
        assertEquals(5, PeriodRules.typicalPeriodLength(listOf(p(4, "03-28"))))
        assertEquals(5, PeriodRules.typicalPeriodLength(periods.take(2)))
    }

    @Test fun endQuestionAfterMedianPlusThree() {
        val periods = listOf(p(1, "01-01", "01-05"), p(2, "03-01"))
        assertNull(PeriodRules.endQuestion(periods, d("03-08")))
        assertEquals(d("03-05"), PeriodRules.endQuestion(periods, d("03-09")))
        assertNull(PeriodRules.endQuestion(periods.take(1), d("03-09")))
    }

    @Test fun lightFlowOutsidePeriodSuggestsStart() {
        val periods = listOf(p(1, "03-01", "03-05"))
        assertTrue(PeriodRules.suggestsPeriodStart(DayLog(d("03-10"), flow = FlowLevel.LIGHT), periods, today(periods)))
        assertFalse(PeriodRules.suggestsPeriodStart(DayLog(d("03-10"), flow = FlowLevel.SPOTTING), periods, today(periods)))
        assertFalse(PeriodRules.suggestsPeriodStart(DayLog(d("03-03"), flow = FlowLevel.LIGHT), periods, today(periods)))
    }

    @Test fun periodOnUsesInclusiveBoundariesAndCapsOngoingAtToday() {
        val periods = listOf(p(1, "03-01", "03-05"), p(2, "03-29"))
        assertEquals(periods[0], PeriodRules.periodOn(periods, d("03-05"), today(periods)))
        assertNull(PeriodRules.periodOn(periods, d("03-06"), today(periods)))
        assertEquals(periods[1], PeriodRules.periodOn(periods, today(periods), today(periods)))
        assertNull(PeriodRules.periodOn(periods, today(periods).plusDays(1), today(periods)))
    }
}
