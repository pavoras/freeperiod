package org.freeperiod.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.freeperiod.engine.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderPreferencesTest : DatabaseTest() {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    @Before fun openStore() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) {
            File(temporary.root, "settings.preferences_pb")
        })
    }
    @After fun closeStore() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    @Test fun reminderPrefsMigrateOnce() = runBlocking {
        settings.update { it.copy(periodReminder = true, periodReminderDaysBefore = 4,
            dailyReminder = true, dailyReminderTime = LocalTime.of(8, 15)) }
        settings.migrateReminders(repository)
        val before = repository.snapshot().reminders
        assertEquals(2, before.size)
        assertEquals(4, before.single { it.kind == ReminderKind.PERIOD_DUE }.daysBefore)
        assertEquals(LocalTime.of(8, 15), before.single { it.kind == ReminderKind.DAILY_LOG }.time)
        assertTrue(before.all { it.enabled })
        assertTrue(settings.settings.first().remindersMigrated)
        settings.migrateReminders(repository)
        assertEquals(before, repository.snapshot().reminders)
    }

    @Test fun migrationRetryKeepsExistingRowsAndDeliveryIdentity() = runBlocking {
        repository.ensureLegacyReminders(AppSettings(periodReminder = true))
        val before = repository.snapshot().reminders
        repository.markReminderDelivered(before.first().id, today)
        settings.migrateReminders(repository)
        assertEquals(before, repository.snapshot().reminders)
        assertEquals(today.toEpochDay(), db.reminderDao().getAll().first().lastDeliveredDate)
    }

    @Test fun legacyControlsUpdateTableWithoutRevertingRestore() = runBlocking {
        settings.migrateReminders(repository)
        settings.update { it.copy(dailyReminder = true, dailyReminderTime = LocalTime.of(9, 30)) }
        assertTrue(repository.snapshot().reminders.single { it.kind == ReminderKind.DAILY_LOG }.enabled)
        val restored = repository.snapshot().copy(reminders = emptyList())
        repository.replaceAll(restored)
        settings.update { it.copy(accent = org.freeperiod.app.ui.theme.Accent.OCEAN) }
        assertTrue(repository.snapshot().reminders.isEmpty())
    }

    @Test fun restoreResetsLegacyDeliveryIdentity() = runBlocking {
        settings.migrateReminders(repository)
        settings.update { it.copy(lastNotifiedPeriodId = 42) }
        val restoring = Repository(db, clock = { today }, afterRestore = { settings.resetReminderDelivery() })
        restoring.replaceAll(repository.snapshot())
        assertNull(settings.settings.first().lastNotifiedPeriodId)
    }

    @Test fun restoreSynchronizesLegacyControlsWithoutChangingRows() = runBlocking {
        settings.migrateReminders(repository)
        val data = repository.snapshot().copy(reminders = listOf(
            Reminder(8, ReminderKind.DAILY_LOG, "Diary", Recurrence.Weekly(java.time.DayOfWeek.MONDAY), LocalTime.of(8, 15), true),
        ))
        val restoring = Repository(db, clock = { today }, afterRestore = { restored -> settings.resetReminderDelivery(restored.reminders) })
        restoring.replaceAll(data)
        assertEquals(data, repository.snapshot())
        assertTrue(settings.settings.first().dailyReminder)
        assertEquals(LocalTime.of(8, 15), settings.settings.first().dailyReminderTime)
        assertFalse(settings.settings.first().periodReminder)
        settings.update { it.copy(dailyReminder = false) }
        assertFalse(repository.snapshot().reminders.single().enabled)
        assertEquals(Recurrence.Weekly(java.time.DayOfWeek.MONDAY), repository.snapshot().reminders.single().recurrence)
    }

    @Test fun failedReminderWriteKeepsPreferences() = runBlocking {
        settings.migrateReminders(repository)
        val before = settings.settings.first()
        assertTrue(runCatching { settings.update { it.copy(periodReminderDaysBefore = -1) } }.isFailure)
        assertEquals(before, settings.settings.first())
        assertEquals(before.periodReminderDaysBefore,
            repository.snapshot().reminders.single { it.kind == ReminderKind.PERIOD_DUE }.daysBefore)
    }
}
