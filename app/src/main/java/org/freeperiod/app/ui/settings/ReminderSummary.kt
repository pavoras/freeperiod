package org.freeperiod.app.ui.settings

import android.content.res.Resources
import org.freeperiod.app.R
import org.freeperiod.engine.*

internal fun reminderSummary(reminder: Reminder, resources: Resources): String {
    val rhythm = if (reminder.kind == ReminderKind.PERIOD_DUE) resources.getQuantityString(
        R.plurals.reminder_before_period, requireNotNull(reminder.daysBefore), reminder.daysBefore)
    else resources.getString(when (reminder.recurrence) {
        Recurrence.Daily -> R.string.recurrence_daily
        is Recurrence.EveryNDays -> R.string.recurrence_days
        is Recurrence.Weekly -> R.string.recurrence_weekly
        is Recurrence.MonthlyOnDay -> R.string.recurrence_monthly
        is Recurrence.EveryNMonths -> R.string.recurrence_months
        is Recurrence.Once -> R.string.recurrence_once
    })
    return resources.getString(R.string.reminder_summary_time, rhythm, reminder.time.toString())
}
