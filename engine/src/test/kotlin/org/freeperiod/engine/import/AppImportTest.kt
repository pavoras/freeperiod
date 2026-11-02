package org.freeperiod.engine.importing

import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.Period
import org.junit.Assert.*
import org.junit.Test

class AppImportTest {
    private val today = LocalDate.parse("2026-10-19")
    private fun d(day: String) = LocalDate.parse(day)

    @Test fun bleedingDaysBecomePeriodsWithoutEdgeSpotting() {
        val days = mapOf(
            // Spotting before and after: the period is the heavier days in between.
            d("2026-01-04") to FlowLevel.SPOTTING, d("2026-01-05") to FlowLevel.HEAVY, d("2026-01-06") to FlowLevel.MEDIUM,
            d("2026-01-07") to FlowLevel.SPOTTING,
            // Spotting only: diary, no period.
            d("2026-01-20") to FlowLevel.SPOTTING, d("2026-01-21") to FlowLevel.SPOTTING,
            // One unlogged day does not split; a bleeding day without strength counts.
            d("2026-02-01") to FlowLevel.LIGHT, d("2026-02-03") to null,
            // Two unlogged days do.
            d("2026-02-06") to FlowLevel.LIGHT,
            d("2026-03-01") to FlowLevel.NONE)
        assertEquals(listOf(Period(0, d("2026-01-05"), d("2026-01-06")), Period(0, d("2026-02-01"), d("2026-02-03")),
            Period(0, d("2026-02-06"), d("2026-02-06"))), BleedingDays.periods(days, today))
    }

    @Test fun bleedingTodayIsOngoing() {
        val days = mapOf(today.minusDays(1) to FlowLevel.HEAVY, today to FlowLevel.MEDIUM)
        assertEquals(listOf(Period(0, today.minusDays(1), null)), BleedingDays.periods(days, today))
    }

    @Test fun mergeKeepsWhatIsLoggedAndFillsTheRest() {
        val existing = DayLog(today, flow = FlowLevel.LIGHT, symptoms = setOf(Symptom.ACNE), note = "mine")
        val imported = DayLog(today, flow = FlowLevel.HEAVY, symptoms = setOf(Symptom.CRAMPS), sex = Sex.PROTECTED, note = "theirs")
        assertEquals(DayLog(today, flow = FlowLevel.LIGHT, symptoms = setOf(Symptom.ACNE, Symptom.CRAMPS), sex = Sex.PROTECTED,
            note = "mine"), BleedingDays.merge(existing, imported, emptyList(), emptyList()))
        assertEquals(imported, BleedingDays.merge(null, imported, emptyList(), emptyList()))
    }

    @Test fun mergeNeverBreaksAPickOneCategory() {
        // Your own sex option (a tag in the built-in category) stays alone.
        val category = CustomCategory(1, "Sex", "builtin:sex", 0, false)
        val tags = listOf(Tag(5, "Toy", categoryId = 1))
        val existing = DayLog(today, tagIds = setOf(5))
        assertEquals(existing, BleedingDays.merge(existing, DayLog(today, sex = Sex.UNPROTECTED), tags, listOf(category)))
    }

    @Test fun clueBackup() {
        // Shape of Clue's .cluedata (version 5) with made-up days; options are strings or lists.
        val json = """
            {"data": [
              {"day": "2026-01-05T00:00:00Z", "period": "heavy", "pain": ["cramps", "ovulation_pain"], "mood": ["happy"],
               "craving": ["sweet_craving"], "sleep": "6_to_9_hours", "tags": ["Long walk", " "]},
              {"day": "2026-01-04T00:00:00Z", "period": "spotting"},
              {"day": "2026-01-06T00:00:00Z", "period": "medium", "energy": ["exhausted"], "sex": ["protected", "high_sex_drive"]},
              {"day": "2026-01-07T00:00:00Z", "period": "spotting", "fluid": "creamy"},
              {"day": "2026-01-15T00:00:00Z", "social": ["sociable"], "tags": ["Long walk"]},
              {"day": "2026-01-16T00:00:00Z", "fluid": "egg_white", "sex": ["withdrawal"], "birth_control": {"type": "pill"}},
              {"day": "2026-10-25T00:00:00Z", "period": "heavy"}
            ],
             "environment": {"application_id": "com.clue.android"}, "export_time": "2026-10-19T10:00:00Z", "version": 5}
        """.trimIndent()
        val parsed = requireNotNull(ClueImport.parse(json.toByteArray(), today))
        assertEquals(listOf(Period(0, d("2026-01-05"), d("2026-01-06"))), parsed.periods)
        assertEquals(listOf(
            DayLog(d("2026-01-04"), flow = FlowLevel.SPOTTING),
            DayLog(d("2026-01-05"), flow = FlowLevel.HEAVY, mood = Mood.GOOD, symptoms = setOf(Symptom.CRAMPS, Symptom.CRAVINGS)),
            DayLog(d("2026-01-06"), flow = FlowLevel.MEDIUM, symptoms = setOf(Symptom.FATIGUE), sex = Sex.PROTECTED, libido = Libido.HIGH),
            DayLog(d("2026-01-07"), flow = FlowLevel.SPOTTING, discharge = Discharge.CREAMY),
            DayLog(d("2026-01-16"), discharge = Discharge.EGG_WHITE, sex = Sex.UNPROTECTED)), parsed.dayLogs)
        assertEquals(mapOf(d("2026-01-05") to setOf("Long walk"), d("2026-01-15") to setOf("Long walk")), parsed.tagNames)
        assertEquals(6, parsed.days)
    }

    @Test fun otherFilesAreNotClue() {
        listOf("""{"operationalData": {"cycles": []}}""", """{"data": []}""", """{"data": [{"day": "2026-01-01T00:00:00Z"}]}""",
            """{"data": [{"period": "heavy"}]}""", "Start,End\n2026-01-01,2026-01-05", "")
            .forEach { assertNull(it, ClueImport.parse(it.toByteArray(), today)) }
    }

    @Test fun bluemoonRows() {
        val bleeding = listOf("2026-03-01" to "SPOTTING", "2026-03-02" to "HEAVY", "2026-03-03" to null, "2026-03-04" to "LIGHT",
            "2026-04-10" to "SPOTTING", "2026-11-01" to "HEAVY")
        val sex = listOf(BluemoonSex("2026-03-10", setOf("IUD")), BluemoonSex("2026-03-12", setOf("CONDOM")),
            BluemoonSex("2026-03-12", emptySet()), BluemoonSex("2026-03-20", setOf("OTHER")))
        val parsed = requireNotNull(BluemoonImport.map(bleeding, sex, today))
        assertEquals(listOf(Period(0, d("2026-03-02"), d("2026-03-04"))), parsed.periods)
        assertEquals(listOf(
            DayLog(d("2026-03-01"), flow = FlowLevel.SPOTTING), DayLog(d("2026-03-02"), flow = FlowLevel.HEAVY),
            DayLog(d("2026-03-04"), flow = FlowLevel.LIGHT),
            DayLog(d("2026-03-10"), sex = Sex.PROTECTED), DayLog(d("2026-03-12"), sex = Sex.UNPROTECTED),
            DayLog(d("2026-03-20"), sex = Sex.PROTECTED), DayLog(d("2026-04-10"), flow = FlowLevel.SPOTTING)), parsed.dayLogs)
        assertNull(BluemoonImport.map(emptyList(), emptyList(), today))
        assertNull(BluemoonImport.map(listOf("03/01/2026" to "HEAVY"), emptyList(), today))
    }
}
