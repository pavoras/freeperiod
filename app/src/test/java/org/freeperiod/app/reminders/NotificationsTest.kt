package org.freeperiod.app.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.freeperiod.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsTest {
    @Test fun neutralNotificationContainsNoPeriodDetails() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = Notifications(app)
        assertTrue(notifications.period(2, explicit = false))
        val value = app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
        assertEquals(app.getString(R.string.reminder_title), value.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(app.getString(R.string.reminder_period_neutral), value.extras.getString(Notification.EXTRA_TEXT))
    }
    @Test fun disabledChannelBlocksDelivery() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Notifications.CHANNEL, "Test", NotificationManager.IMPORTANCE_NONE))
        assertFalse(Notifications(app).period(2, explicit = true))
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun deniedPermissionBlocksDelivery() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(Notifications(app).period(2, explicit = true))
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }
}
