package org.freeperiod.app.ui.day

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.settings.AddItemDialog
import org.freeperiod.app.ui.theme.*
import org.freeperiod.engine.*

internal fun EntryCategory.counted(): Boolean = key in countedFields || category?.isCounted() == true

/** "Name ×2" when logged more than once. */
internal fun countedLabel(label: String, count: Int): String = if (count > 1) "$label ×$count" else label

/** Items counted per day (medication, counted own categories): tap logs once, − and + change how often. */
@Composable
internal fun CountedEntry(category: EntryCategory, state: DayEntryUiState, actions: DayEntryActions, enabled: Boolean,
    open: Boolean, onToggle: () -> Unit) {
    val t = LocalDaylight.current
    val appearance = category.appearance(state)
    val items = state.entryItems(category).filter { it.tag != null }
    var adding by rememberSaveable { mutableStateOf(false) }
    val summary = items.filter { state.selected(it) }
        .map { countedLabel(it.appearance(state).label, state.log.count(requireNotNull(it.tag).id)) }.joinToString()
    FpSectionRow(appearance.icon, appearance.label, summary.ifBlank { stringResource(R.string.entry_none) }, open, onClick = onToggle)
    if (!open) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (category.key == "medication") Text(stringResource(R.string.medication_hint),
            style = MaterialTheme.typography.bodySmall, color = t.muted)
        items.forEach { item ->
            val tag = requireNotNull(item.tag)
            val count = state.log.count(tag.id)
            val label = item.appearance(state)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FpChip(count > 0, { actions.count(tag.id, if (count > 0) 0 else 1) }, label.label, Modifier.weight(1f), enabled,
                    icon = { Icon(painterResource(label.icon), null, Modifier.size(20.dp)) })
                if (count > 0) {
                    IconButton({ actions.count(tag.id, count - 1) }, enabled = enabled) {
                        Icon(painterResource(R.drawable.ic_fp_minus), stringResource(R.string.count_less, label.label))
                    }
                    val times = pluralStringResource(R.plurals.count_times, count, count)
                    Text("$count", Modifier.widthIn(min = 24.dp).semantics { contentDescription = times },
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    IconButton({ actions.count(tag.id, count + 1) }, enabled = enabled && count < MAX_DAILY_COUNT) {
                        Icon(painterResource(R.drawable.ic_item_plus), stringResource(R.string.count_more, label.label))
                    }
                }
            }
        }
        TextButton({ adding = true }, enabled = enabled) { Text(stringResource(R.string.add_entry_item)) }
    }
    if (adding) AddItemDialog({ adding = false }) { name, icon ->
        adding = false
        actions.addItem(category.key.takeIf { it in ownItemFields }, category.category?.id, name, icon, appearance.label)
    }
}
