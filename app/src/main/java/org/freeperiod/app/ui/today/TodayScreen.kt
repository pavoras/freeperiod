package org.freeperiod.app.ui.today

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import org.freeperiod.app.ui.components.FpCard
import org.freeperiod.app.ui.components.MedicalDisclaimer
import org.freeperiod.app.ui.theme.LocalDaylight
import org.freeperiod.engine.PeriodError
import org.freeperiod.engine.PredictionState

/** Everything on Today beyond the basic buttons, in one bundle so the screen keeps a short signature. */
class TodayHandlers(
    val onLongPressDay: (LocalDate) -> Unit = {},
    val onPickDay: (LocalDate) -> Unit = {},
    /** Saves only the picked day (false) or a period that is still going on (true). */
    val onPickSingle: (Boolean) -> Unit = {},
    val onCancelPick: () -> Unit = {},
    val onFoldCard: (Boolean) -> Unit = {},
    val onAnswer: (HomeQuestion, Boolean) -> Unit = { _, _ -> },
    val onTipsSeen: () -> Unit = {},
    val onEndSavedShown: () -> Unit = {},
    val onYearView: (Boolean) -> Unit = {},
    val onOpenMonth: (YearMonth) -> Unit = {},
)

@Composable
fun TodayScreen(state: TodayUiState, onStartPeriod: () -> Unit, onConfirmEnd: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit, onPausePredictions: () -> Unit, onMonthChange: (YearMonth) -> Unit,
    onDismissError: () -> Unit, modifier: Modifier = Modifier,
    onUndoEnd: ((PeriodEndReceipt) -> Unit)? = null, onRingChange: (Boolean) -> Unit = {}, onJumpToToday: () -> Unit = {},
    handlers: TodayHandlers = TodayHandlers()) {
    val t = LocalDaylight.current
    var disclaimer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val haptic = LocalHapticFeedback.current
    var seenStart by remember { mutableStateOf(state.startedPeriodId) }
    val receipt = state.endSaved
    val savedText = receipt?.let { stringResource(R.string.period_end_saved, formatSpokenDate(it.after.end!!, state.today, locale)) }
    val undoText = stringResource(R.string.undo)
    val undo by rememberUpdatedState(onUndoEnd)
    val latest by rememberUpdatedState(state)
    LaunchedEffect(state.startedPeriodId) {
        if (state.startedPeriodId != null && state.startedPeriodId != seenStart) {
            seenStart = state.startedPeriodId
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(receipt) {
        if (receipt == null) snackbar.currentSnackbarData?.dismiss()
        // Once per saved end: not again each time Today opens.
        else if (!latest.endSavedShown) {
            handlers.onEndSavedShown()
            if (snackbar.showSnackbar(requireNotNull(savedText), if (undo != null) undoText else null, withDismissAction = true,
                    duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo?.invoke(receipt)
        }
    }
    // While a period is picked in the calendar, a tap sets its other day instead of opening the day.
    val dayClick: (LocalDate) -> Unit = if (state.pick != null) handlers.onPickDay else onDayClick
    val longPress: (LocalDate) -> Unit = { day ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        handlers.onLongPressDay(day)
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
                                latest.endSaved?.after?.end?.let(onDayClick)
                            }) { Text(stringResource(R.string.edit_period_end), color = t.accent.onContainer) }
                            TextButton(onClick = data::dismiss) { Text(stringResource(R.string.dismiss), color = t.accent.onContainer) }
                        }
                    }
                }
            }
        }) { padding ->
        // Calendar and cycle ring side by side: swipe, or tap the dots. The last view stays.
        val pager = rememberPagerState(if (state.ring) 1 else 0) { 2 }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().padding(padding).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                // The name never wraps.
                Row(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wordmark_name), style = MaterialTheme.typography.headlineSmall, softWrap = false)
                    Text(stringResource(R.string.wordmark_stop), Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.headlineSmall, color = t.accent.accent, softWrap = false)
                }
                // Month or whole year; only for the calendar.
                // A labelled switch, so it is clear what it does.
                if (!state.loading && pager.currentPage == 0) OutlinedButton(onClick = { handlers.onYearView(!state.yearView) },
                    Modifier.padding(start = 8.dp).heightIn(min = 44.dp), border = BorderStroke(1.dp, t.line),
                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Icon(painterResource(if (state.yearView) R.drawable.ic_fp_today else R.drawable.ic_fp_year), null,
                        Modifier.size(22.dp), tint = t.muted)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.yearView) R.string.view_month else R.string.view_year),
                        style = MaterialTheme.typography.labelLarge, color = t.ink)
                } else Spacer(Modifier.width(12.dp))
            }
            if (state.loading) CircularProgressIndicator(Modifier.padding(24.dp))
            else {
                val ring by rememberUpdatedState(state.ring)
                val ringChange by rememberUpdatedState(onRingChange)
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { page -> if ((page == 1) != ring) ringChange(page == 1) }
                }
                LaunchedEffect(state.ring) {
                    val target = if (state.ring) 1 else 0
                    if (pager.settledPage != target && !pager.isScrollInProgress) pager.scrollToPage(target)
                }
                state.pick?.let { pick -> PickBanner(pick, state.today, locale, handlers, Modifier.padding(horizontal = 12.dp)) }
                // With large text the card starts folded, so the calendar stays in sight; it opens for this visit on request.
                val large = LocalDensity.current.fontScale >= 1.3f
                var openedLarge by rememberSaveable { mutableStateOf(false) }
                val folded = state.cardFolded || (large && !openedLarge)
                val onAnswer = handlers.onAnswer
                var year by rememberSaveable { mutableIntStateOf(state.month.year) }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { it }) { page ->
                    // One screen: only the calendar scrolls and the legend always stays visible (see OneScreenColumn).
                    if (page == 0) OneScreenColumn(calendarMinHeight(), modifier = Modifier.fillMaxSize(), top = {
                        when {
                            state.pick != null -> Unit
                            // The year gets the whole room: no card at all.
                            state.yearView -> Unit
                            folded -> FoldedCard(state, onStartPeriod, onConfirmEnd, { onDayClick(state.today) }, onPausePredictions,
                                { openedLarge = true; handlers.onFoldCard(false) }, Modifier.padding(horizontal = 12.dp), onAnswer)
                            else -> TodayCard(state, onStartPeriod, onConfirmEnd, { onDayClick(state.today) }, onPausePredictions,
                                Modifier.padding(horizontal = 12.dp), onJumpToToday, onFold = { openedLarge = false; handlers.onFoldCard(true) },
                                onAnswer = onAnswer)
                        }
                    }, flex = {
                        if (state.yearView) YearCalendar(year, state.days, { year = it }, handlers.onOpenMonth,
                            Modifier.padding(horizontal = 12.dp))
                        else MonthCalendar(state.month, state.days, onMonthChange, dayClick, Modifier.padding(horizontal = 12.dp),
                            scheduledBreak = state.prediction is PredictionState.ScheduledBreak, jump = state.todayJump,
                            jumpMonth = state.jumpMonth, onDayLongClick = longPress, picked = state.pick?.start)
                    }, bottom = {})
                    else CycleRingPage(state, onStartPeriod, onConfirmEnd, dayClick, onPausePredictions,
                        onShowToday = { scope.launch { pager.animateScrollToPage(0) }; onJumpToToday() }, onDayLongClick = longPress)
                }
                // The year view starts at the year of the month on screen.
                LaunchedEffect(state.yearView) { if (state.yearView) year = state.month.year }
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
    if (state.showTips && !state.loading) TipsDialog(handlers.onTipsSeen)
}

