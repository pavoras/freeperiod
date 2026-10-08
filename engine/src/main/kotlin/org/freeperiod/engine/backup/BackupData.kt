package org.freeperiod.engine.backup

import kotlinx.serialization.Serializable
import org.freeperiod.engine.*

/** Domain preferences restored atomically with the logged data. */
@Serializable
data class BackupSettings(val typicalCycleLength: Int?, val predictionsPaused: Boolean)

/** The versioned payload of an encrypted backup, excluding device preferences. */
@Serializable
data class BackupData(
    val schemaVersion: Int = 2,
    val periods: List<Period>,
    val dayLogs: List<DayLog>,
    val tags: List<Tag>,
    val settings: BackupSettings,
    val situation: Situation = Situation(),
    val customCategories: List<CustomCategory> = emptyList(),
    val overrides: List<UiOverride> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val hintDismissals: Set<Long> = emptySet(),
)
