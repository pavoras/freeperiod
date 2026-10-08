package org.freeperiod.app.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.*
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.data.LockTimeout

data class SettingsActions(
    val typicalLength: (Int?) -> Unit = {},
    val paused: (Boolean) -> Unit = {},
    val appearance: () -> Unit = {},
    val situation: () -> Unit = {},
    val reminders: () -> Unit = {},
    val dayEntry: () -> Unit = {},
    val language: () -> Unit = {},
    val backup: () -> Unit = {},
    val csv: () -> Unit = {},
    val privacy: () -> Unit = {},
    val about: () -> Unit = {},
    val deleteAll: () -> Unit = {},
    val lockEnabled: (Boolean) -> Unit = {},
    val lockTimeout: (LockTimeout) -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, recoveryBusy: Boolean = false) {
    var lengthDialog by rememberSaveable { mutableStateOf(false) }
    var csvDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var feedbackDialog by rememberSaveable { mutableStateOf(false) }
    var timeoutDialog by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.loading && !state.writing && !recoveryBusy
    LazyColumn(Modifier.fillMaxSize().background(LocalDaylight.current.background), contentPadding = PaddingValues(FpSpacing.screen)) {
        item {
            FpTopBar(stringResource(R.string.nav_settings))
        }
        item { SettingsRow(R.string.appearance, enabled, actions.appearance, stringResource(accentLabel(state.accent))) }
        if (state.loading) item { CircularProgressIndicator() }
        item {
            SettingsRow(R.string.typical_cycle_length, enabled, { lengthDialog = true },
                state.typicalCycleLength?.let { stringResource(R.string.settings_cycle_days, it) }
                    ?: stringResource(R.string.cycle_unknown))
        }
        item { SettingsSwitch(R.string.pause_predictions, state.predictionsPaused, enabled, actions.paused) }
        item { SettingsRow(R.string.my_situation, enabled, actions.situation) }
        item { SettingsRow(R.string.reminders, enabled, actions.reminders) }
        item { SettingsRow(R.string.customize_day_entry, enabled, actions.dayEntry) }
        item {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SettingsSwitch(R.string.app_lock, state.device.lockEnabled, enabled && (state.device.lockEnabled || state.lockCanEnable), actions.lockEnabled)
            if (!state.lockCanEnable && !state.device.lockEnabled) Text(stringResource(R.string.lock_unavailable), style = MaterialTheme.typography.bodySmall)
            if (state.device.lockEnabled) SettingsRow(R.string.lock_timeout, enabled, { timeoutDialog = true }, stringResource(timeoutLabel(state.device.lockTimeout)))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) item {
            SettingsRow(R.string.language, enabled, actions.language)
        }
        item { SettingsRow(R.string.backup_restore, enabled, actions.backup) }
        item { SettingsRow(R.string.export_csv, enabled, { csvDialog = true }) }
        item { SettingsRow(R.string.privacy_policy, enabled, actions.privacy) }
        item { SettingsRow(R.string.feedback, enabled, { feedbackDialog = true }) }
        item { SettingsRow(R.string.about, enabled, actions.about) }
        item {
            TextButton(onClick = { deleteDialog = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_fp_delete), null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_all_data), color = MaterialTheme.colorScheme.error)
            }
        }
        state.message?.let { label -> item { Text(stringResource(label), Modifier.padding(top = 12.dp)) } }
    }
    if (lengthDialog) TypicalLengthDialog(state.typicalCycleLength, { lengthDialog = false }) {
        lengthDialog = false
        actions.typicalLength(it)
    }
    if (csvDialog) ConfirmationDialog(R.string.export_csv, R.string.csv_warning, R.string.export_csv,
        onDismiss = { csvDialog = false }, onConfirm = { csvDialog = false; actions.csv() })
    if (feedbackDialog) FeedbackDialog { feedbackDialog = false }
    if (deleteDialog) ConfirmationDialog(R.string.delete_all_data, R.string.delete_all_warning, R.string.delete_all_data,
        onDismiss = { deleteDialog = false }, onConfirm = { deleteDialog = false; actions.deleteAll() })
    if (timeoutDialog) AlertDialog(onDismissRequest = { timeoutDialog = false },
        title = { Text(stringResource(R.string.lock_timeout)) }, text = {
            Column {
                LockTimeout.entries.forEach { timeout ->
                    Row(Modifier.fillMaxWidth().clickable { actions.lockTimeout(timeout); timeoutDialog = false }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(timeout == state.device.lockTimeout, null)
                        Text(stringResource(timeoutLabel(timeout)))
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { timeoutDialog = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun SettingsRow(label: Int, enabled: Boolean, onClick: () -> Unit, detail: String? = null) {
    val icon = when (label) {
        R.string.appearance -> R.drawable.ic_fp_appearance
        R.string.my_situation -> R.drawable.ic_fp_situation
        R.string.reminders -> R.drawable.ic_fp_reminder
        R.string.customize_day_entry -> R.drawable.ic_fp_note
        R.string.feedback -> R.drawable.ic_fp_export
        R.string.pill_rhythm -> R.drawable.ic_fp_cycle
        R.string.typical_cycle_length -> R.drawable.ic_fp_cycle
        R.string.language -> R.drawable.ic_fp_language
        R.string.backup_restore -> R.drawable.ic_fp_backup
        R.string.export_csv -> R.drawable.ic_fp_export
        R.string.privacy_policy -> R.drawable.ic_fp_privacy
        R.string.about -> R.drawable.ic_fp_about
        R.string.lock_timeout, R.string.daily_reminder_time -> R.drawable.ic_fp_clock
        R.string.reminder_days_before -> R.drawable.ic_fp_reminder
        else -> R.drawable.ic_fp_settings
    }
    FpSectionRow(icon, stringResource(label), detail.orEmpty(), enabled = enabled, onClick = onClick)
}

@Composable
internal fun SettingsSwitch(label: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FpSwitchRow(stringResource(label), checked, enabled, onChange)
}

private fun timeoutLabel(timeout: LockTimeout): Int = when (timeout) {
    LockTimeout.IMMEDIATELY -> R.string.lock_immediately
    LockTimeout.ONE_MINUTE -> R.string.lock_one_minute
    LockTimeout.FIVE_MINUTES -> R.string.lock_five_minutes
}

@Composable
private fun TypicalLengthDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    val length = value.toIntOrNull()
    val valid = length != null && length in 15..90
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.typical_cycle_length)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value, { value = it }, singleLine = true,
                    label = { Text(stringResource(R.string.cycle_length_range)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = value.isNotEmpty() && !valid)
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.cycle_unknown)) }
            }
        }, confirmButton = { TextButton(onClick = { onSave(length) }, enabled = valid) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun ConfirmationDialog(title: Int, body: Int, confirm: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
