package org.freeperiod.app.ui.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.freeperiod.app.data.LockTimeout
import org.freeperiod.app.data.AppSettings
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.CustomCategory
import org.freeperiod.engine.backup.BackupData
import org.freeperiod.engine.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SettingsClarityTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: SettingsUiState, actions: SettingsActions = SettingsActions()) {
        compose.setContent { FreePeriodTheme { SettingsScreen(state, actions) } }
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    @Test fun measuredCyclesHideTheTypedFallback() {
        // History shows the measured length; the setting only matters before the first full cycle.
        show(SettingsUiState(30, loading = false, measuredCycleLength = 29, measuredCycles = 5))
        compose.onNodeWithText("Typical cycle length").assertDoesNotExist()
        compose.onNodeWithText("29 days").assertDoesNotExist()
    }

    @Test fun withoutCyclesTheTypedLengthIsAFallback() {
        // The dialog itself has a text field, which Robolectric cannot idle inside a dialog window.
        show(SettingsUiState(30, loading = false))
        compose.onNodeWithText("30 days").assertExists()
        compose.onNodeWithText("from your last", substring = true).assertDoesNotExist()
    }

    @Test fun turningOnTheLockExplainsItFirst() {
        var enabled: Boolean? = null
        show(SettingsUiState(loading = false, lockCanEnable = true), SettingsActions(lockEnabled = { enabled = it }))
        scrollTo("App lock")
        compose.onNodeWithContentDescription("App lock").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("fingerprint, face, PIN, pattern or password", substring = true).assertExists()
        assertEquals(null, enabled)
        compose.onNodeWithText("Turn on").performClick()
        compose.waitForIdle()
        assertEquals(true, enabled)
    }

    @Test fun lockTimeoutOffersTenAndFifteenMinutes() {
        var chosen: LockTimeout? = null
        show(SettingsUiState(loading = false, lockCanEnable = true, device = AppSettings(lockEnabled = true)),
            SettingsActions(lockTimeout = { chosen = it }))
        scrollTo("Lock after time in the background")
        compose.onNodeWithText("Lock after time in the background").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("10 minutes").assertExists()
        compose.onNodeWithText("15 minutes").performClick()
        compose.waitForIdle()
        assertEquals(LockTimeout.FIFTEEN_MINUTES, chosen)
    }

    @Test fun archivedCategoriesAreListedAndCanBeRestored() {
        var restored: CustomCategory? = null
        val sport = CustomCategory(7, "Sport", "dumbbell", 100, archived = true)
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false),
            customCategories = listOf(sport))
        compose.setContent { FreePeriodTheme { Surface {
            DayEntrySettingsScreen(data, LocalDate.of(2026, 4, 12), {}, { _, _, _ -> }, {}, {}, {}, { _, _, _, _ -> },
                onRestore = { restored = it })
        } } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Restore"))
        compose.onNodeWithText("Sport").assertExists()
        compose.onNodeWithText("Restore").performClick()
        compose.waitForIdle()
        assertEquals(sport, restored)
    }

    @Test fun aChartedCategoryListsItsLevelsHighestFirstAndHasAChartSwitch() {
        var order: List<String>? = null
        var chart: Pair<String, Boolean>? = null
        val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
        compose.setContent { FreePeriodTheme { Surface {
            DayEntrySettingsScreen(data, LocalDate.of(2026, 4, 12), {}, { _, _, _ -> }, { order = it }, {}, {}, { _, _, _, _ -> },
                onChart = { category, on -> chart = category.overrideKey to on })
        } } }
        compose.onNodeWithText("Pain").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Chart in History"))
        compose.onNode(hasContentDescription("Chart in History") and isToggleable()).assertIsOn()
        // Severe is listed on top; moving it down swaps it with Moderate, stored lowest first.
        val rows = compose.onAllNodes(hasText("Severe") or hasText("Moderate") or hasText("Mild")).fetchSemanticsNodes()
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        assertEquals(listOf("Severe", "Moderate", "Mild"), rows.filter { it in setOf("Severe", "Moderate", "Mild") })
        compose.onNodeWithContentDescription("Move Severe down").performClick()
        assertEquals(listOf("item:pain:NONE", "item:pain:MILD", "item:pain:SEVERE", "item:pain:MODERATE"), order)
        compose.onNode(hasContentDescription("Chart in History") and isToggleable()).performClick()
        assertEquals("category:pain" to false, chart)
    }
}
