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
import org.freeperiod.app.data.AppSettings

internal fun nextDailyTime(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val candidate = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (candidate > now) candidate else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

class ReminderScheduler(private val work: WorkManager, private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {
    private val scheduling = Mutex()
    suspend fun sync(settings: AppSettings, paused: Boolean, rescheduleDaily: Boolean = true) = scheduling.withLock {
        withContext(Dispatchers.IO) {
            if (settings.periodReminder && !paused) {
                work.enqueueUniquePeriodicWork(PERIOD_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()).result.get()
            } else work.cancelUniqueWork(PERIOD_WORK).result.get()
            if (settings.dailyReminder) {
                enqueueDaily(settings.dailyReminderTime, if (rescheduleDaily) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
            } else work.cancelUniqueWork(DAILY_WORK).result.get()
        }
    }

    suspend fun afterDaily(settings: AppSettings) = scheduling.withLock {
        withContext(Dispatchers.IO) {
            if (settings.dailyReminder) {
                val nextAlreadyQueued = work.getWorkInfosForUniqueWork(DAILY_WORK).get()
                    .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                if (!nextAlreadyQueued) enqueueDaily(settings.dailyReminderTime, ExistingWorkPolicy.APPEND_OR_REPLACE)
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
