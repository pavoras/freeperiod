package org.freeperiod.app.reminders

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.FreePeriodApp
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupSettings
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderWorkerTest : DatabaseTest() {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private lateinit var work: WorkManager
    private lateinit var scheduler: ReminderScheduler
    private lateinit var delivery: ReminderDelivery
    private val posted = mutableListOf<Pair<Boolean, Int>>()
    private var notificationsEnabled = true
    private var dailyPosted = 0
    private var now = ZonedDateTime.of(2026, 4, 12, 19, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    @Before fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temporary.root, "settings.preferences_pb") })
        runBlocking { (context.applicationContext as FreePeriodApp).container.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        work = WorkManager.getInstance(context)
        scheduler = ReminderScheduler(work) { now }
        delivery = ReminderDelivery(repository, settings, object : NotificationDelivery {
            override fun period(days: Int, explicit: Boolean): Boolean {
                if (notificationsEnabled) posted += explicit to days
                return notificationsEnabled
            }
            override fun daily(explicit: Boolean): Boolean {
                if (notificationsEnabled) dailyPosted++
                return notificationsEnabled
            }
        }) { today }
    }
    @After fun closeStore() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
    private fun worker(): ReminderWorker = TestListenableWorkerBuilder<ReminderWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                ReminderWorker(appContext, workerParameters, delivery)
        }).build()
    private fun org.freeperiod.app.data.AppSettings.reminders() = listOf(
        Reminder(1, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), periodReminder, periodReminderDaysBefore),
        Reminder(2, ReminderKind.DAILY_LOG, null, Recurrence.Daily, dailyReminderTime, dailyReminder),
    )
    private suspend fun seed() {
        repository.addPeriod(today.minusDays(23), today.minusDays(19)).getOrThrow()
        repository.updateDomainSettings(BackupSettings(28, false))
        settings.update { it.copy(periodReminder = true) }
    }

    @Test fun postsOnceTwoDaysBefore() = runBlocking {
        seed()
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        worker().doWork()
        assertEquals(listOf(false to 2), posted)
        assertEquals(repository.snapshot().periods.single().id, settings.settings.first().lastNotifiedPeriodId)
    }
    @Test fun neutralTextByDefault() = runBlocking {
        seed(); worker().doWork()
        assertFalse(posted.single().first)
    }
    @Test fun nothingWhenPaused() = runBlocking {
        seed(); repository.updateDomainSettings(BackupSettings(28, true)); worker().doWork()
        assertTrue(posted.isEmpty())
    }
    @Test fun tableEnabledFlagControlsDelivery() = runBlocking {
        seed()
        settings.migrateReminders(repository)
        val reminder = repository.snapshot().reminders.single { it.kind == ReminderKind.PERIOD_DUE }
        repository.saveReminder(reminder.copy(enabled = false))
        worker().doWork()
        assertTrue(posted.isEmpty())
        settings.update { it.copy(periodReminder = false) }
        repository.saveReminder(reminder.copy(enabled = true))
        worker().doWork()
        assertEquals(1, posted.size)
        assertEquals(repository.snapshot().periods.single().id, settings.settings.first().lastNotifiedPeriodId)
    }

    @Test fun situationPausesPeriodDelivery() = runBlocking {
        seed()
        repository.updateSituation(Situation(phase = LifePhase.PREGNANT))
        worker().doWork()
        assertTrue(posted.isEmpty())
        repository.updateSituation(Situation())
        worker().doWork()
        assertEquals(1, posted.size)
    }
    @Test fun disabledNotificationsDoNotMarkDelivered() = runBlocking {
        seed(); notificationsEnabled = false; worker().doWork()
        assertNull(settings.settings.first().lastNotifiedPeriodId)
        notificationsEnabled = true; worker().doWork()
        assertEquals(1, posted.size)
    }
    @Test fun scheduleCancelledWhenDisabled() = runBlocking {
        scheduler.sync(org.freeperiod.app.data.AppSettings(periodReminder = true, dailyReminder = true).reminders(), paused = false)
        scheduler.sync(org.freeperiod.app.data.AppSettings().reminders(), paused = false)
        assertTrue(work.getWorkInfosForUniqueWork(ReminderScheduler.PERIOD_WORK).get().all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().all { it.state == WorkInfo.State.CANCELLED })
    }
    @Test fun pauseCancelsOnlyPeriodWork() = runBlocking {
        scheduler.sync(org.freeperiod.app.data.AppSettings(periodReminder = true, dailyReminder = true).reminders(), paused = true)
        assertTrue(work.getWorkInfosForUniqueWork(ReminderScheduler.PERIOD_WORK).get().isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().single().state)
    }
    @Test fun dailyReminderRescheduledAfterTimeChange() = runBlocking {
        val preferences = org.freeperiod.app.data.AppSettings(dailyReminder = true, dailyReminderTime = LocalTime.of(20, 0))
        scheduler.sync(preferences.reminders(), paused = false)
        val first = work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().single().id
        now = now.withZoneSameInstant(ZoneId.of("America/New_York"))
        settings.update { preferences }
        rescheduleAfterTimeChange(Intent.ACTION_TIMEZONE_CHANGED, repository, settings, scheduler)
        val active = work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().filter { !it.state.isFinished }
        assertEquals(1, active.size)
        assertNotEquals(first, active.single().id)
        assertEquals(LocalTime.of(20, 0), nextDailyTime(now, preferences.dailyReminderTime).toLocalTime())
        assertEquals(now.zone, nextDailyTime(now, preferences.dailyReminderTime).zone)
    }
    @Test fun dailyTimeUsesNextLocalDateAndDst() {
        val berlin = ZoneId.of("Europe/Berlin")
        val before = ZonedDateTime.of(2026, 3, 28, 21, 0, 0, 0, berlin)
        val next = nextDailyTime(before, LocalTime.of(20, 0))
        assertEquals(LocalDate.of(2026, 3, 29), next.toLocalDate())
        assertEquals(LocalTime.of(20, 0), next.toLocalTime())
        assertEquals(22, Duration.between(before, next).toHours())
    }
    @Test fun dailyWorkerRequeuesNextRunEvenWhenPredictionsPaused() = runBlocking {
        settings.update { it.copy(dailyReminder = true) }
        repository.updateDomainSettings(BackupSettings(null, true))
        val worker = TestListenableWorkerBuilder<DailyReminderWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    DailyReminderWorker(appContext, workerParameters, delivery, repository, scheduler)
            }).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, dailyPosted)
        assertEquals(1, work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().count { !it.state.isFinished })
    }
    @Test fun initialSyncKeepsDueDailyWork() = runBlocking {
        val preferences = org.freeperiod.app.data.AppSettings(dailyReminder = true)
        scheduler.sync(preferences.reminders(), paused = false)
        val first = work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().single().id
        scheduler.sync(preferences.reminders(), paused = false, rescheduleDaily = false)
        assertEquals(first, work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().single().id)
    }
    @Test fun periodChangesKeepPendingDailyRun() = runBlocking {
        val preferences = org.freeperiod.app.data.AppSettings(dailyReminder = true, periodReminder = true)
        scheduler.sync(preferences.reminders(), paused = false)
        val first = work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().single().id
        scheduler.sync(preferences.reminders(), paused = true, rescheduleDaily = false)
        assertEquals(first, work.getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get().single().id)
    }
}
