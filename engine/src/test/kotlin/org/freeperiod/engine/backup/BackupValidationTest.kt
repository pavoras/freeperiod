package org.freeperiod.engine.backup

import java.time.LocalDate
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private val today = LocalDate.of(2026, 4, 12)
    private val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
    @Test fun validatesSchemaTwoForRepositoryRestore() {
        assertTrue(validBackup(data, today))
        assertFalse(validBackup(data.copy(schemaVersion = 1), today))
        assertFalse(validBackup(data.copy(tags = listOf(Tag(1, "Walk", categoryId = 99))), today))
    }
}
