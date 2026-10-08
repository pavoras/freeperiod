package org.freeperiod.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.*
import java.time.temporal.WeekFields
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpSwitchRow
import org.freeperiod.app.ui.theme.*

@Composable
internal fun PastPeriodsPicker(state: OnboardingUiState, actions: OnboardingActions) {
    val locale = LocalConfiguration.current.locales[0]
    val firstWeekday = WeekFields.of(locale).firstDayOfWeek
    val months = remember(state.today) { (5 downTo 0).map { YearMonth.from(state.today).minusMonths(it.toLong()) } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = months.lastIndex)
    val boxes = remember { mutableMapOf<LocalDate, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableIntStateOf(0) }
    var draggingFrom by remember { mutableStateOf<LocalDate?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val edge = with(LocalDensity.current) { FpSpacing.touch.toPx() }
    val latestRange by rememberUpdatedState(actions.range)
    val enabled = !state.busy && !state.periodsCommitted
    fun selectAt(position: Offset) {
        val start = draggingFrom ?: return
        boxes.entries.firstOrNull { it.value.contains(position + origin) }?.key?.takeIf { it <= state.today }?.let { latestRange(start, it) }
    }
    LaunchedEffect(draggingFrom) {
        while (draggingFrom != null) {
            withFrameNanos { }
            val delta = when {
                pointer.y < edge -> -edge / 3
                pointer.y > height - edge -> edge / 3
                else -> 0f
            }
            if (delta != 0f) { list.scrollBy(delta); selectAt(pointer) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.onboarding_past_periods), Modifier.padding(horizontal = FpSpacing.screen), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.onboarding_picker_help), Modifier.padding(horizontal = FpSpacing.screen), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = actions.importPeriods, enabled = !state.busy, modifier = Modifier.padding(horizontal = FpSpacing.screen)) {
            Text(stringResource(R.string.import_from_app))
        }
        Box(Modifier.padding(horizontal = FpSpacing.screen)) {
            FpSwitchRow(stringResource(R.string.onboarding_range_mode), state.rangeMode, enabled, actions.rangeMode)
        }
        state.rangeStart?.let { Text(stringResource(R.string.onboarding_range_end), Modifier.padding(horizontal = FpSpacing.screen)) }
        if (state.periodsCommitted) Text(stringResource(R.string.onboarding_periods_saved), Modifier.padding(horizontal = FpSpacing.screen))
        // No list scrolling while a drag selects days: the list would claim the vertical move.
        LazyColumn(state = list, contentPadding = PaddingValues(horizontal = FpSpacing.gap), userScrollEnabled = draggingFrom == null, modifier = Modifier.weight(1f)
            .testTag("past-periods-calendar").onSizeChanged { height = it.height }.onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(state.today, enabled) {
                if (enabled) detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        pointer = position
                        draggingFrom = boxes.entries.firstOrNull { it.value.contains(position + origin) }?.key?.takeIf { it <= state.today }
                        selectAt(position)
                    }, onDragEnd = { draggingFrom = null }, onDragCancel = { draggingFrom = null },
                    onDrag = { change, _ -> if (draggingFrom != null) { change.consume(); pointer = change.position; selectAt(pointer) } })
            }) {
            items(months.size, key = { months[it].toString() }) { index ->
                val month = months[index]
                Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), Modifier.padding(vertical = FpSpacing.section), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { index -> val day = firstWeekday.plus(index.toLong())
                        Text(day.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = LocalDaylight.current.muted) }
                }
                val offset = Math.floorMod(month.atDay(1).dayOfWeek.value - firstWeekday.value, 7)
                val rows = (offset + month.lengthOfMonth() + 6) / 7
                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val number = row * 7 + column - offset + 1
                            if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(FpSpacing.touch))
                            else {
                                val date = month.atDay(number)
                                val chosen = date in state.selectedDays || date == state.rangeStart
                                val tones = LocalDaylight.current
                                DisposableEffect(date) { onDispose { boxes.remove(date) } }
                                Box(Modifier.weight(1f).height(FpSpacing.touch).testTag("picker-day-$date")
                                    .onGloballyPositioned { boxes[date] = it.boundsInRoot() }
                                    .background(if (chosen) tones.accent.selectedChipBackground else tones.background, FpShapes.chip)
                                    .selectable(chosen, enabled && date <= state.today, Role.Button, onClick = { actions.day(date) })
                                    .semantics { contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) },
                                    contentAlignment = Alignment.Center) {
                                    Text(number.toString(), style = MaterialTheme.typography.bodyMedium,
                                        color = if (chosen) tones.accent.selectedChipText else if (date > state.today) tones.muted else tones.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