/** Picking a period in the calendar: what to tap next, plus the ways out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickBanner(pick: RangePick, today: LocalDate, locale: java.util.Locale, handlers: TodayHandlers, modifier: Modifier) {
    val t = LocalDaylight.current
    FpCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(when {
                pick.start == null -> stringResource(R.string.pick_first_day)
                pick.periodId != null -> stringResource(R.string.pick_end_day)
                else -> stringResource(R.string.pick_last_day, formatSpokenDate(pick.start, today, locale))
            }, style = MaterialTheme.typography.bodyLarge)
            // Compact buttons, so all three fit in one row on a phone.
            val pad = PaddingValues(horizontal = 8.dp)
            val label = MaterialTheme.typography.labelMedium
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = handlers.onCancelPick, contentPadding = pad) {
                    Text(stringResource(R.string.cancel), color = t.muted, style = label)
                }
                if (pick.start != null && pick.periodId == null) {
                    // A recent start can still be going on, unless a later period began since.
                    if (pick.canContinue) TextButton(onClick = { handlers.onPickSingle(true) }, contentPadding = pad) {
                        Text(stringResource(R.string.pick_still_going), style = label)
                    }
                    TextButton(onClick = { handlers.onPickSingle(false) }, contentPadding = pad) {
                        Text(stringResource(R.string.pick_only_this_day), style = label)
                    }
                }
            }
        }
    }
}

/** The cycle card folded up: only its buttons (and the question, if any), so the calendar gets the screen. */
@Composable
private fun FoldedCard(state: TodayUiState, onStartPeriod: () -> Unit, onConfirmEnd: (LocalDate) -> Unit, onLogToday: () -> Unit,
    onPausePredictions: () -> Unit, onUnfold: (() -> Unit)?, modifier: Modifier, onAnswer: (HomeQuestion, Boolean) -> Unit) {
    FpCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (onUnfold == null) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.question?.let { QuestionLine(it) }
            TodayPrimaryActions(state, onStartPeriod, onConfirmEnd, onLogToday, onPausePredictions, onAnswer = onAnswer)
            onUnfold?.let { FoldHandle(folded = true, it) }
        }
    }
}

/** A chevron across the bottom of the card: up folds the card away, down brings it back. */
@Composable
internal fun FoldHandle(folded: Boolean, onClick: () -> Unit) {
    val t = LocalDaylight.current
    val label = stringResource(if (folded) R.string.card_unfold else R.string.card_fold)
    // The whole width is the tap target, so it is easy to hit without a tall row.
    Box(Modifier.fillMaxWidth().height(36.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(painterResource(if (folded) R.drawable.ic_fp_down else R.drawable.ic_fp_up), null, tint = t.muted)
    }
}

/** A few lines once, after setup: what is not obvious on first sight. */
@Composable
private fun TipsDialog(onDone: () -> Unit) {
    val t = LocalDaylight.current
    AlertDialog(onDismissRequest = onDone, containerColor = t.surface, textContentColor = t.ink,
        title = { Text(stringResource(R.string.tips_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(R.string.tips_swipe, R.string.tips_long_press, R.string.tips_fold, R.string.tips_tabs, R.string.tips_retap).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.got_it)) } })
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
