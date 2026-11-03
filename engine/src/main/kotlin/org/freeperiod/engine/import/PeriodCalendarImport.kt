package org.freeperiod.engine.importing

import java.nio.ByteBuffer
import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.Period

/** A row of the Period table in Period Calendar's backup: start as yyyyMMdd, length in days. */
data class PeriodCalendarPeriod(val date: Int, val length: Int)

/** A row of the Note table: day as yyyyMMdd and the free-text note. */
data class PeriodCalendarNote(val date: Int, val note: String?)

/**
 * Reads the backup of "Period Calendar" (.pc): a Java serialization stream whose data blocks hold a ZIP. The ZIP
 * carries the app's database as `cloud.db` (SQLite; the app reads it) next to obfuscated copies of the same data.
 */
object PeriodCalendarImport {
    private const val STREAM_MAGIC = 0xACED0005.toInt()

    fun isBackup(bytes: ByteArray) = bytes.size > 8 && ByteBuffer.wrap(bytes).getInt(0) == STREAM_MAGIC

    /** The SQLite database inside the backup, or null when the file is something else. */
    fun database(bytes: ByteArray): ByteArray? = try {
        val payload = blockData(bytes)
        val start = (0..payload.size - 4).first { payload[it] == 'P'.code.toByte() && payload[it + 1] == 'K'.code.toByte() &&
            payload[it + 2] == 3.toByte() && payload[it + 3] == 4.toByte() }
        val zip = ZipEntries(payload.copyOfRange(start, payload.size))
        zip.entries.find { it.name == "cloud.db" }?.let { zip.read(it) }
    } catch (_: Exception) { null }

    /**
     * Periods from start and length (the app stores some lengths as negative numbers; the amount counts),
     * notes as diary notes. A period reaching today or later comes in as ongoing.
     */
    fun map(periods: List<PeriodCalendarPeriod>, notes: List<PeriodCalendarNote>, today: LocalDate): AppImport? = try {
        val imported = periods.mapNotNull { row ->
            val start = date(row.date).takeIf { it <= today } ?: return@mapNotNull null
            val length = kotlin.math.abs(row.length).coerceIn(1, 30)
            val end = start.plusDays(length - 1L)
            Period(0, start, end.takeIf { it < today })
        }.distinctBy { it.start }.sortedBy { it.start }
        val logs = notes.mapNotNull { row ->
            val day = date(row.date).takeIf { it <= today } ?: return@mapNotNull null
            DayLog(day, note = row.note?.trim()?.takeIf { it.isNotEmpty() }?.take(2000))
        }.filterNot { it.isEmpty() }.distinctBy { it.date }.sortedBy { it.date }
        AppImport(imported, logs).takeUnless { it.isEmpty() }
    } catch (_: Exception) { null }

    private fun date(value: Int) = LocalDate.of(value / 10000, value / 100 % 100, value % 100)

    /** Joins the block-data records (TC_BLOCKDATA, TC_BLOCKDATALONG) that follow the stream header. */
    private fun blockData(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.getInt(0) == STREAM_MAGIC)
        val out = java.io.ByteArrayOutputStream()
        var i = 4
        while (i < bytes.size) {
            val length = when (bytes[i].toInt() and 0xFF) {
                0x77 -> (bytes[i + 1].toInt() and 0xFF).also { i += 2 }
                0x7A -> buffer.getInt(i + 1).also { i += 5 }
                else -> break
            }
            require(length >= 0 && i + length <= bytes.size)
            out.write(bytes, i, length)
            i += length
        }
        return out.toByteArray()
    }
}
