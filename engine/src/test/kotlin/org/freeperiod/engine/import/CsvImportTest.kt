package org.freeperiod.engine.importing

import java.time.LocalDate
import org.freeperiod.engine.CycleUse
import org.freeperiod.engine.Period
import org.freeperiod.engine.export.CsvExport
import org.junit.Assert.*
import org.junit.Test

class CsvImportTest {
    private fun parsed(csv: String, format: CsvDateFormat? = null) =
        (CsvImport.parse(csv, format) as CsvImportResult.Parsed).periods

    @Test fun isoStartEndAndOwnExport() {
        val periods = listOf(Period(0, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-06"), CycleUse.EXCLUDE),
            Period(0, LocalDate.parse("2026-02-01"), null))
        // The own export is one file of days; an ongoing period comes back ending on the export day, and a
        // logged day without a flow inside a period still counts because period_day decides.
        val export = CsvExport.export(periods, listOf(org.freeperiod.engine.DayLog(LocalDate.parse("2026-01-03"),
            mood = org.freeperiod.engine.Mood.GOOD), org.freeperiod.engine.DayLog(LocalDate.parse("2026-01-20"),
            flow = org.freeperiod.engine.FlowLevel.LIGHT)), emptyList(), today = LocalDate.parse("2026-02-03"))
        assertEquals(listOf(Period(0, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-06")),
            Period(0, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-03"))), parsed(export))
        assertEquals(periods.first().copy(cycleUse = CycleUse.AUTO),
            parsed("Start Date,End Date\n2026-01-02,2026-01-06").single())
    }
    @Test fun perDayRowsGroupConsecutiveDaysAndDeduplicate() {
        val periods = parsed("recorded_on,note\n2026-01-03,\"with, comma\"\n2026-01-02,x\n2026-01-03,x\n2026-01-08,x")
        assertEquals(2, periods.size)
        assertEquals(LocalDate.parse("2026-01-02"), periods[0].start)
        assertEquals(LocalDate.parse("2026-01-03"), periods[0].end)
        assertEquals(periods[1].start, periods[1].end)
    }
    @Test fun germanDateFormat() {
        val period = parsed("Beginn;Ende\r\n28.03.2026;02.04.2026").single()
        assertEquals(LocalDate.parse("2026-03-28"), period.start)
        assertEquals(LocalDate.parse("2026-04-02"), period.end)
    }
    @Test fun ambiguousFormatRequiresExplicitChoice() {
        val csv = "date\n03/04/2026\n04/04/2026"
        assertEquals(setOf(CsvDateFormat.DAY_MONTH_YEAR, CsvDateFormat.MONTH_DAY_YEAR),
            (CsvImport.parse(csv) as CsvImportResult.NeedsFormat).formats.toSet())
        assertEquals(LocalDate.parse("2026-04-03"), parsed(csv, CsvDateFormat.DAY_MONTH_YEAR).first().start)
        assertEquals(LocalDate.parse("2026-03-04"), parsed(csv, CsvDateFormat.MONTH_DAY_YEAR).first().start)
        assertEquals(LocalDate.parse("2026-03-14"), parsed("date\n03/14/2026").single().start)
    }
    @Test fun emptyGarbageAndInvalidDatesRejected() {
        listOf("", "start,end", "garbage", "date\n31.02.2026", "start,end\n2026-02-03,2026-02-02",
            "date\n2026-01-01\ngarbage", "date,note\n2026-01-01,\"unclosed").forEach {
            assertTrue(it, CsvImport.parse(it) is CsvImportResult.Invalid)
        }
    }
    @Test fun dailyExportOnlyImportsBleedingRows() {
        val periods = parsed("date,flow,note\n2026-01-01,none,x\n2026-01-02,light,x\n2026-01-03,heavy,x\n2026-01-04,,x")
        assertEquals(Period(0, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-03")), periods.single())
    }
    @Test fun escapedQuotesNewlinesAndBom() {
        assertEquals(1, parsed("\uFEFFdate,note\r\n2026-01-02,\"a \"\"quote\"\"\nand newline\"").size)
    }
    @Test fun delimiterInsideQuotedHeaderIsIgnored() {
        assertEquals(1, parsed("\nstart;end;\"note, with, commas\"\n2026-01-02;2026-01-06;x").size)
    }
    @Test fun headerlessBleedingDaysKeepFirstRow() {
        val period = parsed("2026-01-02\n2026-01-03").single()
        assertEquals(LocalDate.parse("2026-01-02"), period.start)
        assertEquals(LocalDate.parse("2026-01-03"), period.end)
    }
}
