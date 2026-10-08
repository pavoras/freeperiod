package org.freeperiod.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
                AccentPreview()
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

/** A small, clearly labelled sample (not a working screen): it ignores touches and shows the accent at a glance. */
@Composable
private fun AccentPreview() {
    val t = LocalDaylight.current
    Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Surface(Modifier.fillMaxWidth()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } }
            .clearAndSetSemantics {}, shape = FpShapes.card, color = t.background, border = BorderStroke(1.dp, t.line)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("12", Modifier.alignByBaseline(), style = MaterialTheme.typography.displayMedium)
                        Text(stringResource(R.string.wordmark_stop), Modifier.alignByBaseline(),
                            style = MaterialTheme.typography.displayMedium.copy(fontFamily = DmSans), color = t.accent.accent)
                    }
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(true, true, false, false).forEachIndexed { index, period ->
                            Box(Modifier.size(26.dp).then(
                                if (period) Modifier.background(t.accent.periodFill, CircleShape).border(1.dp, t.accent.periodBorder, CircleShape)
                                else if (index == 2) Modifier.border(2.dp, t.accent.todayRing, CircleShape)
                                else Modifier.border(1.dp, t.predicted, CircleShape)))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FpChip(false, {}, stringResource(R.string.mood_okay), Modifier.weight(1f))
                    FpChip(true, {}, stringResource(R.string.mood_good), Modifier.weight(1f))
                }
                FpButton({}, Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_today)) }
            }
        }
        Surface(Modifier.padding(start = 16.dp), shape = CircleShape, color = t.accent.accent, contentColor = t.accent.onAccent) {
            Text(stringResource(R.string.appearance_preview), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium)
        }
    }
    Text(stringResource(R.string.appearance_preview_hint), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
        color = t.muted, textAlign = TextAlign.Center)
}
