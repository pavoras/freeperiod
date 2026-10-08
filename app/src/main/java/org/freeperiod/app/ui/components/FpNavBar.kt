package org.freeperiod.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.*

data class FpDestination(val key: String, val label: Int, val icon: Int)
val FpDestinations = listOf(FpDestination("today", R.string.nav_today, R.drawable.ic_fp_today),
    FpDestination("history", R.string.nav_history, R.drawable.ic_fp_history),
    FpDestination("settings", R.string.nav_settings, R.drawable.ic_fp_settings))

@Composable
fun FpNavBar(selected: String?, onSelect: (String) -> Unit) {
    val t = LocalDaylight.current
    Surface(color = t.background) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = t.line)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                FpDestinations.forEach { destination ->
                    val active = selected == destination.key
                    Column(Modifier.weight(1f).selectable(active, role = Role.Tab, onClick = { onSelect(destination.key) })
                        .heightIn(min = 66.dp).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(56.dp, 34.dp).background(if (active) t.accent.container else t.background, FpShapes.nav),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(destination.icon), null, Modifier.size(22.dp), tint = if (active) t.ink else t.muted)
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(stringResource(destination.label), style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) t.ink else t.muted)
                    }
                }
            }
        }
    }
}
