package org.freeperiod.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.*

/** Compose animations inherit the platform animator duration scale, including zero. */
@Composable
fun FpChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, stacked: Boolean = false, icon: (@Composable () -> Unit)? = null) {
    val t = LocalDaylight.current
    val background by animateColorAsState(if (selected) t.accent.selectedChipBackground else t.surface,
        tween(120), label = "chipFill")
    val foreground = if (selected) t.accent.selectedChipText else t.ink
    Surface(modifier.widthIn(min = FpSpacing.touch), shape = if (selected) FpShapes.selectedChip else FpShapes.chip,
        color = background, contentColor = if (enabled) foreground else t.muted,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) t.accent.selectedChipBorder else t.control)) {
        val target = Modifier.selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .heightIn(min = if (stacked) 64.dp else 48.dp).padding(horizontal = 4.dp, vertical = 6.dp)
        val text: @Composable () -> Unit = {
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center)
        }
        if (stacked) Column(target, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) { icon?.invoke(); text() }
        else Row(target, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) { icon?.invoke(); text() }
    }
}

@Composable
fun FpFaceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, face: @Composable () -> Unit) =
    FpChip(selected, onClick, label, modifier, enabled, stacked = true, icon = face)
