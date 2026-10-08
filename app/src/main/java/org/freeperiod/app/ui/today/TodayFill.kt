package org.freeperiod.app.ui.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.freeperiod.app.ui.theme.DaylightTokens
import org.freeperiod.app.ui.theme.daylightTokens

internal data class TodayFillColors(val fill: Color, val onFill: Color)

/** Closest accent shade that identifies a borderless marker on both Today surfaces. */
internal fun todayFillColors(tokens: DaylightTokens): TodayFillColors {
    fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    fun visible(fill: Color) = contrast(fill, tokens.background) >= 3.05f && contrast(fill, tokens.surface) >= 3.05f
    var fill = tokens.accent.periodFill
    if (!visible(fill)) {
        var low = 0f
        var high = 1f
        repeat(16) {
            val middle = (low + high) / 2
            if (visible(lerp(tokens.accent.periodFill, tokens.accent.periodBorder, middle))) high = middle else low = middle
        }
        fill = lerp(tokens.accent.periodFill, tokens.accent.periodBorder, high)
    }
    val text = tokens.accent.onAccent.takeIf { contrast(it, fill) >= 4.5f }
        ?: daylightTokens(dark = true).background
    return TodayFillColors(fill, text)
}
