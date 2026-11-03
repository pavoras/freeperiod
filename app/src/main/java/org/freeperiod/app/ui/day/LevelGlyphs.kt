package org.freeperiod.app.ui.day

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.LocalDaylight
import org.freeperiod.engine.FlowLevel
import org.freeperiod.engine.Pain

/**
 * Flow as the app's period dot, filled to the level: empty ring for none, a drop at the bottom for spotting,
 * then a third, two thirds and full. Readable without colour, same look as period days in the calendar.
 */
@Composable
internal fun FlowGlyph(level: FlowLevel, modifier: Modifier = Modifier) {
    val accent = LocalDaylight.current.accent
    Canvas(modifier.size(26.dp)) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2 - stroke
        val fraction = when (level) {
            FlowLevel.NONE, FlowLevel.SPOTTING -> 0f
            FlowLevel.LIGHT -> 0.36f
            FlowLevel.MEDIUM -> 0.64f
            FlowLevel.HEAVY -> 1f
        }
        if (fraction > 0f) clipRect(top = center.y + radius - 2 * radius * fraction) { drawCircle(accent.periodFill, radius) }
        if (level == FlowLevel.SPOTTING) drawCircle(accent.periodBorder, 2.5.dp.toPx(), Offset(center.x, center.y + radius * 0.45f))
        drawCircle(accent.periodBorder, radius, style = Stroke(stroke))
    }
}

/** Pain as three rising bars, filled up to the level; none is a flat line. Uses the chip's text colour. */
@Composable
internal fun PainGlyph(level: Pain, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(26.dp)) {
        val stroke = 1.2.dp.toPx()
        if (level == Pain.NONE) {
            drawLine(color, Offset(size.width * 0.2f, center.y), Offset(size.width * 0.8f, center.y), 2.dp.toPx())
            return@Canvas
        }
        val filled = level.ordinal // MILD 1, MODERATE 2, SEVERE 3
        val width = size.width * 0.17f
        val gap = size.width * 0.09f
        val left = (size.width - 3 * width - 2 * gap) / 2
        val bottom = size.height * 0.85f
        for (bar in 0 until 3) {
            val height = size.height * (0.3f + 0.22f * bar)
            val topLeft = Offset(left + bar * (width + gap), bottom - height)
            val corner = CornerRadius(width / 3)
            if (bar < filled) drawRoundRect(color, topLeft, Size(width, height), corner)
            else drawRoundRect(color.copy(alpha = 0.55f), topLeft + Offset(stroke / 2, stroke / 2),
                Size(width - stroke, height - stroke), corner, style = Stroke(stroke))
        }
    }
}
