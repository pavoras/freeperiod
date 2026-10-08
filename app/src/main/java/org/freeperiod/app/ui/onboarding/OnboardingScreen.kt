package org.freeperiod.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.app.ui.settings.*
import org.freeperiod.engine.*

data class OnboardingActions(
    val next: () -> Unit = {}, val back: () -> Unit = {}, val skip: () -> Unit = {}, val finish: () -> Unit = {},
    val phase: (LifePhase) -> Unit = {}, val method: (Method) -> Unit = {},
    val pillRhythm: (String, String) -> Unit = { _, _ -> }, val packStart: (String) -> Unit = {},
    val day: (LocalDate) -> Unit = {}, val range: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    val rangeMode: (Boolean) -> Unit = {}, val ongoing: (Boolean) -> Unit = {}, val dismissOngoing: () -> Unit = {},
    val length: (String) -> Unit = {}, val unknown: (Boolean) -> Unit = {},
    val periodReminder: (Boolean) -> Unit = {}, val dailyReminder: (Boolean) -> Unit = {},
    val methodReminder: (Reminder?) -> Unit = {}, val systemSettings: () -> Unit = {},
    val importPeriods: () -> Unit = {},
)

@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions, notificationsAvailable: Boolean = true) {
    var methodEditor by rememberSaveable { mutableStateOf(false) }
    BackHandler(methodEditor) { methodEditor = false }
    val preset = state.methodReminder ?: methodReminderPreset(state.method, state.today)
    if (methodEditor && preset != null) {
        ReminderEditor(preset, state.today, { actions.methodReminder(it); methodEditor = false }, { methodEditor = false })
        return
    }
    val tones = LocalDaylight.current
    Column(Modifier.fillMaxSize().background(tones.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = FpSpacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = actions.skip, enabled = !state.busy) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Text(stringResource(R.string.onboarding_step_of, state.page + 1, 6), Modifier.padding(horizontal = FpSpacing.screen), style = MaterialTheme.typography.bodySmall, color = tones.muted)
        Box(Modifier.weight(1f)) {
            if (state.page == 3) PastPeriodsPicker(state, actions)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(FpSpacing.section)) {
                when (state.page) {
                    0 -> {
                        Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineLarge)
                        FpPanel { Text(stringResource(R.string.onboarding_promise), Modifier.padding(FpSpacing.screen), style = MaterialTheme.typography.titleLarge) }
                        Text(stringResource(R.string.onboarding_local))
                        Text(stringResource(R.string.onboarding_optional))
                    }
                    1 -> {
                        Text(stringResource(R.string.life_phase), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.situation_intro))
                        LifePhase.entries.forEach { phase -> FpChip(state.phase == phase, { actions.phase(phase) },
                            stringResource(phaseLabel(phase)), Modifier.fillMaxWidth(), enabled = !state.busy) }
                    }
                    2 -> {
                        Text(stringResource(R.string.tracking_method), style = MaterialTheme.typography.titleLarge)
                        Method.entries.forEach { method -> FpChip(state.method == method, { actions.method(method) },
                            stringResource(methodLabel(method)), Modifier.fillMaxWidth(), enabled = !state.busy) }
                        if (state.method == Method.PILL_COMBINED) {
                            Text(stringResource(R.string.pill_rhythm), style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
                                listOf(21 to 7, 24 to 4, 28 to 0).forEach { (a, b) -> FpChip(state.pillActive == a.toString() && state.pillBreak == b.toString(),
                                    { actions.pillRhythm(a.toString(), b.toString()); if (state.packStart.isBlank()) actions.packStart(state.today.toString()) },
                                    if (b == 0) stringResource(R.string.pill_continuous) else stringResource(R.string.pill_pattern, a, b), Modifier.weight(1f)) }
                            }
                            OutlinedTextField(state.pillActive, { actions.pillRhythm(it, state.pillBreak) }, label = { Text(stringResource(R.string.pill_active_days)) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(state.pillBreak, { actions.pillRhythm(state.pillActive, it) }, label = { Text(stringResource(R.string.pill_break_days)) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(state.packStart, actions.packStart, label = { Text(stringResource(R.string.pack_start_date)) }, singleLine = true)
                            Text(stringResource(R.string.date_format_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    4 -> {
                        Text(stringResource(R.string.typical_cycle_length), style = MaterialTheme.typography.titleLarge)
                        FpSwitchRow(stringResource(R.string.cycle_unknown), state.unknown, !state.busy, actions.unknown)
                        OutlinedTextField(state.typicalLength, actions.length, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.cycle_length_range)) }, singleLine = true,
                            enabled = !state.busy && !state.unknown, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !state.unknown && state.typicalLength.isNotBlank() && state.typicalLength.toIntOrNull() !in 15..90)
                    }
                    5 -> {
                        Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.onboarding_reminders_optional))
                        FpSwitchRow(stringResource(R.string.period_reminder), state.periodReminder, !state.busy, actions.periodReminder)
                        FpSwitchRow(stringResource(R.string.daily_reminder), state.dailyReminder, !state.busy, actions.dailyReminder)
                        if (preset != null) {
                            if (state.methodReminder == null) FpButton({ methodEditor = true }, primary = false) { Text(stringResource(R.string.method_reminder_offer)) }
                            else FpSwitchRow(stringResource(R.string.method_reminder), state.methodReminder.enabled, !state.busy,
                                { actions.methodReminder(state.methodReminder.copy(enabled = it)) })
                        }
                        Text(stringResource(R.string.reminder_disclosure), style = MaterialTheme.typography.bodySmall)
                        if (!notificationsAvailable && (state.periodReminder || state.dailyReminder || state.methodReminder?.enabled == true)) {
                            Text(stringResource(R.string.notifications_off))
                            TextButton(onClick = actions.systemSettings) { Text(stringResource(R.string.notification_settings)) }
                        }
                        if (state.skippedPeriods > 0) Text(pluralStringResource(R.plurals.onboarding_skipped_periods, state.skippedPeriods, state.skippedPeriods))
                    }
                }
            }
        }
        state.message?.let { Text(stringResource(it), Modifier.padding(horizontal = FpSpacing.screen), color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(FpSpacing.screen), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.page > 0) TextButton(onClick = actions.back, enabled = !state.busy) { Text(stringResource(R.string.back)) }
            else Spacer(Modifier.width(FpSpacing.touch))
            val canContinue = !state.busy && when (state.page) {
                2 -> state.validPill
                4 -> state.unknown || state.typicalLength.toIntOrNull() in 15..90
                else -> true
            }
            FpButton(if (state.page == 5) actions.finish else actions.next, enabled = canContinue) {
                Text(stringResource(if (state.page == 5) R.string.onboarding_finish else R.string.onboarding_continue))
            }
        }
    }
    if (state.askOngoing) AlertDialog(onDismissRequest = actions.dismissOngoing, title = { Text(stringResource(R.string.onboarding_still_ongoing)) },
        text = { Text(stringResource(R.string.onboarding_still_ongoing_body)) },
        confirmButton = { TextButton(onClick = { actions.ongoing(true) }) { Text(stringResource(R.string.onboarding_ongoing_yes)) } },
        dismissButton = { TextButton(onClick = { actions.ongoing(false) }) { Text(stringResource(R.string.onboarding_ongoing_no)) } })
}
