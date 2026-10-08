package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun DayEntrySettingsScreen(data: BackupData, today: LocalDate, onBack: () -> Unit,
    onOverride: (String, Boolean, Int) -> Unit, onReorder: (List<String>) -> Unit,
    onCategory: (String, CustomCategory?) -> Unit, onArchive: (CustomCategory) -> Unit,
    onItem: (String, String, Long?, Boolean) -> Unit) {
    val state = dayEntryState(data, today, today)
    val categories = entryCategories(state, includeHidden = true)
    var expanded by rememberSaveable { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var categoryEditor by remember { mutableStateOf<CustomCategory?>(null) }
    var categoryDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        item { FpTopBar(stringResource(R.string.day_entry), onBack) }
        item { Text(stringResource(R.string.entry_customization_intro)) }
        items(categories.size, key = { categories[it].overrideKey }) { index ->
            val category = categories[index]
            FpCard {
                val title = category.category?.name ?: stringResource(category.label)
                FpSwitchRow(title, !category.hidden, onChange = { onOverride(category.overrideKey, !it, category.order) })
                Row {
                    TextButton(enabled = index > 0, onClick = {
                        val keys = categories.map { it.overrideKey }.toMutableList()
                        keys[index] = keys[index - 1].also { keys[index - 1] = keys[index] }; onReorder(keys)
                    }) { Text(stringResource(R.string.move_up)) }
                    TextButton(enabled = index < categories.lastIndex, onClick = {
                        val keys = categories.map { it.overrideKey }.toMutableList()
                        keys[index] = keys[index + 1].also { keys[index + 1] = keys[index] }; onReorder(keys)
                    }) { Text(stringResource(R.string.move_down)) }
                    TextButton(onClick = { expanded = if (expanded == category.key) "" else category.key }) { Text(stringResource(R.string.entry_items)) }
                }
                if (expanded == category.key) {
                    builtInItems(category.key).forEach { (name, label) ->
                        val key = "item:${category.key}:$name"
                        val override = data.overrides.find { it.key == key }
                        FpSwitchRow(stringResource(label), override?.hidden != true, onChange = { onOverride(key, !it, override?.sortOrder ?: 0) })
                    }
                    // Include hidden items here so visibility is reversible.
                    val id = if (category.key == "symptoms") data.customCategories.find { it.iconKey == "builtin:symptoms" }?.id else category.category?.id
                    if ((category.key == "symptoms" && id != null) || category.key == "tags" || category.category != null) data.tags.filter { it.categoryId == id && !it.archived }.forEach { tag ->
                        val key = "tag:${tag.id}"
                        val override = data.overrides.find { it.key == key }
                        FpSwitchRow(tag.name, override?.hidden != true, onChange = { onOverride(key, !it, override?.sortOrder ?: 0) })
                    }
                    if (category.key in listOf("symptoms", "tags") || category.category != null) {
                        TextButton(onClick = { itemCategory = category }) { Text(stringResource(R.string.add_entry_item)) }
                    }
                }
                category.category?.let { custom ->
                    Row {
                        TextButton(onClick = { categoryEditor = custom; categoryDialog = true }) { Text(stringResource(R.string.rename_category)) }
                        TextButton(onClick = { onArchive(custom) }) { Text(stringResource(R.string.archive_category)) }
                    }
                }
            }
        }
        item { FpButton({ categoryEditor = null; categoryDialog = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_category)) } }
    }
    itemCategory?.let { category -> AddItemDialog({ itemCategory = null }) { name, icon ->
        onItem(name, icon, category.category?.id, category.key == "symptoms"); itemCategory = null
    } }
    if (categoryDialog) {
        var name by rememberSaveable(categoryEditor?.id) { mutableStateOf(categoryEditor?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { categoryDialog = false }, title = { Text(stringResource(if (categoryEditor == null) R.string.add_category else R.string.rename_category)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.category_name)) }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCategory(name, categoryEditor); categoryDialog = false }) { Text(stringResource(R.string.settings_save)) } },
            dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddItemDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("tag") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_entry_item)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.item_name)) }, singleLine = true)
            Text(stringResource(R.string.item_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FpSpacing.compact), verticalArrangement = Arrangement.spacedBy(FpSpacing.compact)) {
                FpIcons.itemKeys.forEachIndexed { index, key ->
                    FpChip(icon == key, { icon = key }, "", Modifier.size(FpSpacing.touch),
                        icon = { Icon(painterResource(FpIcons.byKey.getValue(key)), stringResource(R.string.item_icon_number, index + 1)) })
                }
            }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), icon) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
