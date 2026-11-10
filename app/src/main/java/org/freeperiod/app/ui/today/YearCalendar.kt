package org.freeperiod.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.LocalDaylight

/**
 * The whole [year] at a glance: twelve small months with period, expected and today marks. A tap on a month opens it
 * in the scrolling calendar. Only years the calendar covers can be shown.
 */
@Composable
internal fun YearCalendar(year: Int, days: Map<LocalDate, DayMarks>, onYear: (Int) -> Unit, onMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier, locale: Locale = LocalConfiguration.current.locales[0]) {
    val t = LocalDaylight.current
    val years = remember(days) { days.keys.map { it.year }.toSortedSet() }
    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onYear(year - 1) }, enabled = (year - 1) in years) {
                Icon(painterResource(R.drawable.ic_fp_previous), stringResource(R.string.previous_year))
            }
            Text(year.toString(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center)
            IconButton(onClick = { onYear(year + 1) }, enabled = (year + 1) in years) {
                Icon(painterResource(R.drawable.ic_fp_next), stringResource(R.string.next_year))
            }
        }
        HorizontalDivider(color = t.line)
        // The whole year on one screen: the day rows get a little flatter when needed; only when that would squeeze
        // them too much (large text, small screens) the months scroll.
        val label = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() } + 4.dp
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val square = ((maxWidth - 16.dp) / 3 - 8.dp) / 7
            val fitting = (maxHeight - 16.dp - 12.dp * 3 - (label + 8.dp) * 4) / 24
            val cell = if (fitting >= square * 0.75f) minOf(square, fitting) else square
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (1..12).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { number ->
                            MiniMonth(YearMonth.of(year, number), days, locale, { onMonth(YearMonth.of(year, number)) },
                                Modifier.weight(1f), cell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(month: YearMonth, days: Map<LocalDate, DayMarks>, locale: Locale, onClick: () -> Unit, modifier: Modifier,
    cell: Dp) {
    val t = LocalDaylight.current
    val colors = remember(t) { todayFillColors(t) }
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    val offset = (month.atDay(1).dayOfWeek.value - WeekFields.of(locale).firstDayOfWeek.value + 7) % 7
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    Column(modifier.clickable(onClickLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), onClick = onClick)
        .padding(4.dp)) {
        Text(name.replaceFirstChar { it.titlecase(locale) }, Modifier.padding(start = 2.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge, maxLines = 1)
        // Always six week rows, so the three months of a row line up.
        (0 until 6).forEach { week ->
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                repeat(7) { column ->
                    val number = week * 7 + column - offset + 1
                    Box(Modifier.weight(1f).height(cell), contentAlignment = Alignment.Center) {
                        if (week < weeks && number in 1..month.lengthOfMonth()) {
                            val marks = days[month.atDay(number)] ?: DayMarks(false, false, false, false)
                            Canvas(Modifier.fillMaxSize()) {
                                // A small gap between neighbours; expected days get a soft fill, as a dashed ring this
                                // small would run through the numbers.
                                val r = size.minDimension / 2 - 1.dp.toPx()
                                if (marks.period) drawCircle(colors.fill, r)
                                else if (marks.predicted) drawCircle(t.predicted.copy(alpha = 0.3f), r)
                                if (marks.today) drawCircle(t.accent.todayRing, r, style = Stroke(1.5.dp.toPx()))
                                if (marks.higherChance && !marks.period) drawLine(if (marks.peak) t.ink else t.muted,
                                    Offset(center.x - 2.dp.toPx(), size.height - 1.dp.toPx()),
                                    Offset(center.x + 2.dp.toPx(), size.height - 1.dp.toPx()),
                                    strokeWidth = if (marks.peak) 2.dp.toPx() else 1.dp.toPx())
                            }
                            Text(number.toString(), fontSize = 9.sp, lineHeight = 9.sp,
                                color = if (marks.period) colors.onFill else t.ink)
                        }
                    }
                }
            }
        }
    }
}
