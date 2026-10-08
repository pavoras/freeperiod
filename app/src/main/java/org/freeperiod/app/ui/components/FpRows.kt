package org.freeperiod.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.*

@Composable
fun FpSectionRow(icon: Int, title: String, summary: String, expanded: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalDaylight.current
    val state = stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
    Column {
        HorizontalDivider(color = t.line)
        Row(Modifier.fillMaxWidth().semantics { stateDescription = state }
            .clickable(enabled = enabled, onClick = onClick).heightIn(min = 48.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = t.muted)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (summary.isNotBlank()) Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = t.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Icon(painterResource(if (expanded) R.drawable.ic_fp_down else R.drawable.ic_fp_next), null,
                Modifier.size(18.dp), tint = t.muted)
        }
    }
}

@Composable
fun FpSwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit,
    description: String = label) {
    val t = LocalDaylight.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = description },
            colors = SwitchDefaults.colors(checkedTrackColor = t.accent.accent, checkedThumbColor = t.accent.onAccent,
                checkedBorderColor = t.accent.periodBorder, uncheckedTrackColor = t.surface,
                uncheckedThumbColor = t.muted, uncheckedBorderColor = t.control))
    }
}

@Composable
fun FpTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_fp_previous), stringResource(R.string.back))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
        actions()
    }
}
