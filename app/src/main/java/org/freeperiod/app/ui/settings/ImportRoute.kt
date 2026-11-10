package org.freeperiod.app.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.freeperiod.app.AppContainer
import org.freeperiod.app.R

/** Both entry points share the same preview and the existing authenticated restore flow. */
@Composable
fun ImportRoute(container: AppContainer, onRestored: () -> Unit = {}, start: ImportStart? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model: ImportViewModel = viewModel(key = "csv-import", factory = viewModelFactory {
        initializer { ImportViewModel(container.repository, container.backupIo, container.clock, container.appDatabaseReader) }
    })
    val backup: BackupViewModel = viewModel(key = "import-backup", factory = viewModelFactory {
        initializer { BackupViewModel(container.repository, container.backupIo, container.clock) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    val recovery by backup.state.collectAsStateWithLifecycle()
    var restoring by rememberSaveable { mutableStateOf(false) }
    // Opened from the backup page for one kind of file: cancelling that picker goes straight back there.
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null && start != null) onBack() else model.read(it)
    }
    val app = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null && start != null) onBack() else model.readApp(it)
    }
    val launchCsv = {
        try { csv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
        catch (_: ActivityNotFoundException) { model.documentUnavailable() }
        catch (_: SecurityException) { model.documentUnavailable() }
    }
    val launchApp = {
        try { app.launch(arrayOf("application/zip", "application/json", "application/vnd.sqlite3", "application/x-sqlite3",
            "application/octet-stream", "*/*")) }
        catch (_: ActivityNotFoundException) { model.documentUnavailable() }
        catch (_: SecurityException) { model.documentUnavailable() }
    }
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start == null || started) return@LaunchedEffect
        started = true
        if (start == ImportStart.CSV) launchCsv() else launchApp()
    }
    // "Backup" opens the file picker at once; cancelling it returns to the import choices.
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        backup.restoreDocument(it)
        if (it == null) restoring = false
    }
    LaunchedEffect(backup) {
        backup.documents.collect { request ->
            if (request == DocumentRequest.Open) try { restore.launch(arrayOf("application/octet-stream", "*/*")) }
            catch (_: ActivityNotFoundException) { backup.documentLaunchFailed() }
            catch (_: SecurityException) { backup.documentLaunchFailed() }
        }
    }
    val closeRestore = { backup.cancelRestore(); restoring = false }
    BackHandler(restoring, onBack = closeRestore)
    if (restoring) BackupScreen(recovery, { _, _ -> }, backup::openRestore, { backup.decodeRestore(it) },
        { scope.launch {
            backup.confirmRestore().join()
            if (backup.state.value.message == R.string.restore_done) onRestored()
        } }, backup::cancelRestore, closeRestore, restoreOnly = true)
    else ImportScreen(state, launchCsv, { restoring = true; backup.openRestore() }, { model.chooseFormat(it) }, { model.confirm() },
        onBack, launchApp, { model.unlockFlo(it) }, choices = start == null)
}
