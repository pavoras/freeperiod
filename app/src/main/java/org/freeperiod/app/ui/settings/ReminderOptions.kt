package org.freeperiod.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import org.freeperiod.app.R
import org.freeperiod.app.data.AppSettings
import org.freeperiod.app.reminders.Notifications

data class ReminderAccess(val available: Boolean, val requestPermission: () -> Unit, val settingsIntent: Intent)

@Composable
fun rememberReminderAccess(notifications: Notifications): ReminderAccess {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var available by remember { mutableStateOf(notifications.available()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { available = notifications.available() }
    DisposableEffect(lifecycle, notifications) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) available = notifications.available() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return ReminderAccess(available, {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

@Composable
fun ReminderOptions(settings: AppSettings, enabled: Boolean, notificationsAvailable: Boolean,
    onPeriod: (Boolean) -> Unit, onDaysBefore: (Int) -> Unit, onDaily: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit, onExplicit: (Boolean) -> Unit, onSystemSettings: () -> Unit,
    showDetails: Boolean = true) {
    var daysDialog by rememberSaveable { mutableStateOf(false) }
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    SettingsSwitch(R.string.period_reminder, settings.periodReminder, enabled, onPeriod)
    if (showDetails && settings.periodReminder) SettingsRow(R.string.reminder_days_before, enabled, { daysDialog = true },
        pluralStringResource(R.plurals.reminder_days_value, settings.periodReminderDaysBefore, settings.periodReminderDaysBefore))
    SettingsSwitch(R.string.daily_reminder, settings.dailyReminder, enabled, onDaily)
    if (showDetails && settings.dailyReminder) {
        val locale = LocalConfiguration.current.locales[0]
        SettingsRow(R.string.daily_reminder_time, enabled, { timeDialog = true },
            settings.dailyReminderTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)))
    }
    if (showDetails) SettingsSwitch(R.string.explicit_notifications, settings.explicitNotifications, enabled, onExplicit)
    if ((settings.periodReminder || settings.dailyReminder) && !notificationsAvailable) {
        Text(stringResource(R.string.notifications_off), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onSystemSettings) { Text(stringResource(R.string.notification_settings)) }
    }
    if (daysDialog) {
        var days by rememberSaveable { mutableStateOf(settings.periodReminderDaysBefore.toFloat()) }
        AlertDialog(onDismissRequest = { daysDialog = false }, title = { Text(stringResource(R.string.reminder_days_before)) },
            text = {
                Column {
                    Text(pluralStringResource(R.plurals.reminder_days_value, days.roundToInt(), days.roundToInt()))
                    Slider(days, { days = it }, valueRange = 1f..5f, steps = 3)
                }
            }, confirmButton = { TextButton(onClick = { onDaysBefore(days.roundToInt()); daysDialog = false }) { Text(stringResource(R.string.settings_save)) } },
            dismissButton = { TextButton(onClick = { daysDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (timeDialog) ReminderTimeDialog(settings.dailyReminderTime, { timeDialog = false }) { onTime(it); timeDialog = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(current: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(current.hour, current.minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.daily_reminder_time)) },
        text = { TimePicker(state) }, confirmButton = {
            TextButton(onClick = { onSave(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.settings_save)) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
