package org.freeperiod.app.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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

/**
 * Swipe up/down to move between months (owner: "scroll instead of click"). Each page has the same
 * height (always six week rows), so the page under the calendar never jumps.
 */
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
    val anchor = remember { month }
    val first = days.keys.minOrNull()
    val last = days.keys.maxOrNull()
    val months = remember(first, last) {
        if (first == null || last == null) calendarMonths(anchor)
        else generateSequence(YearMonth.from(first)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(last) }.toList()
    }
    val pager = rememberPagerState(initialPage = months.indexOf(month).coerceAtLeast(0)) { months.size }
    val latestChange by rememberUpdatedState(onMonthChange)
    LaunchedEffect(pager, months) {
        snapshotFlow { pager.settledPage }.collect { page -> months.getOrNull(page)?.let(latestChange) }
    }
    LaunchedEffect(month) {
        val index = months.indexOf(month)
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }
    val scope = rememberCoroutineScope()
    val previous = stringResource(R.string.previous_month)
    val next = stringResource(R.string.next_month)
    val density = LocalDensity.current
    val title = MaterialTheme.typography.titleLarge
    val weekday = MaterialTheme.typography.labelSmall
    val cell = maxOf(48.dp, 40.dp * density.fontScale)
    val pageHeight = with(density) { maxOf(48.dp, title.lineHeight.toDp() + 16.dp) + 1.dp + weekday.lineHeight.toDp() + 8.dp } + cell * 6
    VerticalPager(pager, modifier.fillMaxWidth().height(pageHeight).semantics {
        customActions = listOf(
            CustomAccessibilityAction(previous) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }; true },
            CustomAccessibilityAction(next) { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }; true })
    }, key = { months[it].toString() }) { page ->
        val shown = months[page]
        Column(Modifier.fillMaxSize()) {
            Text(shown.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                    .semantics { heading() }, style = title)
            HorizontalDivider(color = LocalDaylight.current.line)
            MonthGrid(shown, days, locale, scheduledBreak, onDayClick)
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, days: Map<LocalDate, DayMarks>, locale: Locale, scheduledBreak: Boolean,
    onDayClick: (LocalDate) -> Unit) {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    Column {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = firstDay.plus(index.toLong())
                Text(day.getDisplayName(TextStyle.SHORT, locale),
                    Modifier.weight(1f).padding(vertical = 4.dp).clearAndSetSemantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                    }, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = LocalDaylight.current.muted)
            }
        }
        // Always six week rows, so every month page has the same height.
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = row * 7 + column - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        CalendarDay(date, days[date] ?: DayMarks(false, false, false, false), locale,
                            { onDayClick(date) }, Modifier.weight(1f), scheduledBreak)
                    } else Spacer(Modifier.weight(1f).height(maxOf(48.dp, 40.dp * LocalDensity.current.fontScale)))
                }
            }
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
