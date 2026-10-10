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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit.DAYS
import org.freeperiod.app.R
import org.freeperiod.app.ui.day.ResolvedEntryAppearance
import org.freeperiod.app.ui.theme.*

/** Days shown in the History charts. */
internal const val CHART_DAYS = 56

private val rowHeight = 28.dp
/** Same axis width in every chart, so days line up between mood and pain. */
private val axisWidth = 72.dp

/**
 * Logged levels as dots per day (index 0 = lowest row), period days shaded. It shows entries as they are:
 * no averages, scores or trends. [icons] puts the level icons on the axis instead of their names.
 */
@Composable
internal fun LevelChart(title: String, levels: List<ResolvedEntryAppearance>, values: Map<LocalDate, Int>,
    periodDays: Set<LocalDate>, to: LocalDate, icons: Boolean) {
    val t = LocalDaylight.current
    val from = to.minusDays(CHART_DAYS - 1L)
    val locale = LocalConfiguration.current.locales[0]
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val spoken = pluralStringResource(R.plurals.history_chart_days, values.size, values.size, CHART_DAYS)
    Column(verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.history_chart_basis), style = MaterialTheme.typography.bodySmall, color = t.muted)
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title. $spoken" }) {
            Column(Modifier.width(axisWidth)) {
                levels.asReversed().forEach { level ->
                    Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (icons) Icon(painterResource(level.icon), null, Modifier.size(20.dp), tint = t.muted)
                        else Text(level.label, style = MaterialTheme.typography.labelSmall, color = t.muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Canvas(Modifier.weight(1f).height(rowHeight * levels.size)) {
                val step = size.width / CHART_DAYS
                val row = size.height / levels.size
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
                val points = values.filterKeys { it in from..to }.toSortedMap()
                // Lines only join neighbouring days; a gap stays a gap.
                points.entries.zipWithNext().forEach { (a, b) ->
                    if (DAYS.between(a.key, b.key) == 1L)
                        drawLine(t.accent.periodBorder, Offset(x(a.key), y(a.value)), Offset(x(b.key), y(b.value)), 1.5.dp.toPx())
                }
                points.forEach { (day, level) -> drawCircle(t.accent.periodBorder, 3.5.dp.toPx(), Offset(x(day), y(level))) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = axisWidth)) {
            Text(from.format(dates), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = t.muted)
            Text(stringResource(R.string.history_chart_today), style = MaterialTheme.typography.labelSmall, color = t.muted)
        }
        Text(spoken, style = MaterialTheme.typography.bodySmall, color = t.muted)
    }
}
