package org.freeperiod.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.freeperiod.app.backup.DocumentIo
import org.freeperiod.app.data.DatabaseTest
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
    private fun model() = ImportViewModel(repository, io) { today }.also { models.put("import", it) }

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
        model.readFlo(Uri.parse("content://test/flo")).join()
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
        model.readFlo(Uri.parse("content://test/flo")).join()
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
        model.readFlo(Uri.parse("content://test/flo")).join()
        assertEquals(org.freeperiod.app.R.string.import_flo_invalid, model.state.value.error)
        model.confirm().join()
        assertTrue(repository.snapshot().periods.isEmpty())
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
