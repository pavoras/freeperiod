package org.freeperiod.app

import android.content.Context
import androidx.room.Room
import java.time.LocalDate
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.freeperiod.app.backup.BackupIo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.freeperiod.app.reminders.*

class AppContainer(context: Context) {
    val clock: () -> LocalDate = { LocalDate.now() }
    private val database = Room.databaseBuilder(context.applicationContext,
        FreePeriodDatabase::class.java, "freeperiod.db").build()
    val repository = Repository(database, clock)
    val settings = SettingsStore(context)
    val backupIo = BackupIo(context.applicationContext.contentResolver)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val notifications = Notifications(context.applicationContext)
    val reminderScheduler by lazy { ReminderScheduler(WorkManager.getInstance(context.applicationContext)) }
    val reminderDelivery = ReminderDelivery(repository, settings, notifications, clock)

    fun observeReminders() {
        notifications.createChannel()
        applicationScope.launch {
            var previousDaily: Pair<Boolean, java.time.LocalTime>? = null
            combine(settings.settings, repository.domainSettings) { device, domain -> device to domain.predictionsPaused }
                .distinctUntilChangedBy { (device, paused) -> listOf(device.periodReminder, device.dailyReminder, device.dailyReminderTime, paused) }
                .collect { (device, paused) ->
                    val daily = device.dailyReminder to device.dailyReminderTime
                    reminderScheduler.sync(device, paused, rescheduleDaily = previousDaily != null && previousDaily != daily)
                    previousDaily = daily
                }
        }
    }
}
