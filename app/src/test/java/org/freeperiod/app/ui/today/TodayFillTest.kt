package org.freeperiod.app.ui.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.freeperiod.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class TodayFillTest {
    @Test fun estimateUnderlinesMeetContrastOnEmptyAndPeriodDaysInEveryAccentAndMode() {
        for (accent in Accent.entries) for (dark in listOf(false, true)) {
            val t = daylightTokens(accent, dark)
            val colors = todayFillColors(t)
            assertTrue("$accent dark=$dark empty day", contrast(t.muted, t.background) >= 3.0)
            assertTrue("$accent dark=$dark period day", contrast(colors.onFill, colors.fill) >= 3.0)
        }
    }
    @Test fun todayUsesTheSameAccentAsEveryScreenAndAnEdgeCarriesTheContrastWhenNeeded() {
        Accent.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val t = daylightTokens(accent, dark)
                val colors = todayFillColors(t)
                assertEquals("$accent dark=$dark same accent", t.accent.periodFill, colors.fill)
                for (surface in listOf(t.background, t.surface)) {
                    val visible = contrast(colors.fill, surface) >= 3.0 || colors.edge?.let { contrast(it, surface) >= 3.0 } == true
                    assertTrue("$accent dark=$dark marker visible", visible)
                }
                assertTrue("$accent dark=$dark text", contrast(colors.onFill, colors.fill) >= 4.5)
            }
        }
    }
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
