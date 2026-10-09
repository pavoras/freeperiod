package org.freeperiod.app.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.LocalDaylight

/** Months the Today calendar can scroll through: two years back, one year ahead. */
internal fun calendarMonths(current: YearMonth): List<YearMonth> = (-24L..12L).map { current.plusMonths(it) }

/** One scrollable row: a month title or one week of that month (days of other months stay empty). */
private sealed interface CalendarRow {
    val month: YearMonth
    data class Title(override val month: YearMonth) : CalendarRow
    data class Week(override val month: YearMonth, val index: Int) : CalendarRow
}

private fun calendarRows(months: List<YearMonth>, locale: Locale): List<CalendarRow> = months.flatMap { month ->
    val offset = (month.atDay(1).dayOfWeek.value - WeekFields.of(locale).firstDayOfWeek.value + 7) % 7
    listOf(CalendarRow.Title(month)) + (0 until (offset + month.lengthOfMonth() + 6) / 7).map { CalendarRow.Week(month, it) }
}

/**
 * Free vertical scrolling through months (owner: "scroll instead of click, no snapping to whole
 * months"). A fling settles so that a row (week or month title) sits at the top edge. Opens on [month].
 */
/** Weekday header, a month title and three week rows: below this the Today screen scrolls instead. */
@Composable
internal fun calendarMinHeight(): Dp {
    val density = LocalDensity.current
    val cell = maxOf(48.dp, 40.dp * density.fontScale)
    val titleHeight = with(density) { maxOf(48.dp, MaterialTheme.typography.titleLarge.lineHeight.toDp() + 16.dp) }
    return 12.dp + cell + titleHeight + cell * 3
}

@Composable
fun MonthCalendar(
    month: YearMonth,
    days: Map<LocalDate, DayMarks>,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = LocalConfiguration.current.locales[0],
    scheduledBreak: Boolean = false,
) {
    val t = LocalDaylight.current
    val anchor = remember { month }
    val first = days.keys.minOrNull()
    val last = days.keys.maxOrNull()
    val months = remember(first, last) {
        if (first == null || last == null) calendarMonths(anchor)
        else generateSequence(YearMonth.from(first)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(last) }.toList()
    }
    val rows = remember(months, locale) { calendarRows(months, locale) }
    val titleIndex = { shown: YearMonth -> rows.indexOfFirst { it is CalendarRow.Title && it.month == shown } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = titleIndex(anchor).coerceAtLeast(0))
    val latestChange by rememberUpdatedState(onMonthChange)
    LaunchedEffect(list, rows) {
        snapshotFlow { rows.getOrNull(list.firstVisibleItemIndex)?.month }.distinctUntilChanged().collect { it?.let(latestChange) }
    }
    val scope = rememberCoroutineScope()
    val previous = stringResource(R.string.previous_month)
    val next = stringResource(R.string.next_month)
    val density = LocalDensity.current
    val cell = maxOf(48.dp, 40.dp * density.fontScale)
    val titleHeight = with(density) { maxOf(48.dp, MaterialTheme.typography.titleLarge.lineHeight.toDp() + 16.dp) }
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = firstDay.plus(index.toLong())
                Text(day.getDisplayName(TextStyle.SHORT, locale),
                    Modifier.weight(1f).padding(vertical = 4.dp).clearAndSetSemantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                    }, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = t.muted)
            }
        }
        HorizontalDivider(color = t.line)
        LazyColumn(Modifier.fillMaxWidth().weight(1f).semantics {
            customActions = listOf(
                CustomAccessibilityAction(previous) {
                    val shown = rows.getOrNull(list.firstVisibleItemIndex)?.month ?: anchor
                    scope.launch { list.animateScrollToItem(titleIndex(shown.minusMonths(1)).coerceAtLeast(0)) }; true
                },
                CustomAccessibilityAction(next) {
                    val shown = rows.getOrNull(list.firstVisibleItemIndex)?.month ?: anchor
                    scope.launch { titleIndex(shown.plusMonths(1)).takeIf { it >= 0 }?.let { list.animateScrollToItem(it) } }; true
                })
        }, state = list, flingBehavior = rememberSnapFlingBehavior(list)) {
            items(rows.size, key = { rows[it].toString() }) { index ->
                when (val row = rows[index]) {
                    is CalendarRow.Title -> Text(row.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                        Modifier.fillMaxWidth().height(titleHeight).wrapContentHeight(Alignment.Bottom)
                            .padding(start = 8.dp, bottom = 6.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    is CalendarRow.Week -> WeekRow(row, days, locale, scheduledBreak, onDayClick, cell)
                }
            }
        }
    }
}

@Composable
private fun WeekRow(row: CalendarRow.Week, days: Map<LocalDate, DayMarks>, locale: Locale, scheduledBreak: Boolean,
    onDayClick: (LocalDate) -> Unit, cell: androidx.compose.ui.unit.Dp) {
    val month = row.month
    val offset = (month.atDay(1).dayOfWeek.value - WeekFields.of(locale).firstDayOfWeek.value + 7) % 7
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { column ->
            val number = row.index * 7 + column - offset + 1
            if (number in 1..month.lengthOfMonth()) {
                val date = month.atDay(number)
                CalendarDay(date, days[date] ?: DayMarks(false, false, false, false), locale,
                    { onDayClick(date) }, Modifier.weight(1f), scheduledBreak)
            } else Spacer(Modifier.weight(1f).height(cell))
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, marks: DayMarks, locale: Locale, onClick: () -> Unit, modifier: Modifier, scheduledBreak: Boolean) {
    val t = LocalDaylight.current
    val colors = remember(t) { todayFillColors(t) }
    val fill by animateColorAsState(if (marks.period) colors.fill else t.background, tween(160), label = "periodFill")
    val numberColor by animateColorAsState(if (marks.period) colors.onFill else t.ink, tween(160), label = "dayText")
    val cellHeight = maxOf(48.dp, 40.dp * LocalDensity.current.fontScale)
    val radius = maxOf(16.dp, 12.dp * LocalDensity.current.fontScale)
    val description = buildList {
        add(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
        if (marks.period) add(stringResource(R.string.calendar_period))
        if (marks.predicted) add(stringResource(if (scheduledBreak) R.string.calendar_scheduled_break else R.string.calendar_predicted))
        if (marks.higherChance) add(stringResource(R.string.calendar_pregnancy_chance))
        if (marks.logged) add(stringResource(R.string.calendar_logged))
        if (marks.today) add(stringResource(R.string.calendar_today))
    }.joinToString(", ")
    Box(modifier.height(cellHeight).clickable(onClickLabel = stringResource(R.string.open_day), onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2 - 2.dp.toPx())
            val r = radius.toPx()
            drawCircle(fill, r, center)
            if (marks.predicted) drawCircle(t.predicted, r + if (marks.period) 3.dp.toPx() else 0f, center,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            if (marks.today) {
                drawCircle(t.accent.todayRing, r + 2.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            if (marks.higherChance) {
                val y = center.y + r - 4.dp.toPx()
                drawLine(if (marks.period) colors.onFill else t.muted,
                    Offset(center.x - 3.dp.toPx(), y), Offset(center.x + 3.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            if (marks.logged) drawCircle(t.ink, 2.dp.toPx(), Offset(center.x, size.height - 3.dp.toPx()))
        }
        Text(date.dayOfMonth.toString(), modifier = Modifier.offset(y = (-2).dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (marks.today) FontWeight.Medium else FontWeight.Normal, color = numberColor)
    }
}
