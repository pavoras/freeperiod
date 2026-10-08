package org.freeperiod.app.ui.today

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimelineDescriptionTest {
    private fun date(value: String) = LocalDate.parse(value)
    private val today = date("2026-04-12")
    private val timeline = CycleTimeline(date("2026-04-10"), date("2026-05-09"), today,
        date("2026-04-10")..today, date("2026-04-13")..date("2026-04-14"),
        date("2026-05-07")..date("2026-05-09"), BracketKind.LIKELY)

    @Test fun describesRecordedExpectedTodayAndLikelyRange() {
        assertEquals("Cycle from 10 April. Period recorded 10 to 12 April. Expected until 14 April. Today 12 April. Next period likely 7 to 9 May.",
            describe(timeline, Locale.US))
    }

    @Test fun germanDescriptionUsesGermanDatesAndWording() {
        assertEquals("Zyklus ab 10. April. Periode vom 10. bis 12. April erfasst. Erwartet bis 14. April. Heute ist der 12. April. Nächste Periode voraussichtlich vom 7. bis 9. Mai.",
            describe(timeline, Locale.GERMANY))
    }

    @Test fun scheduledBreakIsNeverCalledALikelyPeriod() {
        val value = describe(timeline.copy(expectedPeriodRest = null, bracketKind = BracketKind.SCHEDULED_BREAK), Locale.US)
        assertTrue(value.startsWith("Pack from 10 April."))
        assertTrue(value.endsWith("Scheduled break 7 to 9 May."))
        assertFalse(value.contains("likely"))
        assertFalse(value.contains("Expected"))
    }

    @Test fun noPredictionDoesNotInventARange() {
        assertEquals("Cycle from 10 April. Period recorded 10 to 12 April. Today 12 April.",
            describe(timeline.copy(expectedPeriodRest = null, bracket = null, bracketKind = null), Locale.US))
    }

    private fun describe(value: CycleTimeline, locale: Locale): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
        return timelineDescription(value, localized, locale)
    }
}
