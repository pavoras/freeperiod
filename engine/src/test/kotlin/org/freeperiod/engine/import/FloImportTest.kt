package org.freeperiod.engine.importing

import java.time.LocalDate
import org.freeperiod.engine.Period
import org.junit.Assert.*
import org.junit.Test

class FloImportTest {
    private val today = LocalDate.parse("2026-10-19")
    // Shape of Flo's support export, with made-up dates; only the fields the import reads plus some noise.
    private val json = """
        {"symptomCheckerData": {"user_signs_data": [{"sign": "Cravings", "present": true}]},
         "operationalData": {"notes": [], "cycles": [
           {"period_start_date": "2025-02-19 00:00:00.0", "period_end_date": "2025-02-24 00:00:00.0",
            "pregnant": false, "period_intensity": "{}", "additional_fields": "{}"},
           {"period_start_date": "2025-01-22 00:00:00.0", "period_end_date": "2025-01-26 00:00:00.0",
            "pregnant": false, "period_intensity": "{}",
            "additional_fields": "{\"prediction\": \"{\\\"period_start_date\\\": \\\"2025-02-17T22:00:00+00:00\\\"}\"}"},
           {"period_start_date": "2026-10-17 00:00:00.0", "period_end_date": "2026-10-21 00:00:00.0", "pregnant": false},
           {"period_start_date": "2026-11-12 00:00:00.0", "period_end_date": "2026-11-16 00:00:00.0", "pregnant": false},
           {"period_start_date": null, "period_end_date": "2025-03-01 00:00:00.0"}
         ]}}
    """.trimIndent()
    private val expected = listOf(
        Period(0, LocalDate.parse("2025-01-22"), LocalDate.parse("2025-01-26")),
        Period(0, LocalDate.parse("2025-02-19"), LocalDate.parse("2025-02-24")),
        // Ends after today: comes in as ongoing. The one starting in the future is left out.
        Period(0, LocalDate.parse("2026-10-17"), null))

    @Test fun unpackedJsonGivesSortedPeriodsOnly() {
        assertEquals(FloImportResult.Parsed(expected), FloImport.parse(json.toByteArray(), today = today))
    }

    @Test fun passwordProtectedZipFromSupport() {
        val zip = testZip("0000-export.json", json.toByteArray(), "test-password")
        assertEquals(FloImportResult.NeedsPassword, FloImport.parse(zip, today = today))
        assertEquals(FloImportResult.WrongPassword, FloImport.parse(zip, "wrong".toCharArray(), today))
        assertEquals(FloImportResult.Parsed(expected), FloImport.parse(zip, "test-password".toCharArray(), today))
    }

    @Test fun plainZipNeedsNoPassword() {
        assertEquals(FloImportResult.Parsed(expected), FloImport.parse(testZip("export.json", json.toByteArray(), null), today = today))
    }

    @Test fun otherFilesAreInvalid() {
        listOf("""{"operationalData": {"cycles": []}}""", """{"cycles": 3}""", "Start,End\n2026-01-01,2026-01-05", "")
            .forEach { assertEquals(it, FloImportResult.Invalid, FloImport.parse(it.toByteArray(), today = today)) }
        assertEquals(FloImportResult.Invalid, FloImport.parse(testZip("notes.txt", "hi".toByteArray(), null), today = today))
        assertEquals(FloImportResult.Invalid, FloImport.parse(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0), today = today))
    }
}
