package org.freeperiod.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.FpShapes
import org.freeperiod.app.ui.theme.LocalDaylight

/** One answer of a single-choice question: full width, readable, with the brand's full stop as the marker. */
@Composable
fun FpOptionCard(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = LocalDaylight.current
    Surface(modifier.fillMaxWidth(), shape = if (selected) FpShapes.selectedChip else FpShapes.chip,
        color = if (selected) t.accent.selectedChipBackground else t.surface,
        contentColor = if (selected) t.accent.selectedChipText else t.ink,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) t.accent.selectedChipBorder else t.control)) {
        Row(Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val dot = Modifier.size(14.dp)
            Box(if (selected) dot.background(t.accent.periodFill, CircleShape).border(1.dp, t.accent.periodBorder, CircleShape)
                else dot.border(1.5.dp, t.control, CircleShape))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}
