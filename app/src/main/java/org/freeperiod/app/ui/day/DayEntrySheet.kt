package org.freeperiod.app.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
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
    val dateText = state.date.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd")).withLocale(locale))
    var sexOpen by rememberSaveable(state.date.toEpochDay()) { mutableStateOf(false) }
    var dischargeOpen by rememberSaveable(state.date.toEpochDay()) { mutableStateOf(false) }
    var tagsOpen by rememberSaveable(state.date.toEpochDay()) { mutableStateOf(false) }
    var noteOpen by rememberSaveable(state.date.toEpochDay()) { mutableStateOf(false) }
    var moreSymptoms by rememberSaveable(state.date.toEpochDay()) { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val tones = LocalDaylight.current
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tones.surface, dragHandle = {
            Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp, 3.dp).background(tones.muted, FpShapes.bar))
            }
        }) {
        Scaffold(containerColor = tones.surface, snackbarHost = { SnackbarHost(snackbar) }, modifier = Modifier.fillMaxHeight()) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text(stringResource(R.string.day_entry), style = MaterialTheme.typography.labelSmall, color = tones.muted)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dateText, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { onDateChange(state.date.minusDays(1)) }) {
                            Icon(painterResource(R.drawable.ic_fp_previous), stringResource(R.string.previous_day))
                        }
                        IconButton(onClick = { onDateChange(state.date.plusDays(1)) }) {
                            Icon(painterResource(R.drawable.ic_fp_next), stringResource(R.string.next_day))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_fp_close), stringResource(R.string.close))
                        }
                    }
                    if (state.readOnly) Text(stringResource(R.string.day_read_only), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.loading) item { CircularProgressIndicator() }
                state.error?.let { error ->
                    item { Text(stringResource(errorLabel(error)), color = MaterialTheme.colorScheme.error) }
                }
                item {
                    FpSwitchRow(stringResource(R.string.period_start_day), state.periodStart, editable, onChange = {
                        if (it) requestStart() else actions.removePeriodStart()
                    })
                    if (state.canEndPeriod || state.periodEnd) {
                        FpSwitchRow(stringResource(R.string.period_end_day), state.periodEnd, editable && state.canEndPeriod, actions.endPeriod)
                    }
                    HorizontalDivider(color = tones.line)
                }
                item {
                    Text(stringResource(R.string.entry_mood), style = MaterialTheme.typography.titleMedium)
                    ChoiceGrid(listOf(Mood.BAD, Mood.LOW, Mood.OKAY, Mood.GOOD, Mood.GREAT), if (largeText) 3 else 5) { mood ->
                        FpFaceChip(state.log.mood == mood,
                            { actions.mood(if (state.log.mood == mood) null else mood) }, stringResource(moodLabel(mood)),
                            Modifier.fillMaxWidth().fillMaxHeight(), editable) {
                            Icon(painterResource(moodIcon(mood)), null, Modifier.size(27.dp).testTag("mood-icon-${mood.name}"))
                        }
                    }
                }
                item { Choices(R.string.entry_flow, FlowLevel.entries, state.log.flow, editable, ::flowLabel, actions.flow) }
                item { Choices(R.string.entry_pain, Pain.entries, state.log.pain, editable, ::painLabel, actions.pain) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.entry_symptoms), style = MaterialTheme.typography.titleMedium)
                        if (state.log.symptoms.isNotEmpty()) Text(stringResource(R.string.entry_selected_count, state.log.symptoms.size),
                            style = MaterialTheme.typography.bodySmall, color = tones.muted)
                    }
                    val short = listOf(Symptom.CRAMPS, Symptom.BLOATING, Symptom.HEADACHE)
                    val visible = if (moreSymptoms) Symptom.entries else short
                    ChoiceGrid(visible + listOf(null), if (largeText) 2 else 4) { symptom ->
                        if (symptom == null) FpChip(moreSymptoms, { moreSymptoms = !moreSymptoms },
                            stringResource(if (moreSymptoms) R.string.entry_less else R.string.entry_more), Modifier.fillMaxWidth(), stacked = true,
                            icon = { Icon(painterResource(R.drawable.ic_fp_more), null, Modifier.size(20.dp)) })
                        else FpChip(symptom in state.log.symptoms, { actions.symptom(symptom) }, stringResource(symptomLabel(symptom)),
                            Modifier.fillMaxWidth().fillMaxHeight(), editable, stacked = true,
                            icon = { Icon(painterResource(symptomIcon(symptom)), null, Modifier.size(20.dp)) })
                    }
                }
                item {
                    FpSectionRow(R.drawable.ic_fp_sex, stringResource(R.string.entry_sex),
                        state.log.sex?.let { stringResource(sexLabel(it)) } ?: stringResource(R.string.entry_none), sexOpen) { sexOpen = !sexOpen }
                    if (sexOpen) Choices(null, Sex.entries, state.log.sex, editable, ::sexLabel, actions.sex)
                    FpSectionRow(R.drawable.ic_fp_discharge, stringResource(R.string.entry_discharge),
                        state.log.discharge?.let { stringResource(dischargeLabel(it)) } ?: stringResource(R.string.entry_none), dischargeOpen) { dischargeOpen = !dischargeOpen }
                    if (dischargeOpen) Choices(null, Discharge.entries, state.log.discharge, editable, ::dischargeLabel, actions.discharge)
                    FpSectionRow(R.drawable.ic_fp_tags, stringResource(R.string.entry_tags),
                        state.tags.filter { it.id in state.log.tagIds }.joinToString { it.name }.ifBlank { stringResource(R.string.entry_none) },
                        tagsOpen) { tagsOpen = !tagsOpen }
                    if (tagsOpen) TagEntry(state, actions, editable)
                    FpSectionRow(R.drawable.ic_fp_note, stringResource(R.string.entry_note),
                        notePreview(note).ifBlank { stringResource(R.string.entry_none) }, noteOpen) { noteOpen = !noteOpen }
                    if (noteOpen) TextField(value = note, onValueChange = {
                        // Existing long imported notes remain intact; the limit only constrains typing.
                        if (it.length <= 2000 || it.length < note.length) {
                            note = it; noteDirty = true; actions.note(it)
                        }
                    }, placeholder = { Text(stringResource(R.string.entry_note_placeholder)) }, enabled = editable,
                        modifier = Modifier.fillMaxWidth().testTag("entry-note"), minLines = 3,
                        shape = FpShapes.button, colors = TextFieldDefaults.colors(
                            focusedContainerColor = tones.accent.container, unfocusedContainerColor = tones.accent.container,
                            focusedTextColor = tones.accent.onContainer, unfocusedTextColor = tones.accent.onContainer,
                            focusedPlaceholderColor = tones.accent.onContainer, unfocusedPlaceholderColor = tones.accent.onContainer))
                    HorizontalDivider(color = tones.line)
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
private fun <T> ChoiceGrid(values: List<T>, columns: Int, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        values.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { value -> Box(Modifier.weight(1f).fillMaxHeight()) { content(value) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun <T> Choices(title: Int?, values: List<T>, selected: T?, enabled: Boolean, label: (T) -> Int, onChange: (T?) -> Unit) {
    title?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium) }
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 2 else minOf(values.size, 5)
    ChoiceGrid(values, columns) { value ->
        FpChip(selected == value, { onChange(if (selected == value) null else value) },
            stringResource(label(value)), Modifier.fillMaxWidth().fillMaxHeight(), enabled)
    }
}

internal fun notePreview(note: String): String = note.trim().split(Regex("\\s+")).take(7).joinToString(" ")

internal fun errorLabel(error: DayEntryError): Int = when (error) {
    DayEntryError.OVERLAP -> R.string.day_overlap
    DayEntryError.END_BEFORE_START -> R.string.error_period_end_before_start
    DayEntryError.START_IN_FUTURE -> R.string.error_period_start_future
    DayEntryError.END_IN_FUTURE -> R.string.error_period_end_future
    DayEntryError.TAG_NAME -> R.string.tag_name_error
    DayEntryError.STORAGE -> R.string.error_storage
}
