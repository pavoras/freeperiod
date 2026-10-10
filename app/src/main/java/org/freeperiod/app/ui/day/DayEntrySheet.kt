package org.freeperiod.app.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
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
    val ovulationTest: (OvulationTest?) -> Unit = {},
    /** How many times an item was logged (0 removes it). */
    val count: (Long, Int) -> Unit = { _, _ -> },
    /** New own item for a built-in field or an own category, logged once on this day: field, category id, name, icon, category name. */
    val addItem: (String?, Long?, String, String, String) -> Unit = { _, _, _, _, _ -> },
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
    var focusNote by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val noteFocus = remember { FocusRequester() }
    var customOpen by rememberSaveable(state.date.toEpochDay()) { mutableStateOf("") }
    var moreSymptoms by rememberSaveable(state.date.toEpochDay()) { mutableStateOf(false) }
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
        // No swipe-to-close: it was easy to trigger by accident while scrolling. The X button closes.
        containerColor = tones.surface, sheetGesturesEnabled = false, dragHandle = null) {
        Scaffold(containerColor = tones.surface, snackbarHost = { SnackbarHost(snackbar) }, modifier = Modifier.fillMaxHeight()) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), state = list,
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
                    Spacer(Modifier.height(8.dp))
                    PeriodBlock(started = state.periodStart, ended = state.periodEnd,
                        showEnd = state.canEndPeriod || state.periodEnd, startEnabled = editable,
                        endEnabled = editable && state.canEndPeriod,
                        onStart = { if (it) requestStart() else actions.removePeriodStart() }, onEnd = actions.endPeriod)
                    Spacer(Modifier.height(8.dp))
                }
                entryCategories(state).forEach { category ->
                    item(key = category.key) {
                        when (category.key) {
                            "mood", "flow", "pain" -> EntryChoices(category, state, actions, editable)
                            "symptoms" -> {
                                EntryChoices(category, state, actions, editable, moreSymptoms = moreSymptoms,
                                    onMore = { moreSymptoms = !moreSymptoms })
                            }
                            "sex", "discharge" -> {
                                val appearance = category.appearance(state)
                                val summary = state.entryItems(category).filter { state.selected(it) }.map { it.appearance(state).label }
                                    .joinToString().ifBlank { stringResource(R.string.entry_none) }
                                val open = if (category.key == "sex") sexOpen else dischargeOpen
                                FpSectionRow(appearance.icon, appearance.label, summary, open) {
                                    if (category.key == "sex") sexOpen = !sexOpen else dischargeOpen = !dischargeOpen
                                }
                                if (open) EntryChoices(category, state, actions, editable, showTitle = false)
                            }
                            "note" -> {
                                val appearance = category.appearance(state)
                                FpSectionRow(appearance.icon, appearance.label, notePreview(note).ifBlank { stringResource(R.string.entry_none) }, noteOpen) { noteOpen = !noteOpen; focusNote = noteOpen }
                                if (noteOpen) TextField(value = note, onValueChange = {
                                    if (it.length <= 2000 || it.length < note.length) { note = it; noteDirty = true; actions.note(it) }
                                }, placeholder = { Text(stringResource(R.string.entry_note_placeholder)) }, enabled = editable,
                                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus).testTag("entry-note"), minLines = 3, shape = FpShapes.button,
                                    colors = TextFieldDefaults.colors(focusedContainerColor = tones.accent.container, unfocusedContainerColor = tones.accent.container,
                                        focusedTextColor = tones.accent.onContainer, unfocusedTextColor = tones.accent.onContainer,
                                        focusedPlaceholderColor = tones.accent.onContainer, unfocusedPlaceholderColor = tones.accent.onContainer))
                                // Opening the note: type right away. Focus scrolls only as far as needed, so the field
                                // ends up just above the keyboard wherever the note sits in the list (no jump to the top).
                                if (noteOpen) LaunchedEffect(focusNote) {
                                    if (!focusNote) return@LaunchedEffect
                                    noteFocus.requestFocus()
                                    focusNote = false
                                }
                            }
                            "ovulation_test" -> EntryChoices(category, state, actions, editable)
                            else -> if (category.counted()) {
                                val open = category.key in customOpen.split(',')
                                CountedEntry(category, state, actions, editable, open) {
                                    val keys = customOpen.split(',').filter { it.isNotBlank() }.toSet()
                                    customOpen = (if (open) keys - category.key else keys + category.key).joinToString(",")
                                }
                            } else {
                                val items = state.entryItems(category)
                                val tags = items.mapNotNull { it.tag }
                                val open = if (category.key == "tags") tagsOpen else category.key in customOpen.split(',')
                                val appearance = category.appearance(state)
                                FpSectionRow(appearance.icon, appearance.label,
                                    items.filter { state.selected(it) }.map { it.appearance(state).label }.joinToString()
                                        .ifBlank { stringResource(R.string.entry_none) }, open) {
                                    if (category.key == "tags") tagsOpen = !tagsOpen
                                    else { val keys = customOpen.split(',').filter { it.isNotBlank() }.toSet()
                                        customOpen = (if (open) keys - category.key else keys + category.key).joinToString(",") }
                                }
                                if (open) {
                                    if (category.key == "tags") TagEntry(state.copy(tags = tags), actions, editable)
                                    else EntryChoices(category, state, actions, editable, showTitle = false)
                                }
                            }
                        }
                    }
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

