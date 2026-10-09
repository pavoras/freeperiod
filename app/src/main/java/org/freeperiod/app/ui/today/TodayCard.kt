package org.freeperiod.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpButton
import org.freeperiod.app.ui.components.FpCard
import org.freeperiod.app.ui.theme.DmSans
import org.freeperiod.app.ui.theme.LocalDaylight
import org.freeperiod.engine.*

@Composable
fun TodayCard(state: TodayUiState, onStartPeriod: () -> Unit, onConfirmEnd: (LocalDate) -> Unit,
    onLogToday: () -> Unit, onPausePredictions: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val prediction = state.prediction
    val t = LocalDaylight.current
    val large = LocalDensity.current.fontScale >= 1.3f
    val packDay = when (prediction) {
        is PredictionState.ScheduledBreak -> prediction.packDay
        is PredictionState.ContinuousPill -> prediction.packDay
        else -> null
    }
    val number = if (prediction is PredictionState.Menopause) null else packDay ?: state.periodDay ?: state.cycleDay
    val stackTimeline = large || (number ?: 0) >= 100
    val label = when {
        packDay != null -> stringResource(R.string.pack_day_label, packDay)
        state.periodDay != null -> stringResource(R.string.period_day_label, state.periodDay)
        else -> stringResource(R.string.cycle_day_label)
    }
    val numeral: @Composable () -> Unit = {
        if (number != null) Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                Text(number.toString(), Modifier.alignByBaseline(), style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.wordmark_stop), Modifier.alignByBaseline().clearAndSetSemantics {},
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = DmSans), color = t.accent.accent)
            }
        }
    }
    FpCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (number == null && prediction is PredictionState.NoData) EmptyCycleHeader(label)
            if (number != null) {
                if (stackTimeline) {
                    numeral()
                    state.timeline?.let { CycleTimelineView(it, Modifier.fillMaxWidth(), pack = packDay != null) }
                } else Row(Modifier.fillMaxWidth().heightIn(min = 110.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.widthIn(min = 100.dp)) { numeral() }
                    state.timeline?.let { CycleTimelineView(it, Modifier.weight(1f), pack = packDay != null) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(predictionText(prediction, state.today, locale), style = MaterialTheme.typography.bodyLarge)
                if (prediction is PredictionState.Range) {
                    val basis = when (prediction.basis) {
                        Basis.USER_ENTERED -> stringResource(R.string.basis_entered)
                        Basis.EARLY_ESTIMATE -> stringResource(R.string.basis_early)
                        Basis.HISTORY -> stringResource(R.string.basis_history, prediction.cyclesUsed)
                    }
                    Text(basis, style = MaterialTheme.typography.bodySmall, color = t.muted)
                }
                if (prediction is PredictionState.NoData) {
                    Text(stringResource(R.string.today_welcome_hint), style = MaterialTheme.typography.bodySmall, color = t.muted)
                }
                if (state.situation.phase == LifePhase.PERIMENOPAUSE) {
                    Text(stringResource(R.string.today_perimenopause), style = MaterialTheme.typography.bodySmall, color = t.muted)
                }
                if (state.situation.method == Method.PILL_PROGESTIN && prediction is PredictionState.Range) {
                    Text(stringResource(R.string.today_irregular_method), style = MaterialTheme.typography.bodySmall, color = t.muted)
                }
            }
            TodayActions(stringResource(if (state.ongoingPeriodId == null) R.string.period_started else R.string.period_ended),
                stringResource(R.string.log_today), !state.writing,
                { state.periodEndForToday?.let(onConfirmEnd) ?: onStartPeriod() }, onLogToday)
            if (prediction is PredictionState.RangePassed) TextButton(onClick = onPausePredictions, enabled = !state.writing) {
                Text(stringResource(R.string.pause_predictions))
            }
        }
    }
}

