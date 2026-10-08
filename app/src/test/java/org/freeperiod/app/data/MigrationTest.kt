package org.freeperiod.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.freeperiod.app.data.db.MIGRATION_1_2
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun v1ToV2KeepsData() = runBlocking {
        val today = java.time.LocalDate.of(2026, 4, 12)
        val start = today.minusDays(10).toEpochDay()
        val end = start + 4
        val note = "x".repeat(2101)
        context.deleteDatabase("migration-test")
        val schema = JSONObject(context.assets.open("${FreePeriodDatabase::class.java.name}/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("migration-test").also { it.parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
            version = 1
            execSQL("INSERT INTO periods VALUES (7, ?, ?, 'EXCLUDE')", arrayOf(start, end))
            execSQL("INSERT INTO day_logs VALUES (?, 'NONE', NULL, 'HOT_FLUSHES;NIGHT_SWEATS', 'NONE', NULL, NULL, ?)", arrayOf(start, note))
            execSQL("INSERT INTO day_logs VALUES (?, NULL, 'OKAY', '', NULL, NULL, NULL, NULL)", arrayOf(start + 1))
            execSQL("INSERT INTO tags VALUES (9, 'Walk', 1)")
            execSQL("INSERT INTO day_tags VALUES (?, 9)", arrayOf(start))
            execSQL("INSERT INTO domain_settings VALUES (0, 29, 1)")
            close()
        }
        val db = Room.databaseBuilder(context, FreePeriodDatabase::class.java, "migration-test")
            .addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val data = Repository(db) { today }.snapshot()
            assertEquals(Period(7, today.minusDays(10), today.minusDays(6), CycleUse.EXCLUDE), data.periods.single())
            assertEquals(Tag(9, "Walk", true), data.tags.single())
            assertEquals(FlowLevel.NONE, data.dayLogs.first().flow)
            assertEquals(Pain.NONE, data.dayLogs.first().pain)
            assertNull(data.dayLogs[1].flow)
            assertNull(data.dayLogs[1].pain)
            assertEquals(note, data.dayLogs.first().note)
            assertEquals(setOf(9L), data.dayLogs.first().tagIds)
            assertEquals(setOf(Symptom.HOT_FLUSHES, Symptom.NIGHT_SWEATS), data.dayLogs.first().symptoms)
            assertNull(data.dayLogs.first().ovulationTest)
            assertEquals(Situation(), data.situation)
            assertTrue(data.reminders.isEmpty())
        } finally { db.close() }
    }
}
