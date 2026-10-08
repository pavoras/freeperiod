package org.freeperiod.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpCard
import org.freeperiod.app.ui.theme.LocalDaylight
import org.freeperiod.app.ui.theme.FpShapes

@Composable
internal fun SettingsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    FpCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SettingsTextField(value: String, onChange: (String) -> Unit, label: String,
    keyboardType: KeyboardType = KeyboardType.Text) {
    val t = LocalDaylight.current
    TextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        shape = FpShapes.chip, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(focusedContainerColor = t.background, unfocusedContainerColor = t.background,
            focusedIndicatorColor = t.actionText, unfocusedIndicatorColor = t.control))
}

@Composable
internal fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, description: String? = null) {
    val t = LocalDaylight.current
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 48.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp),
            colors = RadioButtonDefaults.colors(selectedColor = t.actionText, unselectedColor = t.control))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.muted) }
        }
    }
}

@Composable
internal fun SettingsPickerRow(title: String, value: String, onClick: () -> Unit) {
    val t = LocalDaylight.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = t.muted)
        }
        Icon(painterResource(R.drawable.ic_fp_next), null, Modifier.size(20.dp), tint = t.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingsChoiceSheet(title: String, options: List<T>, selected: T,
    label: @Composable (T) -> String, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    val t = LocalDaylight.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.background, contentColor = t.ink) {
        Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).selectableGroup(), contentPadding = PaddingValues(20.dp)) {
            items(options, key = { it.toString() }) { option ->
                SettingsChoiceRow(label(option), selected == option, { onSelect(option) })
            }
        }
    }
}
