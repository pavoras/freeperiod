package org.freeperiod.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freeperiod.app.R
import org.freeperiod.app.backup.DocumentIo
import org.freeperiod.app.data.Repository
import org.freeperiod.engine.Period
import org.freeperiod.engine.DayLog
import org.freeperiod.engine.importing.*

data class ImportUiState(
    val periods: List<Period> = emptyList(),
    val formats: List<CsvDateFormat> = emptyList(),
    val busy: Boolean = false,
    val error: Int? = null,
    val imported: Int? = null,
    val skipped: Int = 0,
    val dayLogs: List<DayLog> = emptyList(),
    /** A Flo ZIP was chosen and needs the password from Flo's e-mail. */
    val floPassword: Boolean = false,
)

class ImportViewModel(private val repository: Repository, private val io: DocumentIo,
    private val clock: () -> LocalDate) : ViewModel() {
    private val mutable = MutableStateFlow(ImportUiState())
    val state = mutable.asStateFlow()
    private var text: String? = null
    private var selectedFormat: CsvDateFormat? = null
    /** The chosen Flo file while it waits for its password; cleared after reading. */
    private var floBytes: ByteArray? = null
    private var fromFlo = false

    fun read(uri: Uri?): Job = operation(R.string.file_read_error) {
        if (uri == null) return@operation
        clearFlo()
        text = null
        selectedFormat = null
        mutable.value = ImportUiState(busy = true)
        val bytes = io.read(uri)
        text = try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
        parse(null)
    }

    /** Flo's export: the password-protected ZIP from Flo's support, or the JSON inside it. */
    fun readFlo(uri: Uri?): Job = operation(R.string.file_read_error) {
        if (uri == null) return@operation
        clearFlo()
        text = null
        mutable.value = ImportUiState(busy = true)
        floBytes = io.read(uri)
        fromFlo = true
        parseFlo(null)
    }

    fun unlockFlo(password: String): Job = operation(R.string.import_flo_invalid) {
        if (password.isEmpty() || floBytes == null) return@operation
        val secret = password.toCharArray()
        try { parseFlo(secret) } finally { secret.fill('\u0000') }
    }

    private suspend fun parseFlo(password: CharArray?) {
        val bytes = floBytes ?: return
        val result = withContext(Dispatchers.Default) { FloImport.parse(bytes, password, clock()) }
        if (result != FloImportResult.NeedsPassword && result != FloImportResult.WrongPassword) {
            bytes.fill(0)
            floBytes = null
        }
        mutable.update {
            when (result) {
                is FloImportResult.Parsed -> it.copy(periods = result.periods, floPassword = false)
                FloImportResult.NeedsPassword -> it.copy(floPassword = true)
                FloImportResult.WrongPassword -> it.copy(floPassword = true, error = R.string.import_flo_wrong_password)
                FloImportResult.Invalid -> it.copy(floPassword = false, error = R.string.import_flo_invalid)
            }
        }
    }

    private fun clearFlo() {
        floBytes?.fill(0)
        floBytes = null
        fromFlo = false
    }

    fun chooseFormat(format: CsvDateFormat): Job = operation(R.string.import_invalid) { parse(format) }

    private suspend fun parse(format: CsvDateFormat?) {
        val csv = text ?: return
        selectedFormat = format
        val data = repository.snapshot()
        when (val result = withContext(Dispatchers.Default) { CsvImport.parse(csv, format, data.tags, data.customCategories) }) {
            is CsvImportResult.Parsed -> {
                val today = clock()
                if (result.dayLogs.any { it.date > today } || result.periods.any { it.start > today || it.end?.let { end -> end > today } == true })
                    mutable.update { it.copy(periods = emptyList(), dayLogs = emptyList(), formats = emptyList(), error = R.string.import_invalid) }
                else mutable.update { it.copy(periods = result.periods, dayLogs = result.dayLogs, formats = emptyList()) }
            }
            is CsvImportResult.NeedsFormat -> mutable.update { it.copy(periods = emptyList(), dayLogs = emptyList(), formats = result.formats) }
            CsvImportResult.Invalid -> mutable.update { it.copy(periods = emptyList(), dayLogs = emptyList(), formats = emptyList(), error = R.string.import_invalid) }
        }
    }

    fun confirm(): Job {
        if ((state.value.periods.isEmpty() && state.value.dayLogs.isEmpty()) || state.value.imported != null) return viewModelScope.launch { }
        return operation(R.string.error_storage) {
            val periods = state.value.periods
            if ((periods.isEmpty() && state.value.dayLogs.isEmpty()) || state.value.imported != null) return@operation
            val (imported, skipped) = if (fromFlo) repository.importPeriods(periods)
                else repository.importCsv(requireNotNull(text), selectedFormat)
            text = null
            fromFlo = false
            selectedFormat = null
            mutable.update { it.copy(imported = imported, skipped = skipped) }
        }
    }

    fun documentUnavailable() { mutable.update { it.copy(error = R.string.file_read_error) } }

    override fun onCleared() { clearFlo() }

    private fun operation(failure: Int, action: suspend () -> Unit): Job = viewModelScope.launch {
        if (state.value.busy) return@launch
        mutable.update { it.copy(busy = true, error = null) }
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(error = failure) } }
        finally { mutable.update { it.copy(busy = false) } }
    }
}
