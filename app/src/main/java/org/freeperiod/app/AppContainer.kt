package org.freeperiod.app

import android.content.Context
import androidx.room.Room
import java.time.LocalDate
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.freeperiod.app.data.db.MIGRATION_1_2
import org.freeperiod.app.data.db.MIGRATION_2_3
import org.freeperiod.app.backup.BackupIo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.freeperiod.app.reminders.*
import org.freeperiod.engine.predictionMode

class AppContainer(context: Context) {
    val clock: () -> LocalDate = { LocalDate.now() }
    private val database = Room.databaseBuilder(context.applicationContext,
        FreePeriodDatabase::class.java, "freeperiod.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    val settings = SettingsStore(context)
    val repository: Repository = Repository(database, afterRestore = { data ->
        settings.resetReminderDelivery(data.reminders)
        reminderScheduler.reconcile(repository, force = true)
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
            combine(repository.reminders, repository.domainSettings, repository.situation, repository.periods) { _, _, _, _ -> Unit }
                .collect { reminderScheduler.reconcile(repository) }
        }
    }
}
