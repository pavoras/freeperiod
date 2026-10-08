package org.freeperiod.app.reminders

import androidx.work.*
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

internal fun BackupData.periodRemindersPaused(): Boolean = settings.predictionsPaused || situation.predictionMode() != PredictionMode.STATISTICAL

internal fun nextDailyTime(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val candidate = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (candidate > now) candidate else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

class ReminderScheduler(private val work: WorkManager, private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {
    private val scheduling = Mutex()
    suspend fun sync(reminders: List<Reminder>, paused: Boolean, rescheduleDaily: Boolean = true) = scheduling.withLock {
        withContext(Dispatchers.IO) {
            if (reminders.any { it.kind == ReminderKind.PERIOD_DUE && it.enabled } && !paused) {
                work.enqueueUniquePeriodicWork(PERIOD_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()).result.get()
            } else work.cancelUniqueWork(PERIOD_WORK).result.get()
            val daily = reminders.firstOrNull { it.kind == ReminderKind.DAILY_LOG && it.enabled }
            if (daily != null) {
                enqueueDaily(daily.time, if (rescheduleDaily) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
            } else work.cancelUniqueWork(DAILY_WORK).result.get()
        }
    }

    suspend fun afterDaily(reminders: List<Reminder>) = scheduling.withLock {
        withContext(Dispatchers.IO) {
            val daily = reminders.firstOrNull { it.kind == ReminderKind.DAILY_LOG && it.enabled }
            if (daily != null) {
                val nextAlreadyQueued = work.getWorkInfosForUniqueWork(DAILY_WORK).get()
                    .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                if (!nextAlreadyQueued) enqueueDaily(daily.time, ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
            else work.cancelUniqueWork(DAILY_WORK).result.get()
        }
    }

    private fun enqueueDaily(time: LocalTime, policy: ExistingWorkPolicy) {
        val current = now()
        val delay = Duration.between(current, nextDailyTime(current, time)).toMillis()
        work.enqueueUniqueWork(DAILY_WORK, policy, OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()).result.get()
    }

    companion object {
        const val PERIOD_WORK = "period-reminder"
        const val DAILY_WORK = "daily-log"
    }
}
