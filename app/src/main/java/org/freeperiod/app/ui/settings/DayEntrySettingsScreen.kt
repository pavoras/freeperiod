package org.freeperiod.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.day.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData
import java.time.LocalDate

private data class EntryEditor(val key: String, val appearance: ResolvedEntryAppearance,
    val override: UiOverride, val tag: Tag? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayEntrySettingsScreen(data: BackupData, today: LocalDate, onBack: () -> Unit,
    onOverride: (String, Boolean, Int) -> Unit, onReorder: (List<String>) -> Unit,
    onCategory: (String, String, CustomCategory?, Boolean) -> Unit, onArchive: (CustomCategory) -> Unit,
    onItem: (String, String, Long?, String?) -> Unit, onRestore: (CustomCategory) -> Unit = {},
    onAppearance: (UiOverride) -> Unit = {}, onEditItem: (Tag) -> Unit = {}, onDeleteItem: (Long) -> Unit = {}) {
    val state = dayEntryState(data, today, today)
    val categories = entryCategories(state, includeHidden = true)
    var expanded by rememberSaveable { mutableStateOf("") }
    var reorder by rememberSaveable { mutableStateOf(false) }
    var itemCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var categoryEditor by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryDialog by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EntryEditor?>(null) }
    val t = LocalDaylight.current
    LazyColumn(Modifier.fillMaxSize().background(t.background), contentPadding = PaddingValues(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { FpTopBar(stringResource(R.string.customize_day_entry), onBack) }
        item { Text(stringResource(R.string.entry_customization_intro), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        item {
            TextButton(onClick = { reorder = !reorder; expanded = "" }) {
                Text(stringResource(if (reorder) R.string.reorder_done else R.string.reorder_categories))
            }
        }
        items(categories.size, key = { categories[it].overrideKey }) { index ->
            val category = categories[index]
            val appearance = category.appearance(state)
            val items = state.entryItems(category, includeHidden = true)
            val open = expanded == category.key && !reorder
            Column {
                Row(Modifier.fillMaxWidth().clickable(enabled = !reorder) { expanded = if (open) "" else category.key }
                    .heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(appearance.icon), null, Modifier.size(24.dp), tint = if (category.hidden) t.muted else t.ink)
                    Column(Modifier.weight(1f)) {
                        Text(appearance.label, style = MaterialTheme.typography.bodyLarge, color = if (category.hidden) t.muted else t.ink)
                        if (items.isNotEmpty()) Text(stringResource(R.string.entry_items_shown, items.count { !it.hidden }, items.size),
                            style = MaterialTheme.typography.bodySmall, color = t.muted)
                    }
                    if (reorder) MoveButtons(index, categories.map { it.overrideKey }, appearance.label, onReorder)
                    else {
                        Icon(painterResource(if (open) R.drawable.ic_fp_up else R.drawable.ic_fp_down), null, Modifier.size(20.dp), tint = t.muted)
                        VisibilitySwitch(appearance.label, !category.hidden) { onOverride(category.overrideKey, !it, category.order) }
                    }
                }
                if (open) Column(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    // One row per item: tap the name to rename (and delete own items), arrows to reorder.
                    items.forEachIndexed { itemIndex, item ->
                        val itemAppearance = item.appearance(state)
                        val renameItem = stringResource(R.string.rename_entry_description, itemAppearance.label)
                        val override = data.overrides.find { it.key == item.key } ?: UiOverride(item.key, item.hidden, item.order)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                                .clickable(onClickLabel = renameItem) { editor = EntryEditor(item.key, itemAppearance, override, item.tag) },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(itemAppearance.icon), null, Modifier.size(20.dp), tint = if (item.hidden) t.muted else t.ink)
                                Text(itemAppearance.label, color = if (item.hidden) t.muted else t.ink)
                            }
                            MoveButtons(itemIndex, items.map { it.key }, itemAppearance.label, onReorder)
                            VisibilitySwitch(itemAppearance.label, !item.hidden) { onOverride(item.key, !it, item.order) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (category.key in ownItemFields || category.key == "tags" || category.category != null)
                            TextButton(onClick = { itemCategory = category }) { Text(stringResource(R.string.add_entry_item)) }
                        val renameCategory = stringResource(R.string.rename_entry_description, appearance.label)
                        TextButton(onClick = {
                            category.category?.let { categoryEditor = it; categoryDialog = true } ?: run {
                                editor = EntryEditor(category.overrideKey, appearance,
                                    data.overrides.find { it.key == category.overrideKey } ?: UiOverride(category.overrideKey, category.hidden, category.order))
                            }
                        }) { Text(renameCategory) }
                        category.category?.let { custom ->
                            TextButton(onClick = { onArchive(custom) }) { Text(stringResource(R.string.archive_category)) }
                        }
                    }
                }
                HorizontalDivider(color = t.line)
            }
        }
        item { FpButton({ categoryEditor = null; categoryDialog = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_category)) } }
        val archived = data.customCategories.filter { it.archived && it.builtInField() == null }
        if (archived.isNotEmpty()) item {
            Text(stringResource(R.string.archived_categories), Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.archived_categories_hint), style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
        items(archived.size, key = { "archived:${archived[it].id}" }) { index ->
            val category = archived[index]
            val appearance = entryAppearance("customCategory:${category.id}", R.string.entry_tags, R.drawable.ic_fp_tags, emptyList(), category = category)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(appearance.icon), null, Modifier.size(24.dp), tint = t.muted)
                Text(appearance.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = t.muted)
                TextButton(onClick = { onRestore(category) }) { Text(stringResource(R.string.restore_category)) }
            }
        }
    }
    itemCategory?.let { category -> AddItemDialog({ itemCategory = null }) { name, icon ->
        onItem(name, icon, category.category?.id, category.key.takeIf { it in ownItemFields }); itemCategory = null
    } }
    editor?.let { editing ->
        RenameEntryDialog(editing.appearance.label, editing.appearance.iconKey, { editor = null }, { name, icon ->
            editing.tag?.let { onEditItem(it.copy(name = name, iconKey = icon)) }
                ?: onAppearance(editing.override.copy(
                    label = name.takeUnless { editing.override.label == null && it == editing.appearance.label },
                    iconKey = icon.takeUnless { it.startsWith("default:") || editing.override.iconKey == null && it == editing.appearance.iconKey }))
            editor = null
        }, if (editing.tag == null) ({ onAppearance(editing.override.copy(label = null, iconKey = null)); editor = null }) else null,
            editing.tag?.let { tag -> { onDeleteItem(tag.id); editor = null } })
    }
    if (categoryDialog) {
        var name by rememberSaveable(categoryEditor?.id) { mutableStateOf(categoryEditor?.name.orEmpty()) }
        var icon by rememberSaveable(categoryEditor?.id) { mutableStateOf(categoryEditor?.iconKey ?: "tag") }
        var singleChoice by rememberSaveable(categoryEditor?.id) { mutableStateOf(categoryEditor?.singleChoice ?: false) }
        SettingsEditorDialog(stringResource(if (categoryEditor == null) R.string.add_category else R.string.edit_category),
            { categoryDialog = false }, { onCategory(name.trim(), icon, categoryEditor, singleChoice); categoryDialog = false }, name.isNotBlank()) {
            SettingsTextField(name, { name = it }, stringResource(R.string.category_name))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FpChip(singleChoice, { singleChoice = true }, stringResource(R.string.pick_one), Modifier.weight(1f))
                FpChip(!singleChoice, { singleChoice = false }, stringResource(R.string.pick_several), Modifier.weight(1f))
            }
            IconPicker(icon, { icon = it })
        }
    }
}

