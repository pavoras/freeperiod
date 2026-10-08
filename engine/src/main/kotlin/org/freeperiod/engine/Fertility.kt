package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

/** Opt-in calendar estimate only for statistical ranges in compatible situations. */
fun fertileWindow(state: PredictionState, situation: Situation): ClosedRange<LocalDate>? {
    if (state !is PredictionState.Range || !situation.fertileWindowAllowed()) return null
    val centre = state.earliest.plusDays((DAYS.between(state.earliest, state.latest) + 1) / 2)
    return centre.minusDays(19)..centre.minusDays(13)
}
