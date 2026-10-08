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
    private val posted = mutableListOf<Long>()
    private var notificationsEnabled = true
    private var now = ZonedDateTime.of(2026, 4, 12, 19, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    @Before fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temporary.root, "settings.preferences_pb") })
        runBlocking { (context.applicationContext as FreePeriodApp).container.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        work = WorkManager.getInstance(context)
        scheduler = ReminderScheduler(work) { now }
        delivery = ReminderDelivery(repository, settings, object : NotificationDelivery {
            override fun period(days: Int, explicit: Boolean) = notificationsEnabled
            override fun daily(explicit: Boolean) = notificationsEnabled
            override fun reminder(reminder: Reminder, days: Int?, explicit: Boolean): Boolean {
                if (notificationsEnabled) posted += reminder.id
                return notificationsEnabled
            }
        }) { now.toLocalDate() }
    }
    @After fun closeStore() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
    private suspend fun daily(recurrence: Recurrence = Recurrence.Daily) = repository.saveReminder(
        Reminder(0, ReminderKind.DAILY_LOG, null, recurrence, LocalTime.of(20, 0), true))
    private fun active() = work.getWorkInfosByTag(ReminderScheduler.TAG).get().filter { !it.state.isFinished }
    private fun worker(id: Long, date: LocalDate): ScheduledReminderWorker = TestListenableWorkerBuilder<ScheduledReminderWorker>(context)
        .setInputData(workDataOf("reminderId" to id, "date" to date.toEpochDay()))
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                ScheduledReminderWorker(appContext, workerParameters, delivery, repository, scheduler)
        }).build()
    @Test fun reminderReschedulesAfterFiring() = runBlocking {
        val reminder = daily()
        scheduler.reconcile(repository)
        now = now.withHour(20).withMinute(1)
        assertEquals(ListenableWorker.Result.success(), worker(reminder.id, today).doWork())
        assertEquals(listOf(reminder.id), posted)
        assertEquals(today, repository.lastDeliveredDate(reminder.id))
        assertTrue(active().single().tags.contains(ReminderScheduler.workName(reminder.id, today.plusDays(1))))
        worker(reminder.id, today).doWork()
        assertEquals(1, posted.size)
    }
    @Test fun everyNMonthsNoDrift() = runBlocking {
        val recurrence = Recurrence.EveryNMonths(3, LocalDate.of(2026, 1, 31))
        val reminder = daily(recurrence)
        val expected = listOf(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 10, 31))
        for (date in expected) {
            scheduler.reconcile(repository)
            assertTrue(active().single().tags.contains(ReminderScheduler.workName(reminder.id, date)))
            now = date.atTime(20, 1).atZone(now.zone)
            worker(reminder.id, date).doWork()
        }
        assertEquals(3, posted.size)
    }
    @Test fun disabledNotificationsDoNotMarkDelivered() = runBlocking {
        val reminder = daily()
        notificationsEnabled = false
        now = now.withHour(20)
        worker(reminder.id, today).doWork()
        assertNull(repository.lastDeliveredDate(reminder.id))
        assertTrue(posted.isEmpty())
    }
    @Test fun scheduleCancelledWhenDisabledOrDeleted() = runBlocking {
        val reminder = daily()
        scheduler.reconcile(repository)
        assertEquals(1, active().size)
        repository.saveReminder(reminder.copy(enabled = false)); scheduler.reconcile(repository)
        assertTrue(active().isEmpty())
        repository.saveReminder(reminder); scheduler.reconcile(repository)
        repository.deleteReminder(reminder.id); scheduler.reconcile(repository)
        assertTrue(active().isEmpty())
    }
    @Test fun periodSuppressedForEveryIncompatibleSituation() = runBlocking {
        repository.addPeriod(today.minusDays(23), today.minusDays(19)).getOrThrow()
        repository.updateDomainSettings(BackupSettings(28, false))
        val reminder = repository.saveReminder(Reminder(0, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), true, 2))
        val situations = listOf(Situation(phase = LifePhase.PREGNANT), Situation(phase = LifePhase.POSTPARTUM),
            Situation(phase = LifePhase.MENOPAUSE), Situation(method = Method.PILL_COMBINED),
            Situation(method = Method.PILL_COMBINED, pill = PillSchedule(today.minusDays(1), 21, 7)),
            Situation(method = Method.PILL_COMBINED, pill = PillSchedule(today.minusDays(1), 28, 0)))
        for (situation in situations) {
            repository.updateSituation(situation); scheduler.reconcile(repository)
            assertTrue(active().isEmpty())
            delivery.deliver(reminder.id, today)
        }
        assertTrue(posted.isEmpty())
    }
    @Test fun periodPostsOncePerOccurrence() = runBlocking {
        repository.addPeriod(today.minusDays(23), today.minusDays(19)).getOrThrow()
        repository.updateDomainSettings(BackupSettings(28, false))
        val reminder = repository.saveReminder(Reminder(0, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), true, 2))
        val occurrence = requireNotNull(reminderOccurrence(reminder, repository.snapshot(), now, null))
        delivery.deliver(reminder.id, occurrence.date); delivery.deliver(reminder.id, occurrence.date)
        assertEquals(listOf(reminder.id), posted)
    }
    @Test fun timeChangeReplacesPendingRun() = runBlocking {
        daily(); scheduler.reconcile(repository)
        val first = active().single().id
        now = now.withZoneSameInstant(ZoneId.of("America/New_York"))
        rescheduleAfterTimeChange(Intent.ACTION_TIMEZONE_CHANGED, repository, settings, scheduler)
        assertNotEquals(first, active().single().id)
    }
    @Test fun unchangedSyncKeepsPendingRun() = runBlocking {
        daily(); scheduler.reconcile(repository)
        val first = active().single().id
        scheduler.reconcile(repository)
        assertEquals(first, active().single().id)
    }
    @Test fun dstGapAndOverlapUseCalendarRules() {
        val berlin = ZoneId.of("Europe/Berlin")
        val gap = nextReminderTime(Recurrence.Daily, LocalTime.of(2, 30), ZonedDateTime.of(2026, 3, 28, 21, 0, 0, 0, berlin))!!
        assertEquals(LocalTime.of(3, 30), gap.toLocalTime())
        val overlap = nextReminderTime(Recurrence.Daily, LocalTime.of(2, 30), ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, berlin))!!
        assertEquals(ZoneOffset.ofHours(2), overlap.offset)
    }
}
