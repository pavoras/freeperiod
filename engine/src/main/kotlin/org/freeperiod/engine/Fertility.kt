package org.freeperiod.engine

import java.time.LocalDate

/**
 * Calendar estimate only for statistical ranges with the display preference enabled in a
 * compatible situation. Estimated ovulation is each possible next period start minus 14 days.
 * The five preceding days and that day span `(earliest - 19)..(latest - 14)`, inclusive, over
 * the whole predicted range: six days for a single-day range, eight for a three-day range.
 */
fun fertileWindow(state: PredictionState, situation: Situation): ClosedRange<LocalDate>? {
    if (state !is PredictionState.Range || !situation.fertileWindowAllowed()) return null
    return state.earliest.minusDays(19)..state.latest.minusDays(14)
}
