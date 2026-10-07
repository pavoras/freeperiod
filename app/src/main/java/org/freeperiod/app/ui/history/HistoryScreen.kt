package org.freeperiod.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.freeperiod.app.R
import org.freeperiod.app.ui.day.symptomLabel
import org.freeperiod.engine.Cycle
import org.freeperiod.engine.IneligibleReason

@Composable
fun HistoryScreen(state: HistoryUiState, onInclude: (Long, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    val maxLength = state.cycles.maxOfOrNull { it.length } ?: 1
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium) }
        if (state.loading) {
            item { CircularProgressIndicator() }
        } else {
            if (state.completedPeriods > 0 || state.cycles.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.history_averages), style = MaterialTheme.typography.titleLarge)
                        Text(state.cycleLength?.let { stringResource(R.string.history_cycle_length, numberFormat.format(it)) }
                            ?: stringResource(R.string.history_need_cycle))
                        if (state.eligibleCycles > 0) Text(stringResource(R.string.basis_history, state.eligibleCycles), style = MaterialTheme.typography.bodySmall)
                        Text(state.periodLength?.let { stringResource(R.string.history_period_length, numberFormat.format(it)) }
                            ?: stringResource(R.string.history_need_period))
                        if (state.completedPeriods > 0) Text(stringResource(R.string.history_period_basis, state.completedPeriods), style = MaterialTheme.typography.bodySmall)
                    }
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
private fun CycleRow(cycle: Cycle, maxLength: Int, enabled: Boolean, onInclude: (Long, Boolean) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val date = cycle.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(date, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_cycle_days, cycle.length))
            LinearProgressIndicator(progress = { cycle.length.toFloat() / maxLength }, modifier = Modifier.fillMaxWidth())
            cycle.periodLength?.let { Text(stringResource(R.string.history_period_days, it)) }
            cycle.ineligibleReason?.let { reason ->
                Text(stringResource(when (reason) {
                    IneligibleReason.EXCLUDED_BY_USER -> R.string.history_excluded
                    IneligibleReason.TOO_SHORT -> R.string.history_too_short
                    IneligibleReason.TOO_LONG -> R.string.history_too_long
                }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_use_predictions), Modifier.weight(1f))
                val description = stringResource(R.string.history_toggle_label, date)
                Switch(checked = cycle.eligible, enabled = enabled,
                    onCheckedChange = { onInclude(cycle.startPeriodId, it) },
                    modifier = Modifier.semantics { contentDescription = description })
            }
        }
    }
}
