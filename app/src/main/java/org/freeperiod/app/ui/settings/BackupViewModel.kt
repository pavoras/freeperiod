package org.freeperiod.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freeperiod.app.R
import org.freeperiod.app.backup.BackupTooLargeException
import org.freeperiod.app.backup.DocumentIo
import org.freeperiod.app.data.Repository
import org.freeperiod.engine.backup.*
import org.freeperiod.engine.export.CsvExport

data class BackupSummary(val periods: Int, val days: Int)

data class BackupUiState(
    val busy: Boolean = false,
    val awaitingDocument: Boolean = false,
    val restoreFileSelected: Boolean = false,
    val summary: BackupSummary? = null,
    val message: Int? = null,
    val error: Boolean = false,
)

sealed interface DocumentRequest {
    data class Create(val filename: String, val mime: String) : DocumentRequest
    data object Open : DocumentRequest
}

internal fun decodeMessage(result: DecodeResult): Int? = when (result) {
    is DecodeResult.Ok -> null
    DecodeResult.NotABackup -> R.string.restore_not_backup
    DecodeResult.UnsupportedVersion -> R.string.restore_unsupported
    DecodeResult.WrongPasswordOrCorrupt -> R.string.restore_password_or_corrupt
    DecodeResult.InvalidContent -> R.string.restore_invalid
}

class BackupViewModel(
    private val repository: Repository,
    private val io: DocumentIo,
    private val clock: () -> LocalDate,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val state = mutableState.asStateFlow()
    private val requests = Channel<DocumentRequest>(Channel.BUFFERED)
    val documents = requests.receiveAsFlow()
    private data class Output(val filename: String, val mime: String, val bytes: ByteArray)
    private var outputs = emptyList<Output>()
    private var reading = false
    private var restoreBytes: ByteArray? = null
    private var replacement: BackupData? = null
    private var csv = false

    fun createBackup(password: String, confirm: String): Job = operation(R.string.file_write_error) {
        when {
            password.length < 8 -> message(R.string.backup_password_short, true)
            password != confirm -> message(R.string.backup_password_mismatch, true)
            else -> {
                val snapshot = repository.snapshot()
                val secret = password.toCharArray()
                val bytes = try {
                    withContext(Dispatchers.Default) { BackupCodec.encode(snapshot, secret) }
                } finally { secret.fill('\u0000') }
                csv = false
                outputs = listOf(Output("freeperiod-${clock()}.fpbackup", "application/octet-stream", bytes))
                requestOutput()
            }
        }
    }

    fun openRestore() {
        if (state.value.busy || state.value.awaitingDocument) return
        clearRestore()
        reading = true
        mutableState.value = BackupUiState(awaitingDocument = true)
        requests.trySend(DocumentRequest.Open)
    }

    fun restoreDocument(uri: Uri?): Job {
        if (!reading) {
            message(R.string.file_read_error, true)
            return viewModelScope.launch { }
        }
        reading = false
        mutableState.update { it.copy(awaitingDocument = false) }
        return operation(R.string.file_read_error) {
            if (uri == null) {
                message(R.string.file_read_error, true)
            } else {
                restoreBytes = io.read(uri)
                mutableState.update { it.copy(restoreFileSelected = true) }
            }
        }
    }

    fun decodeRestore(password: String): Job = operation(R.string.restore_invalid) {
        val bytes = restoreBytes ?: return@operation
        replacement = null
        mutableState.update { it.copy(summary = null) }
        val secret = password.toCharArray()
        val decoded = try {
            withContext(Dispatchers.Default) { BackupCodec.decode(bytes, secret, clock) }
        } finally { secret.fill('\u0000') }
        when (decoded) {
            is DecodeResult.Ok -> {
                replacement = decoded.data
                mutableState.update { it.copy(summary = BackupSummary(decoded.data.periods.size, decoded.data.dayLogs.size)) }
                restoreBytes?.fill(0)
                restoreBytes = null
            }
            else -> message(requireNotNull(decodeMessage(decoded)), true)
        }
    }

    fun confirmRestore(): Job {
        if (replacement == null) return viewModelScope.launch { }
        return operation(R.string.error_storage) {
            repository.replaceAll(requireNotNull(replacement))
            clearRestore()
            message(R.string.restore_done)
        }
    }

    fun cancelRestore() {
        if (!state.value.busy && !state.value.awaitingDocument) {
            clearRestore()
            mutableState.value = BackupUiState()
        }
    }

    fun exportCsv(): Job = operation(R.string.file_write_error) {
        val data = repository.snapshot()
        val date = clock()
        outputs = withContext(Dispatchers.Default) {
            listOf(Output("freeperiod-$date-days.csv", "text/csv", CsvExport.days(data.dayLogs, data.tags).toByteArray()),
                Output("freeperiod-$date-periods.csv", "text/csv", CsvExport.periods(data.periods).toByteArray()))
        }
        csv = true
        requestOutput()
    }

    fun createdDocument(uri: Uri?): Job {
        if (outputs.isEmpty() || !state.value.awaitingDocument) {
            message(R.string.file_write_error, true)
            return viewModelScope.launch { }
        }
        mutableState.update { it.copy(awaitingDocument = false) }
        return operation(R.string.file_write_error) {
            if (uri == null) {
                clearOutputs()
                message(R.string.file_write_error, true)
                return@operation
            }
            val output = outputs.first()
            io.write(uri, output.bytes)
            output.bytes.fill(0)
            outputs = outputs.drop(1)
            if (outputs.isNotEmpty()) requestOutput()
            else message(if (csv) R.string.csv_saved else R.string.backup_saved)
        }
    }

    fun documentLaunchFailed() {
        val wasReading = reading
        reading = false
        clearOutputs()
        mutableState.update { it.copy(awaitingDocument = false) }
        message(if (wasReading) R.string.file_read_error else R.string.file_write_error, true)
    }

    private fun requestOutput() {
        val next = outputs.first()
        mutableState.update { it.copy(awaitingDocument = true) }
        requests.trySend(DocumentRequest.Create(next.filename, next.mime))
    }

    private fun message(label: Int, error: Boolean = false) {
        mutableState.update { it.copy(message = label, error = error) }
    }

    private fun clearRestore() {
        restoreBytes?.fill(0)
        restoreBytes = null
        replacement = null
        mutableState.update { it.copy(summary = null, restoreFileSelected = false) }
    }

    private fun clearOutputs() {
        outputs.forEach { it.bytes.fill(0) }
        outputs = emptyList()
    }

    private fun operation(failure: Int, action: suspend () -> Unit): Job = viewModelScope.launch {
        if (state.value.busy || state.value.awaitingDocument) return@launch
        mutableState.update { it.copy(busy = true, message = null, error = false) }
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: BackupTooLargeException) {
            message(R.string.restore_too_large, true)
        } catch (_: Exception) {
            clearOutputs()
            message(failure, true)
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    override fun onCleared() {
        clearRestore()
        clearOutputs()
        requests.close()
    }
}
