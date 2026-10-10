package org.freeperiod.engine.export

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

class CsvExportTest {
    private val date = LocalDate.of(2026, 3, 1)
    private val header = "date,period_day,flow,mood,pain,sex,discharge,symptoms,tags,note,ovulation_test,custom_items,medication\r\n"
    private fun csv(logs: List<DayLog>, tags: List<Tag> = emptyList(), periods: List<Period> = emptyList(),
        categories: List<CustomCategory> = emptyList()) = CsvExport.export(periods, logs, tags, categories, today = date.plusDays(29))

    @Test fun csvQuotesCommasQuotesAndNewlines() {
        assertEquals(header + "2026-03-01,,,,,,,,,\"a,\"\"b\"\"\nc\",,,\r\n", csv(listOf(DayLog(date, note = "a,\"b\"\nc"))))
    }

    @Test fun csvHeaderAndOrder() {
        val logs = listOf(DayLog(date.plusDays(1), flow = FlowLevel.NONE), DayLog(date, flow = FlowLevel.LIGHT))
        assertEquals(header + "2026-03-01,,light,,,,,,,,,,\r\n2026-03-02,,none,,,,,,,,,,\r\n", csv(logs))
    }

    @Test fun periodsBecomeDaysInTheSameFileAndAnOngoingPeriodRunsToToday() {
        val periods = listOf(Period(2, date.plusDays(28), null, CycleUse.EXCLUDE), Period(1, date, date.plusDays(2)))
        val logs = listOf(DayLog(date.plusDays(1), flow = FlowLevel.MEDIUM), DayLog(date.plusDays(10), mood = Mood.GOOD))
        assertEquals(header +
            "2026-03-01,yes,,,,,,,,,,,\r\n" +
            "2026-03-02,yes,medium,,,,,,,,,,\r\n" +
            "2026-03-03,yes,,,,,,,,,,,\r\n" +
            "2026-03-11,,,good,,,,,,,,,\r\n" +
            "2026-03-29,yes,,,,,,,,,,,\r\n" +
            "2026-03-30,yes,,,,,,,,,,,\r\n", csv(logs, periods = periods))
    }

    @Test fun csvContainsAllFieldsAndArchivedTagsInStableOrder() {
        val log = DayLog(date, FlowLevel.MEDIUM, Mood.GOOD, linkedSetOf(Symptom.HEADACHE, Symptom.CRAMPS),
            Pain.MILD, Sex.UNPROTECTED, Discharge.EGG_WHITE, "note", linkedSetOf(2, 1))
        assertEquals(header + "2026-03-01,,medium,good,mild,unprotected,egg_white,cramps;headache,\"Work;\"\"Walk\"\"\",note,,,\r\n",
            csv(listOf(log), listOf(Tag(2, "\"Walk\"", true), Tag(1, "Work"))))
    }

    @Test fun csvUsesLocaleIndependentLowercase() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(csv(listOf(DayLog(date, flow = FlowLevel.LIGHT))).contains(",light,"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun exportsOvulationAndCustomCategoryItemNames() {
        val log = DayLog(date, tagIds = setOf(1, 2), ovulationTest = OvulationTest.NEGATIVE)
        assertEquals(header + "2026-03-01,,,,,,,,Work,,negative,Activities:Walk,\r\n",
            csv(listOf(log), listOf(Tag(1, "Work"), Tag(2, "Walk", categoryId = 9)), categories = listOf(CustomCategory(9, "Activities", "tag", 0, true))))
    }

    @Test fun emptyExportContainsTheHeader() {
        assertEquals(header, csv(emptyList()))
    }
}
