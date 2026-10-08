package org.freeperiod.engine

import java.time.LocalDate

/** Inclusive calendar segments for the current cycle or pill pack. */
data class CycleTimeline(
    val axisStart: LocalDate,
    val axisEnd: LocalDate,
    val today: LocalDate,
    val recordedPeriod: ClosedRange<LocalDate>?,
    val expectedPeriodRest: ClosedRange<LocalDate>?,
    val bracket: ClosedRange<LocalDate>?,
    val bracketKind: BracketKind?,
)

/** Distinguishes statistical estimates from a configured calendar break. */
enum class BracketKind { LIKELY, SCHEDULED_BREAK }

/** Builds a cycle axis with a caller-supplied typical/median/default cycle length. */
fun cycleTimeline(periods: List<Period>, state: PredictionState, pill: PillSchedule?, fallbackLength: Int, today: LocalDate): CycleTimeline? {
    require(fallbackLength > 0) { "Fallback length must be positive" }
    if (state is PredictionState.Menopause) return null
    val last = periods.filter { it.start <= today }.maxByOrNull { it.start }
    if (state is PredictionState.ScheduledBreak || state is PredictionState.ContinuousPill) {
        val pack = pill?.packAround(today) ?: return null
        val recorded = last?.let {
            val start = maxOf(it.start, pack.start)
            val end = minOf(it.end ?: today, pack.endInclusive, today)
            if (start <= end) start..end else null
        }
        val bracket = (state as? PredictionState.ScheduledBreak)?.range
        return CycleTimeline(pack.start, pack.endInclusive, today, recorded, null, bracket,
            if (bracket != null) BracketKind.SCHEDULED_BREAK else null)
    }
    last ?: return null
    val recorded = last.start..minOf(last.end ?: today, today)
    val periodLength = (state as? PredictionState.Range)?.periodLength ?: PeriodRules.typicalPeriodLength(periods.filter { it.start <= today })
    val expectedEnd = last.start.plusDays(periodLength.toLong() - 1)
    val rest = if (last.end == null && expectedEnd > today) today.plusDays(1)..expectedEnd else null
    val bracket = when (state) {
        is PredictionState.Range -> state.earliest..state.latest
        is PredictionState.RangePassed -> state.earliest..state.latest
        else -> null
    }
    val axisEnd = maxOf(today, recorded.endInclusive, rest?.endInclusive ?: today,
        bracket?.endInclusive ?: today.plusDays(3), last.start.plusDays(fallbackLength.toLong() - 1))
    return CycleTimeline(last.start, axisEnd, today, recorded, rest, bracket,
        if (bracket != null) BracketKind.LIKELY else null)
}

/** Today is the first day without bleeding; same-day periods retain their start day. */
fun periodEndForTapToday(ongoing: Period, today: LocalDate): LocalDate = maxOf(ongoing.start, today.minusDays(1))
