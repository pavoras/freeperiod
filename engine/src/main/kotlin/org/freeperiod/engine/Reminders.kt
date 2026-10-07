package org.freeperiod.engine

import java.time.LocalDate

/** Returns whether an undelivered period reminder is due in the inclusive catch-up window. */
fun periodReminderDue(
    state: PredictionState,
    latestPeriodId: Long?,
    daysBefore: Int,
    today: LocalDate,
    lastNotifiedPeriodId: Long?,
): Boolean = state is PredictionState.Range && latestPeriodId != null && latestPeriodId != lastNotifiedPeriodId &&
    today >= state.earliest.minusDays(daysBefore.toLong()) && today <= state.earliest
