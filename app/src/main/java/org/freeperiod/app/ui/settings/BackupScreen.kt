package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.freeperiod.app.R
import org.freeperiod.app.backup.AutoBackupInterval
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*

@Composable
fun BackupScreen(
    state: BackupUiState,
    onBackup: (String, String) -> Unit,
    onOpen: () -> Unit,
    onDecode: (String) -> Unit,
    onRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    restoreOnly: Boolean = false,
    auto: AutoBackupUiState? = null,
    autoActions: AutoBackupActions = AutoBackupActions(),
) {
    // Passwords deliberately do not enter saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var askRestore by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.busy && !state.awaitingDocument
    Column(Modifier.fillMaxSize().background(LocalDaylight.current.background).verticalScroll(rememberScrollState()).imePadding().padding(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsPageHeader(R.string.backup_restore, onBack)
        onImport?.let { action ->
            FpButton(action, primary = false, enabled = enabled) { Text(stringResource(R.string.import_title)) }
        }
        if (state.summary == null) {
            if (!restoreOnly && auto != null) {
                AutoBackupSection(auto, autoActions)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            if (!restoreOnly) {
                Text(stringResource(R.string.create_backup), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.backup_forgotten_warning), style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, R.string.backup_password, enabled)
                PasswordField(confirm, { confirm = it }, R.string.backup_confirm_password, enabled)
                FpButton(onClick = { onBackup(password, confirm); password = ""; confirm = "" }, enabled = enabled) {
                    Text(stringResource(R.string.create_backup))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            Text(stringResource(R.string.restore_backup), style = MaterialTheme.typography.titleLarge)
            FpButton(primary = false, onClick = { restorePassword = ""; onOpen() }, enabled = enabled) {
                Text(stringResource(R.string.restore_choose_file))
            }
            if (state.restoreFileSelected) {
                Text(stringResource(R.string.restore_file_selected), style = MaterialTheme.typography.bodySmall)
                PasswordField(restorePassword, { restorePassword = it }, R.string.backup_password, enabled)
                FpButton(onClick = { onDecode(restorePassword); restorePassword = "" }, enabled = enabled) {
                    Text(stringResource(R.string.restore_read))
                }
            }
        }
        state.summary?.let { summary ->
            FpCard() {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(pluralStringResource(R.plurals.restore_periods, summary.periods, summary.periods) + ", " +
                        pluralStringResource(R.plurals.restore_days, summary.days, summary.days), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.restore_replace_warning))
                    FpButton(onClick = { askRestore = true }, enabled = enabled) { Text(stringResource(R.string.restore_replace)) }
                    TextButton(onClick = onCancelRestore, enabled = enabled) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
        if (state.busy) CircularProgressIndicator()
        RecoveryMessage(state)
    }
    if (askRestore && state.summary != null) ConfirmationDialog(R.string.restore_replace, R.string.restore_replace_warning,
        R.string.restore_replace, onDismiss = { askRestore = false }, onConfirm = { askRestore = false; onRestore() })
}

class AutoBackupActions(
    val interval: (AutoBackupInterval) -> Unit = {},
    val start: (String, String) -> Unit = { _, _ -> },
    val now: () -> Unit = {},
    val off: () -> Unit = {},
)

@Composable
private fun AutoBackupSection(state: AutoBackupUiState, actions: AutoBackupActions) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val t = LocalDaylight.current
    Text(stringResource(R.string.auto_backup), style = MaterialTheme.typography.titleLarge)
    if (state.on) {
        val locale = LocalConfiguration.current.locales[0]
        Text(stringResource(R.string.auto_backup_status, stringResource(intervalLabel(state.interval)), state.folderName.orEmpty()),
            style = MaterialTheme.typography.bodyLarge)
        Text(state.last?.let { stringResource(R.string.auto_backup_last,
            it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))) }
            ?: stringResource(R.string.auto_backup_none_yet), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        if (state.failed) Text(stringResource(R.string.auto_backup_failed), color = MaterialTheme.colorScheme.error)
    } else {
        Text(stringResource(R.string.auto_backup_intro), style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoBackupInterval.entries.forEach {
            FpChip(state.interval == it, { actions.interval(it) }, stringResource(intervalLabel(it)), enabled = !state.busy)
        }
    }
    if (state.on) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FpButton(actions.now, primary = false, enabled = !state.busy) { Text(stringResource(R.string.auto_backup_now)) }
            TextButton(actions.off, enabled = !state.busy) { Text(stringResource(R.string.auto_backup_turn_off)) }
        }
    } else {
        Text(stringResource(R.string.backup_forgotten_warning), style = MaterialTheme.typography.bodyMedium)
        PasswordField(password, { password = it }, R.string.backup_password, !state.busy)
        PasswordField(confirm, { confirm = it }, R.string.backup_confirm_password, !state.busy)
        FpButton(onClick = { actions.start(password, confirm); password = ""; confirm = "" }, enabled = !state.busy) {
            Text(stringResource(R.string.auto_backup_turn_on))
        }
    }
    state.message?.let { Text(stringResource(it), color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
}

internal fun intervalLabel(value: AutoBackupInterval) = when (value) {
    AutoBackupInterval.DAILY -> R.string.auto_backup_daily
    AutoBackupInterval.WEEKLY -> R.string.auto_backup_weekly
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: Int, enabled: Boolean) {
    OutlinedTextField(value, onChange, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}

@Composable
internal fun RecoveryMessage(state: BackupUiState) {
    state.message?.let { Text(stringResource(it), color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
}
