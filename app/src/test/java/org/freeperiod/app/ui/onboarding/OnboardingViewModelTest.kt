package org.freeperiod.app.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.SettingsStore
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest : DatabaseTest() {
    @get:Rule val temporary = TemporaryFolder()
    private val models = ViewModelStore()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temporary.root, "settings.preferences_pb") })
    }
    @After fun cleanup() = runBlocking { models.clear(); scope.coroutineContext[Job]!!.cancelAndJoin(); Dispatchers.resetMain() }
    private fun model() = OnboardingViewModel(repository, settings) { today }.also { models.put("onboarding", it) }
    @Test fun skipMarksDone() = runTest {
        model().skip().join()
        assertTrue(settings.settings.first().onboardingDone)
        assertTrue(repository.snapshot().periods.isEmpty())
    }
    @Test fun enteringDataCreatesPeriodAndTypicalLength() = runTest {
        val vm = model()
        vm.setStart(today.minusDays(5)); vm.setTypicalLength("29"); vm.setUnknown(false)
        vm.finish().join()
        assertEquals(today.minusDays(5), repository.snapshot().periods.single().start)
        assertNull(repository.snapshot().periods.single().end)
        assertEquals(29, repository.snapshot().settings.typicalCycleLength)
        assertTrue(settings.settings.first().onboardingDone)
        assertFalse(settings.settings.first().periodReminder)
    }
    @Test fun onboardingHasEndedCreatesCompletedPeriod() = runTest {
        val vm = model()
        vm.setStart(today.minusDays(8)); vm.setHasEnded(true); vm.setEnd(today.minusDays(4))
        vm.finish().join()
        assertEquals(today.minusDays(4), repository.snapshot().periods.single().end)
    }
    @Test fun endedPeriodRequiresExplicitEndDate() = runTest {
        val vm = model()
        vm.setStart(today.minusDays(8)); vm.setHasEnded(true); vm.finish().join()
        assertTrue(repository.snapshot().periods.isEmpty())
        assertFalse(settings.settings.first().onboardingDone)
    }
    @Test fun skipOnEveryPageLeavesRemindersOffAndDraftUnsaved() = runTest {
        for (page in 0..2) {
            settings.update { it.copy(onboardingDone = false) }
            val vm = model()
            repeat(page) { vm.next() }
            vm.setStart(today.minusDays(5)); vm.setPeriodReminder(true); vm.setDailyReminder(true)
            vm.skip().join()
            assertTrue(settings.settings.first().onboardingDone)
            assertFalse(settings.settings.first().periodReminder || settings.settings.first().dailyReminder)
            assertTrue(repository.snapshot().periods.isEmpty())
        }
    }
    @Test fun invalidTypicalLengthDoesNotSavePeriod() = runTest {
        val vm = model()
        vm.setStart(today.minusDays(5)); vm.setUnknown(false); vm.setTypicalLength("14")
        vm.finish().join()
        assertTrue(repository.snapshot().periods.isEmpty())
        assertFalse(settings.settings.first().onboardingDone)
    }
}
