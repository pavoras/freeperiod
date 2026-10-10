package org.freeperiod.app.ui.settings

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.ui.day.*
import org.freeperiod.engine.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackingSettingsViewModelTest : DatabaseTest() {
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun reset() { models.clear(); Dispatchers.resetMain() }
    private fun model() = TrackingSettingsViewModel(repository).also { models.put("tracking", it) }
    @Test fun hideAndReorderPreserveNamesAndIcons() = runTest {
        val vm = model()
        vm.appearance(UiOverride("item:mood:GOOD", false, 3, "Content", "calm")).join()
        vm.override("item:mood:GOOD", true, 3).join()
        vm.reorder(listOf("item:mood:GOOD", "item:mood:BAD")).join()
        assertEquals(UiOverride("item:mood:GOOD", true, 0, "Content", "calm"),
            repository.snapshot().overrides.single { it.key == "item:mood:GOOD" })
    }

    @Test fun painDiaryShowsPainAndMedicationAndMedicationSwitchKeepsItsPlace() = runTest {
        val vm = model()
        vm.override("category:pain", true, 2).join()
        vm.painDiary(true, listOf(org.freeperiod.app.data.ItemSetCategory("pain_diary:where", "Where it hurts", "person", listOf("Back" to "backache")))).join()
        var overrides = repository.snapshot().overrides
        assertFalse(overrides.single { it.key == "category:pain" }.hidden)
        val medication = overrides.single { it.key == "category:medication" }
        assertFalse(medication.hidden)
        val order = entryCategories(dayEntryState(repository.snapshot(), today, today), includeHidden = true).map { it.overrideKey }
        val pain = order.indexOf("category:pain")
        assertEquals(listOf("category:pain", "category:medication", "customCategory:1"), order.subList(pain, pain + 3))
        vm.showCategory("medication", false).join()
        overrides = repository.snapshot().overrides
        assertEquals(medication.copy(hidden = true), overrides.single { it.key == "category:medication" })
        vm.painDiary(false, emptyList(), archive = false).join()
        assertFalse(repository.snapshot().situation.painDiary)
        assertFalse(repository.snapshot().customCategories.single().archived)
    }

    @Test fun situationPreferencePersistsAcrossPhaseChanges() = runTest {
        val vm = model()
        vm.situation(Situation(fertileWindowEnabled = false)).join()
        assertFalse(repository.snapshot().situation.fertileWindowEnabled)
        vm.situation(repository.snapshot().situation.copy(phase = LifePhase.PREGNANT)).join()
        vm.situation(repository.snapshot().situation.copy(phase = LifePhase.REGULAR)).join()
        assertFalse(repository.snapshot().situation.fertileWindowEnabled)
    }
    @Test fun customSymptomsStayInSymptomsAndKeepCategoryNamesUnique() = runTest {
        repository.addCustomCategory("Symptoms")
        val vm = model()
        vm.item("My item", "warmth", null, "symptoms", "Symptoms").join()
        val data = repository.snapshot()
        assertEquals(2, data.customCategories.size)
        val state = dayEntryState(data, today, today)
        val symptoms = entryCategories(state).single { it.key == "symptoms" }
        assertEquals("My item", state.visibleTags(symptoms).single().name)
        assertEquals(1, entryCategories(state).count { it.category != null })
    }
    @Test fun reorderPersistsAndKeepsAutomaticPhaseItems() = runTest {
        val vm = model()
        val state = dayEntryState(repository.snapshot(), today, today)
        val keys = entryCategories(state, includeHidden = true).map { it.overrideKey }.reversed()
        vm.reorder(keys).join()
        vm.situation(Situation(phase = LifePhase.TRYING_TO_CONCEIVE)).join()
        val after = dayEntryState(repository.snapshot(), today, today)
        assertEquals("ovulation_test", entryCategories(after).first().key)
    }
}
