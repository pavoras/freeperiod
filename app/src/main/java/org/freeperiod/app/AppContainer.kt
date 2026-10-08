package org.freeperiod.app

import android.content.Context
import androidx.room.Room
import java.time.LocalDate
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.freeperiod.app.data.db.MIGRATION_1_2
import org.freeperiod.app.backup.BackupIo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.freeperiod.app.reminders.*
import org.freeperiod.engine.predictionMode

class AppContainer(context: Context) {
    val clock: () -> LocalDate = { LocalDate.now() }
    private val database = Room.databaseBuilder(context.applicationContext,
        FreePeriodDatabase::class.java, "freeperiod.db").addMigrations(MIGRATION_1_2).build()
    val settings = SettingsStore(context)
    val repository = Repository(database, afterRestore = { data ->
        settings.resetReminderDelivery(data.reminders)
        reminderScheduler.sync(data.reminders, data.periodRemindersPaused())
    }, clock = clock)
    val backupIo = BackupIo(context.applicationContext.contentResolver)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val notifications = Notifications(context.applicationContext)
    val reminderScheduler by lazy { ReminderScheduler(WorkManager.getInstance(context.applicationContext)) }
    val reminderDelivery = ReminderDelivery(repository, settings, notifications, clock)

    fun observeReminders() {
        notifications.createChannel()
        applicationScope.launch {
            settings.migrateReminders(repository)
            var previousDaily: Pair<Boolean, java.time.LocalTime>? = null
            combine(repository.reminders, repository.domainSettings, repository.situation) { reminders, domain, situation ->
                reminders to (domain.predictionsPaused || situation.predictionMode() != org.freeperiod.engine.PredictionMode.STATISTICAL)
            }.distinctUntilChanged().collect { (reminders, paused) ->
                    val dailyReminder = reminders.firstOrNull { it.kind == org.freeperiod.engine.ReminderKind.DAILY_LOG && it.enabled }
                    val daily = (dailyReminder != null) to (dailyReminder?.time ?: java.time.LocalTime.of(20, 0))
                    reminderScheduler.sync(reminders, paused, rescheduleDaily = previousDaily != null && previousDaily != daily)
                    previousDaily = daily
                }
        }
    }
}
