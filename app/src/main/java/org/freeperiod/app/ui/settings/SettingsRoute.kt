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
import org.freeperiod.app.lock.lockAvailable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private enum class SettingsPage { MAIN, APPEARANCE, BACKUP, PRIVACY, ABOUT, SITUATION, REMINDERS, REMINDER_EDITOR, DAY_ENTRY, IMPORT }

@Composable
fun SettingsRoute(container: AppContainer) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val model: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(container.repository, container.settings, { lockAvailable(appContext) }) { container.autoBackup.turnOff() } }
    })
    val backup: BackupViewModel = viewModel(factory = viewModelFactory {
        initializer { BackupViewModel(container.repository, container.backupIo, container.clock) }
    })
    val auto: AutoBackupViewModel = viewModel(factory = viewModelFactory {
        initializer { AutoBackupViewModel(container.autoBackup, container.settings) }
    })
    val autoState by auto.state.collectAsStateWithLifecycle()
    val tracking: TrackingSettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { TrackingSettingsViewModel(container.repository) }
    })
    val trackingState by tracking.state.collectAsStateWithLifecycle()
    var editingReminderJson by rememberSaveable { mutableStateOf<String?>(null) }
    val editingReminder = remember(editingReminderJson) { editingReminderJson?.let { Json.decodeFromString<org.freeperiod.engine.Reminder>(it) } }
    fun editReminder(value: org.freeperiod.engine.Reminder) { editingReminderJson = Json.encodeToString(value) }
    val settings by model.state.collectAsStateWithLifecycle()
    val recovery by backup.state.collectAsStateWithLifecycle()
    val reminderAccess = rememberReminderAccess(container.notifications)
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var externalError by remember { mutableStateOf(false) }
    val back = { page = when (page) { SettingsPage.REMINDER_EDITOR -> SettingsPage.REMINDERS; SettingsPage.IMPORT -> SettingsPage.BACKUP; else -> SettingsPage.MAIN }; backup.cancelRestore() }
    fun saveReminder(value: org.freeperiod.engine.Reminder) {
        if (value.enabled && trackingState.data.reminders.none { it.enabled }) reminderAccess.requestPermission()
        tracking.reminder(value)
    }
    BackHandler(page != SettingsPage.MAIN, onBack = back)
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { backup.createdDocument(it) }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { backup.createdDocument(it) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { backup.restoreDocument(it) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { auto.folderChosen(it?.toString()) }
    LaunchedEffect(auto) {
        auto.folderRequests.collect {
            try { chooseFolder.launch(null) }
            catch (_: ActivityNotFoundException) { auto.folderChosen(null) }
            catch (_: SecurityException) { auto.folderChosen(null) }
        }
    }
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
    Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
        Column {
            if (trackingState.error) Text(stringResource(R.string.error_storage))
            if (externalError) Text(stringResource(R.string.settings_external_error))
            when (page) {
                SettingsPage.MAIN -> Column {
                    RecoveryMessage(recovery)
                    SettingsScreen(settings, SettingsActions(
                        typicalLength = { model.setTypicalLength(it) }, paused = { model.setPaused(it) },
                        situation = { page = SettingsPage.SITUATION }, reminders = { page = SettingsPage.REMINDERS },
                        dayEntry = { page = SettingsPage.DAY_ENTRY },
                        appearance = { page = SettingsPage.APPEARANCE }, language = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                openExternal(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }
                        }, backup = { page = SettingsPage.BACKUP }, csv = { backup.exportCsv() },
                        privacy = { page = SettingsPage.PRIVACY }, about = { page = SettingsPage.ABOUT },
                        deleteAll = { model.deleteAllData() },
                        lockEnabled = { model.setLockEnabled(it) }, lockTimeout = { model.setLockTimeout(it) }),
                        recovery.busy || recovery.awaitingDocument)
                }
                SettingsPage.SITUATION -> SituationScreen(trackingState.data.situation, container.clock(), { tracking.situation(it) }, back) {
                    editReminder(it); page = SettingsPage.REMINDER_EDITOR
                }
                SettingsPage.REMINDERS -> RemindersScreen(trackingState.data.reminders, ::saveReminder,
                    { editReminder(it); page = SettingsPage.REMINDER_EDITOR }, {
                        editReminder(org.freeperiod.engine.Reminder(0, org.freeperiod.engine.ReminderKind.CUSTOM, null,
                            org.freeperiod.engine.Recurrence.Daily, java.time.LocalTime.of(20, 0), false))
                        page = SettingsPage.REMINDER_EDITOR
                    }, { tracking.deleteReminder(it) }, back, settings.device.explicitNotifications, { model.setExplicitNotifications(it) },
                    reminderAccess.available, { openExternal(reminderAccess.settingsIntent) })
                SettingsPage.REMINDER_EDITOR -> editingReminder?.let {
                    ReminderEditor(it, container.clock(), { value -> saveReminder(value); page = SettingsPage.REMINDERS }, back)
                } ?: RemindersScreen(trackingState.data.reminders, ::saveReminder, {}, {}, {}, back)
                SettingsPage.DAY_ENTRY -> DayEntrySettingsScreen(trackingState.data, container.clock(), back,
                    { key, hidden, order -> tracking.override(key, hidden, order) }, { tracking.reorder(it) },
                    { name, icon, category, singleChoice -> tracking.category(name, icon, category, singleChoice) }, { tracking.archive(it) },
                    { name, icon, category, field -> tracking.item(name, icon, category, field,
                        context.getString(org.freeperiod.app.ui.day.builtInCategories.find { it.key == field }?.label ?: R.string.entry_tags)) },
                    onRestore = { tracking.restore(it) }, onAppearance = { tracking.appearance(it) },
                    onEditItem = { tracking.editItem(it) }, onDeleteItem = { tracking.deleteItem(it) })
                SettingsPage.APPEARANCE -> AppearanceScreen(settings.accent, { model.setAccent(it) }, back, trackingState.data.overrides)
                SettingsPage.BACKUP -> BackupScreen(recovery, { password, confirm -> backup.createBackup(password, confirm) },
                    backup::openRestore, { backup.decodeRestore(it) }, { backup.confirmRestore() }, backup::cancelRestore, back, onImport = { page = SettingsPage.IMPORT },
                    auto = autoState, autoActions = AutoBackupActions(auto::choose, auto::start, auto::backUpNow, auto::turnOff))
                SettingsPage.IMPORT -> ImportRoute(container) { page = SettingsPage.BACKUP }
                SettingsPage.PRIVACY -> PrivacyScreen(back) { openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.PRIVACY_URL))) }
                SettingsPage.ABOUT -> AboutScreen(version, back) { openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.SOURCE_URL))) }
            }
        }
    }
}
