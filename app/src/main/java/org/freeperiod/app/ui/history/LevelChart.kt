package org.freeperiod.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit.DAYS
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpChip
import org.freeperiod.app.ui.theme.*

/** Time spans the History charts can show; [days] ends today. */
enum class ChartRange(val days: Int, val label: Int) {
    WEEK(7, R.string.history_range_week), MONTH(30, R.string.history_range_month),
    QUARTER(91, R.string.history_range_quarter), YEAR(365, R.string.history_range_year),
}

/** The longest range: History loads this much once and every chart cuts its range from it. */
internal val CHART_DAYS = ChartRange.entries.maxOf { it.days }

/** Same axis width in every chart, so days line up between the charts. */
private val axisWidth = 100.dp

/** Heading, range picker and the shared reading hint above the charts. */
@Composable
internal fun ChartRangePicker(range: ChartRange, onRange: (ChartRange) -> Unit) {
    val t = LocalDaylight.current
    Column(verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        Text(stringResource(R.string.history_charts), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChartRange.entries.forEach { option ->
                FpChip(option == range, { onRange(option) }, stringResource(option.label), Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.history_chart_basis), style = MaterialTheme.typography.bodySmall, color = t.muted)
    }
}

/**
 * Logged levels (label, icon) as dots per day (index 0 = lowest row), period days shaded. It shows entries as they are:
 * no averages, scores or trends. [icons] puts the level icons on the axis instead of their names.
 */
@Composable
internal fun LevelChart(title: String, levels: List<Pair<String, Int>>, values: Map<LocalDate, Int>,
    periodDays: Set<LocalDate>, to: LocalDate, icons: Boolean, days: Int, rowHeight: Dp = 28.dp) {
    val t = LocalDaylight.current
    val from = to.minusDays(days - 1L)
    val shown = values.filterKeys { it in from..to }.toSortedMap()
    val locale = LocalConfiguration.current.locales[0]
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val spoken = pluralStringResource(R.plurals.history_chart_days, shown.size, shown.size, days)
    Column(verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title. $spoken" }) {
            Column(Modifier.width(axisWidth)) {
                levels.asReversed().forEach { (label, icon) ->
                    Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (icons) Icon(painterResource(icon), null, Modifier.size(20.dp), tint = t.muted)
                        else Text(label, style = MaterialTheme.typography.labelSmall, color = t.muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Canvas(Modifier.weight(1f).height(rowHeight * levels.size)) {
                val step = size.width / days
                val row = size.height / levels.size
                // A year has ~1 px per day: dots and lines shrink with the day width instead of merging.
                val dot = (step * 0.42f).coerceIn(1.2.dp.toPx(), 4.dp.toPx())
                val stroke = (step * 0.3f).coerceIn(0.8.dp.toPx(), 1.5.dp.toPx())
                fun x(date: LocalDate) = (DAYS.between(from, date) + 0.5f) * step
                fun y(level: Int) = size.height - (level + 0.5f) * row
                // One band per run of period days (no seams between days).
                periodDays.filter { it in from..to }.sorted().fold(mutableListOf<MutableList<LocalDate>>()) { runs, day ->
                    if (runs.lastOrNull()?.last()?.plusDays(1) == day) runs.last() += day else runs += mutableListOf(day)
                    runs
                }.forEach { run ->
                    drawRect(t.accent.container, Offset(x(run.first()) - step / 2, 0f), Size(step * run.size, size.height))
                }
                repeat(levels.size) { level -> drawLine(t.line, Offset(0f, y(level)), Offset(size.width, y(level)), 1.dp.toPx()) }
                // Lines only join neighbouring days; a gap stays a gap. Over months the days are too narrow
                // for lines to read as more than noise, so only the dots stay.
                if (step >= 4.dp.toPx()) shown.entries.zipWithNext().forEach { (a, b) ->
                    if (DAYS.between(a.key, b.key) == 1L)
                        drawLine(t.accent.periodBorder, Offset(x(a.key), y(a.value)), Offset(x(b.key), y(b.value)), stroke)
                }
                shown.forEach { (day, level) -> drawCircle(t.accent.periodBorder, dot, Offset(x(day), y(level))) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = axisWidth)) {
            Text(from.format(dates), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = t.muted)
            Text(stringResource(R.string.history_chart_today), style = MaterialTheme.typography.labelSmall, color = t.muted)
        }
        Text(spoken, style = MaterialTheme.typography.bodySmall, color = t.muted)
    }
}
