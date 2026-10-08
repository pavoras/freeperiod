package org.freeperiod.app.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FpShapes
import org.freeperiod.app.ui.theme.LocalDaylight

/** The most important answer of a day entry, so it sits first and stands out from the categories. */
@Composable
internal fun PeriodBlock(started: Boolean, ended: Boolean, showEnd: Boolean, startEnabled: Boolean, endEnabled: Boolean,
    onStart: (Boolean) -> Unit, onEnd: (Boolean) -> Unit) {
    val t = LocalDaylight.current
    Surface(Modifier.fillMaxWidth(), shape = FpShapes.card, color = t.accent.container, contentColor = t.accent.onContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp).background(t.accent.periodFill, CircleShape).border(1.dp, t.accent.periodBorder, CircleShape))
                Text(stringResource(R.string.day_period_heading), style = MaterialTheme.typography.titleMedium)
            }
            PeriodToggle(stringResource(R.string.period_start_day), started, startEnabled, onStart)
            if (showEnd) PeriodToggle(stringResource(R.string.period_end_day), ended, endEnabled, onEnd)
        }
    }
}

@Composable
private fun PeriodToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalDaylight.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Switch(checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = t.accent.accent, checkedThumbColor = t.accent.onAccent,
                checkedBorderColor = t.accent.periodBorder, uncheckedTrackColor = t.surface,
                uncheckedThumbColor = t.muted, uncheckedBorderColor = t.control))
    }
}
