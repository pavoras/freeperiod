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
        assertEquals(PredictionState.Range(d("03-28"), d("04-03"), Basis.USER_ENTERED, 0, 5, 10, 30, 2.5),
            predict(starts("03-01"), PredictionSettings(30), d("03-10")))
    }

    @Test fun oneCycleEarlyEstimate() {
        val periods = starts("02-01", "03-03")
        assertEquals(PredictionState.Range(d("03-31"), d("04-04"), Basis.EARLY_ESTIMATE, 1, 5, 11,
            cycleLength = 30, spread = 1.5),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun twoCyclesEarlyEstimate() {
        val periods = lengths(27, 33)
        assertEquals(PredictionState.Range(d("03-29"), d("04-04"), Basis.EARLY_ESTIMATE, 2, 5, 11,
            cycleLength = 30, spread = 3.0),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun regularHistory() {
        val periods = regular()
        assertEquals(PredictionState.Range(d("04-22"), d("04-24"), Basis.HISTORY, 3, 5, 11,
            cycleLength = 28, spread = 1.0),
            predict(periods.reversed(), PredictionSettings(), today(periods)))
    }

    @Test fun irregularHistory() {
        val periods = lengths(26, 30, 35, 28)
        assertEquals(PredictionState.Range(d("05-26"), d("06-01"), Basis.HISTORY, 4, 5, 11,
            cycleLength = 29, spread = 2.9652),
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
        assertEquals(PredictionState.Range(d("08-23"), d("08-25"), Basis.HISTORY, 3, 5, 11,
            cycleLength = 28, spread = 1.0),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun onlyLast12Used() {
        val periods = lengths(40, 40, *IntArray(12) { 28 })
        val last = periods.last().start
        assertEquals(PredictionState.Range(last.plusDays(27), last.plusDays(29), Basis.HISTORY, 12, 5, 11,
            cycleLength = 28, spread = 1.0),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun centreUsesLastSixSpreadUsesLastTwelve() {
        val periods = lengths(*IntArray(6) { 32 }, *IntArray(6) { 28 })
        val last = periods.last().start
        assertEquals(PredictionState.Range(last.plusDays(25), last.plusDays(31), Basis.HISTORY, 12, 5, 11,
            cycleLength = 28, spread = 2.9652),
            predict(periods, PredictionSettings(), today(periods)))
    }

    @Test fun rangePassed() {
        assertEquals(PredictionState.RangePassed(d("04-24"), 3, 33, d("04-22")), predict(regular(), PredictionSettings(), d("04-27")))
    }

    private fun days(from: String, to: String) = generateSequence(d(from)) { it.plusDays(1) }.takeWhile { it <= d(to) }.toList()
    private fun Map<LocalDate, PeriodChance>.at(level: PeriodChance) = filterValues { it == level }.keys.sorted()

    @Test fun regularCyclesMarkThePeriodLengthAsLikelyWithOnePossibleDayEachSide() {
        val periods = regular()
        val chances = periodChances(predict(periods, PredictionSettings(), today(periods)))
        assertEquals(days("04-23", "04-27") + days("05-21", "05-25"), chances.at(PeriodChance.LIKELY))
        assertEquals(listOf(d("04-22"), d("04-28"), d("05-20"), d("05-26")), chances.at(PeriodChance.POSSIBLE))
        assertTrue(periodChances(predict(periods, PredictionSettings(paused = true), today(periods))).isEmpty())
    }

    @Test fun moreSpreadKeepsTheSameDaysButFewerAreLikely() {
        val chances = periodChances(PredictionState.Range(d("05-26"), d("06-01"), Basis.HISTORY, 4, 5, 11, 29, 2.9652))
        assertEquals(listOf(d("05-31")), chances.at(PeriodChance.LIKELY))
        assertEquals(days("05-28", "06-03") - d("05-31") + days("06-24", "07-04"), chances.at(PeriodChance.POSSIBLE))
    }

    @Test fun shortPeriodsWithWideSpreadStillShowABroadPeriodAfterNext() {
        // 3-day periods, cycles varying by a few days: no day of the period after next reaches 30 %.
        val chances = periodChances(PredictionState.Range(d("11-01"), d("11-07"), Basis.HISTORY, 6, 3, 3, 28, 2.9652))
        assertEquals(days("11-03", "11-07") + days("11-30", "12-06"), chances.at(PeriodChance.POSSIBLE))
        assertTrue(chances.at(PeriodChance.LIKELY).isEmpty())
    }

    @Test fun typedLengthAloneMarksOnlyTheNextPeriod() {
        val chances = periodChances(predict(starts("03-01"), PredictionSettings(30), d("03-10")))
        assertEquals(days("03-30", "04-05"), chances.keys.sorted())
    }

    @Test fun veryBroadSpreadFallsBackToThePossibleStartRange() {
        val chances = periodChances(PredictionState.Range(d("05-01"), d("05-15"), Basis.HISTORY, 6, 5, 10, 30, 7.0))
        assertEquals(days("05-01", "05-15").associateWith { PeriodChance.POSSIBLE }, chances)
    }

    @Test fun centreRoundsHalfUpAndLatestIsStillARange() {
        val periods = lengths(28, 29)
        val latest = periods.last().start.plusDays(31)
        assertEquals(PredictionState.Range(latest.minusDays(4), latest, Basis.EARLY_ESTIMATE, 2, 5, 32,
            cycleLength = 29, spread = 1.5),
            predict(periods, PredictionSettings(), latest))
    }

    @Test fun excludedCyclesFallBackToTypicalLength() {
        val periods = lengths(28).map { it.copy(cycleUse = CycleUse.EXCLUDE) }
        val last = periods.last().start
        assertEquals(PredictionState.Range(last.plusDays(27), last.plusDays(33), Basis.USER_ENTERED, 0, 5, 11, 30, 2.5),
            predict(periods, PredictionSettings(30), today(periods)))
    }
}
