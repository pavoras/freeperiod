package org.freeperiod.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.freeperiod.app.data.db.MIGRATION_1_2
import org.freeperiod.app.data.db.MIGRATION_2_3
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

    @Test fun v2ToV3KeepsCustomizationAndEntries() = runBlocking {
        val today = java.time.LocalDate.of(2026, 4, 12)
        context.deleteDatabase("migration-v2-test")
        val schema = JSONObject(context.assets.open("${FreePeriodDatabase::class.java.name}/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("migration-v2-test").also { it.parentFile!!.mkdirs() }
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
            version = 2
            execSQL("INSERT INTO custom_categories VALUES (4, 'My symptoms', 'builtin:symptoms', 5, 0)")
            execSQL("INSERT INTO tags VALUES (9, 'Own symptom', 0, 4, 'leaf')")
            execSQL("INSERT INTO day_logs VALUES (?, 'LIGHT', 'GOOD', 'CRAMPS', 'MILD', 'NONE', 'CREAMY', 'Keep this', 'NEGATIVE')", arrayOf(today.toEpochDay()))
            execSQL("INSERT INTO day_tags VALUES (?, 9)", arrayOf(today.toEpochDay()))
            execSQL("INSERT INTO ui_overrides VALUES ('item:mood:GOOD', 1, 8)")
            close()
        }
        val db = Room.databaseBuilder(context, FreePeriodDatabase::class.java, "migration-v2-test")
            .addMigrations(MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val repository = Repository(db) { today }
            val data = repository.snapshot()
            assertEquals(CustomCategory(4, "My symptoms", "builtin:symptoms", 5, false), data.customCategories.single())
            assertEquals(Tag(9, "Own symptom", categoryId = 4, iconKey = "leaf"), data.tags.single())
            assertEquals(UiOverride("item:mood:GOOD", true, 8), data.overrides.single())
            assertEquals(DayLog(today, FlowLevel.LIGHT, Mood.GOOD, setOf(Symptom.CRAMPS), Pain.MILD,
                Sex.NONE, Discharge.CREAMY, "Keep this", setOf(9), OvulationTest.NEGATIVE), data.dayLogs.single())
            repository.setUiOverride(data.overrides.single().copy(label = "Content", iconKey = "calm"))
            val ownCategory = repository.addCustomCategory("Activities", singleChoice = true)
            assertTrue(repository.snapshot().customCategories.single { it.id == ownCategory.id }.singleChoice)
            assertEquals("Content", repository.snapshot().overrides.single().label)
            assertEquals("calm", repository.snapshot().overrides.single().iconKey)
        } finally { db.close() }
    }

    @Test fun v1ToV3KeepsData() = runBlocking {
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).allowMainThreadQueries().build()
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
