package org.freeperiod.app.ui.settings

import java.time.LocalDate
import org.freeperiod.engine.Period
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class ImportScreenshotTest : SettingsScreenshotFixture() {
    @Test fun preview_enLight() = capture("import/preview_enLight", "Choose CSV file") {
        val periods = listOf("2026-01-02", "2026-02-01", "2026-03-03").map {
            val start = LocalDate.parse(it)
            Period(0, start, start.plusDays(4))
        }
        ImportScreen(ImportUiState(periods = periods), {}, {}, {}, {}, {})
    }

    @Test fun periodCalendar_enLight() = capture("import/periodCalendar_enLight", "File from another app") {
        val start = LocalDate.parse("2026-03-02")
        ImportUiState(periods = listOf(Period(0, start, start.plusDays(4))), dayLogs = listOf(org.freeperiod.engine.DayLog(start, note = "tired")),
            notice = org.freeperiod.app.R.string.import_period_calendar_partial, merges = true).let { ImportScreen(it, {}, {}, {}, {}, {}) }
    }

    @Test fun floPassword_enLight() = capture("import/floPassword_enLight", "Enter the password from Flo's e-mail.") {
        ImportScreen(ImportUiState(floPassword = true), {}, {}, {}, {}, {})
    }
}
