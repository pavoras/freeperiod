package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.LocalDaylight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.FpSpacing
import org.freeperiod.engine.*
import java.time.LocalDate
import java.time.LocalTime

internal fun phaseLabel(value: LifePhase): Int = when (value) {
    LifePhase.REGULAR -> R.string.phase_regular
    LifePhase.TRYING_TO_CONCEIVE -> R.string.phase_ttc
    LifePhase.PREGNANT -> R.string.phase_pregnant
    LifePhase.POSTPARTUM -> R.string.phase_postpartum
    LifePhase.PERIMENOPAUSE -> R.string.phase_perimenopause
    LifePhase.MENOPAUSE -> R.string.phase_menopause
}
internal fun methodLabel(value: Method): Int = when (value) {
    Method.NONE -> R.string.method_none
    Method.PILL_COMBINED -> R.string.method_combined
    Method.PILL_PROGESTIN -> R.string.method_minipill
    Method.RING -> R.string.method_ring
    Method.PATCH -> R.string.method_patch
    Method.IUD_HORMONAL -> R.string.method_iud_hormonal
    Method.IUD_COPPER -> R.string.method_iud_copper
    Method.IMPLANT -> R.string.method_implant
    Method.INJECTION -> R.string.method_injection
    Method.CONDOM -> R.string.method_condom
    Method.OTHER -> R.string.method_other
}

internal fun phaseDescription(value: LifePhase): Int = when (value) {
    LifePhase.REGULAR -> R.string.phase_regular_detail
    LifePhase.TRYING_TO_CONCEIVE -> R.string.phase_ttc_detail
    LifePhase.PREGNANT, LifePhase.POSTPARTUM -> R.string.phase_paused_detail
    LifePhase.PERIMENOPAUSE -> R.string.phase_perimenopause_detail
    LifePhase.MENOPAUSE -> R.string.phase_menopause_detail
}

/** Presets are drafts only. The editor requires explicit saving and enabling. */
internal fun methodReminderPreset(method: Method, today: LocalDate): Reminder? {
    val recurrence = when (method) {
        Method.PILL_COMBINED, Method.PILL_PROGESTIN -> Recurrence.Daily
        Method.RING -> Recurrence.EveryNDays(1, today)
        Method.PATCH -> Recurrence.EveryNDays(1, today)
        Method.INJECTION -> Recurrence.EveryNDays(1, today)
        Method.IUD_HORMONAL, Method.IUD_COPPER, Method.IMPLANT -> Recurrence.Once(today)
        else -> return null
    }
    return Reminder(0, if (method in listOf(Method.PILL_COMBINED, Method.PILL_PROGESTIN)) ReminderKind.PILL else ReminderKind.METHOD,
        null, recurrence, LocalTime.of(20, 0), false)
}

@Composable
fun SituationScreen(situation: Situation, today: LocalDate, onSave: (Situation) -> Unit, onBack: () -> Unit,
    onOfferReminder: (Reminder) -> Unit) {
    var offer by remember { mutableStateOf<Method?>(null) }
    var pillDialog by rememberSaveable { mutableStateOf(false) }
    var methodSheet by rememberSaveable { mutableStateOf(false) }
    val t = LocalDaylight.current
    LazyColumn(Modifier.fillMaxSize().background(t.background), contentPadding = PaddingValues(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FpTopBar(stringResource(R.string.my_situation), onBack) }
        item { Text(stringResource(R.string.situation_intro), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        item {
            SettingsPanel {
                Text(stringResource(R.string.life_phase), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    LifePhase.entries.forEachIndexed { index, phase ->
                        if (index > 0) HorizontalDivider(color = t.line)
                        SettingsChoiceRow(stringResource(phaseLabel(phase)), situation.phase == phase,
                            { onSave(situation.copy(phase = phase)) }, stringResource(phaseDescription(phase)))
                    }
                }
            }
        }
        item {
            SettingsPanel {
                SettingsPickerRow(stringResource(R.string.tracking_method), stringResource(methodLabel(situation.method)), { methodSheet = true })
                if (situation.method == Method.PILL_COMBINED) {
                    HorizontalDivider(color = t.line)
                    SettingsPickerRow(stringResource(R.string.pill_rhythm), situation.pill?.let {
                        stringResource(R.string.pill_rhythm_summary, it.activeDays, it.breakDays, it.packStart.toString())
                    } ?: stringResource(R.string.pill_rhythm_missing), { pillDialog = true })
                }
                if (situation.method == Method.PILL_PROGESTIN) Text(stringResource(R.string.method_irregular),
                    style = MaterialTheme.typography.bodySmall, color = t.muted)
            }
        }
        if (situation.phase == LifePhase.PERIMENOPAUSE) item { Text(stringResource(R.string.phase_vary), color = t.muted) }
        item { Text(stringResource(R.string.situation_disclosure), style = MaterialTheme.typography.bodySmall, color = t.muted) }
    }
    if (methodSheet) SettingsChoiceSheet(stringResource(R.string.tracking_method), Method.entries, situation.method,
        { stringResource(methodLabel(it)) }, { methodSheet = false }) { method ->
        methodSheet = false
        onSave(situation.copy(method = method))
        if (method != situation.method && methodReminderPreset(method, today) != null) offer = method
    }
    offer?.let { method ->
        AlertDialog(containerColor = LocalDaylight.current.surface, onDismissRequest = { offer = null }, title = { Text(stringResource(R.string.method_reminder_offer)) },
            text = { Text(stringResource(R.string.method_reminder_offer_body)) },
            confirmButton = { TextButton(onClick = { offer = null; onOfferReminder(requireNotNull(methodReminderPreset(method, today))) }) { Text(stringResource(R.string.add_reminder)) } },
            dismissButton = { TextButton(onClick = { offer = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (pillDialog) PillRhythmDialog(situation.pill, today, { pillDialog = false }) {
        pillDialog = false; onSave(situation.copy(pill = it))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PillRhythmDialog(current: PillSchedule?, today: LocalDate, onDismiss: () -> Unit, onSave: (PillSchedule?) -> Unit) {
    var active by rememberSaveable { mutableStateOf((current?.activeDays ?: 21).toString()) }
    var rest by rememberSaveable { mutableStateOf((current?.breakDays ?: 7).toString()) }
    var start by rememberSaveable { mutableStateOf((current?.packStart ?: today).toString()) }
    val date = runCatching { LocalDate.parse(start) }.getOrNull()
    val valid = active.toIntOrNull() in 1..365 && rest.toIntOrNull() in 0..30 && date != null
    SettingsEditorDialog(stringResource(R.string.pill_rhythm), onDismiss,
        { onSave(PillSchedule(requireNotNull(date), active.toInt(), rest.toInt())) }, valid) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(21 to 7, 24 to 4, 28 to 0).forEach { (a, b) ->
                FpChip(active == a.toString() && rest == b.toString(), { active = a.toString(); rest = b.toString() },
                    if (b == 0) stringResource(R.string.pill_continuous) else "$a+$b", Modifier.widthIn(min = 64.dp))
            }
        }
        SettingsTextField(active, { active = it }, stringResource(R.string.pill_active_days), keyboardType = KeyboardType.Number)
        SettingsTextField(rest, { rest = it }, stringResource(R.string.pill_break_days), keyboardType = KeyboardType.Number)
        SettingsTextField(start, { start = it }, stringResource(R.string.pack_start_date), keyboardType = KeyboardType.Ascii)
        Text(stringResource(R.string.date_format_hint), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.pill_clear_rhythm)) }
    }
}
