package org.freeperiod.app.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagEntry(state: DayEntryUiState, actions: DayEntryActions, editable: Boolean) {
    var editingId by remember(state.date) { mutableStateOf<Long?>(null) }
    var name by remember(state.date) { mutableStateOf("") }
    var showField by remember(state.date) { mutableStateOf(false) }
    LaunchedEffect(state.error) {
        if (state.error == DayEntryError.TAG_NAME) showField = true
    }
    Text(stringResource(R.string.tag_archive_hint), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tags.filter { !it.archived || it.id in state.log.tagIds }.forEach { tag ->
            val checked = tag.id in state.log.tagIds
            val archiveLabel = stringResource(R.string.archive_tag, tag.name)
            val editLabel = stringResource(R.string.rename_tag, tag.name)
            val tones = LocalDaylight.current
            val fill by animateColorAsState(if (checked) tones.accent.container else tones.surface, tween(120), label = "tagFill")
            Surface(shape = if (checked) FpShapes.selectedChip else FpShapes.chip,
                color = fill, contentColor = if (checked) tones.accent.onContainer else tones.ink,
                border = BorderStroke(if (checked) 2.dp else 1.dp, if (checked) tones.accent.selectedChipBorder else tones.control)) {
                Row(Modifier.heightIn(min = 48.dp).semantics { selected = checked }
                    .combinedClickable(enabled = editable, role = Role.Checkbox,
                        onClick = { actions.tag(tag.id) }, onLongClickLabel = archiveLabel,
                        onLongClick = { actions.archiveTag(tag.id) }).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tag.archived) stringResource(R.string.tag_archived, tag.name) else tag.name,
                        fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal)
                    TextButton(enabled = editable, modifier = Modifier.semantics { contentDescription = editLabel },
                        onClick = { editingId = tag.id; name = tag.name; showField = true }) {
                        Text(stringResource(R.string.edit_tag))
                    }
                }
            }
        }
    }
    TextButton(enabled = editable, onClick = { editingId = null; name = ""; showField = true }) {
        Text(stringResource(R.string.add_tag))
    }
    if (showField) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.tag_name)) },
            enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (state.error == DayEntryError.TAG_NAME) Text(stringResource(R.string.tag_name_error), color = MaterialTheme.colorScheme.error)
        Row {
            TextButton(enabled = editable && name.isNotBlank(), onClick = {
                editingId?.let { actions.renameTag(it, name) } ?: actions.addTag(name)
                showField = false
            }) { Text(stringResource(R.string.save_tag)) }
            TextButton(onClick = { showField = false }) { Text(stringResource(R.string.cancel)) }
        }
    }
}
