package org.freeperiod.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import org.freeperiod.app.R
import org.freeperiod.engine.PeriodError

@Composable
fun TodayScreen(
    state: TodayUiState,
    onStartPeriod: () -> Unit,
    onConfirmEnd: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onPausePredictions: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var suggestedEnd by remember { mutableStateOf<LocalDate?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.nav_today), Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.headlineMedium)
        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(24.dp))
        } else {
            TodayCard(state, onStartPeriod, { suggestedEnd = it }, { onDayClick(state.today) },
                onPausePredictions, Modifier.padding(horizontal = 16.dp))
            MonthCalendar(state.month, state.days, onMonthChange, onDayClick,
                Modifier.padding(horizontal = 12.dp))
        }
        state.error?.let { error ->
            val message = when (error.periodError) {
                PeriodError.OVERLAP -> R.string.error_period_overlap
                PeriodError.END_BEFORE_START -> R.string.error_period_end_before_start
                PeriodError.START_IN_FUTURE -> R.string.error_period_start_future
                PeriodError.END_IN_FUTURE -> R.string.error_period_end_future
                null -> R.string.error_storage
            }
            Text(stringResource(message), Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
    suggestedEnd?.let { date ->
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        AlertDialog(onDismissRequest = { suggestedEnd = null },
            title = { Text(stringResource(R.string.period_ended_question)) },
            text = { Text(stringResource(R.string.confirm_period_end, date.format(
                java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)))) },
            confirmButton = {
                TextButton(onClick = { suggestedEnd = null; onConfirmEnd(date) }) { Text(stringResource(R.string.confirm)) }
            }, dismissButton = {
                TextButton(onClick = { suggestedEnd = null }) { Text(stringResource(R.string.cancel)) }
            })
    }
}
