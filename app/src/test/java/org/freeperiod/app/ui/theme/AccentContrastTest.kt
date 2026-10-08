package org.freeperiod.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AccentContrastTest {
    @Test fun allAccentTextAndEssentialGraphicsMeetContrast() {
        Accent.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val t = daylightTokens(accent, dark)
                val a = t.accent
                val text = listOf(t.ink to t.background, t.ink to t.surface,
                    t.muted to t.background, t.muted to t.surface,
                    a.onAccent to a.accent, a.onContainer to a.container,
                    a.selectedChipText to a.selectedChipBackground, a.onAccent to a.periodFill,
                    t.actionText to t.background, t.actionText to t.surface)
                val graphics = listOf(a.periodBorder to t.background, a.periodBorder to t.surface,
                    a.todayRing to t.background, a.onAccent to a.periodFill,
                    a.selectedChipBorder to a.selectedChipBackground,
                    a.selectedChipBorder to t.surface, t.control to t.surface,
                    t.predicted to t.background, t.predicted to t.surface)
                text.forEach { (foreground, background) ->
                    assertTrue("$accent dark=$dark text ${contrast(foreground, background)}", contrast(foreground, background) >= 4.5)
                }
                graphics.forEach { (foreground, background) ->
                    assertTrue("$accent dark=$dark graphic ${contrast(foreground, background)}", contrast(foreground, background) >= 3.0)
                }
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun luminance(c: Color): Double {
            fun linear(v: Float): Double = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            return .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
