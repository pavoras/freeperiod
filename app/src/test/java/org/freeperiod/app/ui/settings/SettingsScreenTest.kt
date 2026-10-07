package org.freeperiod.app.ui.settings

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val originalSdk = Build.VERSION.SDK_INT
    @After fun restoreSdk() { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdk) }

    private fun show(sdk: Int, actions: SettingsActions = SettingsActions()) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        compose.setContent { FreePeriodTheme { SettingsScreen(SettingsUiState(loading = false), actions) } }
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    @Test fun api30HidesLanguageAndDynamicColor() {
        show(30)
        compose.onNodeWithText("Language").assertDoesNotExist()
        compose.onNodeWithText("Use wallpaper colours").assertDoesNotExist()
    }

    @Test fun api32ShowsDynamicColorButHidesLanguage() {
        show(32)
        compose.onNodeWithText("Language").assertDoesNotExist()
        scrollTo("Use wallpaper colours")
        compose.onNodeWithText("Use wallpaper colours").assertExists()
    }

    @Test fun api33ShowsLanguage() {
        show(33)
        scrollTo("Language")
        compose.onNodeWithText("Language").assertExists()
    }

    @Test fun csvRequiresWarningConfirmation() {
        var requests = 0
        show(35, SettingsActions(csv = { requests++ }))
        scrollTo("Export as CSV")
        compose.onNodeWithText("Export as CSV").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("CSV files are not encrypted.", substring = true).assertExists()
        assertEquals(0, requests)
        compose.onNode(hasText("Export as CSV") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(1, requests)
    }

    @Test fun deleteRequiresWarningConfirmation() {
        var deletions = 0
        show(35, SettingsActions(deleteAll = { deletions++ }))
        scrollTo("Delete all data")
        compose.onNodeWithText("Delete all data").performClick()
        compose.waitForIdle()
        assertEquals(0, deletions)
        compose.onNode(hasText("Delete all data") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(1, deletions)
    }
}
