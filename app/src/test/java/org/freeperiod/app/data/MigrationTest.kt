package org.freeperiod.app.data

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.freeperiod.app.data.db.MIGRATION_1_2
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val schemaRoot = listOf(File("schemas"), File("app/schemas")).first {
        File(it, "${FreePeriodDatabase::class.java.name}/1.json").isFile
    }
    private val schemaAssets = AssetManager::class.java.getDeclaredConstructor().newInstance().also {
        ReflectionHelpers.callInstanceMethod<Int>(it, "addAssetPath", ClassParameter.from(String::class.java, schemaRoot.absolutePath))
    }
    private val instrumentation = object : Instrumentation() {
        override fun getContext(): Context = object : ContextWrapper(this@MigrationTest.context) {
            override fun getAssets(): AssetManager = schemaAssets
        }
        override fun getTargetContext(): Context = this@MigrationTest.context
    }
    @get:Rule val helper = MigrationTestHelper(instrumentation, FreePeriodDatabase::class.java)

    @Test fun v1ToV2KeepsData() = runBlocking {
        val today = java.time.LocalDate.of(2026, 4, 12)
        val start = today.minusDays(10).toEpochDay()
        val end = start + 4
        val note = "x".repeat(2101)
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO periods VALUES (7, ?, ?, 'EXCLUDE')", arrayOf(start, end))
            execSQL("INSERT INTO day_logs VALUES (?, 'NONE', NULL, 'HOT_FLUSHES;NIGHT_SWEATS', 'NONE', NULL, NULL, ?)", arrayOf(start, note))
            execSQL("INSERT INTO day_logs VALUES (?, NULL, 'OKAY', '', NULL, NULL, NULL, NULL)", arrayOf(start + 1))
            execSQL("INSERT INTO tags VALUES (9, 'Walk', 1)")
            execSQL("INSERT INTO day_tags VALUES (?, 9)", arrayOf(start))
            execSQL("INSERT INTO domain_settings VALUES (0, 29, 1)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).close()
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
        } finally { db.close(); schemaAssets.close() }
    }
}
