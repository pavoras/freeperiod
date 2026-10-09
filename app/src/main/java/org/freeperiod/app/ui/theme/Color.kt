package org.freeperiod.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Stable names are persisted, never ordinal positions. */
enum class Accent { CORAL, PLUM, SAGE, OCEAN, OCHRE, INK }

data class AccentColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val periodFill: Color,
    val periodBorder: Color,
    val todayRing: Color,
    val selectedChipBackground: Color,
    val selectedChipBorder: Color,
    val selectedChipText: Color,
)

internal fun accentColors(accent: Accent, dark: Boolean): AccentColors {
    val light = when (accent) {
        Accent.CORAL -> listOf(0xFFF3B6A4, 0xFFF9DED3, 0xFF886357)
        Accent.PLUM -> listOf(0xFFD6B6D6, 0xFFEEDDEE, 0xFF795779)
        Accent.SAGE -> listOf(0xFFB8CCAC, 0xFFE0E9D8, 0xFF566C49)
        Accent.OCEAN -> listOf(0xFFACCADB, 0xFFDCEAF2, 0xFF486B80)
        Accent.OCHRE -> listOf(0xFFE3C17F, 0xFFF3E5C7, 0xFF79612F)
        // Ink is the dark, non-pastel alternative: near-black slate with light text on it.
        Accent.INK -> listOf(0xFF2F3440, 0xFFE4E5EA, 0xFF1E222B)
    }
    val night = when (accent) {
        Accent.CORAL -> listOf(0xFFEFAC93, 0xFF604034)
        Accent.PLUM -> listOf(0xFFD4AED6, 0xFF513752)
        Accent.SAGE -> listOf(0xFFB5CDA4, 0xFF35492E)
        Accent.OCEAN -> listOf(0xFFA5CBDF, 0xFF2E4554)
        Accent.OCHRE -> listOf(0xFFE3C17F, 0xFF534324)
        Accent.INK -> listOf(0xFF9A9EA6, 0xFF34363B)
    }
    val fill = Color(if (dark) night[0] else light[0])
    val container = Color(if (dark) night[1] else light[1])
    val edge = if (dark) fill else Color(light[2])
    val ink = Color(0xFF302923)
    val paper = Color(0xFFFBF2E9)
    val text = if (dark) paper else ink
    val onFill = if (fill.luminance() < 0.2f) paper else ink
    return AccentColors(fill, onFill, container, text, fill, edge, ink,
        container, edge, text)
}
