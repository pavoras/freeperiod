package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PredictionTest {
    private fun d(value: String) = LocalDate.parse("2026-$value")
    private fun starts(vararg dates: String): List<Period> = dates.mapIndexed { index, date ->
        val start = d(date)
        Period(index + 1L, start, if (index == dates.lastIndex) null else start.plusDays(4))
    }
    private fun lengths(vararg lengths: Int): List<Period> {
        val dates = lengths.runningFold(d("01-01")) { start, length -> start.plusDays(length.toLong()) }
        return dates.mapIndexed { index, date -> Period(index + 1L, date, if (index == dates.lastIndex) null else date.plusDays(4)) }
    }
    private fun today(periods: List<Period>) = periods.maxOf { it.start }.plusDays(10)
    private fun regular() = starts("01-01", "01-29", "02-26", "03-26")

    @Test fun noPeriodsGivesNoData() {
        assertEquals(PredictionState.NoData, predict(emptyList(), PredictionSettings(30), d("03-10")))
    }

    @Test fun pausedWins() {
        val periods = regular()
        assertEquals(PredictionState.Paused, predict(periods, PredictionSettings(paused = true), today(periods)))
    }

    @Test fun noCyclesNoTypicalNeedsMoreData() {
        assertEquals(PredictionState.NeedMoreData(10), predict(starts("03-01"), PredictionSettings(), d("03-10")))
    }

    @Test fun typicalLengthOnly() {
        assertEquals(PredictionState.Range(d("03-28"), d("04-03"), Basis.USER_ENTERED, 0, 5, 10),
            predict(starts("03-01"), PredictionSettings(30), d("03-10")))
    }

    @Test fun oneCycleEarlyEstimate() {
        val periods = starts("02-01", "03-03")
        assertEquals(PredictionState.Range(d("03-31"), d("04-04"), Basis.EARLY_ESTIMATE, 1, 5, 11),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun twoCyclesEarlyEstimate() {
        val periods = lengths(27, 33)
        assertEquals(PredictionState.Range(d("03-29"), d("04-04"), Basis.EARLY_ESTIMATE, 2, 5, 11),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun regularHistory() {
        val periods = regular()
        assertEquals(PredictionState.Range(d("04-22"), d("04-24"), Basis.HISTORY, 3, 5, 11),
            predict(periods.reversed(), PredictionSettings(), today(periods)))
    }

    @Test fun irregularHistory() {
        val periods = lengths(26, 30, 35, 28)
        assertEquals(PredictionState.Range(d("05-26"), d("06-01"), Basis.HISTORY, 4, 5, 11),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun variesTooMuch() {
        val periods = lengths(25, 45, 30, 60)
        assertEquals(PredictionState.Varies(25, 60, 4, 11), predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun excludedAndTooLongIgnored() {
        val periods = lengths(28, 95, 28, 28, 28).mapIndexed { index, period ->
            if (index == 2) period.copy(cycleUse = CycleUse.EXCLUDE) else period
        }
        assertEquals(PredictionState.Range(d("08-23"), d("08-25"), Basis.HISTORY, 3, 5, 11),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun onlyLast12Used() {
        val periods = lengths(40, 40, *IntArray(12) { 28 })
        val last = periods.last().start
        assertEquals(PredictionState.Range(last.plusDays(27), last.plusDays(29), Basis.HISTORY, 12, 5, 11),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun rangePassed() {
        assertEquals(PredictionState.RangePassed(d("04-24"), 3, 33), predict(regular(), PredictionSettings(), d("04-27")))
    }

    @Test fun predictedDaysAddsPeriodLength() {
        val periods = regular()
        val state = predict(periods, PredictionSettings(), today(periods))
        assertEquals(d("04-22")..d("04-28"), predictedDays(state))
        assertNull(predictedDays(predict(periods, PredictionSettings(paused = true), today(periods))))
    }

    @Test fun centreRoundsHalfUpAndLatestIsStillARange() {
        val periods = lengths(28, 29)
        val latest = periods.last().start.plusDays(31)
        assertEquals(PredictionState.Range(latest.minusDays(4), latest, Basis.EARLY_ESTIMATE, 2, 5, 32),
            predict(periods, PredictionSettings(), latest))
    }

    @Test fun excludedCyclesFallBackToTypicalLength() {
        val periods = lengths(28).map { it.copy(cycleUse = CycleUse.EXCLUDE) }
        val last = periods.last().start
        assertEquals(PredictionState.Range(last.plusDays(27), last.plusDays(33), Basis.USER_ENTERED, 0, 5, 11),
            predict(periods, PredictionSettings(30), today(periods)))
    }
}
