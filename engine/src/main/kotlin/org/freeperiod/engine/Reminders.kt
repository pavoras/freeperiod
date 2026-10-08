@file:kotlinx.serialization.UseSerializers(LocalTimeSerializer::class)

package org.freeperiod.engine

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** User-configured reminder purpose, stored by its stable name. */
@Serializable
enum class ReminderKind { PERIOD_DUE, DAILY_LOG, PILL, METHOD, CUSTOM }

/** Reminder preferences; delivery identities are intentionally excluded from backups. */
@Serializable
data class Reminder(
    val id: Long,
    val kind: ReminderKind,
    val title: String?,
    val recurrence: Recurrence,
    val time: LocalTime,
    val enabled: Boolean,
    val daysBefore: Int? = null,
)

/** Returns whether an undelivered period reminder is due in the inclusive catch-up window. */
fun periodReminderDue(
    state: PredictionState,
    latestPeriodId: Long?,
    daysBefore: Int,
    today: LocalDate,
    lastNotifiedPeriodId: Long?,
): Boolean = state is PredictionState.Range && latestPeriodId != null && latestPeriodId != lastNotifiedPeriodId &&
    today >= state.earliest.minusDays(daysBefore.toLong()) && today <= state.earliest
