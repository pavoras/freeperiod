package org.freeperiod.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deviceSettings by preferencesDataStore(name = "device_settings")

enum class LockTimeout { IMMEDIATELY, ONE_MINUTE, FIVE_MINUTES }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val dynamicColor: Boolean = false,
    val lockEnabled: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
    val periodReminder: Boolean = false,
    val periodReminderDaysBefore: Int = 2,
    val dailyReminder: Boolean = false,
    val dailyReminderTime: LocalTime = LocalTime.of(20, 0),
    val explicitNotifications: Boolean = false,
    val lastNotifiedPeriodId: Long? = null,
)

class SettingsStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.deviceSettings)

    val settings: Flow<AppSettings> = store.data.map(::read)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val value = transform(read(prefs))
            prefs[onboardingDone] = value.onboardingDone
            prefs[dynamicColor] = value.dynamicColor
            prefs[lockEnabled] = value.lockEnabled
            prefs[lockTimeout] = value.lockTimeout.name
            prefs[periodReminder] = value.periodReminder
            prefs[periodReminderDaysBefore] = value.periodReminderDaysBefore
            prefs[dailyReminder] = value.dailyReminder
            prefs[dailyReminderTime] = value.dailyReminderTime.toString()
            prefs[explicitNotifications] = value.explicitNotifications
            value.lastNotifiedPeriodId?.let { prefs[lastNotifiedPeriodId] = it }
                ?: prefs.remove(lastNotifiedPeriodId)
        }
    }

    private fun read(prefs: Preferences) = AppSettings(
        onboardingDone = prefs[onboardingDone] ?: false,
        dynamicColor = prefs[dynamicColor] ?: false,
        lockEnabled = prefs[lockEnabled] ?: false,
        lockTimeout = prefs[lockTimeout]?.let(LockTimeout::valueOf) ?: LockTimeout.ONE_MINUTE,
        periodReminder = prefs[periodReminder] ?: false,
        periodReminderDaysBefore = prefs[periodReminderDaysBefore] ?: 2,
        dailyReminder = prefs[dailyReminder] ?: false,
        dailyReminderTime = prefs[dailyReminderTime]?.let(LocalTime::parse) ?: LocalTime.of(20, 0),
        explicitNotifications = prefs[explicitNotifications] ?: false,
        lastNotifiedPeriodId = prefs[lastNotifiedPeriodId],
    )

    private companion object {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val periodReminder = booleanPreferencesKey("period_reminder")
        val periodReminderDaysBefore = intPreferencesKey("period_reminder_days_before")
        val dailyReminder = booleanPreferencesKey("daily_reminder")
        val dailyReminderTime = stringPreferencesKey("daily_reminder_time")
        val explicitNotifications = booleanPreferencesKey("explicit_notifications")
        val lastNotifiedPeriodId = longPreferencesKey("last_notified_period_id")
    }
}
