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
}
