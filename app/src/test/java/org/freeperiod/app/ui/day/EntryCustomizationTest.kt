package org.freeperiod.app.ui.day

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import java.time.LocalDate
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// Real text measurement: without native graphics every text is a few pixels wide and "overflows".
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class EntryCustomizationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun theme() { compose.activity.setTheme(R.style.Theme_FreePeriod) }
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun oneToTenMoodsFitWithLargeText() = choicesFit("mood")
    @Test fun oneToTenPainItemsFitWithLargeText() = choicesFit("pain")
    @Test fun oneToTenSexItemsFitWithLargeText() = choicesFit("sex")
    @Test fun oneToTenDischargeItemsFitWithLargeText() = choicesFit("discharge")

    private fun choicesFit(field: String) {
        val builtIns = builtInEntryItems(field)
        fun state(count: Int): DayEntryUiState {
            val tags = (1..(count - builtIns.size).coerceAtLeast(0)).map {
                Tag(100L + it, "Own $it", categoryId = 1, iconKey = "calm")
            }
            val overrides = builtInCategories.map {
                UiOverride(it.overrideKey, it.key != field, -1, if (it.key == field) "Choices" else null)
            } + builtIns.drop(count).map { UiOverride(it.key, true, it.order) }
            return DayEntryUiState(today, today, tags = tags, loading = false, overrides = overrides,
                customCategories = listOf(CustomCategory(1, "Own items", "builtin:$field", 0, false)))
        }
        val current = mutableStateOf(state(1))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                FreePeriodTheme { DayEntrySheet(current.value, DayEntryActions(), {}, {}) }
            }
        }
        compose.waitForIdle()
        if (field == "sex" || field == "discharge") {
            compose.onNodeWithText("Choices").performClick()
            compose.waitForIdle()
        }
        for (count in 1..10) {
            compose.runOnIdle { current.value = state(count) }
            compose.waitForIdle()
            val labels = builtIns.take(count).map { compose.activity.getString(it.label) } + current.value.tags.map { it.name }
            assertEquals(count, labels.size)
            labels.forEach { label ->
                // The collapsed section's summary can show the same word ("None"); check the chip itself.
                compose.onNode(hasText(label) and !hasText("Choices")).assertIsDisplayed()
                compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                    val results = mutableListOf<TextLayoutResult>()
                    assertTrue(requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(results))
                    // A broken word shows up as more lines than words (Robolectric's overflow flags are unreliable here).
                    val layout = results.single()
                    assertTrue("$field/$count/$label lines=${layout.lineCount}", layout.lineCount <= label.split(' ').size)
                }
            }
        }
    }

    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun namesAndIconsResolveOverridesOwnRowsAndTranslatedDefaults() {
        var builtIn: ResolvedEntryAppearance? = null
        var own: ResolvedEntryAppearance? = null
        var reset: ResolvedEntryAppearance? = null
        compose.setContent { FreePeriodTheme {
            builtIn = entryAppearance("item:mood:GOOD", R.string.mood_good, R.drawable.ic_mood_good,
                listOf(UiOverride("item:mood:GOOD", false, 0, "Content", "leaf")))
            own = entryAppearance("tag:1", R.string.item_name, R.drawable.ic_fp_tags, emptyList(), Tag(1, "Calm", iconKey = "calm"))
            reset = entryAppearance("item:mood:GOOD", R.string.mood_good, R.drawable.ic_mood_good, emptyList())
        } }
        compose.waitForIdle()
        assertEquals("Content", builtIn?.label)
        assertEquals(org.freeperiod.app.ui.components.FpIcons.byKey["leaf"], builtIn?.icon)
        assertEquals("Calm", own?.label)
        assertEquals(org.freeperiod.app.ui.components.FpIcons.byKey["calm"], own?.icon)
        assertEquals(compose.activity.getString(R.string.mood_good), reset?.label)
        assertEquals(R.drawable.ic_mood_good, reset?.icon)
    }
}
