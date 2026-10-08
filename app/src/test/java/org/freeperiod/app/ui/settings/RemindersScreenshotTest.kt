package org.freeperiod.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FreePeriodTheme
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class RemindersScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val today = LocalDate.of(2026, 4, 12)
    @Before fun setup() { compose.activity.setTheme(R.style.Theme_FreePeriod); Locale.setDefault(Locale.US) }
    @After fun reset() { Locale.setDefault(originalLocale) }
    @Test fun list_enLight() {
        val reminders = listOf(Reminder(1, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), false, 2),
            Reminder(2, ReminderKind.DAILY_LOG, null, Recurrence.Daily, LocalTime.of(20, 0), false),
            Reminder(3, ReminderKind.CUSTOM, "A little time outside", Recurrence.Weekly(java.time.DayOfWeek.SUNDAY), LocalTime.of(10, 0), true))
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { RemindersScreen(reminders, {}, {}, {}, {}, {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/reminders/list_enLight.png")
    }
    @Test fun editor_enLight() {
        compose.setContent { FreePeriodTheme(darkTheme = false) { Surface { ReminderEditor(
            Reminder(0, ReminderKind.CUSTOM, null, Recurrence.Daily, LocalTime.of(20, 0), false), today, {}, {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/reminders/editor_enLight.png")
    }
}
