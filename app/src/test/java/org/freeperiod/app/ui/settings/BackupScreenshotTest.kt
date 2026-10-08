package org.freeperiod.app.ui.settings

import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class BackupScreenshotTest : SettingsScreenshotFixture() {
    @Test fun backupForm_enLight() = capture("backup/backupForm_enLight", "Backup & restore") {
        BackupScreen(BackupUiState(), { _, _ -> }, {}, {}, {}, {}, {}, onImport = {})
    }
    @Test @Config(sdk = [35], qualifiers = "de-rDE-w360dp-h800dp-xxhdpi")
    fun restoreSummary_deLight() = capture("backup/restoreSummary_deLight", "3 Perioden, 12 Tage erfasst", Locale.GERMANY) {
        BackupScreen(BackupUiState(restoreFileSelected = true, summary = BackupSummary(3, clock().dayOfMonth)),
            { _, _ -> }, {}, {}, {}, {}, {})
    }
}
