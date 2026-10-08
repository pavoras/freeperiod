package org.freeperiod.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.app.data.AppSettings
import org.freeperiod.app.ui.settings.ReminderOptions

data class OnboardingActions(
    val next: () -> Unit = {}, val back: () -> Unit = {}, val skip: () -> Unit = {}, val finish: () -> Unit = {},
    val start: (LocalDate?) -> Unit = {}, val ended: (Boolean) -> Unit = {}, val end: (LocalDate) -> Unit = {},
    val length: (String) -> Unit = {}, val unknown: (Boolean) -> Unit = {},
    val periodReminder: (Boolean) -> Unit = {}, val dailyReminder: (Boolean) -> Unit = {}, val systemSettings: () -> Unit = {},
)

@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions, notificationsAvailable: Boolean = true) {
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    Column(Modifier.fillMaxSize().background(LocalDaylight.current.background).imePadding().padding(FpSpacing.screen)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = actions.skip, enabled = !state.busy) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.onboarding_step, state.page + 1), style = MaterialTheme.typography.bodySmall)
            when (state.page) {
                0 -> {
                    Spacer(Modifier.height(40.dp))
                    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineLarge)
                    FpPanel { Text(stringResource(R.string.onboarding_promise), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
                    Text(stringResource(R.string.onboarding_local))
                    Text(stringResource(R.string.onboarding_optional))
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_your_cycle), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.onboarding_optional))
                    FpButton(primary = false, onClick = { pickStart = true }, enabled = !state.busy) {
                        Text(state.start?.let { stringResource(R.string.onboarding_start_value, it.format(dates)) }
                            ?: stringResource(R.string.onboarding_choose_start))
                    }
                    if (state.start != null) {
                        TextButton(onClick = { actions.start(null) }, enabled = !state.busy) { Text(stringResource(R.string.onboarding_clear_date)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.hasEnded, actions.ended, enabled = !state.busy)
                            Text(stringResource(R.string.onboarding_has_ended))
                        }
                        if (state.hasEnded) FpButton(primary = false, onClick = { pickEnd = true }, enabled = !state.busy) {
                            Text(state.end?.let { stringResource(R.string.onboarding_end_value, it.format(dates)) }
                                ?: stringResource(R.string.onboarding_choose_end))
                        }
                    }
                    Text(stringResource(R.string.typical_cycle_length), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.unknown, actions.unknown, enabled = !state.busy)
                        Text(stringResource(R.string.cycle_unknown))
                    }
                    OutlinedTextField(state.typicalLength, actions.length, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.cycle_length_range)) }, singleLine = true,
                        enabled = !state.busy && !state.unknown, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !state.unknown && state.typicalLength.isNotEmpty() && state.typicalLength.toIntOrNull()?.let { it in 15..90 } != true)
                }
                2 -> {
                    Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.onboarding_reminders_optional))
                    ReminderOptions(AppSettings(periodReminder = state.periodReminder, dailyReminder = state.dailyReminder),
                        !state.busy, notificationsAvailable, actions.periodReminder, {}, actions.dailyReminder, {}, {}, actions.systemSettings, showDetails = false)
                }
            }
            state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.page > 0) TextButton(onClick = actions.back, enabled = !state.busy) { Text(stringResource(R.string.back)) }
            else Spacer(Modifier.width(1.dp))
            FpButton(onClick = if (state.page == 2) actions.finish else actions.next,
                enabled = !state.busy && (state.page == 0 || state.valid)) {
                Text(stringResource(if (state.page == 2) R.string.onboarding_finish else R.string.onboarding_continue))
            }
        }
    }
    if (pickStart) OnboardingDatePicker(state.start, null, state.today, { pickStart = false }) { actions.start(it); pickStart = false }
    if (pickEnd && state.start != null) OnboardingDatePicker(state.end, state.start, state.today, { pickEnd = false }) { actions.end(it); pickEnd = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingDatePicker(current: LocalDate?, minimum: LocalDate?, today: LocalDate, onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = (current ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return date <= today && (minimum == null || date >= minimum)
            }
            override fun isSelectableYear(year: Int): Boolean = year <= today.year && (minimum == null || year >= minimum.year)
        })
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { picker.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
            enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.confirm)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
}
