package org.freeperiod.app.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.time.LocalTime
import java.util.Locale
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSummaryTest {
    @Test fun periodDueShowsOffsetInsteadOfDaily() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reminder = Reminder(1, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), false, 2)
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.US) }).resources
        val german = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.GERMANY) }).resources
        assertEquals("2 days before your period · 20:00", reminderSummary(reminder, english))
        assertEquals("2 Tage vor deiner Periode · 20:00", reminderSummary(reminder, german))
    }
}
