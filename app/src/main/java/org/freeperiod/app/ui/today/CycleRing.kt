package org.freeperiod.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.DmSans
import org.freeperiod.app.ui.theme.LocalDaylight

/** Ring radius and the size of a marked day for [days] dots in a square of [side] px. */
private data class RingGeometry(val radius: Float, val dot: Float, val small: Float)

private fun Density.ringGeometry(side: Float, days: Int): RingGeometry {
    val largest = 11.dp.toPx()
    // Room outside the dots for the higher-chance ticks and the today ring.
    val radius = side / 2 - largest - 10.dp.toPx()
    val dot = if (days == 0) largest else minOf(largest, radius * sin(PI / days).toFloat() * 0.82f)
    return RingGeometry(radius, dot, (dot * 0.33f).coerceAtLeast(1.5.dp.toPx()))
}

/**
 * The current cycle (or pill pack) as one dot per day, in the calendar's own marks: filled period days,
 * dashed expected days, a tick for higher-chance days, a ring for today. The middle says where you are.
 * The same day marks as the calendar, so both views always agree.
 */
@Composable
internal fun CycleRingPage(state: TodayUiState, onStartPeriod: () -> Unit, onConfirmEnd: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit, onPausePredictions: () -> Unit, modifier: Modifier = Modifier, onShowToday: () -> Unit = {}) {
    // The ring takes the free space, centred with a clear margin; the actions sit below it, stacked
    // (owner's choice after trying them on top).
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val locale = LocalConfiguration.current.locales[0]
            val headline = cycleHeadline(state)
            val lines = listOf(predictionText(state.prediction, state.today, locale) to MaterialTheme.typography.bodyMedium) +
                todayHints(state).map { stringResource(it) to MaterialTheme.typography.bodySmall }
            val side = minOf(maxWidth, maxHeight)
            val (boxWidth, boxHeight) = maxWidth to maxHeight
            // The sentence stays inside the ring when it fits there with the number; with large text or a short
            // screen it moves below the ring, so it never runs over the dots.
            val (inside, below) = with(density) {
                val radius = ringGeometry(side.toPx(), 1).radius
                val width = radius * 1.1f
                fun height(maxWidth: Float) = lines.sumOf { (text, style) ->
                    measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth.roundToInt())).size.height + 2.dp.roundToPx()
                }
                val number = if (headline.number == null) 0 else
                    measurer.measure(headline.label, MaterialTheme.typography.bodyMedium).size.height +
                        measurer.measure(headline.number.toString(), MaterialTheme.typography.displayLarge).size.height
                val free = 2 * (radius - 11.dp.toPx() - 24.dp.toPx())
                val label = if (headline.number == null) 0 else measurer.measure(headline.label, MaterialTheme.typography.bodyMedium,
                    constraints = Constraints(maxWidth = maxWidth.roundToPx())).size.height
                (number + height(width) <= free) to (height(maxWidth.toPx()) + label + 16.dp.toPx()).toDp()
            }
            if (inside) CycleRing(state, onDayClick, onShowToday, Modifier.size(side))
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CycleRing(state, onDayClick, onShowToday, Modifier.size(minOf(boxWidth, boxHeight - below)), sentence = false)
                Spacer(Modifier.height(16.dp))
                RingSentence(state, headline.number != null, label = headline.label.takeIf { headline.number != null })
            }
        }
        Box(Modifier.padding(horizontal = 12.dp)) {
            TodayPrimaryActions(state, onStartPeriod, onConfirmEnd, { onDayClick(state.today) }, onPausePredictions, stacked = true)
        }
    }
}

