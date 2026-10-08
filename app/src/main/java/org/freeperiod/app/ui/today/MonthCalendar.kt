package org.freeperiod.app.ui.today

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
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
    val monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthLabel, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_fp_previous), stringResource(R.string.previous_month))
            }
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_fp_next), stringResource(R.string.next_month))
            }
        }
        HorizontalDivider(color = t.line)
        AnimatedContent(targetState = CalendarPage(month, days), contentKey = { it.month }, label = "month", transitionSpec = {
            val direction = if (targetState.month > initialState.month) 1 else -1
            (slideInHorizontally(tween(180)) { direction * it / 5 } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(tween(180)) { -direction * it / 5 } + fadeOut(tween(180)))
        }) { shown ->
            MonthGrid(shown.month, shown.days, locale, scheduledBreak, onDayClick)
        }
    }
}

private data class CalendarPage(val month: YearMonth, val days: Map<LocalDate, DayMarks>)

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
        repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = row * 7 + column - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        CalendarDay(date, days[date] ?: DayMarks(false, false, false, false), locale,
                            { onDayClick(date) }, Modifier.weight(1f), scheduledBreak)
                    } else Spacer(Modifier.weight(1f).height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, marks: DayMarks, locale: Locale, onClick: () -> Unit, modifier: Modifier, scheduledBreak: Boolean) {
    val t = LocalDaylight.current
    val fill by animateColorAsState(if (marks.period) t.accent.periodFill else t.background, tween(160), label = "periodFill")
    val numberColor by animateColorAsState(if (marks.period) t.accent.onAccent else t.ink, tween(160), label = "dayText")
    val cellHeight = maxOf(48.dp, 40.dp * LocalDensity.current.fontScale)
    val radius = maxOf(16.dp, 12.dp * LocalDensity.current.fontScale)
    val description = buildList {
        add(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
        if (marks.period) add(stringResource(R.string.calendar_period))
        if (marks.predicted) add(stringResource(if (scheduledBreak) R.string.calendar_scheduled_break else R.string.calendar_predicted))
        if (marks.logged) add(stringResource(R.string.calendar_logged))
        if (marks.today) add(stringResource(R.string.calendar_today))
    }.joinToString(", ")
    Box(modifier.height(cellHeight).clickable(onClickLabel = stringResource(R.string.open_day), onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2 - 2.dp.toPx())
            val r = radius.toPx()
            drawCircle(fill, r, center)
            if (marks.period) drawCircle(t.accent.periodBorder, r, center, style = Stroke(1.dp.toPx()))
            if (marks.predicted) drawCircle(t.predicted, r + if (marks.period) 3.dp.toPx() else 0f, center,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            if (marks.today) {
                drawCircle(t.accent.todayRing, r + 2.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                if (marks.period) drawCircle(t.accent.onAccent, r - 1.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
            if (marks.logged) drawCircle(t.ink, 2.dp.toPx(), Offset(center.x, size.height - 3.dp.toPx()))
        }
        Text(date.dayOfMonth.toString(), modifier = Modifier.offset(y = (-2).dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (marks.today) FontWeight.Medium else FontWeight.Normal, color = numberColor)
    }
}
