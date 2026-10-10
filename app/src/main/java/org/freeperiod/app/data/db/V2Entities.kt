package org.freeperiod.app.data.db

import androidx.room.*

@Entity(tableName = "custom_categories", indices = [Index(value = ["name"], unique = true)])
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val iconKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    @ColumnInfo(defaultValue = "0") val singleChoice: Boolean = false,
    @ColumnInfo(defaultValue = "0") val counted: Boolean = false,
    val itemSet: String? = null,
)

@Entity(tableName = "ui_overrides")
data class UiOverrideEntity(@PrimaryKey val key: String, val hidden: Boolean, val sortOrder: Int,
    val label: String? = null, val iconKey: String? = null)

@Entity(tableName = "situation")
data class SituationEntity(
    @PrimaryKey val id: Int = 0,
    val phase: String,
    val method: String,
    val pillPackStartEpochDay: Long?,
    val pillActiveDays: Int?,
    val pillBreakDays: Int?,
    val fertileWindowEnabled: Boolean,
    @ColumnInfo(defaultValue = "0") val painDiary: Boolean = false,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String?,
    val recurrenceKind: String,
    val recurrenceN: Int? = null,
    val anchorEpochDay: Long? = null,
    val weekday: String? = null,
    val monthDay: Int? = null,
    val onceEpochDay: Long? = null,
    val time: String,
    val enabled: Boolean,
    val daysBefore: Int? = null,
    val lastDeliveredDate: Long? = null,
)

@Entity(tableName = "hint_dismissals", foreignKeys = [ForeignKey(entity = PeriodEntity::class,
    parentColumns = ["id"], childColumns = ["startPeriodId"], onDelete = ForeignKey.CASCADE)])
data class HintDismissalEntity(@PrimaryKey val startPeriodId: Long)
