package org.freeperiod.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.LocalDaylight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FpLegend(modifier: Modifier = Modifier) {
    val t = LocalDaylight.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.legend_period, R.string.legend_predicted, R.string.legend_today, R.string.legend_entry)
            .forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(12.dp)) {
                        when (index) {
                            0 -> { drawCircle(t.accent.periodFill); drawCircle(t.accent.periodBorder, style = Stroke(1.dp.toPx())) }
                            1 -> drawCircle(t.predicted, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
                            2 -> drawCircle(t.accent.todayRing, style = Stroke(1.5.dp.toPx()))
                            else -> drawCircle(t.ink, radius = 2.dp.toPx())
                        }
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = t.muted)
                }
            }
    }
}