@Composable
private fun TodayActions(primaryLabel: String, secondaryLabel: String, enabled: Boolean,
    onPrimary: () -> Unit, onSecondary: () -> Unit) {
    val tokens = LocalDaylight.current
    val colors = remember(tokens) { todayFillColors(tokens) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val primaryTextWidth = measurer.measure(AnnotatedString(primaryLabel), style, softWrap = false).size.width
    val secondaryTextWidth = measurer.measure(AnnotatedString(secondaryLabel), style, softWrap = false).size.width
    val primaryPadding = ButtonDefaults.ContentPadding.calculateLeftPadding(LayoutDirection.Ltr) +
        ButtonDefaults.ContentPadding.calculateRightPadding(LayoutDirection.Ltr)
    val secondaryPadding = PaddingValues(horizontal = 12.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val secondaryWidth = maxOf(48.dp, with(density) { secondaryTextWidth.toDp() } + 24.dp)
        val fitsSideBySide = with(density) { primaryTextWidth.toDp() } + primaryPadding + 8.dp + secondaryWidth <= maxWidth
        val primary: @Composable (Modifier) -> Unit = { modifier ->
            FpButton(onPrimary, modifier, enabled, border = null,
                colors = ButtonDefaults.buttonColors(containerColor = colors.fill, contentColor = colors.onFill)) {
                Text(primaryLabel, Modifier.fillMaxWidth(), style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
        val secondary: @Composable (Modifier) -> Unit = { modifier ->
            TextButton(onSecondary, modifier.heightIn(min = 48.dp), contentPadding = secondaryPadding) {
                Text(secondaryLabel, Modifier.fillMaxWidth(), style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
        if (fitsSideBySide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            primary(Modifier.weight(1f))
            secondary(Modifier.width(secondaryWidth))
        } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            primary(Modifier.fillMaxWidth())
            secondary(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun predictionText(prediction: PredictionState, today: LocalDate, locale: Locale): String = when (prediction) {
    PredictionState.NoData -> stringResource(R.string.today_welcome)
    is PredictionState.NeedMoreData -> stringResource(R.string.today_need_more)
    is PredictionState.Range -> stringResource(R.string.today_range, formatPredictionRange(prediction.earliest, prediction.latest, today, locale))
    is PredictionState.Varies -> stringResource(R.string.today_varies, prediction.minLength, prediction.maxLength)
    is PredictionState.RangePassed -> stringResource(R.string.today_range_passed, prediction.daysPassed)
    PredictionState.Paused -> stringResource(R.string.today_paused)
    is PredictionState.ScheduledBreak -> stringResource(R.string.today_scheduled_break, formatPredictionRange(prediction.range.start, prediction.range.endInclusive, today, locale))
    is PredictionState.ContinuousPill -> stringResource(R.string.today_continuous_pill)
    PredictionState.NeedsPillRhythm -> stringResource(R.string.today_needs_pill_rhythm)
    is PredictionState.Menopause -> prediction.fullMonthsSinceLastEnd?.let { pluralStringResource(R.plurals.today_menopause_months, it, it) }
        ?: prediction.lastEnd?.let { stringResource(R.string.today_last_period_end, formatSpokenDate(it, today, locale)) }
        ?: stringResource(R.string.today_menopause_no_period)
}

/** Same shape as the cycle header before the first period, so the calendar does not jump after the first entry. */
@Composable
private fun EmptyCycleHeader(label: String) {
    val t = LocalDaylight.current
    Row(Modifier.fillMaxWidth().heightIn(min = 110.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.widthIn(min = 100.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.wordmark_stop), Modifier.alignByBaseline().clearAndSetSemantics {},
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = DmSans), color = t.accent.accent)
            }
        }
        Canvas(Modifier.weight(1f).height(16.dp).clearAndSetSemantics {}) {
            val y = size.height / 2
            drawLine(t.line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
            drawCircle(t.line, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width - 6.dp.toPx(), y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}
