package org.freeperiod.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.freeperiod.app.FreePeriodApp
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*

class ReminderDelivery(private val repository: Repository, private val settings: SettingsStore,
    private val notifications: NotificationDelivery, private val clock: () -> LocalDate) {
    private val periodDelivery = Mutex()
    suspend fun period() = periodDelivery.withLock {
        val device = settings.settings.first()
        if (!device.periodReminder) return@withLock
        val data = repository.snapshot()
        val today = clock()
        val state = predict(data.periods, PredictionSettings(data.settings.typicalCycleLength, data.settings.predictionsPaused), today)
        val periodId = data.periods.filter { it.start <= today }.maxByOrNull { it.start }?.id
        if (periodReminderDue(state, periodId, device.periodReminderDaysBefore, today, device.lastNotifiedPeriodId)) {
            val days = ChronoUnit.DAYS.between(today, (state as PredictionState.Range).earliest).toInt()
            if (notifications.period(days, device.explicitNotifications)) {
                settings.update { it.copy(lastNotifiedPeriodId = periodId) }
            }
        }
    }

    suspend fun daily() {
        val device = settings.settings.first()
        if (device.dailyReminder) notifications.daily(device.explicitNotifications)
    }
}

class ReminderWorker internal constructor(context: Context, params: WorkerParameters,
    private val delivery: ReminderDelivery) : CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) : this(context, params,
        (context.applicationContext as FreePeriodApp).container.reminderDelivery)
    override suspend fun doWork(): Result = try {
        delivery.period()
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}

class DailyReminderWorker internal constructor(context: Context, params: WorkerParameters,
    private val delivery: ReminderDelivery, private val settings: SettingsStore,
    private val scheduler: ReminderScheduler) : CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) : this(context, params,
        (context.applicationContext as FreePeriodApp).container.reminderDelivery,
        (context.applicationContext as FreePeriodApp).container.settings,
        (context.applicationContext as FreePeriodApp).container.reminderScheduler)
    override suspend fun doWork(): Result = try {
        delivery.daily()
        scheduler.afterDaily(settings.settings.first())
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}
