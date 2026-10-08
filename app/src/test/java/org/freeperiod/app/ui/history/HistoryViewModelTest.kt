package org.freeperiod.app.ui.history

import androidx.lifecycle.ViewModelStore
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.engine.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryViewModelTest : DatabaseTest() {
    private val models = ViewModelStore()
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { models.clear(); Dispatchers.resetMain() }
    private fun viewModel() = HistoryViewModel(repository) { today }.also { models.put("history", it) }

    private suspend fun periods(): List<Period> {
        val starts = listOf("2026-01-01", "2026-01-29", "2026-02-12", "2026-03-14")
        return starts.map { LocalDate.parse(it) }.map { repository.addPeriod(it, it.plusDays(4)).getOrThrow() }
    }

    @Test fun averagesUseEligibleOnly() = runTest {
        periods()
        val vm = viewModel()
        vm.refresh().join()
        assertEquals(29.0, vm.state.value.cycleLength!!, 0.0)
        assertEquals(5.0, vm.state.value.periodLength!!, 0.0)
        assertEquals(2, vm.state.value.eligibleCycles)
        assertEquals(listOf(28, 14, 30), vm.state.value.cycles.map { it.length })
    }

    @Test fun toggleExcludeUpdatesPeriod() = runTest {
        val period = periods().first()
        val vm = viewModel()
        vm.setCycleIncluded(period.id, false).join()
        assertEquals(CycleUse.EXCLUDE, repository.snapshot().periods.first().cycleUse)
        vm.setCycleIncluded(period.id, true).join()
        assertEquals(CycleUse.INCLUDE, repository.snapshot().periods.first().cycleUse)
    }

    @Test fun emptyHistoryState() = runTest {
        val vm = viewModel()
        vm.refresh().join()
        assertTrue(vm.state.value.cycles.isEmpty())
        assertNull(vm.state.value.cycleLength)
        assertNull(vm.state.value.periodLength)
        assertTrue(vm.state.value.symptomCounts.isEmpty())
    }

    @Test fun automaticallyExcludedCycleCanBeIncluded() = runTest {
        val period = periods()[1]
        val vm = viewModel()
        vm.refresh().join()
        assertFalse(vm.state.value.cycles.first { it.startPeriodId == period.id }.eligible)
        vm.setCycleIncluded(period.id, true).join()
        assertEquals(CycleUse.INCLUDE, repository.snapshot().periods.first { it.id == period.id }.cycleUse)
        assertTrue(vm.state.value.cycles.first { it.startPeriodId == period.id }.eligible)
    }

    @Test fun excludedListMatchesVisibleRange() = runTest {
        val periods = periods()
        val vm = viewModel()
        vm.setCycleIncluded(periods.first().id, false).join()
        val visible = setOf(periods[1].id, periods[2].id)
        assertEquals(listOf(periods[1].id), excludedCyclesInRange(vm.state.value.cycles, visible).map { it.startPeriodId })
    }
    @Test fun longCycleHintDismissPersists() = runTest {
        val starts = listOf(134L, 106L, 78L, 50L, 5L).map { today.minusDays(it) }
        val periods = starts.map { repository.addPeriod(it, it.plusDays(4)).getOrThrow() }
        val vm = viewModel(); vm.refresh().join()
        assertEquals(setOf(periods[3].id), vm.state.value.longCycleHintIds)
        vm.dismissLongCycleHint(periods[3].id).join()
        assertEquals(setOf(periods[3].id), repository.snapshot().hintDismissals)
        val recreated = viewModel(); recreated.refresh().join()
        assertTrue(recreated.state.value.longCycleHintIds.isEmpty())
    }

    @Test fun symptomWindowUsesLastThreeCompletedCycles() = runTest {
        val starts = listOf("2025-12-04", "2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26")
            .map(LocalDate::parse)
        starts.forEach { repository.addPeriod(it, it.plusDays(4)).getOrThrow() }
        val from = starts[1]
        val to = starts.last()
        listOf(from.minusDays(1), from, to.minusDays(1), to, today).forEach {
            repository.saveDayLog(DayLog(it, symptoms = setOf(Symptom.HEADACHE)))
        }
        val vm = viewModel()
        vm.refresh().join()
        assertEquals(2, vm.state.value.symptomCounts[Symptom.HEADACHE])
        assertEquals(3, vm.state.value.symptomCycles)
    }

    @Test fun shorterSymptomWindowUsesAllCompletedCycles() = runTest {
        repository.addPeriod(today.minusDays(30), today.minusDays(26)).getOrThrow()
        repository.addPeriod(today.minusDays(2), null).getOrThrow()
        repository.saveDayLog(DayLog(today.minusDays(30), symptoms = setOf(Symptom.CRAMPS)))
        val vm = viewModel()
        vm.refresh().join()
        assertEquals(1, vm.state.value.symptomCycles)
        assertEquals(1, vm.state.value.symptomCounts[Symptom.CRAMPS])
    }
}
