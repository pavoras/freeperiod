package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.LocalDaylight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.*
import java.time.format.TextStyle
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.FpSpacing
import org.freeperiod.engine.*

internal fun reminderLabel(kind: ReminderKind): Int = when (kind) {
    ReminderKind.PERIOD_DUE -> R.string.period_reminder
    ReminderKind.DAILY_LOG -> R.string.daily_reminder
    ReminderKind.PILL -> R.string.pill_reminder
    ReminderKind.METHOD -> R.string.method_reminder
    ReminderKind.CUSTOM -> R.string.custom_reminder
}
private enum class ReminderPicker { KIND, RHYTHM, WEEKDAY }
private enum class Rhythm { DAILY, DAYS, WEEKLY, MONTHLY, MONTHS, ONCE }
private fun Rhythm.label(): Int = when (this) {
    Rhythm.DAILY -> R.string.recurrence_daily
    Rhythm.DAYS -> R.string.recurrence_days
    Rhythm.WEEKLY -> R.string.recurrence_weekly
    Rhythm.MONTHLY -> R.string.recurrence_monthly
    Rhythm.MONTHS -> R.string.recurrence_months
    Rhythm.ONCE -> R.string.recurrence_once
}
private fun Recurrence.rhythm(): Rhythm = when (this) {
    Recurrence.Daily -> Rhythm.DAILY
    is Recurrence.EveryNDays -> Rhythm.DAYS
    is Recurrence.Weekly -> Rhythm.WEEKLY
    is Recurrence.MonthlyOnDay -> Rhythm.MONTHLY
    is Recurrence.EveryNMonths -> Rhythm.MONTHS
    is Recurrence.Once -> Rhythm.ONCE
}

@Composable
fun RemindersScreen(reminders: List<Reminder>, onSave: (Reminder) -> Unit, onEdit: (Reminder) -> Unit,
    onAdd: () -> Unit, onDelete: (Long) -> Unit, onBack: () -> Unit,
    explicit: Boolean = false, onExplicit: (Boolean) -> Unit = {}, notificationsAvailable: Boolean = true,
    onSystemSettings: () -> Unit = {}) {
    var deleting by remember { mutableStateOf<Reminder?>(null) }
    val t = LocalDaylight.current
    LazyColumn(Modifier.fillMaxSize().background(t.background), contentPadding = PaddingValues(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FpTopBar(stringResource(R.string.reminders), onBack) }
        item { Text(stringResource(R.string.reminder_disclosure), style = MaterialTheme.typography.bodySmall, color = t.muted) }
        if (reminders.isEmpty()) item { SettingsPanel { Text(stringResource(R.string.reminders_empty), color = t.muted) } }
        items(reminders.size, key = { reminders[it].id }) { index ->
            val reminder = reminders[index]
            SettingsPanel(Modifier.testTag("reminder_${reminder.id}")) {
                FpSwitchRow(reminder.title ?: stringResource(reminderLabel(reminder.kind)), reminder.enabled,
                    onChange = { onSave(reminder.copy(enabled = it)) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(reminderSummary(reminder, LocalContext.current.resources), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = t.muted)
                    IconButton(onClick = { onEdit(reminder) }) {
                        Icon(painterResource(R.drawable.ic_fp_note), stringResource(R.string.edit_reminder), Modifier.size(20.dp))
                    }
                    IconButton(onClick = { deleting = reminder }) {
                        Icon(painterResource(R.drawable.ic_fp_delete), stringResource(R.string.delete_reminder), Modifier.size(20.dp), tint = t.muted)
                    }
                }
            }
        }
        item { FpButton(onAdd, Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_reminder)) } }
        item {
            Spacer(Modifier.height(8.dp))
            SettingsPanel {
                Text(stringResource(R.string.notification_content), style = MaterialTheme.typography.titleMedium)
                FpSwitchRow(stringResource(R.string.explicit_notifications), explicit, onChange = onExplicit)
            }
        }
        if (!notificationsAvailable && reminders.any { it.enabled }) item {
            SettingsPanel {
                Text(stringResource(R.string.notifications_off), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSystemSettings) { Text(stringResource(R.string.notification_settings)) }
            }
        }
    }
    deleting?.let { reminder -> ConfirmationDialog(R.string.delete_reminder, R.string.delete_reminder_body,
        R.string.delete_reminder, { deleting = null }, { deleting = null; onDelete(reminder.id) }) }
}

