package org.freeperiod.app.ui.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.freeperiod.app.ui.theme.DaylightTokens
import org.freeperiod.app.ui.theme.daylightTokens

internal data class TodayFillColors(val fill: Color, val onFill: Color, val edge: Color?)

/**
 * The period accent exactly as on every other screen. Where the fill alone is too light to stand out
 * (3:1) on the Today surfaces, [TodayFillColors.edge] is the accent's border colour for a thin outline.
 */
internal fun todayFillColors(tokens: DaylightTokens): TodayFillColors {
    fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    fun visible(fill: Color) = contrast(fill, tokens.background) >= 3.05f && contrast(fill, tokens.surface) >= 3.05f
    val fill = tokens.accent.periodFill
    val text = tokens.accent.onAccent.takeIf { contrast(it, fill) >= 4.5f }
        ?: daylightTokens(dark = true).background
    return TodayFillColors(fill, text, tokens.accent.periodBorder.takeUnless { visible(fill) })
}
