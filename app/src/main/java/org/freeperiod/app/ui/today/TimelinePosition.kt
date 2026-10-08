package org.freeperiod.app.ui.today

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import org.freeperiod.engine.CycleTimeline

/** Two continuous date scales preserve order while giving the period segment room to read. */
internal fun timelinePosition(timeline: CycleTimeline, date: LocalDate, width: Float, minimumPeriodWidth: Float): Float {
    val span = DAYS.between(timeline.axisStart, timeline.axisEnd).coerceAtLeast(1).toFloat()
    val day = DAYS.between(timeline.axisStart, date).toFloat().coerceIn(0f, span)
    val periodEnd = timeline.expectedPeriodRest?.endInclusive ?: timeline.recordedPeriod?.endInclusive
    val periodDays = periodEnd?.let { DAYS.between(timeline.axisStart, it).toFloat().coerceIn(0f, span) } ?: 0f
    if (periodDays <= 0f || periodDays >= span) return width * day / span
    val periodWidth = maxOf(width * periodDays / span, minOf(minimumPeriodWidth, width * .45f))
    return if (day <= periodDays) periodWidth * day / periodDays
        else periodWidth + (width - periodWidth) * (day - periodDays) / (span - periodDays)
}
