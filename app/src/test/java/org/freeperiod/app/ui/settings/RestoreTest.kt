package org.freeperiod.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.freeperiod.app.R
import org.freeperiod.app.backup.DocumentIo
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestoreTest : DatabaseTest() {
    private val models = ViewModelStore()
    private val password = "test password"
    private val uri = Uri.parse("content://test/backup")
    private val io = FakeDocumentIo()
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { models.clear(); Dispatchers.resetMain() }
    private fun model() = BackupViewModel(repository, io) { today }.also { models.put("backup", it) }

    @Test fun failedRestoreKeepsExistingData() = runTest {
        repository.addPeriod(today.minusDays(10), today.minusDays(6)).getOrThrow()
        repository.saveDayLog(DayLog(today, mood = Mood.GOOD))
        repository.updateDomainSettings(BackupSettings(29, true))
        val before = repository.snapshot()
        io.bytes = BackupCodec.encode(before, password.toCharArray(), iterations = 100_000)
        val vm = model()
        vm.openRestore()
        vm.restoreDocument(uri).join()
        vm.decodeRestore("wrong password").join()
        vm.confirmRestore().join()
        assertEquals(R.string.restore_password_or_corrupt, vm.state.value.message)
        assertNull(vm.state.value.summary)
        assertEquals(before, repository.snapshot())
    }

    @Test fun restoreReplacesDataAndSettings() = runTest {
        repository.addPeriod(today.minusDays(30), today.minusDays(26)).getOrThrow()
        val replacement = BackupData(periods = listOf(Period(8, today.minusDays(8), today.minusDays(4))),
            dayLogs = listOf(DayLog(today, mood = Mood.OKAY, tagIds = setOf(7))),
            tags = listOf(Tag(7, "Travel")), settings = BackupSettings(31, true))
        io.bytes = BackupCodec.encode(replacement, password.toCharArray(), iterations = 100_000)
        val vm = model()
        vm.openRestore()
        vm.restoreDocument(uri).join()
        vm.decodeRestore(password).join()
        assertEquals(BackupSummary(1, 1), vm.state.value.summary)
        assertNotEquals(replacement, repository.snapshot())
        vm.confirmRestore().join()
        assertEquals(replacement, repository.snapshot())
    }

    @Test fun passwordMismatchBlocksBackup() = runTest {
        val vm = model()
        vm.createBackup(password, "different password").join()
        assertEquals(R.string.backup_password_mismatch, vm.state.value.message)
        assertFalse(vm.state.value.awaitingDocument)
        assertEquals(0, io.writes)
    }

    @Test fun shortPasswordBlocksBackup() = runTest {
        val vm = model()
        vm.createBackup("short", "short").join()
        assertEquals(R.string.backup_password_short, vm.state.value.message)
        assertFalse(vm.state.value.awaitingDocument)
    }

    @Test fun cancelledAndFailedWritesNeverReportSuccess() = runTest {
        val vm = model()
        vm.createBackup(password, password).join()
        vm.createdDocument(null).join()
        assertEquals(R.string.file_write_error, vm.state.value.message)
        vm.createBackup(password, password).join()
        io.failWrite = true
        vm.createdDocument(uri).join()
        assertEquals(R.string.file_write_error, vm.state.value.message)
        assertFalse(vm.state.value.awaitingDocument)
    }

    @Test fun csvUsesOneSnapshotAndReportsSuccessOnlyAfterBothWrites() = runTest {
        repository.saveDayLog(DayLog(today, mood = Mood.GOOD))
        val vm = model()
        vm.exportCsv().join()
        assertNull(vm.state.value.message)
        vm.createdDocument(uri).join()
        assertTrue(vm.state.value.awaitingDocument)
        assertNull(vm.state.value.message)
        repository.saveDayLog(DayLog(today, mood = Mood.BAD))
        vm.createdDocument(uri).join()
        assertEquals(2, io.writes)
        assertEquals(R.string.csv_saved, vm.state.value.message)
        assertTrue(io.contents.first().contains("good"))
    }
    @Test fun csvIncludesCustomItems() = runTest {
        val category = repository.addCustomCategory("Movement")
        val item = repository.addTag("Walk", category.id, "energetic")
        repository.saveDayLog(DayLog(today, tagIds = setOf(item.id)))
        val vm = model()
        vm.exportCsv().join()
        vm.createdDocument(uri).join()
        assertTrue(io.contents.single().contains("Movement:Walk"))
        assertTrue(vm.state.value.awaitingDocument)
        vm.createdDocument(uri).join()
        assertEquals(R.string.csv_saved, vm.state.value.message)
    }

    @Test fun decodeErrorsMapToMessages() {
        val results = listOf(DecodeResult.NotABackup, DecodeResult.UnsupportedVersion,
            DecodeResult.WrongPasswordOrCorrupt, DecodeResult.InvalidContent)
        val expected = listOf(R.string.restore_not_backup, R.string.restore_unsupported,
            R.string.restore_password_or_corrupt, R.string.restore_invalid)
        assertEquals(expected, results.map(::decodeMessage))
        assertNull(decodeMessage(DecodeResult.Ok(BackupData(periods = emptyList(), dayLogs = emptyList(),
            tags = emptyList(), settings = BackupSettings(null, false)))))
    }

    @Test fun pickerResultAfterProcessDeathReportsError() = runTest {
        val vm = model()
        vm.createdDocument(uri).join()
        assertEquals(R.string.file_write_error, vm.state.value.message)
        vm.restoreDocument(uri).join()
        assertEquals(R.string.file_read_error, vm.state.value.message)
        assertEquals(0, io.writes)
    }
}

private class FakeDocumentIo : DocumentIo {
    var bytes = byteArrayOf()
    var writes = 0
    var failWrite = false
    val contents = mutableListOf<String>()
    override suspend fun read(uri: Uri): ByteArray = bytes.copyOf()
    override suspend fun write(uri: Uri, bytes: ByteArray) {
        if (failWrite) throw java.io.IOException("Test failure")
        writes++
        contents += bytes.toString(Charsets.UTF_8)
    }
}
