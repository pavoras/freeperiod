package org.freeperiod.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var settingsStore: SettingsStore
    private val file get() = File(temporary.root, "settings.preferences_pb")

    @Before fun createStore() { settingsStore = openStore() }
    @After fun closeStore() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    private fun openStore(): SettingsStore {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test fun defaultsMatchSpec() = runBlocking {
        val settings = settingsStore.settings.first()
        assertEquals(AppSettings(), settings)
        assertFalse(settings.onboardingDone)
        assertFalse(settings.dynamicColor)
        assertFalse(settings.lockEnabled)
        assertEquals(LockTimeout.ONE_MINUTE, settings.lockTimeout)
        assertFalse(settings.periodReminder)
        assertEquals(2, settings.periodReminderDaysBefore)
        assertFalse(settings.dailyReminder)
        assertEquals(LocalTime.of(20, 0), settings.dailyReminderTime)
        assertFalse(settings.explicitNotifications)
        assertNull(settings.lastNotifiedPeriodId)
    }

    @Test fun updatePersists() = runBlocking {
        val expected = AppSettings(true, true, true, LockTimeout.FIVE_MINUTES,
            true, 4, true, LocalTime.of(8, 15), true, 42)
        settingsStore.update { expected }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        assertEquals(expected, openStore().settings.first())
    }
}
