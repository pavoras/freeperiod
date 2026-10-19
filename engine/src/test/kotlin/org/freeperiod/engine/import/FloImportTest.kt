package org.freeperiod.engine.importing

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.util.zip.CRC32
import java.util.zip.Deflater
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
        val zip = zip("0000-export.json", json.toByteArray(), "test-password")
        assertEquals(FloImportResult.NeedsPassword, FloImport.parse(zip, today = today))
        assertEquals(FloImportResult.WrongPassword, FloImport.parse(zip, "wrong".toCharArray(), today))
        assertEquals(FloImportResult.Parsed(expected), FloImport.parse(zip, "test-password".toCharArray(), today))
    }

    @Test fun plainZipNeedsNoPassword() {
        assertEquals(FloImportResult.Parsed(expected), FloImport.parse(zip("export.json", json.toByteArray(), null), today = today))
    }

    @Test fun otherFilesAreInvalid() {
        listOf("""{"operationalData": {"cycles": []}}""", """{"cycles": 3}""", "Start,End\n2026-01-01,2026-01-05", "")
            .forEach { assertEquals(it, FloImportResult.Invalid, FloImport.parse(it.toByteArray(), today = today)) }
        assertEquals(FloImportResult.Invalid, FloImport.parse(zip("notes.txt", "hi".toByteArray(), null), today = today))
        assertEquals(FloImportResult.Invalid, FloImport.parse(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0), today = today))
    }

    /** A one-entry ZIP like Flo's: deflated, and with ZipCrypto when a password is given. */
    private fun zip(name: String, content: ByteArray, password: String?): ByteArray {
        val crc = CRC32().apply { update(content) }.value.toInt()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true).apply { setInput(content); finish() }
        val compressed = ByteArrayOutputStream().apply {
            val chunk = ByteArray(1024)
            while (!deflater.finished()) write(chunk, 0, deflater.deflate(chunk))
        }.toByteArray().also { deflater.end() }
        val data = if (password == null) compressed else {
            val keys = ZipCryptoKeys(password.toCharArray())
            val header = ByteArray(12) { if (it == 11) (crc ushr 24).toByte() else (it * 37).toByte() }
            (header + compressed).map(keys::encrypt).toByteArray()
        }
        val flags = if (password == null) 0x800 else 0x801
        val nameBytes = name.toByteArray()
        fun le(size: Int, fill: ByteBuffer.() -> Unit) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()
        val local = le(30) { putInt(0x04034b50); putShort(20); putShort(flags.toShort()); putShort(8); putInt(0)
            putInt(crc); putInt(data.size); putInt(content.size); putShort(nameBytes.size.toShort()); putShort(0) }
        val central = le(46) { putInt(0x02014b50); putShort(20); putShort(20); putShort(flags.toShort()); putShort(8); putInt(0)
            putInt(crc); putInt(data.size); putInt(content.size); putShort(nameBytes.size.toShort()); putShort(0); putShort(0)
            putShort(0); putShort(0); putInt(0); putInt(0) }
        val centralStart = local.size + nameBytes.size + data.size
        val end = le(22) { putInt(0x06054b50); putShort(0); putShort(0); putShort(1); putShort(1)
            putInt(central.size + nameBytes.size); putInt(centralStart); putShort(0) }
        return local + nameBytes + data + central + nameBytes + end
    }
}
