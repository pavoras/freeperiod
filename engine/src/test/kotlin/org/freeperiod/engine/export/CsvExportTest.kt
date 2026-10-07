package org.freeperiod.engine.export

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

class CsvExportTest {
    private val date = LocalDate.of(2026, 3, 1)

    @Test fun csvQuotesCommasQuotesAndNewlines() {
        val csv = CsvExport.days(listOf(DayLog(date, note = "a,\"b\"\nc")), emptyList())
        assertEquals("date,flow,mood,pain,sex,discharge,symptoms,tags,note\r\n2026-03-01,,,,,,,,\"a,\"\"b\"\"\nc\"\r\n", csv)
    }

    @Test fun csvHeaderAndOrder() {
        val logs = listOf(DayLog(date.plusDays(1), flow = FlowLevel.NONE), DayLog(date, flow = FlowLevel.LIGHT))
        assertEquals("date,flow,mood,pain,sex,discharge,symptoms,tags,note\r\n2026-03-01,light,,,,,,,\r\n2026-03-02,none,,,,,,,\r\n",
            CsvExport.days(logs, emptyList()))
        val periods = listOf(Period(2, date.plusDays(28), null, CycleUse.EXCLUDE), Period(1, date, date.plusDays(4)))
        assertEquals("start,end,cycle_use\r\n2026-03-01,2026-03-05,auto\r\n2026-03-29,,exclude\r\n", CsvExport.periods(periods))
    }

    @Test fun csvContainsAllFieldsAndArchivedTagsInStableOrder() {
        val log = DayLog(date, FlowLevel.MEDIUM, Mood.GOOD, linkedSetOf(Symptom.HEADACHE, Symptom.CRAMPS),
            Pain.MILD, Sex.UNPROTECTED, Discharge.EGG_WHITE, "note", linkedSetOf(2, 1))
        assertEquals("date,flow,mood,pain,sex,discharge,symptoms,tags,note\r\n2026-03-01,medium,good,mild,unprotected,egg_white,cramps;headache,\"Work;\"\"Walk\"\"\",note\r\n",
            CsvExport.days(listOf(log), listOf(Tag(2, "\"Walk\"", true), Tag(1, "Work"))))
        assertEquals("start,end,cycle_use\r\n2026-03-01,,include\r\n", CsvExport.periods(listOf(Period(1, date, null, CycleUse.INCLUDE))))
    }

    @Test fun csvUsesLocaleIndependentLowercase() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(CsvExport.days(listOf(DayLog(date, flow = FlowLevel.LIGHT)), emptyList()).contains(",light,"))
            assertTrue(CsvExport.periods(listOf(Period(1, date, null, CycleUse.INCLUDE))).contains(",include\r\n"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun emptyExportsContainHeaders() {
        assertEquals("date,flow,mood,pain,sex,discharge,symptoms,tags,note\r\n", CsvExport.days(emptyList(), emptyList()))
        assertEquals("start,end,cycle_use\r\n", CsvExport.periods(emptyList()))
    }
}

