package org.freeperiod.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.DAYS
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.LocalDaylight
import org.freeperiod.engine.BracketKind
import org.freeperiod.engine.CycleTimeline

@Composable
fun CycleTimelineView(timeline: CycleTimeline, modifier: Modifier = Modifier, pack: Boolean = false) {
    val t = LocalDaylight.current
    val locale = LocalConfiguration.current.locales[0]
    val description = timelineDescription(timeline, LocalContext.current, locale, pack)
    val dateFormat = DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM" else "MMM d", locale)
    val bracket = timeline.bracket
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        // Today has its own label lane: day 1 and day 3 remain readable on a full-cycle axis.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fraction = (DAYS.between(timeline.axisStart, timeline.today).toFloat() /
                DAYS.between(timeline.axisStart, timeline.axisEnd).coerceAtLeast(1)).coerceIn(0f, 1f)
            val labelWidth = minOf(80.dp, maxWidth)
            val left = (8.dp + (maxWidth - 16.dp) * fraction - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
            Column(Modifier.width(labelWidth).offset(x = left)) {
                Text(timeline.today.format(dateFormat), Modifier.fillMaxWidth(), color = t.ink,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.legend_today), Modifier.fillMaxWidth(), color = t.ink,
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val inset = 8.dp.toPx()
            val span = DAYS.between(timeline.axisStart, timeline.axisEnd).coerceAtLeast(1).toFloat()
            fun x(date: LocalDate) = inset + (size.width - 2 * inset) *
                (DAYS.between(timeline.axisStart, date).toFloat() / span).coerceIn(0f, 1f)
            val y = size.height / 2
            val todayX = x(timeline.today)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            drawLine(t.muted, Offset(inset, y), Offset(todayX, y), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(t.muted, Offset(todayX, y), Offset(size.width - inset, y), 1.5.dp.toPx(), pathEffect = dash)
            fun segment(range: ClosedRange<LocalDate>, expected: Boolean) {
                val left = x(range.start) - 3.dp.toPx()
                val right = x(range.endInclusive) + 3.dp.toPx()
                val top = Offset(left, y - 4.dp.toPx())
                val dimensions = Size(right - left, 8.dp.toPx())
                drawRoundRect(if (expected) t.accent.container else t.accent.periodFill, top, dimensions, CornerRadius(4.dp.toPx()))
                drawRoundRect(t.accent.periodBorder, top, dimensions, CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            timeline.expectedPeriodRest?.let { segment(it, true) }
            timeline.recordedPeriod?.let { segment(it, false) }
            bracket?.let {
                val top = Offset(x(it.start) - 3.dp.toPx(), y - 5.dp.toPx())
                val dimensions = Size((x(it.endInclusive) - x(it.start) + 6.dp.toPx()).coerceAtLeast(6.dp.toPx()), 10.dp.toPx())
                drawRoundRect(t.surface, top, dimensions, CornerRadius(4.dp.toPx()))
                drawRoundRect(t.predicted, top, dimensions, CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
            }
            // The surface separates the today ring from both light period fills and dark backgrounds.
            drawCircle(t.surface, 7.dp.toPx(), Offset(todayX, y))
            drawCircle(t.accent.todayRing, 7.dp.toPx(), Offset(todayX, y), style = Stroke(2.dp.toPx()))
            drawCircle(t.ink, 2.dp.toPx(), Offset(todayX, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(timeline.axisStart.format(dateFormat), color = t.muted, style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.timeline_start), color = t.muted, style = MaterialTheme.typography.labelSmall)
            }
            if (bracket != null) Column(Modifier.weight(1.4f)) {
                Text(formatPredictionRange(bracket.start, bracket.endInclusive, timeline.today, locale), Modifier.fillMaxWidth(),
                    color = t.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(stringResource(if (timeline.bracketKind == BracketKind.SCHEDULED_BREAK)
                    R.string.timeline_scheduled_break else R.string.timeline_likely), Modifier.fillMaxWidth(), color = t.muted,
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
    }
}
