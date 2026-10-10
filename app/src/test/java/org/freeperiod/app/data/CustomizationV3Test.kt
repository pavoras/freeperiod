package org.freeperiod.app.data

import kotlinx.coroutines.runBlocking
import org.freeperiod.app.ui.day.*
import org.freeperiod.engine.*
import org.freeperiod.engine.export.CsvExport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomizationV3Test : DatabaseTest() {
    @Test fun ungroupedTagsOnlyBelongToTheTagsCategory() = runBlocking {
        repository.addTag("Walk")
        val state = dayEntryState(repository.snapshot(), today, today)
        builtInCategories.filter { it.key != "tags" }.forEach { category ->
            assertTrue(category.key, state.entryItems(category, includeHidden = true).none { it.tag != null })
        }
        assertEquals("Walk", state.entryItems(builtInCategories.single { it.key == "tags" }).single().tag?.name)
    }
    @Test fun deletingUsedItemsArchivesAndUnusedItemsAreRemoved() = runBlocking {
        val used = repository.addBuiltInItem("mood", "Calm", "calm", "Mood")
        val unused = repository.addBuiltInItem("mood", "Excited", "sparkles", "Mood")
        repository.saveDayLog(DayLog(today.minusDays(1), tagIds = setOf(used.id)))
        repository.setUiOverride(UiOverride("tag:${unused.id}", false, 2))
        repository.deleteItem(used.id)
        repository.deleteItem(unused.id)
        val data = repository.snapshot()
        assertEquals(listOf(used.copy(archived = true)), data.tags)
        assertTrue(data.overrides.isEmpty())
        val current = dayEntryState(data, today, today)
        assertTrue(current.visibleTags(builtInCategories.first { it.key == "mood" }).isEmpty())
        val past = dayEntryState(data, today.minusDays(1), today)
        assertEquals(used.id, past.visibleTags(builtInCategories.first { it.key == "mood" }).single().id)
    }

    @Test fun painDiaryCreatesItsCategoriesOnceAndNeverTouchesEntries() = runBlocking {
        val sets = listOf(ItemSetCategory("pain_diary:where", "Where it hurts", "person", listOf("Back" to "backache")),
            ItemSetCategory("pain_diary:daily", "Daily life", "home", listOf("Rested in bed" to "bed")))
        repository.addCustomCategory("Daily life")
        repository.turnOnPainDiary(sets, listOf(UiOverride("category:medication", false, 9)))
        var data = repository.snapshot()
        assertTrue(data.situation.painDiary)
        assertFalse(data.overrides.single { it.key == "category:medication" }.hidden)
        assertEquals("Daily life 2", data.customCategories.single { it.itemSet == "pain_diary:daily" }.name)
        assertEquals(listOf("Back", "Rested in bed"), data.tags.map { it.name })
        val back = data.tags.first()
        repository.saveDayLog(DayLog(today, tagIds = setOf(back.id)))
        repository.turnOffPainDiary(archiveCategories = true)
        data = repository.snapshot()
        assertFalse(data.situation.painDiary)
        assertTrue(data.customCategories.filter { it.itemSet != null }.all { it.archived })
        assertEquals(setOf(back.id), data.dayLogs.single().tagIds)
        repository.turnOnPainDiary(sets, emptyList())
        data = repository.snapshot()
        assertEquals(3, data.customCategories.size)
        assertTrue(data.customCategories.none { it.archived })
        assertEquals(2, data.tags.size)
    }

    @Test fun deletingForeverRemovesItemsFromPastDaysAndDropsEmptyDays() = runBlocking {
        val calm = repository.addBuiltInItem("mood", "Calm", "calm", "Mood")
        repository.saveDayLog(DayLog(today.minusDays(2), tagIds = setOf(calm.id)))
        repository.saveDayLog(DayLog(today.minusDays(1), symptoms = setOf(Symptom.CRAMPS), tagIds = setOf(calm.id)))
        repository.deleteItem(calm.id)
        repository.deleteItemsForever(setOf(calm.id))
        val data = repository.snapshot()
        assertEquals(listOf(DayLog(today.minusDays(1), symptoms = setOf(Symptom.CRAMPS))), data.dayLogs)
        assertTrue(data.tags.isEmpty())
    }

    @Test fun deletingACategoryRemovesItsItemsAndOverrides() = runBlocking {
        val category = repository.addCustomCategory("Activities")
        val walk = repository.addTag("Walk", category.id)
        val other = repository.addTag("Read")
        repository.saveDayLog(DayLog(today.minusDays(1), tagIds = setOf(walk.id, other.id)))
        repository.setUiOverride(UiOverride("customCategory:${category.id}", true, 3))
        repository.updateCustomCategory(category.copy(archived = true))
        repository.deleteCustomCategory(category.id)
        val data = repository.snapshot()
        assertTrue(data.customCategories.isEmpty())
        assertEquals(listOf(other), data.tags)
        assertEquals(setOf(other.id), data.dayLogs.single().tagIds)
        assertTrue(data.overrides.isEmpty())
    }

    @Test fun restoringDefaultsKeepsOwnItemsCategoriesAndEntries() = runBlocking {
        val calm = repository.addBuiltInItem("mood", "Calm", "calm", "Mood")
        val category = repository.addCustomCategory("Activities")
        repository.saveDayLog(DayLog(today, mood = Mood.GOOD))
        repository.setUiOverride(UiOverride("category:mood", true, 4, "Feelings", "calm"))
        repository.setUiOverride(UiOverride("item:mood:GOOD", true, 0, "Content", null))
        repository.resetCustomization()
        val data = repository.snapshot()
        assertTrue(data.overrides.isEmpty())
        assertEquals(listOf(calm), data.tags)
        assertTrue(category in data.customCategories)
        assertEquals(Mood.GOOD, data.dayLogs.single().mood)
    }

    @Test fun hiddenPastItemsAndCategoriesRemainReadableInUserOrder() = runBlocking {
        val own = repository.addBuiltInItem("mood", "Calm", "calm", "Mood")
        repository.saveDayLog(DayLog(today.minusDays(1), mood = Mood.GOOD, symptoms = setOf(Symptom.CRAMPS)))
        repository.setUiOverride(UiOverride("category:mood", true, 0, "Feelings", "calm"))
        repository.setUiOverride(UiOverride("item:mood:GOOD", true, -1, "Content", "leaf"))
        repository.setUiOverride(UiOverride("tag:${own.id}", false, -2))
        val data = repository.snapshot()
        val past = dayEntryState(data, today.minusDays(1), today)
        val mood = entryCategories(past).single { it.key == "mood" }
        assertEquals(listOf("tag:${own.id}", "item:mood:GOOD"), past.entryItems(mood).take(2).map { it.key })
        assertFalse(entryCategories(dayEntryState(data, today, today)).any { it.key == "mood" })
    }

    @Test fun categoryTypeChangePreservesPastSelections() = runBlocking {
        val category = repository.addCustomCategory("Activities")
        val walk = repository.addTag("Walk", category.id)
        val run = repository.addTag("Run", category.id)
        val log = DayLog(today.minusDays(1), tagIds = setOf(walk.id, run.id))
        repository.saveDayLog(log)
        repository.updateCustomCategory(category.copy(singleChoice = true))
        assertEquals(log, repository.snapshot().dayLogs.single())
        assertTrue(org.freeperiod.engine.backup.validBackup(repository.snapshot(), today))
    }

    @Test fun csvImportPersistsOwnMoodAndSymptomAndReusesItems() = runBlocking {
        val mood = repository.addBuiltInItem("mood", "Calm", "calm", "Mood")
        val symptom = repository.addBuiltInItem("symptoms", "Own symptom", "leaf", "Symptoms")
        repository.saveDayLog(DayLog(today, tagIds = setOf(mood.id, symptom.id)))
        val before = repository.snapshot()
        val csv = CsvExport.export(before.periods, before.dayLogs, before.tags, before.customCategories, today)
        repository.importCsv(csv, null)
        repository.importCsv(csv, null)
        assertEquals(before, repository.snapshot())
    }
}
