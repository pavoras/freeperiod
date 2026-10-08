package org.freeperiod.app.ui.onboarding

import android.os.Bundle
import android.os.Parcel
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
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
    private fun model(handle: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(repository, settings, { today }, handle).also { models.put("onboarding", it) }
    @Suppress("DEPRECATION")
    @Test fun onboardingStateSurvivesRecreation() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle)
        vm.setPhase(LifePhase.PERIMENOPAUSE); vm.setMethod(Method.PILL_COMBINED)
        vm.setPillRhythm("24", "4"); vm.setPackStart("2026-04-01")
        vm.markRange(today.minusDays(8), today.minusDays(4))
        vm.setTypicalLength("29"); vm.setPeriodReminder(true)
        vm.next(); vm.next()
        val parcel = Parcel.obtain()
        val restored: Bundle = try {
            parcel.writeBundle(requireNotNull(handle.get<Bundle>("draft")))
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally { parcel.recycle() }
        models.clear()
        val recreated = model(SavedStateHandle(mapOf("draft" to restored)))
        assertEquals(vm.state.value.copy(busy = false, message = null), recreated.state.value)
        assertFalse(recreated.state.value.unknown)
    }
    @Test fun tapToggleSplitsBlock() = runTest {
        val vm = model()
        vm.markRange(today.minusDays(6), today.minusDays(2))
        vm.toggleDay(today.minusDays(4))
        assertEquals(listOf(2, 2), vm.state.value.draftPeriods.map { java.time.temporal.ChronoUnit.DAYS.between(it.start, it.end) + 1 }.map { it.toInt() })
    }
    @Test fun dragAcrossMonths() = runTest {
        val vm = model()
        vm.markRange(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 4, 3))
        assertEquals(6, vm.state.value.selectedDays.size)
        assertEquals(LocalDate.of(2026, 3, 29), vm.state.value.draftPeriods.single().start)
        assertEquals(LocalDate.of(2026, 4, 3), vm.state.value.draftPeriods.single().end)
    }
    @Test fun stillOngoingCreatesOpenPeriod() = runTest {
        val vm = model(); vm.markRange(today.minusDays(2), today)
        vm.finish().join()
        assertFalse(settings.settings.first().onboardingDone)
        assertTrue(vm.state.value.askOngoing)
        vm.setOngoing(true); vm.finish().join()
        assertNull(repository.snapshot().periods.single().end)
        assertTrue(settings.settings.first().onboardingDone)
    }
    @Test fun overlapMergesNotDuplicates() = runTest {
        repository.addPeriod(today.minusDays(8), today.minusDays(4)).getOrThrow()
        val vm = model(); vm.markRange(today.minusDays(6), today.minusDays(2))
        vm.finish().join()
        assertEquals(1, repository.snapshot().periods.size)
        assertEquals(1, vm.state.value.skippedPeriods)
        assertFalse(settings.settings.first().onboardingDone)
        vm.finish().join()
        assertTrue(settings.settings.first().onboardingDone)
        assertEquals(1, repository.snapshot().periods.size)
    }
    @Test fun ongoingAnswerSurvivesMidnight() = runTest {
        var date = today
        val vm = OnboardingViewModel(repository, settings, { date }, SavedStateHandle()).also { models.put("onboarding", it) }
        vm.markRange(today.minusDays(2), today); vm.setOngoing(true)
        date = today.plusDays(1)
        vm.onResume().join(); vm.finish().join()
        assertNull(repository.snapshot().periods.single().end)
    }
    @Test fun cycleLengthHiddenWithEligibleCycle() = runTest {
        val vm = model()
        vm.markRange(today.minusDays(35), today.minusDays(31))
        vm.markRange(today.minusDays(7), today.minusDays(3))
        assertTrue(vm.state.value.hideCycleLength)
        repeat(4) { vm.next() }
        assertEquals(5, vm.state.value.page)
    }
    @Test fun unknownIsNotPreselectedAndInvalidLengthNeverCommits() = runTest {
        val vm = model()
        assertFalse(vm.state.value.unknown)
        vm.markRange(today.minusDays(8), today.minusDays(4))
        vm.setTypicalLength("14"); vm.finish().join()
        assertTrue(repository.snapshot().periods.isEmpty())
        assertFalse(settings.settings.first().onboardingDone)
    }
    @Test fun allStepsAreSkippableAndRemindersStayOff() = runTest {
        val vm = model()
        repeat(6) { vm.skip().join() }
        assertTrue(settings.settings.first().onboardingDone)
        assertTrue(repository.snapshot().periods.isEmpty())
        assertTrue(repository.snapshot().reminders.none { it.enabled })
    }
}
