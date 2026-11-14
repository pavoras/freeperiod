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
import org.freeperiod.app.backup.AppDatabaseReader
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
    /** A note on what the chosen file leaves out (shown with the preview). */
    val notice: Int? = null,
    /** Entries from another app keep what is already logged instead of replacing it. */
    val merges: Boolean = false,
)

class ImportViewModel(private val repository: Repository, private val io: DocumentIo,
    private val clock: () -> LocalDate, private val databases: AppDatabaseReader? = null) : ViewModel() {
    private val mutable = MutableStateFlow(ImportUiState())
    val state = mutable.asStateFlow()
    private var text: String? = null
    private var selectedFormat: CsvDateFormat? = null
    /** The chosen Flo file while it waits for its password; cleared after reading. */
    private var floBytes: ByteArray? = null
    private var fromFlo = false
    /** Periods and days read from Clue or Bluemoon, waiting for confirmation. */
    private var fromApp: AppImport? = null

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

    /**
     * Another app's file, told apart by its content: Bluemoon's SQLite backup, Period Calendar's backup (.pc),
     * Clue's backup (JSON with day entries), or Flo's export (the JSON). Any of these may come in a ZIP, also a
     * password-protected one (Clue's data download, Flo's export); its files are read the same way.
     */
    fun readApp(uri: Uri?): Job = operation(R.string.file_read_error) {
        if (uri == null) return@operation
        clearFlo()
        text = null
        mutable.value = ImportUiState(busy = true)
        floBytes = io.read(uri)
        openApp(null)
    }

    fun unlockFlo(password: String): Job = operation(R.string.import_app_invalid) {
        if (password.isEmpty() || floBytes == null) return@operation
        val secret = password.toCharArray()
        try { openApp(secret) } finally { secret.fill('\u0000') }
    }

    /** Unpacks the chosen file (asking for the password when needed) and shows the first file inside that can be read. */
    private suspend fun openApp(password: CharArray?) {
        val bytes = floBytes ?: return
        val files = try {
            withContext(Dispatchers.Default) { ImportArchive.files(bytes, password) }
        } catch (_: ZipPasswordRequired) {
            mutable.update { it.copy(floPassword = true) }
            return
        } catch (_: ZipWrongPassword) {
            mutable.update { it.copy(floPassword = true, error = R.string.import_flo_wrong_password) }
            return
        } catch (_: Exception) {
            emptyList()
        }
        floBytes = null
        try {
            val today = clock()
            for (file in files) if (show(file, today)) return
            mutable.update { it.copy(floPassword = false, error = R.string.import_app_invalid) }
        } finally {
            files.forEach { it.fill(0) }
            bytes.fill(0)
        }
    }

    /** Reads one file; false when it is none of the supported apps' files. */
    private suspend fun show(bytes: ByteArray, today: LocalDate): Boolean {
        val periodCalendar = PeriodCalendarImport.isBackup(bytes)
        val database = if (periodCalendar) PeriodCalendarImport.database(bytes)
            else bytes.takeIf { AppDatabaseReader.isSqlite(it) }
        val other = withContext(Dispatchers.IO) {
            if (database != null) databases?.read(database, today) else ClueImport.parse(bytes, today)
        }
        if (database != null && database !== bytes) database.fill(0)
        if (other != null) {
            fromApp = other
            // Days with only Clue tags or other Clue options count as entries too.
            val days = other.dayLogs + (other.tagNames.keys + other.items.keys - other.dayLogs.map { it.date }.toSet()).map { DayLog(it) }
            mutable.update { it.copy(periods = other.periods, dayLogs = days.sortedBy { it.date }, floPassword = false,
                notice = R.string.import_period_calendar_partial.takeIf { periodCalendar }, merges = true) }
            return true
        }
        if (database != null || periodCalendar) return false
        val flo = withContext(Dispatchers.Default) { FloImport.parse(bytes, null, today) }
        if (flo !is FloImportResult.Parsed) return false
        fromFlo = true
        mutable.update { it.copy(periods = flo.periods, floPassword = false) }
        return true
    }

    private fun clearFlo() {
        floBytes?.fill(0)
        floBytes = null
        fromFlo = false
        fromApp = null
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
            val app = fromApp
            val (imported, skipped) = if (app != null) repository.importApp(app)
                else if (fromFlo) repository.importPeriods(periods)
                else repository.importCsv(requireNotNull(text), selectedFormat)
            text = null
            fromFlo = false
            fromApp = null
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
