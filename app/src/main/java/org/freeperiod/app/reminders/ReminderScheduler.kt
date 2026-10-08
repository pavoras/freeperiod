package org.freeperiod.app.reminders

import androidx.work.*
import java.time.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.freeperiod.app.data.Repository
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

internal fun BackupData.periodRemindersPaused(): Boolean = settings.predictionsPaused || situation.predictionMode() != PredictionMode.STATISTICAL
internal fun nextDailyTime(now: ZonedDateTime, time: LocalTime): ZonedDateTime =
    requireNotNull(nextReminderTime(Recurrence.Daily, time, now))

internal fun nextReminderTime(recurrence: Recurrence, time: LocalTime, now: ZonedDateTime,
    afterDate: LocalDate? = null): ZonedDateTime? {
    val threshold = maxOf(now.toLocalDate(), afterDate?.plusDays(1) ?: now.toLocalDate())
    var date = recurrence.nextDate(threshold, inclusive = true) ?: return null
    var candidate = date.atTime(time).atZone(now.zone)
    if (candidate <= now) {
        date = recurrence.nextDate(date, inclusive = false) ?: return null
        candidate = date.atTime(time).atZone(now.zone)
    }
    return candidate
}

internal data class ReminderOccurrence(val date: LocalDate, val time: ZonedDateTime)
internal fun reminderOccurrence(reminder: Reminder, data: BackupData, now: ZonedDateTime,
    delivered: LocalDate?): ReminderOccurrence? {
    if (!reminder.enabled) return null
    if (reminder.kind != ReminderKind.PERIOD_DUE) {
        val time = nextReminderTime(reminder.recurrence, reminder.time, now, delivered) ?: return null
        return ReminderOccurrence(time.toLocalDate(), time)
    }
    if (data.periodRemindersPaused()) return null
    val prediction = predict(data.periods, PredictionSettings(data.settings.typicalCycleLength, data.settings.predictionsPaused),
        data.situation, now.toLocalDate()) as? PredictionState.Range ?: return null
    val date = prediction.earliest.minusDays(requireNotNull(reminder.daysBefore).toLong())
    if (date == delivered || now.toLocalDate() > prediction.earliest) return null
    val requested = date.atTime(reminder.time).atZone(now.zone)
    return ReminderOccurrence(date, if (requested > now) requested else now)
}

class ReminderScheduler(private val work: WorkManager, private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {
    private val scheduling = Mutex()
    suspend fun reconcile(repository: Repository, force: Boolean = false) = scheduling.withLock {
        val data = repository.snapshot()
        val current = now()
        val occurrences = data.reminders.mapNotNull { reminder ->
            reminderOccurrence(reminder, data, current, repository.lastDeliveredDate(reminder.id))?.let { reminder to it }
        }
        withContext(Dispatchers.IO) {
            // Retire the v1 periodic/daily chains, including after an app upgrade.
            work.cancelUniqueWork(PERIOD_WORK).result.get()
            work.cancelUniqueWork(DAILY_WORK).result.get()
            val desired = occurrences.map { (r, o) -> workName(r.id, o.date) }.toSet()
            work.getWorkInfosByTag(TAG).get().filter { !it.state.isFinished && it.tags.none(desired::contains) }
                .forEach { work.cancelWorkById(it.id).result.get() }
            occurrences.forEach { (reminder, occurrence) ->
                val name = workName(reminder.id, occurrence.date)
                val existing = work.getWorkInfosForUniqueWork(name).get().firstOrNull { !it.state.isFinished }
                val timeTag = "at:${occurrence.time.toInstant().toEpochMilli()}"
                if (existing == null || force || timeTag !in existing.tags) {
                    work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<ScheduledReminderWorker>()
                            .addTag(TAG).addTag(name).addTag(timeTag)
                            .setInputData(workDataOf("reminderId" to reminder.id, "date" to occurrence.date.toEpochDay()))
                            .setInitialDelay(maxOf(0, Duration.between(current, occurrence.time).toMillis()), TimeUnit.MILLISECONDS)
                            .build()).result.get()
                }
            }
        }
    }
    companion object {
        const val PERIOD_WORK = "period-reminder"
        const val DAILY_WORK = "daily-log"
        const val TAG = "reminder-v2"
        fun workName(id: Long, date: LocalDate) = "reminder:$id:${date.toEpochDay()}"
    }
}
