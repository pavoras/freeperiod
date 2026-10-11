package org.freeperiod.app.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** One heart of the thank-you: where it starts, how far it rises and how it sways, as fractions of the screen. */
private class Heart(random: Random) {
    val x = 0.12f + random.nextFloat() * 0.76f
    val start = 0.55f + random.nextFloat() * 0.2f
    val rise = 0.3f + random.nextFloat() * 0.3f
    val delay = random.nextFloat() * 0.3f
    val size = 12f + random.nextFloat() * 16f
    val sway = 6f + random.nextFloat() * 12f
    val phase = random.nextFloat() * 2f * PI.toFloat()
    val tilt = -18f + random.nextFloat() * 36f
    val tone = random.nextInt(3)
}

internal const val THANK_YOU_MILLIS = 2200

/** Plays the hearts once (0 → 1); the caller keeps the [progress] so a recomposition doesn't restart them. */
suspend fun Animatable<Float, *>.playThankYou() {
    snapTo(0f)
    animateTo(1f, tween(THANK_YOU_MILLIS, easing = LinearEasing))
}

/** A small, calm burst of hearts rising from the buttons after a purchase; [progress] runs from 0 to 1. */
@Composable
fun ThankYouHearts(progress: Float, seed: Int, colors: List<Color>, outline: Color, modifier: Modifier = Modifier) {
    val hearts = remember(seed) { Random(seed).let { random -> List(16) { Heart(random) } } }
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier.testTag("thank-you-hearts")) {
        hearts.forEach { heart ->
            val local = ((progress - heart.delay) / (1f - heart.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            // Pop in quickly, float, fade out over the last 40 %.
            val alpha = minOf(local / 0.12f, 1f, (1f - local) / 0.4f)
            val grow = 0.4f + 0.6f * minOf(local / 0.15f, 1f)
            val center = Offset(
                size.width * heart.x + heart.sway.dp.toPx() * sin(heart.phase + local * 2.2f * PI.toFloat()),
                size.height * (heart.start - heart.rise * easeOut(local)),
            )
            rotate(heart.tilt * (1f - local), center) {
                drawHeart(center, heart.size.dp.toPx() * grow, colors[heart.tone % colors.size].copy(alpha = alpha),
                    outline.copy(alpha = alpha))
            }
        }
    }
}

private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x)

/** A heart of width [width] centred on [center], with a thin [outline] so it stands out on the buttons too. */
private fun DrawScope.drawHeart(center: Offset, width: Float, color: Color, outline: Color) {
    val w = width / 2f
    val path = Path().apply {
        moveTo(center.x, center.y + w * 0.9f)
        cubicTo(center.x - w * 1.2f, center.y + w * 0.1f, center.x - w * 1.0f, center.y - w * 0.95f, center.x, center.y - w * 0.4f)
        cubicTo(center.x + w * 1.0f, center.y - w * 0.95f, center.x + w * 1.2f, center.y + w * 0.1f, center.x, center.y + w * 0.9f)
        close()
    }
    drawPath(path, outline, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    drawPath(path, color)
}
