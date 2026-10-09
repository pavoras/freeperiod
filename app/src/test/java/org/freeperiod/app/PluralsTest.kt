package org.freeperiod.app

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluralsTest {
    private fun text(locale: Locale, id: Int, count: Int, vararg args: Any): String {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
        return context.resources.getQuantityString(id, count, count, *args)
    }

    @Test fun countsReadNaturallyForOneAndMany() {
        assertEquals("based on your last cycle", text(Locale.US, R.plurals.basis_history, 1))
        assertEquals("based on your last 4 cycles", text(Locale.US, R.plurals.basis_history, 4))
        assertEquals("Your expected range has passed (1 day ago)", text(Locale.US, R.plurals.today_range_passed, 1))
        assertEquals("Your period may start tomorrow", text(Locale.US, R.plurals.reminder_period_explicit, 1))
        assertEquals("basierend auf deinem letzten Zyklus", text(Locale.GERMANY, R.plurals.basis_history, 1))
        assertEquals("Dein erwarteter Zeitraum ist vorbei (vor 1 Tag)", text(Locale.GERMANY, R.plurals.today_range_passed, 1))
        assertEquals("1 Periode vom 2. März bis 6. März", text(Locale.GERMANY, R.plurals.import_preview, 1, "2. März", "6. März"))
        assertEquals("12 Tage erfasst", text(Locale.GERMANY, R.plurals.restore_days, 12))
    }
}
