package org.freeperiod.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.freeperiod.app.MainActivity
import org.freeperiod.app.R
import org.freeperiod.engine.Reminder
import org.freeperiod.engine.ReminderKind

interface NotificationDelivery {
    fun period(days: Int, explicit: Boolean): Boolean
    fun daily(explicit: Boolean): Boolean
    fun reminder(reminder: Reminder, days: Int?, explicit: Boolean): Boolean = when (reminder.kind) {
        ReminderKind.PERIOD_DUE -> period(requireNotNull(days), explicit)
        else -> daily(explicit)
    }
}

class Notifications(private val context: Context) : NotificationDelivery {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE })
    }

    fun available(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
    }

    override fun period(days: Int, explicit: Boolean): Boolean = post(1,
        if (explicit) context.resources.getQuantityString(R.plurals.reminder_period_explicit, days, days) else context.getString(R.string.reminder_period_neutral))
    override fun daily(explicit: Boolean): Boolean = post(2, context.getString(R.string.reminder_daily))
    override fun reminder(reminder: Reminder, days: Int?, explicit: Boolean): Boolean = post(reminder.id.toInt(), when (reminder.kind) {
        ReminderKind.PERIOD_DUE -> if (explicit) requireNotNull(days).let { context.resources.getQuantityString(R.plurals.reminder_period_explicit, it, it) } else context.getString(R.string.reminder_period_neutral)
        ReminderKind.DAILY_LOG -> context.getString(R.string.reminder_daily)
        else -> if (explicit) reminder.title ?: context.getString(R.string.reminder_general) else context.getString(R.string.reminder_general)
    })

    @SuppressLint("MissingPermission") // available() checks POST_NOTIFICATIONS before notify().
    private fun post(id: Int, text: String): Boolean {
        createChannel()
        if (!available()) return false
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title)).setContentText(text)
            .setContentIntent(intent).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try {
            // Permission, global notifications and channel importance were checked above.
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) { false }
    }

    companion object { const val CHANNEL = "reminders" }
}
