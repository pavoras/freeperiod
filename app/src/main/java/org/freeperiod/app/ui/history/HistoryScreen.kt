package org.freeperiod.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.app.ui.day.symptomLabel
import org.freeperiod.engine.*

@Composable
fun HistoryScreen(state: HistoryUiState, onInclude: (Long, Boolean) -> Unit, modifier: Modifier = Modifier,
    onDismissHint: (Long) -> Unit = {}) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var visibleIds by remember(state.cycles.map { it.startPeriodId }) { mutableStateOf(emptySet<Long>()) }
    val tones = LocalDaylight.current
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    LazyColumn(modifier.fillMaxSize().background(tones.background).testTag("history-list"), contentPadding = PaddingValues(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FpSpacing.section)) {
        item { FpTopBar(stringResource(R.string.nav_history)) }
        if (state.loading) item { CircularProgressIndicator() }
        else {
            if (state.phase == LifePhase.MENOPAUSE) {
                state.fullMonthsSinceLastEnd?.takeIf { state.completedPeriods > 0 }?.let { months -> item { FpPanel {
                    Text(pluralStringResource(R.plurals.history_full_months, months, months), Modifier.padding(FpSpacing.screen), style = MaterialTheme.typography.titleMedium)
                } } }
            } else {
                if (state.completedPeriods > 0 || state.cycles.isNotEmpty()) item {
                    Text(stringResource(R.string.history_averages), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = FpSpacing.gap))
                    FpPanel {
                        val cycle = state.cycleLength?.let(numberFormat::format) ?: stringResource(R.string.history_no_value)
                        val period = state.periodLength?.let(numberFormat::format) ?: stringResource(R.string.history_no_value)
                        if (LocalDensity.current.fontScale >= 1.3f) Column(Modifier.padding(FpSpacing.section), verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
                            AverageValue(cycle, R.string.history_cycle_label); HorizontalDivider(color = tones.accent.periodBorder); AverageValue(period, R.string.history_period_label)
                        } else Row(Modifier.fillMaxWidth().padding(vertical = FpSpacing.section).height(IntrinsicSize.Min)) {
                            AverageValue(cycle, R.string.history_cycle_label, Modifier.weight(1f).padding(horizontal = FpSpacing.section))
                            VerticalDivider(color = tones.accent.periodBorder)
                            AverageValue(period, R.string.history_period_label, Modifier.weight(1f).padding(horizontal = FpSpacing.section))
                        }
                    }
                    Column(Modifier.padding(top = 8.dp)) {
                        if (state.eligibleCycles > 0) Text(pluralStringResource(R.plurals.basis_history, state.eligibleCycles, state.eligibleCycles), style = MaterialTheme.typography.bodySmall)
                        if (state.completedPeriods > 0) Text(pluralStringResource(R.plurals.history_period_basis, state.completedPeriods, state.completedPeriods), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.cycles.isEmpty()) item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge) }
            else {
                item {
                    Text(stringResource(R.string.history_cycles), style = MaterialTheme.typography.titleLarge)
                    CycleChart(state.cycles, { selectedId = it }, { visibleIds = it })
                }
                val excluded = excludedCyclesInRange(state.cycles, visibleIds)
                if (excluded.isNotEmpty()) item {
                    Text(stringResource(R.string.history_visible_excluded), style = MaterialTheme.typography.titleMedium)
                    excluded.forEach { cycle ->
                        FpSwitchRow(stringResource(R.string.history_excluded_row, cycle.start.format(dateFormat), cycle.length), false, !state.writing,
                            { onInclude(cycle.startPeriodId, it) }, stringResource(R.string.history_toggle_label, cycle.start.format(dateFormat)))
                    }
                }
                state.cycles.filter { it.startPeriodId in state.longCycleHintIds && it.startPeriodId in visibleIds }.forEach { cycle -> item(key = "hint:${cycle.startPeriodId}") {
                    FpCard {
                        Text(stringResource(R.string.history_long_cycle_hint), Modifier.padding(FpSpacing.section))
                        TextButton(onClick = { onDismissHint(cycle.startPeriodId) }, enabled = !state.writing) { Text(stringResource(R.string.history_dismiss_hint)) }
                    }
                } }
            }
            if (state.phase == LifePhase.MENOPAUSE) item { MonthlySymptoms(state) }
            else item { SymptomFrequency(state.symptomCounts, pluralStringResource(R.plurals.history_symptom_basis, state.symptomCycles, state.symptomCycles)) }
            if (state.error) item { Text(stringResource(R.string.error_storage), color = MaterialTheme.colorScheme.error) }
        }
    }
    state.cycles.find { it.startPeriodId == selectedId }?.let { cycle ->
        CycleDetailsSheet(cycle, !state.writing, onInclude, { selectedId = null })
    }
}

@Composable
private fun AverageValue(value: String, label: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FpSpacing.compact)) {
            Text(value, style = MaterialTheme.typography.displayMedium, modifier = Modifier.alignByBaseline())
            Text(stringResource(R.string.history_days_unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.alignByBaseline())
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CycleDetailsSheet(cycle: Cycle, enabled: Boolean, onInclude: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalDaylight.current.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(FpSpacing.section)) {
            Text(stringResource(R.string.history_cycle_details), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_cycle_dates, cycle.start.format(date), cycle.nextStart.minusDays(1).format(date)))
            Text(stringResource(R.string.history_cycle_days, cycle.length))
            cycle.periodLength?.let { length ->
                Text(pluralStringResource(R.plurals.history_period_days, length, length))
                Text(stringResource(R.string.history_period_dates, cycle.start.format(date), cycle.start.plusDays(length.toLong() - 1).format(date)))
            }
            cycle.ineligibleReason?.let { Text(stringResource(reasonLabel(it)), color = LocalDaylight.current.muted) }
            FpSwitchRow(stringResource(R.string.history_use_predictions), cycle.eligible, enabled, { onInclude(cycle.startPeriodId, it) },
                stringResource(R.string.history_toggle_label, cycle.start.format(date)))
            FpButton(onDismiss, Modifier.fillMaxWidth(), primary = false) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun MonthlySymptoms(state: HistoryUiState) {
    var monthDay by rememberSaveable { mutableLongStateOf(state.today.withDayOfMonth(1).toEpochDay()) }
    val month = YearMonth.from(LocalDate.ofEpochDay(monthDay))
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthDay = month.minusMonths(1).atDay(1).toEpochDay() }) {
                Icon(painterResource(R.drawable.ic_fp_previous), stringResource(R.string.history_previous_month))
            }
            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { monthDay = month.plusMonths(1).atDay(1).toEpochDay() }, enabled = month < YearMonth.from(state.today)) {
                Icon(painterResource(R.drawable.ic_fp_next), stringResource(R.string.history_next_month))
            }
        }
        SymptomFrequency(state.monthlySymptoms[month].orEmpty(), stringResource(R.string.history_monthly_symptoms))
    }
}

@Composable
private fun SymptomFrequency(counts: Map<Symptom, Int>, basis: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        Text(stringResource(R.string.history_symptoms), style = MaterialTheme.typography.titleLarge)
        Text(basis, style = MaterialTheme.typography.bodySmall, color = LocalDaylight.current.muted)
        if (counts.isEmpty()) Text(stringResource(R.string.history_no_symptoms))
        counts.entries.sortedByDescending { it.value }.forEach { (symptom, count) ->
            Text(pluralStringResource(R.plurals.history_symptom_count, count, count, stringResource(symptomLabel(symptom))))
        }
    }
}
