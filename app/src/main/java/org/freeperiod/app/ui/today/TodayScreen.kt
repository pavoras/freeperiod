package org.freeperiod.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.MedicalDisclaimer
import org.freeperiod.app.ui.theme.LocalDaylight
import org.freeperiod.engine.PeriodError
import org.freeperiod.engine.PredictionState

@Composable
fun TodayScreen(state: TodayUiState, onStartPeriod: () -> Unit, onConfirmEnd: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit, onPausePredictions: () -> Unit, onMonthChange: (YearMonth) -> Unit,
    onDismissError: () -> Unit, modifier: Modifier = Modifier,
    onUndoEnd: ((PeriodEndReceipt) -> Unit)? = null, onRingChange: (Boolean) -> Unit = {}, onJumpToToday: () -> Unit = {}) {
    val t = LocalDaylight.current
    var disclaimer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val haptic = LocalHapticFeedback.current
    var seenStart by remember { mutableStateOf(state.startedPeriodId) }
    var shownEnd by remember { mutableStateOf<PeriodEndReceipt?>(null) }
    val receipt = state.endSaved
    val savedText = receipt?.let { stringResource(R.string.period_end_saved, formatSpokenDate(it.after.end!!, state.today, locale)) }
    val undoText = stringResource(R.string.undo)
    val undo by rememberUpdatedState(onUndoEnd)
    LaunchedEffect(state.startedPeriodId) {
        if (state.startedPeriodId != null && state.startedPeriodId != seenStart) {
            seenStart = state.startedPeriodId
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(receipt) {
        if (receipt == null) {
            shownEnd = null
            snackbar.currentSnackbarData?.dismiss()
        } else if (receipt != shownEnd) {
            shownEnd = receipt
            if (snackbar.showSnackbar(requireNotNull(savedText), if (undo != null) undoText else null, withDismissAction = true,
                    duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo?.invoke(receipt)
        }
    }
    Scaffold(modifier, containerColor = t.background, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(Modifier.padding(12.dp), containerColor = t.accent.container, contentColor = t.accent.onContainer) {
                    Column {
                        Text(data.visuals.message)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            if (onUndoEnd != null) TextButton(onClick = data::performAction, enabled = !state.writing) { Text(undoText, color = t.accent.onContainer) }
                            TextButton(onClick = {
                                data.dismiss()
                                shownEnd?.after?.end?.let(onDayClick)
                            }) { Text(stringResource(R.string.edit_period_end), color = t.accent.onContainer) }
                            TextButton(onClick = data::dismiss) { Text(stringResource(R.string.dismiss), color = t.accent.onContainer) }
                        }
                    }
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wordmark_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.wordmark_stop), Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.headlineSmall, color = t.accent.accent)
                }
                Text(stringResource(R.string.today_space), Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall,
                    color = t.muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (state.loading) CircularProgressIndicator(Modifier.padding(24.dp))
            else {
                // Calendar and cycle ring side by side: swipe, or tap the dots. The last view stays.
                val pager = rememberPagerState(if (state.ring) 1 else 0) { 2 }
                val scope = rememberCoroutineScope()
                val ring by rememberUpdatedState(state.ring)
                val ringChange by rememberUpdatedState(onRingChange)
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { page -> if ((page == 1) != ring) ringChange(page == 1) }
                }
                LaunchedEffect(state.ring) {
                    val target = if (state.ring) 1 else 0
                    if (pager.settledPage != target && !pager.isScrollInProgress) pager.scrollToPage(target)
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { it }) { page ->
                    // One screen: only the calendar scrolls and the legend always stays visible (see OneScreenColumn).
                    if (page == 0) OneScreenColumn(calendarMinHeight(), modifier = Modifier.fillMaxSize(), top = {
                        TodayCard(state, onStartPeriod, onConfirmEnd, { onDayClick(state.today) }, onPausePredictions,
                            Modifier.padding(horizontal = 12.dp), onJumpToToday)
                    }, flex = {
                        MonthCalendar(state.month, state.days, onMonthChange, onDayClick, Modifier.padding(horizontal = 12.dp),
                            scheduledBreak = state.prediction is PredictionState.ScheduledBreak, jump = state.todayJump)
                    }, bottom = {})
                    else CycleRingPage(state, onStartPeriod, onConfirmEnd, onDayClick, onPausePredictions,
                        onShowToday = { scope.launch { pager.animateScrollToPage(0) }; onJumpToToday() })
                }
                ViewDots(pager.currentPage, Modifier.padding(vertical = 4.dp)) { page -> scope.launch { pager.animateScrollToPage(page) } }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = t.line)
                TodayLegend(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    scheduledBreak = state.prediction is PredictionState.ScheduledBreak,
                    higherChance = state.fertileWindow != null,
                    possible = state.prediction is PredictionState.Range,
                    onEstimateInfo = { disclaimer = true })
            }
            state.error?.let { error ->
                val message = when (error.periodError) {
                    PeriodError.OVERLAP -> R.string.error_period_overlap
                    PeriodError.END_BEFORE_START -> R.string.error_period_end_before_start
                    PeriodError.START_IN_FUTURE -> R.string.error_period_start_future
                    PeriodError.END_IN_FUTURE -> R.string.error_period_end_future
                    null -> R.string.error_storage
                }
                Text(stringResource(message), Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
    if (disclaimer) AlertDialog(onDismissRequest = { disclaimer = false }, containerColor = t.surface, textContentColor = t.ink,
        text = { MedicalDisclaimer(Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { disclaimer = false }) { Text(stringResource(R.string.got_it)) } })
}

/** Two dots under the views: where you are, and a tap target for those who don't swipe. */
@Composable
private fun ViewDots(page: Int, modifier: Modifier = Modifier, onPage: (Int) -> Unit) {
    val t = LocalDaylight.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        listOf(R.string.home_view_calendar, R.string.home_view_ring).forEachIndexed { index, label ->
            val name = stringResource(label)
            Canvas(Modifier.size(width = 44.dp, height = 32.dp)
                .selectable(page == index, role = Role.Tab, onClick = { onPage(index) })
                .semantics { contentDescription = name }) {
                val width = if (page == index) 16.dp.toPx() else 6.dp.toPx()
                val height = 6.dp.toPx()
                drawRoundRect(if (page == index) t.accent.periodBorder else t.line, Offset(center.x - width / 2, center.y - height / 2),
                    Size(width, height), CornerRadius(height / 2))
            }
        }
    }
}
