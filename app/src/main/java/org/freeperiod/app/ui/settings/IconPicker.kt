package org.freeperiod.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpIcons
import org.freeperiod.app.ui.theme.FpSpacing
import org.freeperiod.app.ui.theme.LocalDaylight

/** Single choice from the bundled icons, grouped by theme, for entry items and categories. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalDaylight.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.item_icon), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            // The current choice stays visible while the groups scroll.
            FpIcons.byKey[selected]?.let {
                Box(Modifier.size(40.dp).clip(CircleShape).background(t.accent.selectedChipBackground)
                    .border(2.dp, t.accent.selectedChipBorder, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(it), null, Modifier.size(24.dp), tint = t.accent.selectedChipText)
                }
            }
        }
        FpIcons.groups.forEach { group ->
            Text(stringResource(group.title), Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() },
                style = MaterialTheme.typography.labelLarge, color = t.muted)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.icons.forEach { icon ->
                    val isSelected = icon.key == selected
                    Box(Modifier.size(FpSpacing.touch).clip(CircleShape)
                        .background(if (isSelected) t.accent.selectedChipBackground else t.surface)
                        .then(if (isSelected) Modifier.border(2.dp, t.accent.selectedChipBorder, CircleShape) else Modifier)
                        .selectable(isSelected, role = Role.RadioButton) { onSelect(icon.key) },
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon.drawable), stringResource(icon.label), Modifier.size(24.dp),
                            tint = if (isSelected) t.accent.selectedChipText else t.ink)
                    }
                }
            }
        }
    }
}
