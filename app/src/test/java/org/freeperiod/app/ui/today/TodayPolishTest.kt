package org.freeperiod.app.ui.today

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
class TodayPolishTest {
    @get:Rule val compose = createComposeRule()
    @Test fun germanLightLabelsFit() = verify(false, 1f)
    @Test fun germanDarkLabelsFit() = verify(true, 1f)
    @Test fun germanLargeLightLabelsFit() = verify(false, 1.5f)
    @Test fun germanLargeDarkLabelsFit() = verify(true, 1.5f)
    @Test fun germanLargeEndLabelFits() = verify(false, 1.5f, "ongoing", "Periode beendet")

    private fun verify(dark: Boolean, scale: Float, scenario: String = "regular", primary: String = "Periode begonnen") {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FreePeriodTheme(darkTheme = dark) { TodayCard(todayFixture(scenario), {}, {}, {}, {}, Modifier.width(336.dp)) }
            }
        }
        compose.waitForIdle()
        for (label in listOf(primary, "Heute eintragen")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(label, 1, layout.lineCount)
            assertFalse(label, layout.hasVisualOverflow)
            assertEquals(label, layout.size.width / 2f, (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f, 2f)
        }
    }
}
