package org.freeperiod.app.ui.today

import java.time.temporal.ChronoUnit.DAYS
import org.junit.Assert.*
import org.junit.Test

class TimelinePositionTest {
    @Test fun dayThreeLeavesColourVisibleOnBothSidesOfToday() {
        val timeline = todayFixture("ongoing").timeline!!
        val rest = requireNotNull(timeline.expectedPeriodRest)
        val recorded = requireNotNull(timeline.recordedPeriod)
        fun x(date: java.time.LocalDate) = timelinePosition(timeline, date, 160f, 40f)
        assertTrue(x(rest.endInclusive) - x(timeline.axisStart) >= 40f)
        assertTrue(x(timeline.today) - x(recorded.start) >= 16f)
        assertTrue(x(rest.endInclusive) - x(timeline.today) >= 16f)
    }

    @Test fun allDatesKeepTheirOrderAndEndpointsAcrossScenarios() {
        for (scenario in listOf("regular", "ongoing", "rangePassed", "scheduledBreak")) {
            val timeline = todayFixture(scenario).timeline!!
            val positions = (0..DAYS.between(timeline.axisStart, timeline.axisEnd)).map {
                timelinePosition(timeline, timeline.axisStart.plusDays(it), 160f, 40f)
            }
            assertEquals(0f, positions.first(), .001f)
            assertEquals(160f, positions.last(), .001f)
            assertTrue(positions.zipWithNext().all { (a, b) -> b > a })
        }
    }
}