/**
 * As many columns as fit without breaking a word inside: the longest word (or soft-hyphen part) of any
 * label decides. Large fonts and long German words get fewer, wider chips instead of broken letters.
 */
@Composable
private fun <T> ChoiceGrid(values: List<T>, labels: List<String>, maxColumns: Int, inlineIcon: Boolean = false, content: @Composable (T) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val parts = labels.flatMap { it.split(' ') }.flatMap { word ->
            word.split('­').let { pieces -> pieces.mapIndexed { i, piece -> if (i < pieces.lastIndex) "$piece-" else piece } }
        }
        val longest = parts.maxOfOrNull { measurer.measure(it, style, softWrap = false).size.width } ?: 0
        val gap = 4.dp
        val needed = with(LocalDensity.current) { longest.toDp() } + 16.dp + if (inlineIcon) 24.dp else 0.dp // padding, borders and icon
        val columns = ((maxWidth + gap) / (needed + gap)).toInt().coerceIn(1, maxOf(1, maxColumns))
        // Balance the rows (5 items in 4 columns become 3 + 2, not 4 + 1).
        val rows = (values.size + columns - 1) / columns
        val balanced = if (rows > 1) (values.size + rows - 1) / rows else columns
        ChoiceRows(values, balanced, content)
    }
}

@Composable
private fun <T> ChoiceRows(values: List<T>, columns: Int, content: @Composable (T) -> Unit) {
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
private fun EntryChoices(category: EntryCategory, state: DayEntryUiState, actions: DayEntryActions, enabled: Boolean,
    showTitle: Boolean = true, moreSymptoms: Boolean = true, onMore: (() -> Unit)? = null) {
    if (showTitle) {
        val appearance = category.appearance(state)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(appearance.icon), null, Modifier.size(20.dp))
            Text(appearance.label, style = MaterialTheme.typography.titleMedium)
        }
    }
    val symptoms = state.visibleSymptoms(moreSymptoms).map { it.name }.toSet()
    val items = state.entryItems(category).filter { category.key != "symptoms" || it.tag != null || it.name in symptoms }
    val appearances = items.map { it.appearance(state) }
    // Scales and plain choices stay text-only unless the user picked an icon; one repeated icon adds nothing.
    val iconFields = setOf("mood", "symptoms")
    val showIcon = items.map { item -> category.key in iconFields || item.tag != null ||
        state.overrides.any { it.key == item.key && it.iconKey != null } }
    val moreLabel = stringResource(if (moreSymptoms) R.string.entry_less else R.string.entry_more)
    val values = if (onMore != null) items + null else items
    val labels = appearances.map { it.label } + if (onMore != null) listOf(moreLabel) else emptyList()
    ChoiceGrid(values, labels, if (category.key == "symptoms") 4 else 5, inlineIcon = category.key !in iconFields && showIcon.any { it }) { item ->
        if (item == null) FpChip(moreSymptoms, { onMore?.invoke() }, moreLabel, Modifier.fillMaxWidth(), stacked = true,
            icon = { Icon(painterResource(R.drawable.ic_fp_more), null, Modifier.size(20.dp)) })
        else {
            val appearance = appearances[items.indexOf(item)]
            val selected = state.selected(item)
            FpChip(selected, { item.tag?.let { actions.tag(it.id) } ?: run {
                val name = item.name.takeUnless { selected }
                when (category.key) {
                    "mood" -> actions.mood(name?.let(Mood::valueOf))
                    "flow" -> actions.flow(name?.let(FlowLevel::valueOf))
                    "pain" -> actions.pain(name?.let(Pain::valueOf))
                    "sex" -> actions.sex(name?.let(Sex::valueOf))
                    "discharge" -> actions.discharge(name?.let(Discharge::valueOf))
                    "ovulation_test" -> actions.ovulationTest(name?.let(OvulationTest::valueOf))
                    "symptoms" -> actions.symptom(Symptom.valueOf(requireNotNull(item.name)))
                }
            } }, appearance.label, Modifier.fillMaxWidth().fillMaxHeight(), enabled,
                stacked = category.key in setOf("mood", "symptoms"),
                icon = if (!showIcon[items.indexOf(item)]) null else { { Icon(painterResource(appearance.icon), null,
                    Modifier.size(if (category.key == "mood") 27.dp else 20.dp)
                        .testTag(if (category.key == "mood" && item.tag == null) "mood-icon-${item.name}" else "entry-icon-${item.key}")) } })
        }
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
