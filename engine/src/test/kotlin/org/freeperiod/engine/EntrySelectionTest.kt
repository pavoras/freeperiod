package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EntrySelectionTest {
    @Test fun dischargeDefaultsToSeveralAndNoneIsExclusiveAcrossOwnItems() {
        fun tap(log: DayLog, name: String) = EntrySelection.builtIn(log, "discharge", name, tags, categories)
        val creamy = tap(DayLog(date), "CREAMY")
        val several = tap(creamy, "UNUSUAL")
        assertEquals(setOf(Discharge.CREAMY, Discharge.UNUSUAL), several.discharge)
        val own = EntrySelection.tag(several, 8, tags, categories)
        assertEquals(several.discharge, own.discharge)
        assertTrue(EntrySelection.valid(own, tags, categories))
        val none = tap(own, "NONE")
        assertEquals(setOf(Discharge.NONE), none.discharge)
        assertFalse(8L in none.tagIds)
        assertEquals(setOf(Discharge.STICKY), tap(none, "STICKY").discharge)
        assertEquals(emptySet<Discharge>(), EntrySelection.tag(none, 8, tags, categories).discharge)
    }

    @Test fun switchingToOnePreservesPastSelectionsAndNextTapReplacesThem() {
        val several = EntrySelection.builtIn(EntrySelection.builtIn(DayLog(date), "discharge", "CREAMY", tags, categories),
            "discharge", "UNUSUAL", tags, categories)
        val pickOne = categories.map { if (it.builtInField() == "discharge") it.copy(singleChoice = true) else it }
        assertEquals(setOf(Discharge.CREAMY, Discharge.UNUSUAL), several.discharge)
        assertTrue(EntrySelection.valid(several, tags, pickOne))
        assertEquals(setOf(Discharge.CREAMY), EntrySelection.builtIn(several, "discharge", "CREAMY", tags, pickOne).discharge)
        val own = EntrySelection.tag(several, 8, tags, pickOne)
        assertEquals(emptySet<Discharge>(), own.discharge)
        assertEquals(setOf(8L), own.tagIds)
    }

    @Test fun sexDefaultsToOneAndCanPickSeveral() {
        val protected = EntrySelection.builtIn(DayLog(date), "sex", "PROTECTED", tags, emptyList())
        assertEquals(setOf(Sex.UNPROTECTED), EntrySelection.builtIn(protected, "sex", "UNPROTECTED", tags, emptyList()).sex)
        val multiple = categories.map { it.copy(singleChoice = false) }
        val several = EntrySelection.builtIn(protected, "sex", "UNPROTECTED", tags, multiple)
        assertEquals(setOf(Sex.PROTECTED, Sex.UNPROTECTED), several.sex)
        assertEquals(setOf(Sex.NONE), EntrySelection.builtIn(several, "sex", "NONE", tags, multiple).sex)
        assertEquals(setOf(Sex.PROTECTED), EntrySelection.builtIn(several, "sex", "UNPROTECTED", tags, multiple).sex)
        val none = EntrySelection.builtIn(several, "sex", "NONE", tags, multiple)
        assertEquals(emptySet<Sex>(), EntrySelection.tag(none, 7, tags, multiple).sex)
        assertEquals(setOf(Sex.PROTECTED), EntrySelection.builtIn(none, "sex", "PROTECTED", tags, multiple).sex)
    }

    @Test fun ownItemsFollowSeveralAndOneIncludingPreviouslySelectedItems() {
        val moreTags = tags + Tag(9, "Another discharge", categoryId = 6)
        val log = DayLog(date, discharge = setOf(Discharge.CREAMY), tagIds = setOf(8))
        val several = EntrySelection.tag(log, 9, moreTags, categories)
        assertEquals(setOf(8L, 9L), several.tagIds)
        assertEquals(log.discharge, several.discharge)
        val one = categories.map { if (it.builtInField() == "discharge") it.copy(singleChoice = true) else it }
        assertTrue(EntrySelection.valid(several, moreTags, one))
        val selected = EntrySelection.tag(several, 8, moreTags, one)
        assertEquals(setOf(8L), selected.tagIds)
        assertTrue(selected.discharge.isEmpty())
        assertTrue(EntrySelection.tag(selected, 8, moreTags, one).isEmpty())
        assertEquals(setOf(Discharge.STICKY), EntrySelection.builtIn(several, "discharge", "STICKY", moreTags, one).discharge)
        assertTrue(EntrySelection.builtIn(several, "discharge", "STICKY", moreTags, one).tagIds.isEmpty())
    }

    private val date = LocalDate.of(2026, 10, 10)
    private val categories = listOf(
        CustomCategory(1, "Mood items", "builtin:mood", 0, false),
        CustomCategory(2, "Pain items", "builtin:pain", 0, false),
        CustomCategory(3, "Activities", "tag", 10, false, singleChoice = true),
        CustomCategory(4, "Symptoms", "builtin:symptoms", 0, false),
        CustomCategory(5, "Sex items", "builtin:sex", 0, false, singleChoice = true),
        CustomCategory(6, "Discharge items", "builtin:discharge", 0, false),
    )
    private val tags = listOf(Tag(1, "Calm", categoryId = 1), Tag(2, "Excited", categoryId = 1),
        Tag(3, "Own pain", categoryId = 2), Tag(4, "Walk", categoryId = 3),
        Tag(5, "Run", categoryId = 3), Tag(6, "Own symptom", categoryId = 4),
        Tag(7, "Own sex", categoryId = 5), Tag(8, "Own discharge", categoryId = 6))

    @Test fun countsGoUpAndDownAndZeroRemovesTheItem() {
        val once = EntrySelection.setCount(DayLog(date), 9, 1)
        assertEquals(setOf(9L), once.tagIds)
        assertTrue(once.tagCounts.isEmpty())
        val thrice = EntrySelection.setCount(once, 9, 3)
        assertEquals(3, thrice.count(9))
        assertEquals(mapOf(9L to 3), thrice.tagCounts)
        assertEquals(1, EntrySelection.setCount(thrice, 9, 1).count(9))
        assertEquals(MAX_DAILY_COUNT, EntrySelection.setCount(thrice, 9, 500).count(9))
        assertEquals(DayLog(date), EntrySelection.setCount(thrice, 9, 0))
    }

    @Test fun togglingOrReplacingACountedItemDropsItsCount() {
        val medication = CustomCategory(7, "Medication", "builtin:medication", 0, false)
        val pills = Tag(9, "Own medicine", categoryId = 7)
        val counted = DayLog(date, tagIds = setOf(9), tagCounts = mapOf(9L to 2))
        assertEquals(DayLog(date), EntrySelection.tag(counted, 9, tags + pills, categories + medication))
        assertTrue(medication.isCounted())
        assertTrue(EntrySelection.valid(counted, tags + pills, categories + medication))
        assertFalse(EntrySelection.valid(DayLog(date, tagCounts = mapOf(9L to 2)), tags + pills, categories + medication))
        assertFalse(EntrySelection.valid(counted.copy(tagCounts = mapOf(9L to 1)), tags + pills, categories + medication))
    }

    @Test fun builtInSelectionRemovesOnlyItsOwnTags() {
        val log = DayLog(date, tagIds = setOf(1, 2, 3, 6))
        val selected = EntrySelection.builtIn(log, "mood", "GOOD", tags, categories)
        assertEquals(Mood.GOOD, selected.mood)
        assertEquals(setOf(3L, 6L), selected.tagIds)
        assertEquals(selected.copy(mood = null), EntrySelection.builtIn(selected, "mood", null, tags, categories))
    }

    @Test fun ownSingleChoiceReplacesEnumAndOtherOwnItems() {
        val log = DayLog(date, mood = Mood.GREAT, pain = Pain.MILD, sex = setOf(Sex.NONE),
            discharge = setOf(Discharge.STICKY), tagIds = setOf(1, 4, 6))
        val selected = EntrySelection.tag(log, 2, tags, categories)
        assertNull(selected.mood)
        assertEquals(setOf(2L, 4L, 6L), selected.tagIds)
        assertEquals(setOf(4L, 6L), EntrySelection.tag(selected, 2, tags, categories).tagIds)
        assertNull(EntrySelection.tag(log, 3, tags, categories).pain)
        assertTrue(EntrySelection.tag(log, 7, tags, categories).sex.isEmpty())
        assertEquals(log.discharge, EntrySelection.tag(log, 8, tags, categories).discharge)
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
