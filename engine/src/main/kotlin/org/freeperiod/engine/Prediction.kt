package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Domain settings for a user-entered fallback and prediction pause. */
data class PredictionSettings(val typicalCycleLength: Int? = null, val paused: Boolean = false)

/** Explains the observations behind a predicted range. */
enum class Basis { USER_ENTERED, EARLY_ESTIMATE, HISTORY }

/** A prediction outcome suitable for display without inventing missing data. */
sealed interface PredictionState {
    /** Predictions are hidden by the user. */
    data object Paused : PredictionState
    /** No period has been logged on or before today. */
    data object NoData : PredictionState
    /** Periods exist, but neither eligible cycles nor a fallback length are available. */
    data class NeedMoreData(val cycleDay: Int) : PredictionState
    /**
     * An inclusive predicted start range and the estimated period length, plus the cycle length and the
     * spread (standard deviation, days) of the start that [periodChances] uses for calendar days.
     */
    data class Range(
        val earliest: LocalDate, val latest: LocalDate, val basis: Basis,
        val cyclesUsed: Int, val periodLength: Int, val cycleDay: Int,
        val cycleLength: Int = 28, val spread: Double = USER_ENTERED_SPREAD,
    ) : PredictionState
    /** Variation is too broad for a useful date range. */
    data class Varies(val minLength: Int, val maxLength: Int, val cyclesUsed: Int, val cycleDay: Int) : PredictionState
    /** The predicted range has ended without a newer logged start. */
    data class RangePassed(val latest: LocalDate, val daysPassed: Int, val cycleDay: Int, val earliest: LocalDate = latest) : PredictionState
    /** Calendar break in the current pill pack, with a one-based pack day. */
    data class ScheduledBreak(val range: ClosedRange<LocalDate>, val packDay: Int) : PredictionState
    /** Continuous combined pill use has no scheduled break. */
    data class ContinuousPill(val packDay: Int) : PredictionState
    /** No usable combined pill rhythm has begun yet. */
    data object NeedsPillRhythm : PredictionState
    /** Recorded history only, counting complete months from the first day after bleeding. */
    data class Menopause(val fullMonthsSinceLastEnd: Int?, val lastEnd: LocalDate?) : PredictionState
}

/** Keeps the original API equivalent to regular tracking without a method. */
fun predict(periods: List<Period>, settings: PredictionSettings, today: LocalDate): PredictionState =
    predict(periods, settings, Situation(), today)

/** Applies pause and phase precedence before method-specific or statistical predictions. */
fun predict(periods: List<Period>, settings: PredictionSettings, situation: Situation, today: LocalDate): PredictionState {
    if (settings.paused) return PredictionState.Paused
    when (situation.predictionMode()) {
        PredictionMode.PAUSED -> return PredictionState.Paused
        PredictionMode.MENOPAUSE -> return PredictionState.Menopause(
            fullMonthsSinceLastPeriodEnded(periods, today),
            periods.mapNotNull { it.end }.filter { it <= today }.maxOrNull(),
        )
        PredictionMode.SCHEDULED_BREAK, PredictionMode.CONTINUOUS_PILL, PredictionMode.NEEDS_PILL_RHYTHM -> {
            val pill = situation.pill ?: return PredictionState.NeedsPillRhythm
            val packDay = pill.packDay(today) ?: return PredictionState.NeedsPillRhythm
            return if (pill.breakDays == 0) PredictionState.ContinuousPill(packDay)
                else PredictionState.ScheduledBreak(requireNotNull(pill.breakDaysAround(today)), packDay)
        }
        PredictionMode.STATISTICAL -> Unit
    }
    val history = periods.filter { it.start <= today }
    val last = history.maxByOrNull { it.start } ?: return PredictionState.NoData
    val cycleDay = requireNotNull(PeriodRules.cycleDay(history, today))
    val lengths = PeriodRules.cycles(history).filter { it.eligible }.takeLast(12).map { it.length }
    val centreOffset: Int
    val halfWidth: Int
    val basis: Basis
    val spread: Double
    if (lengths.isEmpty()) {
        centreOffset = settings.typicalCycleLength ?: return PredictionState.NeedMoreData(cycleDay)
        halfWidth = 3
        basis = Basis.USER_ENTERED
        spread = USER_ENTERED_SPREAD
    } else {
        centreOffset = Stats.median(lengths.takeLast(6)).roundToInt()
        if (lengths.size <= 2) {
            halfWidth = max(2, ceil((lengths.max().toDouble() - lengths.min()) / 2).toInt())
            basis = Basis.EARLY_ESTIMATE
            spread = max(1.5, (lengths.max() - lengths.min()) / 2.0)
        } else {
            halfWidth = max(1, ceil(Stats.mad(lengths) * 1.5).toInt())
            basis = Basis.HISTORY
            // 1.4826 x MAD is the usual robust estimate of a standard deviation; no cycle is that exact.
            spread = max(1.0, Stats.mad(lengths) * 1.4826)
        }
    }
    if (halfWidth > 7) return PredictionState.Varies(lengths.min(), lengths.max(), lengths.size, cycleDay)
    val centre = last.start.plusDays(centreOffset.toLong())
    val earliest = centre.minusDays(halfWidth.toLong())
    val latest = centre.plusDays(halfWidth.toLong())
    if (today > latest) return PredictionState.RangePassed(latest, Math.toIntExact(DAYS.between(latest, today)), cycleDay, earliest)
    return PredictionState.Range(earliest, latest, basis, lengths.size, PeriodRules.typicalPeriodLength(history), cycleDay,
        centreOffset, spread)
}

private const val USER_ENTERED_SPREAD = 2.5

/** How likely a calendar day is a period day. */
enum class PeriodChance { POSSIBLE, LIKELY }

/**
 * Calendar days of the next period and, when logged cycles back it, the one after: the start is spread
 * normally around the middle of the range, each start covers the typical period length, and a day is
 * LIKELY from a 60 % chance of being a period day, POSSIBLE from 30 %. Two cycles add their variances,
 * so the period after next uses spread × √2. When no day reaches 30 %, the start range stays POSSIBLE.
 */
fun periodChances(state: PredictionState): Map<LocalDate, PeriodChance> {
    if (state !is PredictionState.Range) return emptyMap()
    val centre = state.earliest.plusDays(DAYS.between(state.earliest, state.latest) / 2)
    val next = periodDayChances(centre, state.spread, state.periodLength).ifEmpty {
        generateSequence(state.earliest) { it.plusDays(1) }.takeWhile { it <= state.latest }.associateWith { PeriodChance.POSSIBLE }
    }
    val following = if (state.basis == Basis.USER_ENTERED) emptyMap()
        else periodDayChances(centre.plusDays(state.cycleLength.toLong()), state.spread * sqrt(2.0), state.periodLength)
    return following + next
}

private fun periodDayChances(centre: LocalDate, spread: Double, length: Int): Map<LocalDate, PeriodChance> {
    val reach = ceil(spread * 4).toInt()
    val weights = (-reach..reach).map { exp(-(it * it) / (2 * spread * spread)) }
    val total = weights.sum()
    // A day is a period day when the period started on it or up to length - 1 days before.
    return (-reach until reach + length).mapNotNull { offset ->
        val chance = (offset - length + 1..offset).sumOf { weights.getOrElse(it + reach) { 0.0 } } / total
        val level = when {
            chance >= 0.6 -> PeriodChance.LIKELY
            chance >= 0.3 -> PeriodChance.POSSIBLE
            else -> null
        }
        level?.let { centre.plusDays(offset.toLong()) to it }
    }.toMap()
}
