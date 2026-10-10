package org.freeperiod.app.ui.day

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.Repository
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
class DayEntryViewModelTest : DatabaseTest() {
    private val models = ViewModelStore()
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { models.clear(); Dispatchers.resetMain() }
    private fun viewModel(date: java.time.LocalDate = today) = DayEntryViewModel(date, repository) { today }
        .also { models.put("day", it) }

    @Test fun queuedEnumAndOwnChoicesRemainExclusive() = runTest {
        val first = repository.addBuiltInItem("mood", "Calm", "calm", "Mood")
        val second = repository.addBuiltInItem("mood", "Excited", "sparkles", "Mood")
        val activity = repository.addTag("Walk")
        val vm = viewModel()
        vm.toggleTag(activity.id)
        vm.setMood(Mood.GOOD)
        vm.toggleTag(first.id)
        vm.toggleTag(second.id).join()
        assertNull(repository.snapshot().dayLogs.single().mood)
        assertEquals(setOf(activity.id, second.id), repository.snapshot().dayLogs.single().tagIds)
        vm.setMood(Mood.GREAT).join()
        assertEquals(Mood.GREAT, repository.snapshot().dayLogs.single().mood)
        assertEquals(setOf(activity.id), repository.snapshot().dayLogs.single().tagIds)
    }

    @Test fun tapSavesImmediately() = runTest {
        val vm = viewModel()
        vm.setMood(Mood.GOOD).join()
        assertEquals(Mood.GOOD, repository.snapshot().dayLogs.single().mood)
    }

    @Test fun clearDayUndoRestores() = runTest {
        val tag = repository.addTag("Travel")
        val log = DayLog(today, flow = FlowLevel.NONE, mood = Mood.GOOD,
            symptoms = setOf(Symptom.HEADACHE), note = "Test", tagIds = setOf(tag.id))
        repository.saveDayLog(log)
        val vm = viewModel()
        vm.clearDay().join()
        assertTrue(repository.snapshot().dayLogs.isEmpty())
        val event = vm.events.first() as DayEntryEvent.Cleared
        assertEquals(log, event.previous)
        vm.undoClear(event.previous).join()
        assertEquals(listOf(log), repository.snapshot().dayLogs)
    }

    @Test fun suggestStartOnLightFlow() = runTest {
        val vm = viewModel()
        vm.setFlow(FlowLevel.LIGHT).join()
        assertEquals(DayEntryEvent.SuggestPeriodStart, vm.events.first())
        assertTrue(repository.snapshot().periods.isEmpty())
    }

    @Test fun overlapShowsError() = runTest {
        repository.addPeriod(today.minusDays(5), null).getOrThrow()
        val vm = viewModel()
        vm.startPeriod().join()
        assertEquals(DayEntryError.OVERLAP, vm.state.value.error)
        assertEquals(1, repository.snapshot().periods.size)
    }

    @Test fun futureDayNotEditable() = runTest {
        val vm = viewModel(today.plusDays(1))
        vm.refresh().join()
        assertTrue(vm.state.value.readOnly)
        vm.setMood(Mood.BAD).join()
        vm.startPeriod().join()
        vm.addTag("Travel").join()
        assertTrue(repository.snapshot().dayLogs.isEmpty())
        assertTrue(repository.snapshot().periods.isEmpty())
        assertTrue(repository.snapshot().tags.isEmpty())
    }

    @Test fun rapidChangesKeepEveryFieldAndLatestNote() = runTest {
        val vm = viewModel()
        vm.setMood(Mood.GOOD)
        vm.setPain(Pain.MILD)
        vm.toggleSymptom(Symptom.HEADACHE)
        vm.toggleSymptom(Symptom.CRAMPS)
        vm.setNote("A")
        vm.setNote("AB").join()
        assertEquals(DayLog(today, mood = Mood.GOOD, pain = Pain.MILD,
            symptoms = setOf(Symptom.HEADACHE, Symptom.CRAMPS), note = "AB"), repository.snapshot().dayLogs.single())
    }

    @Test fun historicalPeriodIsCreatedWithExplicitEnd() = runTest {
        repository.addPeriod(today.minusDays(2), null).getOrThrow()
        val date = today.minusDays(30)
        val vm = viewModel(date)
        vm.startPeriod(date.plusDays(4)).join()
        assertNull(vm.state.value.error)
        assertEquals(date.plusDays(4), repository.snapshot().periods.first().end)
    }

    @Test fun archivedTagRemainsLinkedAndCanBeRenamed() = runTest {
        val vm = viewModel()
        vm.addTag("Travel").join()
        val tag = repository.snapshot().tags.single()
        vm.renameTag(tag.id, "Exercise").join()
        vm.archiveTag(tag.id).join()
        assertEquals("Exercise", repository.snapshot().tags.single().name)
        assertTrue(repository.snapshot().tags.single().archived)
        assertEquals(setOf(tag.id), repository.snapshot().dayLogs.single().tagIds)
    }

    @Test fun endToggleAndClearingEndValidateOverlap() = runTest {
        val first = repository.addPeriod(today.minusDays(30), today.minusDays(26)).getOrThrow()
        repository.addPeriod(today.minusDays(2), null).getOrThrow()
        val vm = viewModel(first.end!!)
        vm.setPeriodEnd(false).join()
        assertEquals(DayEntryError.OVERLAP, vm.state.value.error)
        assertEquals(first, repository.snapshot().periods.first())
    }

    @Test fun readOnlyRecomputesOnResume() = runTest {
        var date = today
        val clock = { date }
        val vm = DayEntryViewModel(today.plusDays(1), Repository(db, clock), clock).also { models.put("day", it) }
        vm.refresh().join()
        assertTrue(vm.state.value.readOnly)
        date = today.plusDays(1)
        vm.onResume().join()
        assertFalse(vm.state.value.readOnly)
        vm.setMood(Mood.GOOD).join()
        assertEquals(today.plusDays(1), repository.snapshot().dayLogs.single().date)
    }

    @Test fun duplicateTagNameShowsErrorWithoutChangingLinks() = runTest {
        val vm = viewModel()
        vm.addTag("Travel").join()
        val before = repository.snapshot()
        vm.addTag("travel").join()
        assertEquals(DayEntryError.TAG_NAME, vm.state.value.error)
        assertEquals(before, repository.snapshot())
    }
}
