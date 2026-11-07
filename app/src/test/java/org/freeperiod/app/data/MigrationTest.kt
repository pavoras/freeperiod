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
import org.freeperiod.app.data.db.MIGRATION_3_4
import org.freeperiod.app.data.db.MIGRATION_4_5
import org.freeperiod.app.data.db.MIGRATION_5_6
import org.freeperiod.app.data.db.MIGRATION_6_7
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

    @Test fun v6ToV7PreservesAllScalarValuesAndChoiceDefaults() = runBlocking {
        val today = java.time.LocalDate.of(2026, 4, 12)
        createDatabase(6, "migration-v6-test") {
            execSQL("INSERT INTO custom_categories (id, name, iconKey, sortOrder, archived) VALUES (1, 'Sex', 'builtin:sex', 0, 0), (2, 'Discharge', 'builtin:discharge', 0, 0)")
            for ((index, values) in Sex.entries.flatMap { sex -> Discharge.entries.map { sex to it } }.withIndex()) {
                execSQL("INSERT INTO day_logs (epochDay, symptoms, sex, discharge) VALUES (?, '', ?, ?)",
                    arrayOf<Any>(today.minusDays(index.toLong()).toEpochDay(), values.first.name, values.second.name))
            }
            execSQL("INSERT INTO day_logs (epochDay, symptoms, note) VALUES (?, '', 'Keep empty')", arrayOf(today.minusDays(30).toEpochDay()))
        }
        val db = Room.databaseBuilder(context, FreePeriodDatabase::class.java, "migration-v6-test")
            .addMigrations(MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            val repository = Repository(db) { today }
            val data = repository.snapshot()
            val logs = data.dayLogs.associateBy { it.date }
            for ((index, values) in Sex.entries.flatMap { sex -> Discharge.entries.map { sex to it } }.withIndex()) {
                val log = logs.getValue(today.minusDays(index.toLong()))
                assertEquals(setOf(values.first), log.sex)
                assertEquals(setOf(values.second), log.discharge)
            }
            assertTrue(logs.getValue(today.minusDays(30)).sex.isEmpty())
            assertTrue(logs.getValue(today.minusDays(30)).discharge.isEmpty())
            assertTrue(data.customCategories.isSingleChoice("sex"))
            assertFalse(data.customCategories.isSingleChoice("discharge"))
            val several = DayLog(today, sex = setOf(Sex.PROTECTED, Sex.UNPROTECTED),
                discharge = setOf(Discharge.CREAMY, Discharge.UNUSUAL))
            repository.saveDayLog(several)
            assertEquals(several, repository.snapshot().dayLogs.last())
        } finally { db.close() }
    }

    private fun createDatabase(version: Int, name: String, seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(name)
        val schema = JSONObject(context.assets.open("${FreePeriodDatabase::class.java.name}/$version.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema.getString("identityHash")))
            db.version = version
            db.seed()
        }
    }

    @Test fun v2ToV7KeepsCustomizationAndEntries() = runBlocking {
        val today = java.time.LocalDate.of(2026, 4, 12)
        createDatabase(2, "migration-v2-test") {
            execSQL("INSERT INTO custom_categories VALUES (4, 'My symptoms', 'builtin:symptoms', 5, 0)")
            execSQL("INSERT INTO tags VALUES (9, 'Own symptom', 0, 4, 'leaf')")
            execSQL("INSERT INTO day_logs VALUES (?, 'LIGHT', 'GOOD', 'CRAMPS', 'MILD', 'NONE', 'CREAMY', 'Keep this', 'NEGATIVE')", arrayOf(today.toEpochDay()))
            execSQL("INSERT INTO day_tags VALUES (?, 9)", arrayOf(today.toEpochDay()))
            execSQL("INSERT INTO ui_overrides VALUES ('item:mood:GOOD', 1, 8)")
            execSQL("INSERT INTO ui_overrides VALUES ('category:ovulation_test', 0, 3)")
        }
        val db = Room.databaseBuilder(context, FreePeriodDatabase::class.java, "migration-v2-test")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()
        try {
            val repository = Repository(db) { today }
            val data = repository.snapshot()
            assertEquals(CustomCategory(4, "My symptoms", "builtin:symptoms", 5, false), data.customCategories.single())
            assertEquals(Tag(9, "Own symptom", categoryId = 4, iconKey = "leaf"), data.tags.single())
            assertEquals(UiOverride("item:mood:GOOD", true, 8), data.overrides.single { it.key == "item:mood:GOOD" })
            // Version 6: a reorder's "shown" for the ovulation test outside trying to conceive becomes hidden.
            assertTrue(data.overrides.single { it.key == "category:ovulation_test" }.hidden)
            assertEquals(DayLog(today, FlowLevel.LIGHT, Mood.GOOD, setOf(Symptom.CRAMPS), Pain.MILD,
                setOf(Sex.NONE), setOf(Discharge.CREAMY), "Keep this", setOf(9), OvulationTest.NEGATIVE), data.dayLogs.single())
            repository.setUiOverride(data.overrides.single { it.key == "item:mood:GOOD" }.copy(label = "Content", iconKey = "calm"))
            val ownCategory = repository.addCustomCategory("Activities", singleChoice = true)
            assertTrue(repository.snapshot().customCategories.single { it.id == ownCategory.id }.singleChoice)
            assertEquals("Content", repository.snapshot().overrides.single { it.key == "item:mood:GOOD" }.label)
            assertEquals("calm", repository.snapshot().overrides.single { it.key == "item:mood:GOOD" }.iconKey)
            // Version 4: counts per item, counted categories, pain diary flag.
            val pills = repository.addBuiltInItem("medication", "Own medicine", "pill", "Medication")
            val counted = DayLog(today.minusDays(1), tagIds = setOf(pills.id), tagCounts = mapOf(pills.id to 3))
            repository.saveDayLog(counted)
            assertEquals(counted, repository.snapshot().dayLogs.first())
            repository.updateCustomCategory(ownCategory.copy(counted = true))
            assertTrue(repository.snapshot().customCategories.single { it.id == ownCategory.id }.counted)
            repository.updateSituation(Situation(painDiary = true))
            assertTrue(repository.snapshot().situation.painDiary)
            // Version 5: appetite and sex drive.
            val levels = DayLog(today.minusDays(2), appetite = Appetite.HIGH, libido = Libido.LOW)
            repository.saveDayLog(levels)
            assertEquals(levels, repository.snapshot().dayLogs.first())
            // Version 6: chart choice per category.
            assertNull(repository.snapshot().overrides.single { it.key == "item:mood:GOOD" }.chart)
            repository.setUiOverride(UiOverride("category:sex", false, 5, chart = true))
            assertEquals(true, repository.snapshot().overrides.single { it.key == "category:sex" }.chart)
        } finally { db.close() }
    }

    @Test fun v1ToV7KeepsData() = runBlocking {
        val today = java.time.LocalDate.of(2026, 4, 12)
        val start = today.minusDays(10).toEpochDay()
        val end = start + 4
        val note = "x".repeat(2101)
        createDatabase(1, "migration-test") {
            execSQL("INSERT INTO periods VALUES (7, ?, ?, 'EXCLUDE')", arrayOf(start, end))
            execSQL("INSERT INTO day_logs VALUES (?, 'NONE', NULL, 'HOT_FLUSHES;NIGHT_SWEATS', 'NONE', NULL, NULL, ?)", arrayOf<Any>(start, note))
            execSQL("INSERT INTO day_logs VALUES (?, NULL, 'OKAY', '', NULL, NULL, NULL, NULL)", arrayOf(start + 1))
            execSQL("INSERT INTO tags VALUES (9, 'Walk', 1)")
            execSQL("INSERT INTO day_tags VALUES (?, 9)", arrayOf(start))
            execSQL("INSERT INTO domain_settings VALUES (0, 29, 1)")
        }
        val db = Room.databaseBuilder(context, FreePeriodDatabase::class.java, "migration-test")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).allowMainThreadQueries().build()
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
            assertNull(data.dayLogs.first().appetite)
            assertEquals(Situation(), data.situation)
            assertTrue(data.reminders.isEmpty())
        } finally { db.close() }
    }
}
