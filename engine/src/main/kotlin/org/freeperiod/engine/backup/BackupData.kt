package org.freeperiod.engine.backup

import kotlinx.serialization.Serializable
import org.freeperiod.engine.DayLog
import org.freeperiod.engine.Period
import org.freeperiod.engine.Tag

/** Domain preferences restored atomically with the logged data. */
@Serializable
data class BackupSettings(val typicalCycleLength: Int?, val predictionsPaused: Boolean)

/** The versioned payload of an encrypted backup, excluding device preferences. */
@Serializable
data class BackupData(
    val schemaVersion: Int = 1,
    val periods: List<Period>,
    val dayLogs: List<DayLog>,
    val tags: List<Tag>,
    val settings: BackupSettings,
)
