package org.freeperiod.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.engine.Basis
import org.freeperiod.engine.PredictionState

@Composable
fun TodayCard(
    state: TodayUiState,
    onStartPeriod: () -> Unit,
    onConfirmEnd: (LocalDate) -> Unit,
    onLogToday: () -> Unit,
    onPausePredictions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val prediction = state.prediction
    val primaryIsPeriod = state.ongoingPeriodId == null || state.endQuestion != null
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.cycleDay?.let {
                Text(stringResource(R.string.cycle_day, it), style = MaterialTheme.typography.headlineLarge)
            }
            Text(predictionText(prediction, state.today, locale), style = MaterialTheme.typography.bodyLarge)
            if (prediction is PredictionState.Range) {
                val basis = when (prediction.basis) {
                    Basis.USER_ENTERED -> stringResource(R.string.basis_entered)
                    Basis.EARLY_ESTIMATE -> stringResource(R.string.basis_early)
                    Basis.HISTORY -> stringResource(R.string.basis_history, prediction.cyclesUsed)
                }
                Text(basis, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                modifier = Modifier.fillMaxWidth(), enabled = !state.writing,
                onClick = {
                    when {
                        state.ongoingPeriodId == null -> onStartPeriod()
                        state.endQuestion != null -> onConfirmEnd(state.endQuestion)
                        else -> onLogToday()
                    }
                },
            ) {
                Text(stringResource(when {
                    state.ongoingPeriodId == null -> R.string.period_started
                    state.endQuestion != null -> R.string.period_ended_question
                    else -> R.string.log_today
                }))
            }
            if (primaryIsPeriod) {
                TextButton(onClick = onLogToday, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.log_today))
                }
            }
            if (prediction is PredictionState.RangePassed) {
                TextButton(onClick = onPausePredictions, enabled = !state.writing,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_predictions))
                }
            }
        }
    }
}

@Composable
private fun predictionText(prediction: PredictionState, today: LocalDate, locale: Locale): String = when (prediction) {
    PredictionState.NoData -> stringResource(R.string.today_welcome)
    is PredictionState.NeedMoreData -> stringResource(R.string.today_need_more)
    is PredictionState.Range -> stringResource(R.string.today_range, formatPredictionRange(prediction.earliest, prediction.latest, today, locale))
    is PredictionState.Varies -> stringResource(R.string.today_varies, prediction.minLength, prediction.maxLength)
    is PredictionState.RangePassed -> stringResource(R.string.today_range_passed, prediction.daysPassed)
    PredictionState.Paused -> stringResource(R.string.today_paused)
}
