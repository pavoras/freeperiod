package org.freeperiod.app.ui.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.freeperiod.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class TodayFillTest {
    @Test fun borderlessFillsAndTheirTextMeetContrastForAllAccentsAndModes() {
        Accent.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val t = daylightTokens(accent, dark)
                val colors = todayFillColors(t)
                for (surface in listOf(t.background, t.surface)) {
                    assertTrue("$accent dark=$dark fill", contrast(colors.fill, surface) >= 3.0)
                }
                assertTrue("$accent dark=$dark text", contrast(colors.onFill, colors.fill) >= 4.5)
                if (dark) assertEquals(t.accent.periodFill, colors.fill)
            }
        }
    }
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
