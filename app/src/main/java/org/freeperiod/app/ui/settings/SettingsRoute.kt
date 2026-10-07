package org.freeperiod.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.freeperiod.app.AppContainer
import org.freeperiod.app.R

private enum class SettingsPage { MAIN, BACKUP, PRIVACY, ABOUT }

@Composable
fun SettingsRoute(container: AppContainer) {
    val context = LocalContext.current
    val model: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(container.repository, container.settings) }
    })
    val backup: BackupViewModel = viewModel(factory = viewModelFactory {
        initializer { BackupViewModel(container.repository, container.backupIo, container.clock) }
    })
    val settings by model.state.collectAsStateWithLifecycle()
    val recovery by backup.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var externalError by remember { mutableStateOf(false) }
    val back = { page = SettingsPage.MAIN; backup.cancelRestore() }
    BackHandler(page != SettingsPage.MAIN, onBack = back)
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { backup.createdDocument(it) }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { backup.createdDocument(it) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { backup.restoreDocument(it) }
    LaunchedEffect(backup) {
        backup.documents.collect { request ->
            try {
                when (request) {
                    DocumentRequest.Open -> openBackup.launch(arrayOf("application/octet-stream", "*/*"))
                    is DocumentRequest.Create -> if (request.mime == "text/csv") createCsv.launch(request.filename)
                        else createBackup.launch(request.filename)
                }
            } catch (_: ActivityNotFoundException) { backup.documentLaunchFailed() }
            catch (_: SecurityException) { backup.documentLaunchFailed() }
        }
    }
    fun openExternal(intent: Intent) {
        externalError = false
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { externalError = true }
        catch (_: SecurityException) { externalError = true }
    }
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    Surface(Modifier.fillMaxSize()) {
        Column {
            if (externalError) Text(stringResource(R.string.settings_external_error))
            when (page) {
                SettingsPage.MAIN -> Column {
                    RecoveryMessage(recovery)
                    SettingsScreen(settings, SettingsActions(
                        typicalLength = { model.setTypicalLength(it) }, paused = { model.setPaused(it) },
                        dynamicColor = { model.setDynamicColor(it) }, language = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                openExternal(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }
                        }, backup = { page = SettingsPage.BACKUP }, csv = { backup.exportCsv() },
                        privacy = { page = SettingsPage.PRIVACY }, about = { page = SettingsPage.ABOUT },
                        deleteAll = { model.deleteAllData() }), recovery.busy || recovery.awaitingDocument)
                }
                SettingsPage.BACKUP -> BackupScreen(recovery, { password, confirm -> backup.createBackup(password, confirm) },
                    backup::openRestore, { backup.decodeRestore(it) }, { backup.confirmRestore() }, backup::cancelRestore, back)
                SettingsPage.PRIVACY -> PrivacyScreen(back) { openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.PRIVACY_URL))) }
                SettingsPage.ABOUT -> AboutScreen(version, back) { openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.SOURCE_URL))) }
            }
        }
    }
}
