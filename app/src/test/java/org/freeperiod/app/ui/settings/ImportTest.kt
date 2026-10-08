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
    private val io = object : DocumentIo {
        override suspend fun read(uri: Uri) = content.toByteArray()
        override suspend fun write(uri: Uri, bytes: ByteArray) = Unit
    }
    @Before fun main() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    private fun model() = ImportViewModel(repository, io) { today }.also { models.put("import", it) }

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
