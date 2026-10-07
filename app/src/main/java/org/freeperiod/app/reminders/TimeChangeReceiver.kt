package org.freeperiod.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.freeperiod.app.FreePeriodApp
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore

private val timeChangeActions = setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_BOOT_COMPLETED)

internal suspend fun rescheduleAfterTimeChange(action: String?, repository: Repository, settings: SettingsStore, scheduler: ReminderScheduler) {
    if (action in timeChangeActions) scheduler.sync(settings.settings.first(), repository.snapshot().settings.predictionsPaused)
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in timeChangeActions) return
        val pending = goAsync()
        val container = (context.applicationContext as FreePeriodApp).container
        container.applicationScope.launch {
            try {
                rescheduleAfterTimeChange(intent.action, container.repository, container.settings, container.reminderScheduler)
            } finally { pending.finish() }
        }
    }
}
