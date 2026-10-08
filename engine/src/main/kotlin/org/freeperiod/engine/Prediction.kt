package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

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
    /** An inclusive predicted start range and the estimated period length. */
    data class Range(
        val earliest: LocalDate, val latest: LocalDate, val basis: Basis,
        val cyclesUsed: Int, val periodLength: Int, val cycleDay: Int,
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
    if (lengths.isEmpty()) {
        centreOffset = settings.typicalCycleLength ?: return PredictionState.NeedMoreData(cycleDay)
        halfWidth = 3
        basis = Basis.USER_ENTERED
    } else {
        centreOffset = Stats.median(lengths.takeLast(6)).roundToInt()
        if (lengths.size <= 2) {
            halfWidth = max(2, ceil((lengths.max().toDouble() - lengths.min()) / 2).toInt())
            basis = Basis.EARLY_ESTIMATE
        } else {
            halfWidth = max(1, ceil(Stats.mad(lengths) * 1.5).toInt())
            basis = Basis.HISTORY
        }
    }
    if (halfWidth > 7) return PredictionState.Varies(lengths.min(), lengths.max(), lengths.size, cycleDay)
    val centre = last.start.plusDays(centreOffset.toLong())
    val earliest = centre.minusDays(halfWidth.toLong())
    val latest = centre.plusDays(halfWidth.toLong())
    if (today > latest) return PredictionState.RangePassed(latest, Math.toIntExact(DAYS.between(latest, today)), cycleDay, earliest)
    return PredictionState.Range(earliest, latest, basis, lengths.size, PeriodRules.typicalPeriodLength(history), cycleDay)
}

/** Returns all potential period days for a range, or null for other states. */
fun predictedDays(state: PredictionState): ClosedRange<LocalDate>? = when (state) {
    is PredictionState.Range -> state.earliest..state.latest.plusDays(state.periodLength.toLong() - 1)
    is PredictionState.ScheduledBreak -> state.range
    else -> null
}
