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
    private val deliveries = Mutex()
    suspend fun deliver(id: Long, date: LocalDate): Boolean = deliveries.withLock {
        val data = repository.snapshot()
        val reminder = data.reminders.find { it.id == id && it.enabled } ?: return@withLock true
        if (repository.lastDeliveredDate(id)?.let { it >= date } == true) return@withLock true
        val today = clock()
        if (date > today) return@withLock true
        val device = settings.settings.first()
        val days = if (reminder.kind == ReminderKind.PERIOD_DUE) {
            if (data.periodRemindersPaused()) return@withLock true
            val prediction = predict(data.periods, PredictionSettings(data.settings.typicalCycleLength,
                data.settings.predictionsPaused), data.situation, today) as? PredictionState.Range ?: return@withLock true
            if (date != prediction.earliest.minusDays(requireNotNull(reminder.daysBefore).toLong()) || today > prediction.earliest) return@withLock true
            ChronoUnit.DAYS.between(today, prediction.earliest).toInt()
        } else {
            if (reminder.recurrence.nextDate(date, inclusive = true) != date || today != date) return@withLock true
            null
        }
        val posted = notifications.reminder(reminder, days, device.explicitNotifications)
        if (posted) repository.markReminderDelivered(id, date)
        // Blocked period notifications retain the catch-up opportunity through WorkManager backoff.
        posted || reminder.kind != ReminderKind.PERIOD_DUE
    }
}

class ScheduledReminderWorker internal constructor(context: Context, params: WorkerParameters,
    private val delivery: ReminderDelivery, private val repository: Repository,
    private val scheduler: ReminderScheduler) : CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) : this(context, params,
        (context.applicationContext as FreePeriodApp).container.reminderDelivery,
        (context.applicationContext as FreePeriodApp).container.repository,
        (context.applicationContext as FreePeriodApp).container.reminderScheduler)
    override suspend fun doWork(): Result = try {
        val id = inputData.getLong("reminderId", -1)
        val epoch = inputData.getLong("date", Long.MIN_VALUE)
        if (id <= 0 || epoch == Long.MIN_VALUE) Result.failure()
        else if (!delivery.deliver(id, LocalDate.ofEpochDay(epoch))) Result.retry()
        else { scheduler.reconcile(repository); Result.success() }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}
