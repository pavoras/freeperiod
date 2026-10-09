package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
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
