package org.freeperiod.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.freeperiod.app.backup.AppDatabaseReader
import org.freeperiod.app.backup.DocumentIo
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.engine.*
import org.freeperiod.engine.Period
import org.freeperiod.engine.importing.CsvDateFormat
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportTest : DatabaseTest() {
    private val models = ViewModelStore()
    private var content = "start,end\n2026-04-01,2026-04-05\n2026-03-01,2026-03-05"
    private var bytes: ByteArray? = null
    private val io = object : DocumentIo {
        override suspend fun read(uri: Uri) = bytes?.copyOf() ?: content.toByteArray()
        override suspend fun write(uri: Uri, bytes: ByteArray) = Unit
    }
    @Before fun main() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    @get:Rule val folder = org.junit.rules.TemporaryFolder()
    private val scratch by lazy { folder.newFolder("import") }
    private fun model() = ImportViewModel(repository, io, { today }, AppDatabaseReader(scratch)).also { models.put("import", it) }
    private fun day(month: Int, day: Int) = today.withMonth(month).withDayOfMonth(day)

    @Test fun csvWithoutSituationKeepsDefaultOnAndRespectsAnExistingOptOut() = runTest {
        val vm = model()
        vm.read(Uri.parse("content://test/csv")).join()
        vm.confirm().join()
        assertTrue(repository.snapshot().situation.fertileWindowEnabled)
        repository.updateSituation(org.freeperiod.engine.Situation(fertileWindowEnabled = false))
        vm.read(Uri.parse("content://test/csv")).join()
        vm.confirm().join()
        assertFalse(repository.snapshot().situation.fertileWindowEnabled)
    }

    @Test fun previewDoesNotWriteAndImportReportsOverlaps() = runTest {
        repository.addPeriod(today.withDayOfMonth(1), today.withDayOfMonth(5)).getOrThrow()
        val before = repository.snapshot()
        val model = model()
        model.read(Uri.parse("content://test/csv")).join()
        assertEquals(2, model.state.value.periods.size)
        assertEquals(before, repository.snapshot())
        model.confirm().join()
        assertEquals(1, model.state.value.imported)
        assertEquals(1, model.state.value.skipped)
        model.confirm().join()
        assertEquals(2, repository.snapshot().periods.size)
    }
    @Test fun ambiguityHasNoPreviewUntilChosen() = runTest {
        content = "date\n03/04/2026"
        val model = model()
        model.read(Uri.parse("content://test/csv")).join()
        assertEquals(2, model.state.value.formats.size)
        assertTrue(model.state.value.periods.isEmpty())
        model.chooseFormat(CsvDateFormat.DAY_MONTH_YEAR).join()
        assertEquals(Period(0, today.withDayOfMonth(3), today.withDayOfMonth(3)), model.state.value.periods.single())
    }
    @Test fun floJsonPreviewsThenImports() = runTest {
        content = """{"operationalData": {"cycles": [
            {"period_start_date": "2026-03-02 00:00:00.0", "period_end_date": "2026-03-06 00:00:00.0"},
            {"period_start_date": "2026-03-30 00:00:00.0", "period_end_date": "2026-04-03 00:00:00.0"}]}}"""
        val model = model()
        model.readApp(Uri.parse("content://test/flo")).join()
        assertEquals(2, model.state.value.periods.size)
        assertFalse(model.state.value.floPassword)
        assertTrue(repository.snapshot().periods.isEmpty())
        model.confirm().join()
        assertEquals(2, model.state.value.imported)
        assertEquals(listOf(today.withMonth(3).withDayOfMonth(2) to today.withMonth(3).withDayOfMonth(6),
            today.withMonth(3).withDayOfMonth(30) to today.withDayOfMonth(3)),
            repository.snapshot().periods.sortedBy { it.start }.map { it.start to it.end })
    }

    @Test fun floZipAsksForThePasswordFirst() = runTest {
        // Synthetic export (the two periods above), encrypted like Flo's ZIP with the password "flo-test-pw".
        bytes = java.util.Base64.getDecoder().decode(FLO_ZIP)
        val model = model()
        model.readApp(Uri.parse("content://test/flo")).join()
        assertTrue(model.state.value.floPassword)
        assertTrue(model.state.value.periods.isEmpty())
        assertNull(model.state.value.error)
        model.unlockFlo("").join()
        assertNull(model.state.value.error)
        model.unlockFlo("wrong").join()
        assertEquals(org.freeperiod.app.R.string.import_flo_wrong_password, model.state.value.error)
        assertTrue(model.state.value.floPassword)
        model.unlockFlo("flo-test-pw").join()
        assertFalse(model.state.value.floPassword)
        assertEquals(2, model.state.value.periods.size)
        model.confirm().join()
        assertEquals(2, repository.snapshot().periods.size)
    }

    @Test fun otherFilesAreNotAFloExport() = runTest {
        val model = model()
        model.readApp(Uri.parse("content://test/flo")).join()
        assertEquals(org.freeperiod.app.R.string.import_app_invalid, model.state.value.error)
        model.confirm().join()
        assertTrue(repository.snapshot().periods.isEmpty())
    }

    @Test fun clueBackupImportsPeriodsEntriesAndTags() = runTest {
        // Made-up days in the shape of Clue's .cluedata.
        content = """{"version": 5, "data": [
            {"day": "2026-03-02T00:00:00Z", "period": "heavy", "pain": ["cramps"], "tags": ["Long walk"]},
            {"day": "2026-03-03T00:00:00Z", "period": "light", "mood": ["happy"]},
            {"day": "2026-03-04T00:00:00Z", "period": "spotting"},
            {"day": "2026-03-20T00:00:00Z", "tags": ["long walk", "Yoga"]}]}"""
        repository.addTag("Long walk")
        repository.saveDayLog(DayLog(day(3, 3), mood = Mood.BAD, note = "mine"))
        val model = model()
        model.readApp(Uri.parse("content://test/clue")).join()
        assertEquals(listOf(Period(0, day(3, 2), day(3, 3))), model.state.value.periods)
        assertEquals(4, model.state.value.dayLogs.size)
        assertEquals(1, repository.snapshot().dayLogs.size)
        model.confirm().join()
        assertEquals(1, model.state.value.imported)
        val data = repository.snapshot()
        val tags = data.tags.associate { it.name to it.id }
        assertEquals(setOf("Long walk", "Yoga"), tags.keys)
        val logs = data.dayLogs.associateBy { it.date }
        assertEquals(DayLog(day(3, 2), flow = FlowLevel.HEAVY, symptoms = setOf(Symptom.CRAMPS), tagIds = setOf(tags.getValue("Long walk"))),
            logs[day(3, 2)])
        // What was already logged stays; only the flow is added.
        assertEquals(DayLog(day(3, 3), flow = FlowLevel.LIGHT, mood = Mood.BAD, note = "mine"), logs[day(3, 3)])
        assertEquals(FlowLevel.SPOTTING, logs.getValue(day(3, 4)).flow)
        assertEquals(setOf(tags.getValue("Long walk"), tags.getValue("Yoga")), logs.getValue(day(3, 20)).tagIds)
    }

    @Test fun clueDownloadKeepsOptionsWithoutCounterpart() = runTest {
        // Made-up entries in the shape of measurements.json from Clue's "Download my data".
        content = """[{"type": "period", "date": "2026-03-02", "id": "1", "value": {"option": "heavy"}},
            {"type": "pain", "date": "2026-03-02", "id": "2", "value": [{"option": "cramps"}, {"option": "vulvar"}]},
            {"type": "skin", "date": "2026-03-02", "id": "3", "value": [{"option": "ok"}]},
            {"type": "collection_method", "date": "2026-03-02", "id": "4", "value": [{"option": "tampon"}]},
            {"type": "sex_life", "date": "2026-03-02", "id": "5", "value": [{"option": "unprotected_sex"}, {"option": "sex_toys"}]},
            {"type": "skin", "date": "2026-03-03", "id": "6", "value": [{"option": "ok"}]},
            {"type": "feelings", "date": "2026-03-03", "id": "7", "value": [{"option": "happy"}, {"option": "insecure"}]}]"""
        repository.addCustomCategory("Skin")
        repeat(2) {
            val model = model()
            model.readApp(Uri.parse("content://test/clue-download")).join()
            assertNull(model.state.value.error)
            assertEquals(2, model.state.value.dayLogs.size)
            model.confirm().join()
        }
        val data = repository.snapshot()
        // Your "Skin" is reused; options of a category that takes several items join it (symptoms), the rest get
        // an own category under Clue's name. Importing again adds nothing twice.
        val categories = data.customCategories.associateBy { it.id }
        val tags = data.tags.associate { (categories[it.categoryId]?.let { c -> c.builtInField() ?: c.name } to it.name) to it.id }
        assertEquals(setOf("Skin" to "Ok", "Collection method" to "Tampon", "Sex life" to "Sex toys",
            "symptoms" to "Vulvar", "symptoms" to "Insecure"), tags.keys)
        assertEquals(4, data.customCategories.size)
        val logs = data.dayLogs.associateBy { it.date }
        assertEquals(DayLog(day(3, 2), flow = FlowLevel.HEAVY, symptoms = setOf(Symptom.CRAMPS), sex = setOf(Sex.UNPROTECTED),
            tagIds = setOf(tags.getValue("Skin" to "Ok"), tags.getValue("Collection method" to "Tampon"),
                tags.getValue("Sex life" to "Sex toys"), tags.getValue("symptoms" to "Vulvar"))), logs[day(3, 2)])
        assertEquals(DayLog(day(3, 3), mood = Mood.GOOD,
            tagIds = setOf(tags.getValue("Skin" to "Ok"), tags.getValue("symptoms" to "Insecure"))), logs[day(3, 3)])
    }

    @Test fun bluemoonBackupImportsAndLeavesNoCopy() = runTest {
        bytes = bluemoonBackup()
        assertEquals(2, bytes!![18].toInt())
        val model = model()
        model.readApp(Uri.parse("content://test/bluemoon")).join()
        assertNull(model.state.value.error)
        // Bleeding on today comes in as ongoing.
        assertEquals(listOf(Period(0, day(3, 2), day(3, 4)), Period(0, today, null)), model.state.value.periods)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
        model.confirm().join()
        assertEquals(2, model.state.value.imported)
        val logs = repository.snapshot().dayLogs.associateBy { it.date }
        assertEquals(FlowLevel.SPOTTING, logs.getValue(day(3, 1)).flow)
        assertNull(logs[day(3, 3)])
        assertEquals(setOf(Sex.PROTECTED), logs.getValue(day(3, 15)).sex)
        assertEquals(setOf(Sex.UNPROTECTED), logs.getValue(day(3, 16)).sex)
    }

    @Test fun periodCalendarBackupImports() = runTest {
        val database = sqlite { db ->
            db.execSQL("CREATE TABLE Period (_id INTEGER PRIMARY KEY, date INTEGER, period INTEGER, cycle INTEGER)")
            db.execSQL("CREATE TABLE Note (_id INTEGER PRIMARY KEY, date INTEGER, note TEXT, symptom TEXT, mood TEXT)")
            db.execSQL("INSERT INTO Period (date, period, cycle) VALUES (20260302, 5, 28), (20260330, -4, 28)")
            db.execSQL("INSERT INTO Note (date, note) VALUES (20260303, 'tired'), (20260304, '')")
        }
        // Wrapped like the app's .pc file: Java data blocks around a ZIP that holds cloud.db.
        val zip = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("cloud.db")); z.write(database); z.closeEntry() }
        }.toByteArray()
        bytes = java.io.ByteArrayOutputStream().also { out ->
            java.io.ObjectOutputStream(out).use { o -> o.writeInt(-1); o.writeInt(1); o.writeInt(0); o.write(zip) }
        }.toByteArray()
        val model = model()
        model.readApp(Uri.parse("content://test/pc")).join()
        assertNull(model.state.value.error)
        assertEquals(listOf(Period(0, day(3, 2), day(3, 6)), Period(0, day(3, 30), day(4, 2))), model.state.value.periods)
        assertEquals(org.freeperiod.app.R.string.import_period_calendar_partial, model.state.value.notice)
        assertTrue(model.state.value.merges)
        model.confirm().join()
        assertEquals(2, model.state.value.imported)
        assertEquals(listOf(DayLog(day(3, 3), note = "tired")), repository.snapshot().dayLogs)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun zippedPeriodCalendarBackupImports() = runTest {
        val database = sqlite { db ->
            db.execSQL("CREATE TABLE Period (_id INTEGER PRIMARY KEY, date INTEGER, period INTEGER, cycle INTEGER)")
            db.execSQL("CREATE TABLE Note (_id INTEGER PRIMARY KEY, date INTEGER, note TEXT, symptom TEXT, mood TEXT)")
            db.execSQL("INSERT INTO Period (date, period, cycle) VALUES (20260302, 5, 28)")
        }
        val inner = zipOf("cloud.db" to database)
        val pc = java.io.ByteArrayOutputStream().also { out ->
            java.io.ObjectOutputStream(out).use { o -> o.writeInt(-1); o.writeInt(1); o.writeInt(0); o.write(inner) }
        }.toByteArray()
        // The .pc backup zipped again, as when it is shared or downloaded, next to a small other file.
        bytes = zipOf("PeriodCalendar.pc" to pc, "readme.txt" to "hi".toByteArray())
        val model = model()
        model.readApp(Uri.parse("content://test/pczip")).join()
        assertNull(model.state.value.error)
        assertFalse(model.state.value.floPassword)
        assertEquals(listOf(Period(0, day(3, 2), day(3, 6))), model.state.value.periods)
        model.confirm().join()
        assertEquals(1, model.state.value.imported)
    }

    @Test fun zippedClueBackupIsNotTakenForFlo() = runTest {
        bytes = zipOf("data.cluedata" to """{"data": [{"day": "2026-03-02T00:00:00Z", "period": "heavy"},
            {"day": "2026-03-03T00:00:00Z", "period": "medium"}]}""".toByteArray())
        val model = model()
        model.readApp(Uri.parse("content://test/cluezip")).join()
        assertNull(model.state.value.error)
        assertFalse(model.state.value.floPassword)
        assertEquals(listOf(Period(0, day(3, 2), day(3, 3))), model.state.value.periods)
        assertTrue(model.state.value.merges)
    }

    private fun zipOf(vararg files: Pair<String, ByteArray>) = java.io.ByteArrayOutputStream().also { out ->
        java.util.zip.ZipOutputStream(out).use { z ->
            files.forEach { (name, content) -> z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(content); z.closeEntry() }
        }
    }.toByteArray()

    @Test fun otherSqliteFilesAreNotImported() = runTest {
        bytes = sqlite { it.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT)") }
        val model = model()
        model.readApp(Uri.parse("content://test/other")).join()
        assertEquals(org.freeperiod.app.R.string.import_app_invalid, model.state.value.error)
        assertFalse(model.state.value.floPassword)
        model.confirm().join()
        assertTrue(repository.snapshot().periods.isEmpty())
    }

    /** Made-up rows in the tables of Bluemoon's backup, written in WAL mode like the app's file. */
    private fun bluemoonBackup() = sqlite { db ->
        db.execSQL("CREATE TABLE Bleeding (id INTEGER PRIMARY KEY, strength TEXT, date TEXT NOT NULL, logged_at TEXT)")
        db.execSQL("CREATE TABLE Sex (id INTEGER PRIMARY KEY, type TEXT NOT NULL, date TEXT NOT NULL, logged_at TEXT)")
        db.execSQL("CREATE TABLE ContraceptiveUse (id INTEGER PRIMARY KEY, contraceptive TEXT NOT NULL, usedOnSexId INTEGER NOT NULL)")
        listOf("2026-03-01" to "'SPOTTING'", "2026-03-02" to "'HEAVY'", "2026-03-03" to "NULL", "2026-03-04" to "'LIGHT'",
            "$today" to "'MEDIUM'").forEach { (date, strength) ->
            db.execSQL("INSERT INTO Bleeding (strength, date) VALUES ($strength, '$date')")
        }
        db.execSQL("INSERT INTO Sex (id, type, date) VALUES (1, 'PARTNER', '2026-03-15'), (2, 'PARTNER', '2026-03-16')")
        db.execSQL("INSERT INTO ContraceptiveUse (contraceptive, usedOnSexId) VALUES ('IUD', 1)")
    }

    private fun sqlite(fill: (SQLiteDatabase) -> Unit): ByteArray {
        val file = File(folder.root, "source-${System.nanoTime()}.sqlite3")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.enableWriteAheadLogging()
            fill(db)
        }
        return file.readBytes()
    }

    @Test fun futureAndUnreadableFilesNeverWrite() = runTest {
        val model = model()
        for (text in listOf("date\n2027-01-01", "garbage")) {
            content = text
            model.read(Uri.parse("content://test/csv")).join()
            model.confirm().join()
            assertTrue(model.state.value.periods.isEmpty())
            assertNotNull(model.state.value.error)
            assertTrue(repository.snapshot().periods.isEmpty())
        }
    }
}

private const val FLO_ZIP = "UEsDBBQAAQgIAAAAAADd80NFcgAAANkAAAALAAAAZXhwb3J0Lmpzb26ddfUQKNou15iQLR3dQztY11uLI2CKLaifE/qRCCYjVvgnY4POaTGrDmuvksi8iK2Ch5hzXFhdvYybaWBJttr8qsJTcUTd0+PWSH+KXCgLVrtgc1x+/IHGYztGuZ4ns/LBzgewNvatA+kZGT3tg0gIMJhQSwECFAAUAAEICAAAAAAA3fNDRXIAAADZAAAACwAAAAAAAAAAAAAAAAAAAAAAZXhwb3J0Lmpzb25QSwUGAAAAAAEAAQA5AAAAmwAAAAAA"
