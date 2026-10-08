package org.freeperiod.app.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest : DatabaseTest() {
    private val models = ViewModelStore()
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var storeScope: CoroutineScope
    private lateinit var deviceSettings: SettingsStore
    @Before fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        deviceSettings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) {
            File(temporary.root, "settings.preferences_pb")
        })
    }
    @After fun resetMain() = runBlocking {
        models.clear()
        storeScope.coroutineContext[Job]!!.cancelAndJoin()
        Dispatchers.resetMain()
    }
    private fun model() = SettingsViewModel(repository, deviceSettings).also { models.put("settings", it) }

    @Test fun deleteAllDataClearsEverything() = runTest {
        val tag = repository.addTag("Travel")
        repository.addPeriod(today.minusDays(8), today.minusDays(4)).getOrThrow()
        repository.saveDayLog(DayLog(today, note = "Test", tagIds = setOf(tag.id)))
        repository.updateDomainSettings(BackupSettings(29, true))
        val category = repository.addCustomCategory("Activities", "leaf")
        repository.addTag("Walk", category.id, "leaf")
        repository.setUiOverride(UiOverride("customCategory:${category.id}", true, 2))
        repository.updateSituation(Situation(phase = LifePhase.POSTPARTUM))
        repository.saveReminder(Reminder(0, ReminderKind.CUSTOM, "Check", Recurrence.Once(today), java.time.LocalTime.NOON, true))
        repository.dismissLongCycleHint(repository.snapshot().periods.single().id)
        model().deleteAllData().join()
        assertEquals(BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false)), repository.snapshot())
    }

    @Test fun typicalLengthAcceptsBoundsAndUnknown() = runTest {
        val vm = model()
        for (value in listOf(15, 90, null)) {
            vm.setTypicalLength(value).join()
            assertEquals(value, repository.snapshot().settings.typicalCycleLength)
        }
        vm.setTypicalLength(14).join()
        assertNull(repository.snapshot().settings.typicalCycleLength)
        assertNotNull(vm.state.value.message)
    }

    @Test fun rapidDomainChangesKeepBothValues() = runTest {
        val vm = model()
        vm.setTypicalLength(30)
        vm.setPaused(true).join()
        assertEquals(BackupSettings(30, true), repository.snapshot().settings)
    }

    @Test fun accentUpdatesOnlyDeviceSettings() = runTest {
        val before = repository.snapshot()
        model().setAccent(org.freeperiod.app.ui.theme.Accent.OCEAN).join()
        assertEquals(org.freeperiod.app.ui.theme.Accent.OCEAN, deviceSettings.settings.first().accent)
        assertEquals(before, repository.snapshot())
    }
    @Test fun lockRequiresSecureDevice() = runTest {
        val vm = model()
        vm.setLockEnabled(true).join()
        assertFalse(deviceSettings.settings.first().lockEnabled)
        assertEquals(org.freeperiod.app.R.string.lock_unavailable, vm.state.value.message)
    }
    @Test fun reminderSettingsValidateOffsetAndPersistTime() = runTest {
        val vm = model()
        vm.setPeriodReminder(true).join()
        vm.setReminderDays(5).join()
        vm.setDailyReminder(true).join()
        vm.setReminderTime(java.time.LocalTime.of(8, 15)).join()
        vm.setExplicitNotifications(true).join()
        val value = deviceSettings.settings.first()
        assertTrue(value.periodReminder && value.dailyReminder && value.explicitNotifications)
        assertEquals(5, value.periodReminderDaysBefore)
        assertEquals(java.time.LocalTime.of(8, 15), value.dailyReminderTime)
        vm.setReminderDays(6).join()
        assertEquals(5, deviceSettings.settings.first().periodReminderDaysBefore)
    }
}
