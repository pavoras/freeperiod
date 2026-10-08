package org.freeperiod.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.app.ui.day.symptomLabel
import org.freeperiod.engine.Cycle
import org.freeperiod.engine.IneligibleReason

@Composable
fun HistoryScreen(state: HistoryUiState, onInclude: (Long, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    val maxLength = state.cycles.maxOfOrNull { it.length } ?: 1
    LazyColumn(modifier.fillMaxSize().background(LocalDaylight.current.background), contentPadding = PaddingValues(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FpTopBar(stringResource(R.string.nav_history)) }
        if (state.loading) {
            item { CircularProgressIndicator() }
        } else {
            if (state.completedPeriods > 0 || state.cycles.isNotEmpty()) item {
                Text(stringResource(R.string.history_averages), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
                FpPanel {
                    val cycleValue = state.cycleLength?.let(numberFormat::format) ?: stringResource(R.string.history_no_value)
                    val periodValue = state.periodLength?.let(numberFormat::format) ?: stringResource(R.string.history_no_value)
                    if (LocalDensity.current.fontScale >= 1.3f) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AverageValue(cycleValue, R.string.history_cycle_label)
                            HorizontalDivider(color = LocalDaylight.current.accent.periodBorder)
                            AverageValue(periodValue, R.string.history_period_label)
                        }
                    } else Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(IntrinsicSize.Min)) {
                        AverageValue(cycleValue, R.string.history_cycle_label, Modifier.weight(1f).padding(horizontal = 16.dp))
                        VerticalDivider(color = LocalDaylight.current.accent.periodBorder)
                        AverageValue(periodValue, R.string.history_period_label, Modifier.weight(1f).padding(horizontal = 16.dp))
                    }
                }
                Column(Modifier.padding(top = 8.dp)) {
                    if (state.cycleLength == null) Text(stringResource(R.string.history_need_cycle), style = MaterialTheme.typography.bodySmall)
                    if (state.periodLength == null) Text(stringResource(R.string.history_need_period), style = MaterialTheme.typography.bodySmall)
                    if (state.eligibleCycles > 0) Text(stringResource(R.string.basis_history, state.eligibleCycles), style = MaterialTheme.typography.bodySmall)
                    if (state.completedPeriods > 0) Text(stringResource(R.string.history_period_basis, state.completedPeriods), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.cycles.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge) }
            } else {
                item { Text(stringResource(R.string.history_cycles), style = MaterialTheme.typography.titleLarge) }
                items(state.cycles, key = { it.startPeriodId }) { cycle ->
                    CycleRow(cycle, maxLength, !state.writing, onInclude)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.history_symptoms), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.history_symptom_basis, state.symptomCycles), style = MaterialTheme.typography.bodySmall)
                        if (state.symptomCounts.isEmpty()) Text(stringResource(R.string.history_no_symptoms))
                        state.symptomCounts.entries.sortedByDescending { it.value }.forEach { (symptom, count) ->
                            Text(stringResource(R.string.history_symptom_count, stringResource(symptomLabel(symptom)), count))
                        }
                    }
                }
            }
            if (state.error) item { Text(stringResource(R.string.error_storage), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AverageValue(value: String, label: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.displayMedium, modifier = Modifier.alignByBaseline())
            Text(stringResource(R.string.history_days_unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.alignByBaseline())
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CycleRow(cycle: Cycle, maxLength: Int, enabled: Boolean, onInclude: (Long, Boolean) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val date = cycle.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    FpCard() {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(date, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_cycle_days, cycle.length))
            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, FpShapes.bar)) {
                Box(Modifier.fillMaxWidth((cycle.length.toFloat() / maxLength).coerceIn(0f, 1f)).fillMaxHeight()
                    .background(LocalDaylight.current.accent.periodBorder, FpShapes.bar))
            }
            cycle.periodLength?.let { Text(stringResource(R.string.history_period_days, it)) }
            cycle.ineligibleReason?.let { reason ->
                Text(stringResource(when (reason) {
                    IneligibleReason.EXCLUDED_BY_USER -> R.string.history_excluded
                    IneligibleReason.TOO_SHORT -> R.string.history_too_short
                    IneligibleReason.TOO_LONG -> R.string.history_too_long
                }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FpSwitchRow(stringResource(R.string.history_use_predictions), cycle.eligible, enabled,
                { onInclude(cycle.startPeriodId, it) }, stringResource(R.string.history_toggle_label, date))
        }
    }
}
