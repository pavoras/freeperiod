package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.roundToInt

/** Pure rules for period validation and derived calendar information. */
object PeriodRules {
    /** Validates a candidate against other IDs, treating ongoing periods as unbounded. */
    fun validate(existing: List<Period>, candidate: Period, today: LocalDate): PeriodError? {
        if (candidate.end != null && candidate.end < candidate.start) return PeriodError.END_BEFORE_START
        if (candidate.start > today) return PeriodError.START_IN_FUTURE
        if (candidate.end != null && candidate.end > today) return PeriodError.END_IN_FUTURE
        return if (existing.any {
            it.id != candidate.id && (candidate.end == null || it.start <= candidate.end) &&
                (it.end == null || candidate.start <= it.end)
        }) PeriodError.OVERLAP else null
    }

    /** Derives completed start-to-start cycles in ascending date order. */
    fun cycles(periods: List<Period>): List<Cycle> = periods.sortedBy { it.start }.zipWithNext { period, next ->
        val length = Math.toIntExact(DAYS.between(period.start, next.start))
        val reason = when {
            period.cycleUse == CycleUse.EXCLUDE -> IneligibleReason.EXCLUDED_BY_USER
            period.cycleUse == CycleUse.INCLUDE -> null
            length < 15 -> IneligibleReason.TOO_SHORT
            length > 90 -> IneligibleReason.TOO_LONG
            else -> null
        }
        Cycle(period.id, period.start, next.start, length, period.end?.let { inclusiveLength(period.start, it) }, reason == null, reason)
    }

    /** Finds a period covering the date, with ongoing coverage capped at today. */
    fun periodOn(periods: List<Period>, date: LocalDate, today: LocalDate): Period? =
        periods.firstOrNull { date >= it.start && date <= (it.end ?: today) }

    /** Returns the one-based day since the latest start on or before today. */
    fun cycleDay(periods: List<Period>, today: LocalDate): Int? =
        periods.filter { it.start <= today }.maxByOrNull { it.start }?.let { inclusiveLength(it.start, today) }

    /** Returns the half-up rounded median completed period length, defaulting to five days. */
    fun typicalPeriodLength(periods: List<Period>): Int {
        val lengths = periods.mapNotNull { period -> period.end?.let { inclusiveLength(period.start, it) } }
        return if (lengths.isEmpty()) 5 else Stats.median(lengths).roundToInt()
    }

    /** Suggests an end once an ongoing period exceeds typical length plus three days. */
    fun endQuestion(periods: List<Period>, today: LocalDate): LocalDate? {
        val ongoing = periods.filter { it.end == null && it.start <= today }.maxByOrNull { it.start } ?: return null
        val length = typicalPeriodLength(periods)
        return if (inclusiveLength(ongoing.start, today) > length.toLong() + 3) ongoing.start.plusDays(length.toLong() - 1) else null
    }

    /** Offers a start for LIGHT or heavier flow outside a period on a nonfuture day. */
    fun suggestsPeriodStart(log: DayLog, periods: List<Period>, today: LocalDate): Boolean =
        log.date <= today && log.flow in setOf(FlowLevel.LIGHT, FlowLevel.MEDIUM, FlowLevel.HEAVY) &&
            periodOn(periods, log.date, today) == null

    private fun inclusiveLength(start: LocalDate, end: LocalDate): Int = Math.toIntExact(DAYS.between(start, end) + 1)
}
