package org.freeperiod.app.ui.today

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionRangeFormatterTest {
    private val today = LocalDate.of(2026, 4, 12)

    @Test fun sameMonthEnglish() = assertEquals("Apr 28–30", range("2026-04-28", "2026-04-30", Locale.US))
    @Test fun sameMonthGerman() = assertEquals("28.–30. Apr.", range("2026-04-28", "2026-04-30", Locale.GERMANY))
    @Test fun acrossMonthsEnglish() = assertEquals("Apr 29 – May 2", range("2026-04-29", "2026-05-02", Locale.US))
    @Test fun acrossMonthsGerman() = assertEquals("29. Apr. – 2. Mai", range("2026-04-29", "2026-05-02", Locale.GERMANY))
    @Test fun outsideCurrentYearEnglish() = assertEquals("Dec 29, 2025 – Jan 2", range("2025-12-29", "2026-01-02", Locale.US))
    @Test fun outsideCurrentYearGerman() = assertEquals("29. Dez. 2025 – 2. Jan.", range("2025-12-29", "2026-01-02", Locale.GERMANY))

    private fun range(start: String, end: String, locale: Locale) =
        formatPredictionRange(LocalDate.parse(start), LocalDate.parse(end), today, locale)
}
