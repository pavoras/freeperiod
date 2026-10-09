package org.freeperiod.app.ui.today

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.LocalDaylight

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayLegend(modifier: Modifier = Modifier, scheduledBreak: Boolean = false, higherChance: Boolean = false,
    onEstimateInfo: () -> Unit = {}) {
    val t = LocalDaylight.current
    val colors = todayFillColors(t)
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.legend_period, if (scheduledBreak) R.string.timeline_scheduled_break else R.string.legend_predicted, R.string.legend_today, R.string.legend_entry)
            .forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(12.dp)) {
                        when (index) {
                            0 -> drawCircle(colors.fill)
                            1 -> drawCircle(t.predicted, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
                            2 -> drawCircle(t.accent.todayRing, style = Stroke(1.5.dp.toPx()))
                            else -> drawCircle(t.ink, radius = 2.dp.toPx())
                        }
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = t.muted)
                }
            }
        // The only place this estimate is described; the info opens the "not a medical device" note.
        if (higherChance) Row(Modifier.clickable(onClickLabel = stringResource(R.string.pregnancy_chance_info), onClick = onEstimateInfo),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.size(12.dp)) {
                drawLine(t.muted, Offset(3.dp.toPx(), center.y), Offset(9.dp.toPx(), center.y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            Text(stringResource(R.string.pregnancy_chance_estimate, stringResource(R.string.pregnancy_chance_label)),
                style = MaterialTheme.typography.labelSmall, color = t.muted)
            Icon(painterResource(R.drawable.ic_fp_about), stringResource(R.string.pregnancy_chance_info),
                Modifier.size(18.dp), tint = t.muted)
        }
    }
}
