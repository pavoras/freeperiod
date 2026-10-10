package org.freeperiod.engine.backup

import java.time.LocalDate
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    private val today = LocalDate.of(2026, 4, 12)
    private val data = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false))
    @Test fun validatesSchemaTwoAndThreeForRepositoryRestore() {
        assertTrue(validBackup(data, today))
        assertTrue(validBackup(data.copy(schemaVersion = 2), today))
        assertFalse(validBackup(data.copy(schemaVersion = 1), today))
        assertFalse(validBackup(data.copy(tags = listOf(Tag(1, "Walk", categoryId = 99))), today))
    }

    @Test fun rejectsBlankAppearanceAndConflictingOwnChoices() {
        val category = CustomCategory(1, "Mood items", "builtin:mood", 0, false)
        val own = data.copy(customCategories = listOf(category),
            tags = listOf(Tag(1, "Calm", categoryId = 1), Tag(2, "Excited", categoryId = 1)),
            dayLogs = listOf(DayLog(today, tagIds = setOf(1))))
        assertTrue(validBackup(own, today))
        assertFalse(validBackup(own.copy(dayLogs = listOf(DayLog(today, mood = Mood.GOOD, tagIds = setOf(1)))), today))
        assertFalse(validBackup(own.copy(dayLogs = listOf(DayLog(today, tagIds = setOf(1, 2)))), today))
        assertFalse(validBackup(own.copy(customCategories = listOf(category, category.copy(id = 2, name = "Other"))), today))
        assertFalse(validBackup(data.copy(overrides = listOf(UiOverride("category:mood", false, 0, " "))), today))
        assertFalse(validBackup(data.copy(overrides = listOf(UiOverride("item:mood:GOOD", false, 0, iconKey = ""))), today))
        assertTrue(validBackup(data.copy(overrides = listOf(UiOverride("category:mood", false, 0, "Feelings", "leaf"))), today))
    }
}