@Composable
fun ReminderEditor(reminder: Reminder, today: LocalDate, onSave: (Reminder) -> Unit, onBack: () -> Unit) {
    var kind by rememberSaveable(reminder.id) { mutableStateOf(reminder.kind) }
    var title by rememberSaveable(reminder.id) { mutableStateOf(reminder.title.orEmpty()) }
    var rhythm by rememberSaveable(reminder.id) { mutableStateOf(reminder.recurrence.rhythm()) }
    var n by rememberSaveable(reminder.id) { mutableStateOf(if (reminder.id == 0L && reminder.kind == ReminderKind.METHOD) "" else when (val r = reminder.recurrence) { is Recurrence.EveryNDays -> r.n; is Recurrence.EveryNMonths -> r.n; else -> 1 }.toString()) }
    var anchor by rememberSaveable(reminder.id) { mutableStateOf(when (val r = reminder.recurrence) { is Recurrence.EveryNDays -> r.anchor; is Recurrence.EveryNMonths -> r.anchor; else -> today }.toString()) }
    var dateText by rememberSaveable(reminder.id) { mutableStateOf((reminder.recurrence as? Recurrence.Once)?.date?.takeIf { reminder.id != 0L }?.toString().orEmpty()) }
    var day by rememberSaveable(reminder.id) { mutableStateOf(((reminder.recurrence as? Recurrence.MonthlyOnDay)?.day ?: today.dayOfMonth).toString()) }
    var weekday by rememberSaveable(reminder.id) { mutableStateOf((reminder.recurrence as? Recurrence.Weekly)?.day ?: today.dayOfWeek) }
    var time by rememberSaveable(reminder.id) { mutableStateOf(reminder.time.toString()) }
    var before by rememberSaveable(reminder.id) { mutableStateOf((reminder.daysBefore ?: 2).toString()) }
    var enabled by rememberSaveable(reminder.id) { mutableStateOf(reminder.enabled) }
    val parsedTime = runCatching { LocalTime.parse(time) }.getOrNull()
    val recurrence = if (kind == ReminderKind.PERIOD_DUE) Recurrence.Daily else runCatching {
        when (rhythm) {
            Rhythm.DAILY -> Recurrence.Daily
            Rhythm.DAYS -> Recurrence.EveryNDays(n.toInt(), LocalDate.parse(anchor))
            Rhythm.WEEKLY -> Recurrence.Weekly(weekday)
            Rhythm.MONTHLY -> Recurrence.MonthlyOnDay(day.toInt())
            Rhythm.MONTHS -> Recurrence.EveryNMonths(n.toInt(), LocalDate.parse(anchor))
            Rhythm.ONCE -> Recurrence.Once(LocalDate.parse(dateText))
        }
    }.getOrNull()
    val valid = recurrence != null && parsedTime != null && (kind != ReminderKind.CUSTOM || title.isNotBlank()) &&
        (kind != ReminderKind.PERIOD_DUE || before.toIntOrNull() in 0..365)
    val locale = LocalConfiguration.current.locales[0]
    var picker by rememberSaveable { mutableStateOf<ReminderPicker?>(null) }
    val t = LocalDaylight.current
    LazyColumn(Modifier.fillMaxSize().background(t.background), contentPadding = PaddingValues(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FpTopBar(stringResource(if (reminder.id == 0L) R.string.add_reminder else R.string.edit_reminder), onBack) }
        item {
            SettingsPanel {
                SettingsPickerRow(stringResource(R.string.reminder_kind), stringResource(reminderLabel(kind)), { picker = ReminderPicker.KIND })
                if (kind == ReminderKind.CUSTOM) SettingsTextField(title, { title = it }, stringResource(R.string.reminder_custom_title))
            }
        }
        item {
            SettingsPanel {
                Text(stringResource(R.string.reminder_recurrence), style = MaterialTheme.typography.titleMedium)
                // Period due uses the prediction date; daily recurrence is retained in its stored row.
                if (kind == ReminderKind.PERIOD_DUE) {
                    SettingsTextField(before, { before = it }, stringResource(R.string.reminder_days_before), KeyboardType.Number)
                } else {
                    SettingsPickerRow(stringResource(R.string.reminder_repeat), stringResource(rhythm.label()), { picker = ReminderPicker.RHYTHM })
                    if (rhythm == Rhythm.DAYS || rhythm == Rhythm.MONTHS) {
                        SettingsTextField(n, { n = it }, stringResource(R.string.reminder_interval), KeyboardType.Number)
                        SettingsTextField(anchor, { anchor = it }, stringResource(R.string.reminder_anchor), KeyboardType.Ascii)
                        Text(stringResource(R.string.date_format_hint), style = MaterialTheme.typography.bodySmall, color = t.muted)
                    }
                    if (rhythm == Rhythm.MONTHLY) SettingsTextField(day, { day = it }, stringResource(R.string.reminder_month_day), KeyboardType.Number)
                    if (rhythm == Rhythm.ONCE) {
                        SettingsTextField(dateText, { dateText = it }, stringResource(R.string.reminder_once_date), KeyboardType.Ascii)
                        Text(stringResource(R.string.date_format_hint), style = MaterialTheme.typography.bodySmall, color = t.muted)
                    }
                    if (rhythm == Rhythm.WEEKLY) SettingsPickerRow(stringResource(R.string.reminder_weekday),
                        weekday.getDisplayName(TextStyle.FULL, locale), { picker = ReminderPicker.WEEKDAY })
                }
                SettingsTextField(time, { time = it }, stringResource(R.string.reminder_time_format), KeyboardType.Ascii)
            }
        }
        item { SettingsPanel { FpSwitchRow(stringResource(R.string.reminder_enabled), enabled, onChange = { enabled = it }) } }
        item { Text(stringResource(R.string.reminder_disclosure), style = MaterialTheme.typography.bodySmall, color = t.muted) }
        item { FpButton({ onSave(reminder.copy(kind = kind, title = title.trim().takeIf { kind == ReminderKind.CUSTOM },
            recurrence = if (kind == ReminderKind.PERIOD_DUE) Recurrence.Daily else requireNotNull(recurrence),
            time = requireNotNull(parsedTime), enabled = enabled, daysBefore = before.toIntOrNull().takeIf { kind == ReminderKind.PERIOD_DUE })) },
            Modifier.fillMaxWidth(), enabled = valid) { Text(stringResource(R.string.settings_save)) } }
    }
    when (picker) {
        ReminderPicker.KIND -> SettingsChoiceSheet(stringResource(R.string.reminder_kind), ReminderKind.entries, kind,
            { stringResource(reminderLabel(it)) }, { picker = null }) { kind = it; picker = null }
        ReminderPicker.RHYTHM -> SettingsChoiceSheet(stringResource(R.string.reminder_recurrence), Rhythm.entries, rhythm,
            { stringResource(it.label()) }, { picker = null }) { rhythm = it; picker = null }
        ReminderPicker.WEEKDAY -> SettingsChoiceSheet(stringResource(R.string.reminder_weekday), DayOfWeek.entries, weekday,
            { it.getDisplayName(TextStyle.FULL, locale) }, { picker = null }) { weekday = it; picker = null }
        null -> Unit
    }
}
