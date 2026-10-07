package org.freeperiod.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.nav.NavigationIcons

@Composable
fun MonthCalendar(
    month: YearMonth,
    days: Map<LocalDate, DayMarks>,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = LocalConfiguration.current.locales[0],
) {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(NavigationIcons.Previous, stringResource(R.string.previous_month))
            }
            Text(monthLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(NavigationIcons.Next, stringResource(R.string.next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = firstDay.plus(index.toLong())
                Text(day.getDisplayName(TextStyle.SHORT, locale),
                    Modifier.weight(1f).padding(vertical = 8.dp).clearAndSetSemantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                    }, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = row * 7 + column - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        CalendarDay(date, days[date] ?: DayMarks(false, false, false, false),
                            locale, { onDayClick(date) }, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f).height(52.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, marks: DayMarks, locale: Locale, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = buildList {
        add(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
        if (marks.period) add(stringResource(R.string.calendar_period))
        if (marks.predicted) add(stringResource(R.string.calendar_predicted))
        if (marks.logged) add(stringResource(R.string.calendar_logged))
        if (marks.today) add(stringResource(R.string.calendar_today))
    }.joinToString(", ")
    Box(modifier.height(52.dp).clickable(onClickLabel = stringResource(R.string.open_day), onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, 23.dp.toPx())
            if (marks.period) drawCircle(colors.primary, 17.dp.toPx(), center)
            if (marks.today) drawCircle(colors.onSurface, 20.dp.toPx(), center,
                style = Stroke(1.5.dp.toPx()))
            if (marks.predicted) drawCircle(colors.primary, 23.dp.toPx(), center,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            if (marks.logged) drawCircle(colors.onSurface, 2.dp.toPx(), Offset(center.x, 48.dp.toPx()))
        }
        Text(date.dayOfMonth.toString(), modifier = Modifier.offset(y = (-3).dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            color = if (marks.period) colors.onPrimary else colors.onSurface)
    }
}
