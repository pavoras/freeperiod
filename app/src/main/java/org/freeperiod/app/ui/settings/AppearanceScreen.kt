package org.freeperiod.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*

@Composable
fun AppearanceScreen(accent: Accent, onAccent: (Accent) -> Unit, onBack: () -> Unit) {
    var preview by remember(accent) { mutableStateOf(accent) }
    val dark = isSystemInDarkTheme()
    FreePeriodTheme(darkTheme = dark, accent = preview) {
        Surface(color = LocalDaylight.current.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FpTopBar(stringResource(R.string.appearance), onBack)
                Text(stringResource(R.string.accent_colour_intro), style = MaterialTheme.typography.bodyMedium, color = LocalDaylight.current.muted)
                Text(stringResource(R.string.accent_colour), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Accent.entries.chunked(if (LocalDensity.current.fontScale >= 1.3f) 2 else 3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                val t = daylightTokens(option, dark)
                                val active = preview == option
                                Surface(Modifier.weight(1f), shape = if (active) FpShapes.selectedChip else FpShapes.chip,
                                    color = if (active) t.accent.container else t.surface,
                                    contentColor = if (active) t.accent.onContainer else t.ink,
                                    border = BorderStroke(if (active) 2.dp else 1.dp, if (active) t.accent.selectedChipBorder else t.line)) {
                                    Column(Modifier.selectable(active, role = Role.RadioButton, onClick = { preview = option; onAccent(option) })
                                        .padding(8.dp).heightIn(min = 72.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                                        Surface(Modifier.size(36.dp), color = t.accent.accent, shape = CircleShape,
                                            border = BorderStroke(1.dp, t.accent.periodBorder)) {}
                                        Text(stringResource(accentLabel(option)), style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.titleMedium)
                SettingsPanel {
                    Text(stringResource(R.string.day_entry), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.entry_mood), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FpChip(false, {}, stringResource(R.string.mood_okay), Modifier.weight(1f))
                        FpChip(true, {}, stringResource(R.string.mood_good), Modifier.weight(1f))
                        FpChip(false, {}, stringResource(R.string.mood_great), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    FpButton({}, Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_today)) }
                }
            }
        }
    }
}

internal fun accentLabel(accent: Accent): Int = when (accent) {
    Accent.CORAL -> R.string.accent_coral
    Accent.PLUM -> R.string.accent_plum
    Accent.SAGE -> R.string.accent_sage
    Accent.OCEAN -> R.string.accent_ocean
    Accent.OCHRE -> R.string.accent_ochre
    Accent.INK -> R.string.accent_ink
}
