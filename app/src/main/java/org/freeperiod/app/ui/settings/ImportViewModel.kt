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
)

class ImportViewModel(private val repository: Repository, private val io: DocumentIo,
    private val clock: () -> LocalDate) : ViewModel() {
    private val mutable = MutableStateFlow(ImportUiState())
    val state = mutable.asStateFlow()
    private var text: String? = null
    private var selectedFormat: CsvDateFormat? = null

    fun read(uri: Uri?): Job = operation(R.string.file_read_error) {
        if (uri == null) return@operation
        text = null
        selectedFormat = null
        mutable.value = ImportUiState(busy = true)
        val bytes = io.read(uri)
        text = try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
        parse(null)
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
            val (imported, skipped) = repository.importCsv(requireNotNull(text), selectedFormat)
            text = null
            selectedFormat = null
            mutable.update { it.copy(imported = imported, skipped = skipped) }
        }
    }

    fun documentUnavailable() { mutable.update { it.copy(error = R.string.file_read_error) } }

    private fun operation(failure: Int, action: suspend () -> Unit): Job = viewModelScope.launch {
        if (state.value.busy) return@launch
        mutable.update { it.copy(busy = true, error = null) }
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(error = failure) } }
        finally { mutable.update { it.copy(busy = false) } }
    }
}
