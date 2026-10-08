package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    private fun d(value: String) = LocalDate.parse("2026-$value")
    private val ongoing = Period(1, d("04-10"), null)
    private val range = PredictionState.Range(d("05-07"), d("05-09"), Basis.HISTORY, 3, 5, 3)

    @Test fun timelineDayThreeOfOngoingPeriod() {
        assertEquals(CycleTimeline(d("04-10"), d("05-09"), d("04-12"),
            d("04-10")..d("04-12"), d("04-13")..d("04-14"), d("05-07")..d("05-09"), BracketKind.LIKELY),
            cycleTimeline(listOf(ongoing), range, null, 28, d("04-12")))
    }

    @Test fun timelineWithoutPredictionExtendsPastToday() {
        val timeline = cycleTimeline(listOf(ongoing), PredictionState.Paused, null, 28, d("05-15"))!!
        assertEquals(d("05-18"), timeline.axisEnd)
        assertNull(timeline.expectedPeriodRest)
        assertNull(timeline.bracket)
        assertEquals(d("06-08"), cycleTimeline(listOf(ongoing), PredictionState.Paused, null, 60, d("04-12"))!!.axisEnd)
    }

    @Test fun timelineScheduledBreak() {
        val pill = PillSchedule(d("01-01"), 21, 7)
        val state = predict(emptyList(), PredictionSettings(), Situation(method = Method.PILL_COMBINED, pill = pill), d("02-01"))
        val timeline = cycleTimeline(emptyList(), state, pill, 28, d("02-01"))!!
        assertEquals(d("01-29"), timeline.axisStart)
        assertEquals(d("02-25"), timeline.axisEnd)
        assertEquals(d("02-19")..d("02-25"), timeline.bracket)
        assertEquals(BracketKind.SCHEDULED_BREAK, timeline.bracketKind)
    }

    @Test fun passedRangeRetainsBracketAndAxisIncludesToday() {
        val state = PredictionState.RangePassed(d("05-09"), 3, 33, earliest = d("05-07"))
        val timeline = cycleTimeline(listOf(ongoing.copy(end = d("04-14"))), state, null, 28, d("05-12"))!!
        assertEquals(d("05-07")..d("05-09"), timeline.bracket)
        assertEquals(d("05-12"), timeline.axisEnd)
        assertNull(timeline.expectedPeriodRest)
    }

    @Test fun emptyAndMenopauseHaveNoTimeline() {
        assertNull(cycleTimeline(emptyList(), PredictionState.NoData, null, 28, d("04-12")))
        assertNull(cycleTimeline(listOf(ongoing), PredictionState.Menopause(0, null), null, 28, d("04-12")))
    }

    @Test fun periodEndTapIsYesterday() {
        assertEquals(d("04-11"), periodEndForTapToday(ongoing, d("04-12")))
    }

    @Test fun periodEndTapSameDayIsStart() {
        assertEquals(ongoing.start, periodEndForTapToday(ongoing, ongoing.start))
    }
}