@Composable
private fun CycleRing(state: TodayUiState, onDayClick: (LocalDate) -> Unit, onShowToday: () -> Unit, modifier: Modifier,
    sentence: Boolean = true) {
    val t = LocalDaylight.current
    val colors = remember(t) { todayFillColors(t) }
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = t.muted)
    // Large text needs the room inside the ring.
    val dateLabels = density.fontScale < 1.3f
    val timeline = state.timeline
    val days = remember(timeline) {
        timeline?.let { generateSequence(it.axisStart) { day -> day.plusDays(1) }.takeWhile { day -> day <= it.axisEnd }.toList() }.orEmpty()
    }
    val pack = state.prediction.let { it is org.freeperiod.engine.PredictionState.ScheduledBreak || it is org.freeperiod.engine.PredictionState.ContinuousPill }
    val description = timeline?.let { timelineDescription(it, context, locale, pack) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val geometry = with(density) { ringGeometry(side, days.size) }
        Canvas(Modifier.fillMaxSize()
            .clearAndSetSemantics { description?.let { contentDescription = it } }
            .pointerInput(days, geometry) {
                // A tap on or near a dot opens that day, like a tap in the calendar.
                detectTapGestures { tap ->
                    if (days.isEmpty()) return@detectTapGestures
                    val offset = tap - Offset(size.width / 2f, size.height / 2f)
                    if (abs(offset.getDistance() - geometry.radius) > maxOf(24.dp.toPx(), geometry.dot * 2)) return@detectTapGestures
                    val angle = (atan2(offset.y, offset.x) + PI / 2 + 2 * PI) % (2 * PI)
                    onDayClick(days[(angle / (2 * PI / days.size)).roundToInt() % days.size])
                }
            }) {
            val (radius, dot, small) = geometry
            if (days.isEmpty()) {
                drawCircle(t.line, radius, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
                return@Canvas
            }
            days.forEachIndexed { index, date ->
                val angle = -PI / 2 + index * 2 * PI / days.size
                fun at(r: Float) = center + Offset((cos(angle) * r).toFloat(), (sin(angle) * r).toFloat())
                val point = at(radius)
                val marks = state.days[date] ?: DayMarks(false, false, false, false)
                when {
                    marks.period -> {
                        drawCircle(colors.fill, dot, point)
                        colors.edge?.let { drawCircle(it, dot, point, style = Stroke(1.dp.toPx())) }
                    }
                    marks.predicted -> drawCircle(t.predicted, dot, point,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                    marks.possible -> drawCircle(t.predicted.copy(alpha = 0.55f), dot, point,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 4.dp.toPx()))))
                    else -> drawCircle(if (marks.logged) t.ink else if (date < state.today) t.control else t.line, small, point)
                }
                if (marks.higherChance) drawLine(t.muted, at(radius + dot + 4.dp.toPx()), at(radius + dot + 9.dp.toPx()),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                if (marks.today) {
                    drawCircle(t.accent.todayRing, dot + 3.dp.toPx(), point, style = Stroke(2.dp.toPx()))
                    if (!marks.period) drawCircle(t.ink, 2.5.dp.toPx(), point)
                }
                // Every fifth date of the month, numbered by that date: the dot next to "15" is the 15th.
                if (date.dayOfMonth % 5 == 0 && dateLabels) {
                    val label = measurer.measure(date.dayOfMonth.toString(), labelStyle)
                    val spot = at(radius - dot - 6.dp.toPx() - label.size.width / 2f)
                    drawText(label, topLeft = spot - Offset(label.size.width / 2f, label.size.height / 2f))
                }
            }
        }
        RingCenter(state, onShowToday, sentence, Modifier.widthIn(max = with(density) { (geometry.radius * 1.1f).toDp() }))
    }
}

@Composable
private fun RingCenter(state: TodayUiState, onShowToday: () -> Unit, sentence: Boolean, modifier: Modifier) {
    val t = LocalDaylight.current
    val locale = LocalConfiguration.current.locales[0]
    val headline = cycleHeadline(state)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (headline.number != null) {
            if (sentence) Text(headline.label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            // Like the number on the card: back to today in the calendar.
            Row(Modifier.clickable(onClickLabel = stringResource(R.string.jump_to_today), onClick = onShowToday),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline.number.toString(), Modifier.alignByBaseline(), style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.wordmark_stop), Modifier.alignByBaseline().clearAndSetSemantics {},
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = DmSans), color = t.accent.accent)
            }
        }
        // Pulled up a little: the big numeral's line box leaves a gap below the digits.
        if (sentence) Box(Modifier.offset(y = (-6).dp)) { RingSentence(state, headline.number != null) }
    }
}

/** Where the next period stands, plus the phase hints; inside the ring or, when it doesn't fit, below it. */
@Composable
private fun RingSentence(state: TodayUiState, quiet: Boolean, label: String? = null) {
    val t = LocalDaylight.current
    val locale = LocalConfiguration.current.locales[0]
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Below the ring the number's label leads, since the ring then holds only the number.
        label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        Text(predictionText(state.prediction, state.today, locale), style = MaterialTheme.typography.bodyMedium,
            color = if (quiet) t.muted else t.ink, textAlign = TextAlign.Center)
        todayHints(state).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = t.muted, textAlign = TextAlign.Center)
        }
    }
}
