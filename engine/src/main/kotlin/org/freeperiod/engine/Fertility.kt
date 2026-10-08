package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Calendar estimate only for statistical ranges with the display preference enabled in a
 * compatible situation. Estimated ovulation = middle of the predicted next-period range − 14 days;
 * the window is the five days before plus that day, widened by one day on each side when the
 * prediction spans three days or more. So it is 6–8 days, never the whole spread of a wide range
 * (owner feedback: Nov 1–7 gave a 12-day window that helped nobody).
 */
fun fertileWindow(state: PredictionState, situation: Situation): ClosedRange<LocalDate>? {
    if (state !is PredictionState.Range || !situation.fertileWindowAllowed()) return null
    val span = ChronoUnit.DAYS.between(state.earliest, state.latest)
    val middle = state.earliest.plusDays(span / 2)
    val pad = if (span >= 2) 1L else 0L
    return middle.minusDays(19 + pad)..middle.minusDays(14 - pad)
}
