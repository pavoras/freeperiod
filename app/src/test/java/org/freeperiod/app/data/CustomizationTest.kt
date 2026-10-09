package org.freeperiod.app.data

import kotlinx.coroutines.runBlocking
import org.freeperiod.engine.*
import org.freeperiod.engine.export.CsvExport
import org.freeperiod.app.ui.day.entryCategories
import org.freeperiod.app.ui.day.dayEntryState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomizationTest : DatabaseTest() {
    @Test fun phaseChangeKeepsData() = runBlocking {
        repository.addPeriod(today.minusDays(10), today.minusDays(6)).getOrThrow()
        repository.saveDayLog(DayLog(today, note = "Keep this"))
        val before = repository.snapshot()
        for (phase in LifePhase.entries) repository.updateSituation(Situation(phase = phase))
        val after = repository.snapshot()
        assertEquals(before.periods, after.periods)
        assertEquals(before.dayLogs, after.dayLogs)
    }
    @Test fun reorderPersists() = runBlocking {
        repository.setUiOverride(UiOverride("category:note", false, -1))
        val state = dayEntryState(repository.snapshot(), today, today)
        assertEquals("note", entryCategories(state).first().key)
    }
    @Test fun customCategoryShowsInEntry() = runBlocking {
        val category = repository.addCustomCategory("Movement", "energetic")
        val item = repository.addTag("Walk", category.id, "energetic")
        repository.saveDayLog(DayLog(today, tagIds = setOf(item.id)))
        val state = dayEntryState(repository.snapshot(), today, today)
        assertTrue(entryCategories(state).any { it.category?.id == category.id })
        assertEquals(setOf(item.id), state.log.tagIds)
    }
    @Test fun reorderingDoesNotHidePhaseSpecificItems() = runBlocking {
        repository.setUiOverride(UiOverride("category:ovulation_test", false, 0))
        assertFalse(entryCategories(dayEntryState(repository.snapshot(), today, today)).any { it.key == "ovulation_test" })
        repository.updateSituation(Situation(phase = LifePhase.TRYING_TO_CONCEIVE))
        assertTrue(entryCategories(dayEntryState(repository.snapshot(), today, today)).any { it.key == "ovulation_test" })
    }
    @Test fun hiddenBuiltInStaysInHistory() = runBlocking {
        repository.saveDayLog(DayLog(today, symptoms = setOf(Symptom.CRAMPS)))
        repository.setUiOverride(UiOverride("item:symptoms:CRAMPS", true, 0))
        assertEquals(setOf(Symptom.CRAMPS), repository.snapshot().dayLogs.single().symptoms)
    }
    @Test fun csvIncludesCustomItems() = runBlocking {
        val category = repository.addCustomCategory("Movement")
        val item = repository.addTag("Walk", category.id)
        repository.saveDayLog(DayLog(today, tagIds = setOf(item.id)))
        val data = repository.snapshot()
        assertTrue(CsvExport.export(data.periods, data.dayLogs, data.tags, data.customCategories, today).contains("Movement:Walk"))
    }
}
