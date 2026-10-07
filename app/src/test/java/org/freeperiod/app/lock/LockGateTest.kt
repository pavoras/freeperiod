package org.freeperiod.app.lock

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.freeperiod.app.FreePeriodApp
import org.freeperiod.app.MainActivity
import org.freeperiod.app.data.LockTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class LockGateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val settings get() = (compose.activity.application as FreePeriodApp).container.settings
    @After fun resetSettings() = runBlocking { settings.update { it.copy(lockEnabled = false, onboardingDone = false) } }

    @Test fun enabledLockHidesContentAndSecuresWindow() {
        runBlocking { settings.update { it.copy(onboardingDone = true, lockEnabled = true) } }
        compose.waitUntil(10_000) { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Typical cycle length").assertExists()
        compose.runOnIdle {
            val lock = ViewModelProvider(compose.activity)[AppLock::class.java]
            lock.background(0)
            lock.resume(true, LockTimeout.IMMEDIATELY, 1)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Unlock to open FreePeriod.").assertExists()
        compose.onNodeWithText("Today").assertDoesNotExist()
        compose.onNodeWithText("Typical cycle length").assertDoesNotExist()
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppLock::class.java].unlock() }
        compose.waitForIdle()
        compose.onNodeWithText("Typical cycle length").assertExists()
        runBlocking { settings.update { it.copy(lockEnabled = false) } }
        compose.waitUntil(10_000) { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }
        compose.waitForIdle()
        compose.onNodeWithText("Unlock to open FreePeriod.").assertDoesNotExist()
        assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }
}
