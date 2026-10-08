package org.freeperiod.app.ui.settings

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.LocalDaylight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.FpSpacing
import org.freeperiod.app.ui.day.*
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData
import java.time.LocalDate

internal fun builtInItems(key: String): List<Pair<String, Int>> = when (key) {
    "mood" -> Mood.entries.map { it.name to moodLabel(it) }
    "flow" -> FlowLevel.entries.map { it.name to flowLabel(it) }
    "pain" -> Pain.entries.map { it.name to painLabel(it) }
    "symptoms" -> Symptom.entries.map { it.name to symptomLabel(it) }
    "sex" -> Sex.entries.map { it.name to sexLabel(it) }
    "discharge" -> Discharge.entries.map { it.name to dischargeLabel(it) }
    "ovulation_test" -> listOf("NEGATIVE" to R.string.ovulation_negative, "POSITIVE" to R.string.ovulation_positive)
    else -> emptyList()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayEntrySettingsScreen(data: BackupData, today: LocalDate, onBack: () -> Unit,
    onOverride: (String, Boolean, Int) -> Unit, onReorder: (List<String>) -> Unit,
    onCategory: (String, CustomCategory?) -> Unit, onArchive: (CustomCategory) -> Unit,
    onItem: (String, String, Long?, Boolean) -> Unit) {
    val state = dayEntryState(data, today, today)
    val categories = entryCategories(state, includeHidden = true)
    var expanded by rememberSaveable { mutableStateOf("") }
    var reorder by rememberSaveable { mutableStateOf(false) }
    var itemCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var categoryEditor by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryDialog by rememberSaveable { mutableStateOf(false) }
    val t = LocalDaylight.current
    LazyColumn(Modifier.fillMaxSize().background(t.background), contentPadding = PaddingValues(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { FpTopBar(stringResource(R.string.customize_day_entry), onBack) }
        item { Text(stringResource(R.string.entry_customization_intro), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        item {
            TextButton(onClick = { reorder = !reorder; expanded = "" }) {
                Text(stringResource(if (reorder) R.string.reorder_done else R.string.reorder_categories))
            }
        }
        items(categories.size, key = { categories[it].overrideKey }) { index ->
            val category = categories[index]
            val title = category.category?.name ?: stringResource(category.label)
            // Hidden items stay listed so visibility is reversible.
            val id = if (category.key == "symptoms") data.customCategories.find { it.iconKey == "builtin:symptoms" }?.id else category.category?.id
            val tags = if ((category.key == "symptoms" && id != null) || category.key == "tags" || category.category != null)
                data.tags.filter { it.categoryId == id && !it.archived } else emptyList()
            val itemKeys = builtInItems(category.key).map { "item:${category.key}:${it.first}" } + tags.map { "tag:${it.id}" }
            val canEdit = itemKeys.isNotEmpty() || category.key in listOf("symptoms", "tags") || category.category != null
            val open = expanded == category.key && !reorder
            Column {
                Row(Modifier.fillMaxWidth().clickable(enabled = canEdit && !reorder) { expanded = if (open) "" else category.key }
                    .heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (category.hidden) t.muted else t.ink)
                        if (itemKeys.isNotEmpty()) {
                            val shown = itemKeys.count { key -> data.overrides.find { it.key == key }?.hidden != true }
                            Text(stringResource(R.string.entry_items_shown, shown, itemKeys.size), style = MaterialTheme.typography.bodySmall, color = t.muted)
                        }
                    }
                    if (reorder) {
                        IconButton(enabled = index > 0, onClick = {
                            val keys = categories.map { it.overrideKey }.toMutableList()
                            keys[index] = keys[index - 1].also { keys[index - 1] = keys[index] }; onReorder(keys)
                        }) { Icon(painterResource(R.drawable.ic_fp_up), stringResource(R.string.category_move_up, title)) }
                        IconButton(enabled = index < categories.lastIndex, onClick = {
                            val keys = categories.map { it.overrideKey }.toMutableList()
                            keys[index] = keys[index + 1].also { keys[index + 1] = keys[index] }; onReorder(keys)
                        }) { Icon(painterResource(R.drawable.ic_fp_down), stringResource(R.string.category_move_down, title)) }
                    } else {
                        if (canEdit) Icon(painterResource(if (open) R.drawable.ic_fp_up else R.drawable.ic_fp_down), null, Modifier.size(20.dp), tint = t.muted)
                        Switch(!category.hidden, { onOverride(category.overrideKey, !it, category.order) },
                            Modifier.semantics { contentDescription = title },
                            colors = SwitchDefaults.colors(checkedTrackColor = t.accent.accent, checkedThumbColor = t.accent.onAccent,
                                checkedBorderColor = t.accent.periodBorder, uncheckedTrackColor = t.surface,
                                uncheckedThumbColor = t.muted, uncheckedBorderColor = t.control))
                    }
                }
                if (open) Column(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    builtInItems(category.key).forEach { (name, label) ->
                        val key = "item:${category.key}:$name"
                        val override = data.overrides.find { it.key == key }
                        FpSwitchRow(stringResource(label), override?.hidden != true, onChange = { onOverride(key, !it, override?.sortOrder ?: 0) })
                    }
                    tags.forEach { tag ->
                        val key = "tag:${tag.id}"
                        val override = data.overrides.find { it.key == key }
                        FpSwitchRow(tag.name, override?.hidden != true, onChange = { onOverride(key, !it, override?.sortOrder ?: 0) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (category.key in listOf("symptoms", "tags") || category.category != null) {
                            TextButton(onClick = { itemCategory = category }) { Text(stringResource(R.string.add_entry_item)) }
                        }
                        category.category?.let { custom ->
                            TextButton(onClick = { categoryEditor = custom; categoryDialog = true }) { Text(stringResource(R.string.rename_category)) }
                            TextButton(onClick = { onArchive(custom) }) { Text(stringResource(R.string.archive_category)) }
                        }
                    }
                }
                HorizontalDivider(color = t.line)
            }
        }
        item { FpButton({ categoryEditor = null; categoryDialog = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_category)) } }
    }
    itemCategory?.let { category -> AddItemDialog({ itemCategory = null }) { name, icon ->
        onItem(name, icon, category.category?.id, category.key == "symptoms"); itemCategory = null
    } }
    if (categoryDialog) {
        var name by rememberSaveable(categoryEditor?.id) { mutableStateOf(categoryEditor?.name.orEmpty()) }
        AlertDialog(containerColor = LocalDaylight.current.surface, onDismissRequest = { categoryDialog = false }, title = { Text(stringResource(if (categoryEditor == null) R.string.add_category else R.string.rename_category)) },
            text = { SettingsTextField(name, { name = it }, stringResource(R.string.category_name)) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCategory(name, categoryEditor); categoryDialog = false }) { Text(stringResource(R.string.settings_save)) } },
            dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddItemDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("tag") }
    SettingsEditorDialog(stringResource(R.string.add_entry_item), onDismiss,
        { onSave(name.trim(), icon) }, name.isNotBlank()) {
        SettingsTextField(name, { name = it }, stringResource(R.string.item_name))
        Text(stringResource(R.string.item_icon), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FpSpacing.compact), verticalArrangement = Arrangement.spacedBy(FpSpacing.compact)) {
            FpIcons.itemKeys.forEachIndexed { index, key ->
                FpChip(icon == key, { icon = key }, "", Modifier.size(FpSpacing.touch),
                    icon = { Icon(painterResource(FpIcons.byKey.getValue(key)), stringResource(R.string.item_icon_number, index + 1)) })
            }
        }
    }
}
