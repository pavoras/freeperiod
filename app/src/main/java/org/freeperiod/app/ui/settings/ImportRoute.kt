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
fun ImportRoute(container: AppContainer, onRestored: () -> Unit = {}, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model: ImportViewModel = viewModel(key = "csv-import", factory = viewModelFactory {
        initializer { ImportViewModel(container.repository, container.backupIo, container.clock) }
    })
    val backup: BackupViewModel = viewModel(key = "import-backup", factory = viewModelFactory {
        initializer { BackupViewModel(container.repository, container.backupIo, container.clock) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    val recovery by backup.state.collectAsStateWithLifecycle()
    var restoring by rememberSaveable { mutableStateOf(false) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.read(it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { backup.restoreDocument(it) }
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
    else ImportScreen(state, {
        try { csv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
        catch (_: ActivityNotFoundException) { model.documentUnavailable() }
        catch (_: SecurityException) { model.documentUnavailable() }
    }, { restoring = true }, { model.chooseFormat(it) }, { model.confirm() }, onBack)
}
