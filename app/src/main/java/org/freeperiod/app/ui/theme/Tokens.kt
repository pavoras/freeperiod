package org.freeperiod.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class DaylightTokens(
    val background: Color, val surface: Color, val ink: Color, val muted: Color,
    val line: Color, val control: Color, val predicted: Color, val actionText: Color,
    val accent: AccentColors,
)

fun daylightTokens(accent: Accent = Accent.CORAL, dark: Boolean = false): DaylightTokens {
    val tones = accentColors(accent, dark)
    return if (dark) DaylightTokens(Color(0xFF211E1C), Color(0xFF302724), Color(0xFFFBF2E9),
        Color(0xFFC6B5A9), Color(0xFF51463F), Color(0xFFA38C7D), Color(0xFFCABBD5),
        tones.accent, tones.copy(todayRing = Color(0xFFFBF2E9)))
    else DaylightTokens(Color(0xFFFBF7F2), Color(0xFFFFFDF9), Color(0xFF302923),
        Color(0xFF6E6159), Color(0xFFE3D8CF), Color(0xFF9B8B80), Color(0xFF75637D),
        Color(0xFF614C42), tones)
}

// For today on a period fill, pair the outer todayRing with an inner onAccent outline.
val LocalDaylight = staticCompositionLocalOf { daylightTokens() }

object FpSpacing {
    val screen = 20.dp
    val entry = 16.dp
    val section = 16.dp
    val gap = 8.dp
    val compact = 4.dp
    val touch = 48.dp
}
