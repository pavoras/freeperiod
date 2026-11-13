package org.freeperiod.engine.importing

import java.time.LocalDate
import org.freeperiod.engine.FlowLevel
import org.freeperiod.engine.Period
import org.junit.Assert.*
import org.junit.Test

class ImportArchiveTest {
    private val today = LocalDate.parse("2026-11-13")
    // Made-up days in the shape of Clue's backup.
    private val clue = """{"data": [{"day": "2026-10-02T00:00:00Z", "period": "heavy"},
        {"day": "2026-10-03T00:00:00Z", "period": "light"}]}""".toByteArray()

    @Test fun aFileThatIsNoZipIsItself() {
        val bytes = "Start,End".toByteArray()
        assertSame(bytes, ImportArchive.files(bytes).single())
    }

    @Test fun passwordProtectedClueDownloadIsReadAfterThePassword() {
        val zip = testZip("ClueDataExport.json", clue, "clue-test-pw")
        assertThrows(ZipPasswordRequired::class.java) { ImportArchive.files(zip) }
        assertThrows(ZipWrongPassword::class.java) { ImportArchive.files(zip, "wrong".toCharArray()) }
        val file = ImportArchive.files(zip, "clue-test-pw".toCharArray()).single()
        val imported = requireNotNull(ClueImport.parse(file, today))
        assertEquals(listOf(Period(0, LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-03"))), imported.periods)
        assertEquals(FlowLevel.HEAVY, imported.dayLogs.first().flow)
    }

    @Test fun plainZipNeedsNoPassword() {
        assertArrayEquals(clue, ImportArchive.files(testZip("backup.cluedata", clue, null)).single())
    }

    @Test fun aDamagedZipFails() {
        assertThrows(Exception::class.java) { ImportArchive.files(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0)) }
    }
}
