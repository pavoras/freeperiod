package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.MONTHS
import kotlinx.serialization.Serializable

/** User-selected views; preferences remain stored when a view suppresses them. */
@Serializable
enum class LifePhase { REGULAR, TRYING_TO_CONCEIVE, PREGNANT, POSTPARTUM, PERIMENOPAUSE, MENOPAUSE }

/** Stable method names used by persistence and backup. */
@Serializable
enum class Method { NONE, PILL_COMBINED, PILL_PROGESTIN, RING, PATCH, IUD_HORMONAL, IUD_COPPER, COPPER_CHAIN, COPPER_BALL, IMPLANT, INJECTION, CONDOM, OTHER }

/** Optional method configuration, independent of the life phase and display preferences. */
@Serializable
data class Situation(
    val phase: LifePhase = LifePhase.REGULAR,
    val method: Method = Method.NONE,
    val pill: PillSchedule? = null,
    val fertileWindowEnabled: Boolean = true,
    /** The pain diary item set is on (pain, medication and its own categories in the day entry). */
    val painDiary: Boolean = false,
)

/** Prediction source before manual pause and calendar-date checks are applied. */
enum class PredictionMode { STATISTICAL, SCHEDULED_BREAK, CONTINUOUS_PILL, NEEDS_PILL_RHYTHM, PAUSED, MENOPAUSE }

/** Phases suppress methods; combined pills suppress statistical estimates. */
fun Situation.predictionMode(): PredictionMode = when {
    phase == LifePhase.PREGNANT || phase == LifePhase.POSTPARTUM -> PredictionMode.PAUSED
    phase == LifePhase.MENOPAUSE -> PredictionMode.MENOPAUSE
    method != Method.PILL_COMBINED -> PredictionMode.STATISTICAL
    pill == null -> PredictionMode.NEEDS_PILL_RHYTHM
    pill.breakDays == 0 -> PredictionMode.CONTINUOUS_PILL
    else -> PredictionMode.SCHEDULED_BREAK
}

/** Calendar estimates require the display preference and a compatible phase and method. */
fun Situation.fertileWindowAllowed(): Boolean = fertileWindowEnabled &&
    phase in setOf(LifePhase.REGULAR, LifePhase.TRYING_TO_CONCEIVE, LifePhase.PERIMENOPAUSE) &&
    method in setOf(Method.NONE, Method.IUD_COPPER, Method.COPPER_CHAIN, Method.COPPER_BALL, Method.CONDOM, Method.OTHER)

/** Complete months since end + 1; ongoing bleeding returns zero and absent history returns null. */
fun fullMonthsSinceLastPeriodEnded(periods: List<Period>, today: LocalDate): Int? {
    val history = periods.filter { it.start <= today }
    if (history.any { it.end == null || it.end > today }) return 0
    val lastEnd = history.mapNotNull { it.end }.maxOrNull() ?: return null
    return Math.toIntExact(maxOf(0L, MONTHS.between(lastEnd.plusDays(1), today)))
}
