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
    @Test fun customSymptomsStayInSymptomsAndKeepCategoryNamesUnique() = runTest {
        repository.addCustomCategory("Symptoms")
        val vm = model()
        vm.item("My item", "warmth", null, true, "Symptoms").join()
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
