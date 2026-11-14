package org.freeperiod.app.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.LocalDate
import org.freeperiod.engine.importing.AppImport
import org.freeperiod.engine.importing.BluemoonImport
import org.freeperiod.engine.importing.BluemoonSex
import org.freeperiod.engine.importing.PeriodCalendarImport
import org.freeperiod.engine.importing.PeriodCalendarNote
import org.freeperiod.engine.importing.PeriodCalendarPeriod

/**
 * Reads another app's SQLite database – Bluemoon's backup, or the one inside Period Calendar's backup – from a
 * private temporary copy that is deleted right after.
 */
class AppDatabaseReader(private val dir: File) {
    /** Null when the file is neither or holds nothing to import. */
    fun read(bytes: ByteArray, today: LocalDate): AppImport? {
        if (!isSqlite(bytes)) return null
        dir.mkdirs()
        val file = File.createTempFile("import", ".db", dir)
        return try {
            // Bytes 18 and 19 set write-ahead logging; the copy is read alone, so it opens as a plain file.
            file.writeBytes(bytes.copyOf().also { it[18] = 1; it[19] = 1 })
            SQLiteDatabase.openDatabase(file.path, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)
                    .use { generateSequence { if (it.moveToNext()) it.getString(0) else null }.toSet() }
                if ("Period" in tables && "Note" in tables) return periodCalendar(db, today)
                if ("Bleeding" !in tables) return null
                val bleeding = db.rawQuery("SELECT date, strength FROM Bleeding", null).use { rows ->
                    generateSequence { if (rows.moveToNext()) rows.getString(0) to rows.getString(1) else null }.toList()
                }
                val sex = if ("Sex" !in tables) emptyList() else {
                    val used = if ("ContraceptiveUse" !in tables) emptyMap() else
                        db.rawQuery("SELECT usedOnSexId, contraceptive FROM ContraceptiveUse", null).use { rows ->
                            generateSequence { if (rows.moveToNext()) rows.getLong(0) to rows.getString(1) else null }
                                .groupBy({ it.first }, { it.second })
                        }
                    db.rawQuery("SELECT id, date, type FROM Sex", null).use { rows ->
                        generateSequence { if (rows.moveToNext()) BluemoonSex(rows.getString(1),
                            used[rows.getLong(0)].orEmpty().filterNotNull().toSet(), rows.getString(2)) else null }.toList()
                    }
                }
                val pills = if ("Pill" !in tables) emptyList() else
                    db.rawQuery("SELECT date FROM Pill WHERE taken", null).use { rows ->
                        generateSequence { if (rows.moveToNext()) rows.getString(0) else null }.toList()
                    }
                BluemoonImport.map(bleeding, sex, today, pills)
            }
        } catch (_: Exception) {
            null
        } finally {
            file.writeBytes(ByteArray(0))
            listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
        }
    }

    private fun periodCalendar(db: SQLiteDatabase, today: LocalDate): AppImport? {
        val periods = db.rawQuery("SELECT date, period FROM Period", null).use { rows ->
            generateSequence { if (rows.moveToNext()) PeriodCalendarPeriod(rows.getInt(0), rows.getInt(1)) else null }.toList()
        }
        val notes = db.rawQuery("SELECT date, note FROM Note", null).use { rows ->
            generateSequence { if (rows.moveToNext()) PeriodCalendarNote(rows.getInt(0), rows.getString(1)) else null }.toList()
        }
        return PeriodCalendarImport.map(periods, notes, today)
    }

    companion object {
        private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        fun isSqlite(bytes: ByteArray) = bytes.size > 100 && MAGIC.indices.all { bytes[it] == MAGIC[it] }
    }
}
