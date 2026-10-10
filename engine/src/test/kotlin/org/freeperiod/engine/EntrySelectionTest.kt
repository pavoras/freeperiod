package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EntrySelectionTest {
    private val date = LocalDate.of(2026, 10, 10)
    private val categories = listOf(
        CustomCategory(1, "Mood items", "builtin:mood", 0, false),
        CustomCategory(2, "Pain items", "builtin:pain", 0, false),
        CustomCategory(3, "Activities", "tag", 10, false, singleChoice = true),
        CustomCategory(4, "Symptoms", "builtin:symptoms", 0, false),
        CustomCategory(5, "Sex items", "builtin:sex", 0, false),
        CustomCategory(6, "Discharge items", "builtin:discharge", 0, false),
    )
    private val tags = listOf(Tag(1, "Calm", categoryId = 1), Tag(2, "Excited", categoryId = 1),
        Tag(3, "Own pain", categoryId = 2), Tag(4, "Walk", categoryId = 3),
        Tag(5, "Run", categoryId = 3), Tag(6, "Own symptom", categoryId = 4),
        Tag(7, "Own sex", categoryId = 5), Tag(8, "Own discharge", categoryId = 6))

    @Test fun builtInSelectionRemovesOnlyItsOwnTags() {
        val log = DayLog(date, tagIds = setOf(1, 2, 3, 6))
        val selected = EntrySelection.builtIn(log, "mood", "GOOD", tags, categories)
        assertEquals(Mood.GOOD, selected.mood)
        assertEquals(setOf(3L, 6L), selected.tagIds)
        assertEquals(selected.copy(mood = null), EntrySelection.builtIn(selected, "mood", null, tags, categories))
    }

    @Test fun ownSingleChoiceReplacesEnumAndOtherOwnItems() {
        val log = DayLog(date, mood = Mood.GREAT, pain = Pain.MILD, sex = Sex.NONE,
            discharge = Discharge.STICKY, tagIds = setOf(1, 4, 6))
        val selected = EntrySelection.tag(log, 2, tags, categories)
        assertNull(selected.mood)
        assertEquals(setOf(2L, 4L, 6L), selected.tagIds)
        assertEquals(setOf(4L, 6L), EntrySelection.tag(selected, 2, tags, categories).tagIds)
        assertNull(EntrySelection.tag(log, 3, tags, categories).pain)
        assertNull(EntrySelection.tag(log, 7, tags, categories).sex)
        assertNull(EntrySelection.tag(log, 8, tags, categories).discharge)
    }

    @Test fun customSingleChoiceAndSymptomsKeepOtherCategories() {
        val log = DayLog(date, symptoms = setOf(Symptom.CRAMPS), tagIds = setOf(1, 4))
        assertEquals(setOf(1L, 5L), EntrySelection.tag(log, 5, tags, categories).tagIds)
        val selected = EntrySelection.tag(log, 6, tags, categories)
        assertEquals(setOf(1L, 4L, 6L), selected.tagIds)
        assertEquals(log.symptoms, selected.symptoms)
        assertEquals(setOf(4L, 5L), EntrySelection.tag(log.copy(tagIds = setOf(4)), 5, tags,
            categories.map { it.copy(singleChoice = false) }).tagIds)
    }
}
