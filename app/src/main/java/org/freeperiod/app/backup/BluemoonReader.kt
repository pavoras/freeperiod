package org.freeperiod.app.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.LocalDate
import org.freeperiod.engine.importing.AppImport
import org.freeperiod.engine.importing.BluemoonImport
import org.freeperiod.engine.importing.BluemoonSex

/** Reads Bluemoon's backup, an SQLite file, from a private temporary copy that is deleted right after. */
class BluemoonReader(private val dir: File) {
    /** Null when the file is not a Bluemoon backup or holds nothing to import. */
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
                    db.rawQuery("SELECT id, date FROM Sex", null).use { rows ->
                        generateSequence { if (rows.moveToNext()) BluemoonSex(rows.getString(1),
                            used[rows.getLong(0)].orEmpty().filterNotNull().toSet()) else null }.toList()
                    }
                }
                BluemoonImport.map(bleeding, sex, today)
            }
        } catch (_: Exception) {
            null
        } finally {
            file.writeBytes(ByteArray(0))
            listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
        }
    }

    companion object {
        private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        fun isSqlite(bytes: ByteArray) = bytes.size > 100 && MAGIC.indices.all { bytes[it] == MAGIC[it] }
    }
}
