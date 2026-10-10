package org.freeperiod.engine.importing

import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.export.CsvExport
import org.junit.Assert.*
import org.junit.Test

class CsvDiaryTest {
    private val today = LocalDate.of(2026, 10, 10)
    @Test fun aSingleDiaryColumnDoesNotCreateAPeriod() {
        listOf("mood", "pain", "sex", "discharge", "symptoms").forEach { field ->
            val result = CsvImport.parse("date,$field\n$today,My item") as CsvImportResult.Parsed
            assertTrue(field, result.periods.isEmpty())
            assertEquals(field, result.customCategories.single().builtInField())
            assertEquals(setOf(result.tags.single().id), result.dayLogs.single().tagIds)
        }
    }

    @Test fun ambiguousDiaryDatesRequireAFormatEvenWithoutPeriods() {
        val csv = "date,period_day,mood,note\n03/04/2026,,Calm,"
        assertEquals(setOf(CsvDateFormat.DAY_MONTH_YEAR, CsvDateFormat.MONTH_DAY_YEAR),
            (CsvImport.parse(csv) as CsvImportResult.NeedsFormat).formats.toSet())
        val restored = CsvImport.parse(csv, CsvDateFormat.DAY_MONTH_YEAR) as CsvImportResult.Parsed
        assertEquals(LocalDate.of(2026, 4, 3), restored.dayLogs.single().date)
    }

    @Test fun notesAndNamesWithSeparatorsOrEnumNamesRoundTrip() {
        val categories = listOf(CustomCategory(1, "Mood items", "builtin:mood", 0, false),
            CustomCategory(2, "Symptom items", "builtin:symptoms", 0, false),
            CustomCategory(3, "A:B;C", "tag", 0, false))
        val tags = listOf(Tag(1, "GOOD", categoryId = 1), Tag(2, "One;two:50%", categoryId = 2),
            Tag(3, "A;B:C", categoryId = 3))
        val log = DayLog(today, tagIds = setOf(1, 2, 3), note = "  A note,\nwith space  ")
        val csv = CsvExport.export(emptyList(), listOf(log), tags, categories, today)
        val restored = CsvImport.parse(csv) as CsvImportResult.Parsed
        assertEquals(log, restored.dayLogs.single())
        assertEquals(tags.map { it.name }, restored.tags.map { it.name })
        assertEquals("A:B;C", restored.customCategories.single { it.builtInField() == null }.name)
    }
    @Test fun ownMoodAndSymptomsRoundTripIntoTheirFieldColumns() {
        val categories = listOf(CustomCategory(1, "Mood items", "builtin:mood", 0, false),
            CustomCategory(2, "Symptom items", "builtin:symptoms", 0, false))
        val tags = listOf(Tag(10, "Calm", categoryId = 1), Tag(11, "My symptom", categoryId = 2))
        val log = DayLog(today, symptoms = setOf(Symptom.CRAMPS), tagIds = setOf(10, 11), note = "A note, with a comma")
        val csv = CsvExport.export(emptyList(), listOf(log), tags, categories, today)
        assertTrue(csv.contains(",Calm,"))
        assertFalse(csv.contains("Mood items:Calm"))
        val restored = CsvImport.parse(csv) as CsvImportResult.Parsed
        assertTrue(restored.periods.isEmpty())
        val ownMood = restored.tags.single { it.name == "Calm" }
        val ownSymptom = restored.tags.single { it.name == "My symptom" }
        assertEquals(log.copy(tagIds = setOf(ownMood.id, ownSymptom.id)), restored.dayLogs.single())
        assertEquals("builtin:mood", restored.customCategories.single { it.id == ownMood.categoryId }.iconKey)
        assertEquals("builtin:symptoms", restored.customCategories.single { it.id == ownSymptom.categoryId }.iconKey)
        assertEquals(csv, CsvExport.export(restored.periods, restored.dayLogs, restored.tags, restored.customCategories, today))
        val again = CsvImport.parse(csv, tags = restored.tags, customCategories = restored.customCategories) as CsvImportResult.Parsed
        assertEquals(restored.tags, again.tags)
    }

    @Test fun stableBuiltInsAndCustomCategoriesRoundTrip() {
        val category = CustomCategory(1, "Activities", "tag", 0, false)
        val tag = Tag(1, "Walk", categoryId = 1)
        val log = DayLog(today, FlowLevel.LIGHT, Mood.GOOD, setOf(Symptom.HEADACHE), Pain.MILD,
            Sex.PROTECTED, Discharge.CREAMY, tagIds = setOf(1), ovulationTest = OvulationTest.NEGATIVE)
        val csv = CsvExport.export(listOf(Period(1, today, today)), listOf(log), listOf(tag), listOf(category), today)
        val restored = CsvImport.parse(csv) as CsvImportResult.Parsed
        assertEquals(log, restored.dayLogs.single())
        assertEquals("Walk", restored.tags.single().name)
        assertEquals("Activities", restored.customCategories.single().name)
    }

    @Test fun unknownFlowOrOvulationValuesAreRejected() {
        listOf("flow", "ovulation_test").forEach { field ->
            assertEquals(CsvImportResult.Invalid, CsvImport.parse("date,period_day,mood,note,$field\n$today,,Calm,,Extra"))
        }
    }
}