@Composable
private fun VisibilitySwitch(title: String, visible: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalDaylight.current
    Switch(visible, onChange, Modifier.semantics { contentDescription = title },
        colors = SwitchDefaults.colors(checkedTrackColor = t.accent.accent, checkedThumbColor = t.accent.onAccent,
            checkedBorderColor = t.accent.periodBorder, uncheckedTrackColor = t.surface,
            uncheckedThumbColor = t.muted, uncheckedBorderColor = t.control))
}

@Composable
private fun MoveButtons(index: Int, keys: List<String>, title: String, onReorder: (List<String>) -> Unit) {
    fun move(other: Int) {
        val reordered = keys.toMutableList()
        reordered[index] = reordered[other].also { reordered[other] = reordered[index] }
        onReorder(reordered)
    }
    IconButton(enabled = index > 0, onClick = { move(index - 1) }) {
        Icon(painterResource(R.drawable.ic_fp_up), stringResource(R.string.category_move_up, title))
    }
    IconButton(enabled = index < keys.lastIndex, onClick = { move(index + 1) }) {
        Icon(painterResource(R.drawable.ic_fp_down), stringResource(R.string.category_move_down, title))
    }
}

@Composable
internal fun RenameEntryDialog(initialName: String, initialIcon: String, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit, onReset: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var icon by rememberSaveable(initialIcon) { mutableStateOf(initialIcon) }
    SettingsEditorDialog(stringResource(R.string.rename_entry), onDismiss, { onSave(name.trim(), icon) }, name.isNotBlank()) {
        SettingsTextField(name, { name = it }, stringResource(R.string.item_name))
        onReset?.let { TextButton(onClick = it) { Text(stringResource(R.string.reset_entry_appearance)) } }
        onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.delete_entry_item), color = MaterialTheme.colorScheme.error) } }
        IconPicker(icon, { icon = it })
    }
}

@Composable
internal fun AddItemDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("tag") }
    SettingsEditorDialog(stringResource(R.string.add_entry_item), onDismiss, { onSave(name.trim(), icon) }, name.isNotBlank()) {
        SettingsTextField(name, { name = it }, stringResource(R.string.item_name))
        IconPicker(icon, { icon = it })
    }
}
