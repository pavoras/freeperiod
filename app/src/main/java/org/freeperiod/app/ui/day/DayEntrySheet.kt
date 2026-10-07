package org.freeperiod.app.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.freeperiod.app.R
import org.freeperiod.app.ui.nav.NavigationIcons
import org.freeperiod.engine.*

data class DayEntryActions(
    val flow: (FlowLevel?) -> Unit = {},
    val mood: (Mood?) -> Unit = {},
    val pain: (Pain?) -> Unit = {},
    val sex: (Sex?) -> Unit = {},
    val discharge: (Discharge?) -> Unit = {},
    val note: (String) -> Unit = {},
    val symptom: (Symptom) -> Unit = {},
    val tag: (Long) -> Unit = {},
    val addTag: (String) -> Unit = {},
    val renameTag: (Long, String) -> Unit = { _, _ -> },
    val archiveTag: (Long) -> Unit = {},
    val startPeriod: (LocalDate?) -> Unit = {},
    val removePeriodStart: () -> Unit = {},
    val endPeriod: (Boolean) -> Unit = {},
    val clear: () -> Unit = {},
    val undo: (DayLog) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DayEntrySheet(
    state: DayEntryUiState,
    actions: DayEntryActions,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    events: Flow<DayEntryEvent> = emptyFlow(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val editable = !state.readOnly && !state.loading
    var askEndDate by remember(state.date) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val clearedMessage = stringResource(R.string.day_cleared)
    val undoLabel = stringResource(R.string.undo)
    val suggestionMessage = stringResource(R.string.mark_period_start)
    val startLabel = stringResource(R.string.mark_start)
    var note by remember(state.date) { mutableStateOf(state.log.note.orEmpty()) }
    var noteDirty by remember(state.date) { mutableStateOf(false) }
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.log.note) {
        if (!noteDirty) note = state.log.note.orEmpty()
        else if (note == state.log.note.orEmpty()) noteDirty = false
    }
    fun requestStart() {
        if (!latestState.readOnly && !latestState.loading) {
            if (latestState.date < latestState.today) askEndDate = true else actions.startPeriod(null)
        }
    }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is DayEntryEvent.Cleared -> {
                    if (snackbar.showSnackbar(clearedMessage, undoLabel, withDismissAction = true) == SnackbarResult.ActionPerformed) {
                        note = event.previous.note.orEmpty()
                        noteDirty = true
                        actions.undo(event.previous)
                    }
                }
                DayEntryEvent.SuggestPeriodStart -> {
                    if (snackbar.showSnackbar(suggestionMessage, startLabel, withDismissAction = true) == SnackbarResult.ActionPerformed) requestStart()
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, modifier = Modifier.fillMaxHeight()) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.day_entry), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onDateChange(state.date.minusDays(1)) }) {
                            Icon(NavigationIcons.Previous, stringResource(R.string.previous_day))
                        }
                        Text(dateText, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { onDateChange(state.date.plusDays(1)) }) {
                            Icon(NavigationIcons.Next, stringResource(R.string.next_day))
                        }
                    }
                    if (state.readOnly) Text(stringResource(R.string.day_read_only), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.loading) item { CircularProgressIndicator() }
                state.error?.let { error ->
                    item { Text(stringResource(errorLabel(error)), color = MaterialTheme.colorScheme.error) }
                }
                item {
                    PeriodToggle(stringResource(R.string.period_start_day), state.periodStart, editable) {
                        if (it) requestStart() else actions.removePeriodStart()
                    }
                    PeriodToggle(stringResource(R.string.period_end_day), state.periodEnd, editable && state.canEndPeriod, actions.endPeriod)
                }
                item { Choices(R.string.entry_flow, FlowLevel.entries, state.log.flow, editable, ::flowLabel, actions.flow) }
                item {
                    Text(stringResource(R.string.entry_mood), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mood.entries.forEach { mood ->
                            FilterChip(selected = state.log.mood == mood,
                                onClick = { actions.mood(if (state.log.mood == mood) null else mood) }, enabled = editable,
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(stringResource(moodLabel(mood))) },
                                leadingIcon = { Icon(painterResource(moodIcon(mood)), null,
                                    Modifier.size(24.dp).testTag("mood-icon-${mood.name}"), tint = MaterialTheme.colorScheme.primary) })
                        }
                    }
                }
                item { Choices(R.string.entry_pain, Pain.entries, state.log.pain, editable, ::painLabel, actions.pain) }
                item { Choices(R.string.entry_sex, Sex.entries, state.log.sex, editable, ::sexLabel, actions.sex) }
                item { Choices(R.string.entry_discharge, Discharge.entries, state.log.discharge, editable, ::dischargeLabel, actions.discharge) }
                item {
                    Text(stringResource(R.string.entry_symptoms), style = MaterialTheme.typography.titleMedium)
                    Symptom.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { symptom ->
                                FilterChip(selected = symptom in state.log.symptoms, onClick = { actions.symptom(symptom) },
                                    enabled = editable, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    label = { Text(stringResource(symptomLabel(symptom))) },
                                    leadingIcon = { Icon(painterResource(symptomIcon(symptom)), null, Modifier.size(24.dp)) })
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { TagEntry(state, actions, editable) }
                item {
                    OutlinedTextField(value = note, onValueChange = { note = it; noteDirty = true; actions.note(it) },
                        label = { Text(stringResource(R.string.entry_note)) }, enabled = editable,
                        modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                item {
                    TextButton(onClick = { note = ""; noteDirty = true; actions.clear() },
                        enabled = editable && !state.log.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.clear_day))
                    }
                }
            }
        }
    }
    if (askEndDate) PeriodEndPicker(state.date, state.today, { askEndDate = false }) { end ->
        askEndDate = false
        actions.startPeriod(end)
    }
}

@Composable
private fun PeriodToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(title: Int, values: List<T>, selected: T?, enabled: Boolean, label: (T) -> Int, onChange: (T?) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected == value, { onChange(if (selected == value) null else value) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(label(value))) })
        }
    }
}

internal fun errorLabel(error: DayEntryError): Int = when (error) {
    DayEntryError.OVERLAP -> R.string.day_overlap
    DayEntryError.END_BEFORE_START -> R.string.error_period_end_before_start
    DayEntryError.START_IN_FUTURE -> R.string.error_period_start_future
    DayEntryError.END_IN_FUTURE -> R.string.error_period_end_future
    DayEntryError.TAG_NAME -> R.string.tag_name_error
    DayEntryError.STORAGE -> R.string.error_storage
}
