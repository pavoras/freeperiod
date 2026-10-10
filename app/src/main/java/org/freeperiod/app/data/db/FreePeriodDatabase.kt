package org.freeperiod.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PeriodEntity::class, DayLogEntity::class, TagEntity::class,
    DayTagEntity::class, DomainSettingsEntity::class, CustomCategoryEntity::class, UiOverrideEntity::class,
    SituationEntity::class, ReminderEntity::class, HintDismissalEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FreePeriodDatabase : RoomDatabase() {
    abstract fun periodDao(): PeriodDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun tagDao(): TagDao
    abstract fun domainSettingsDao(): DomainSettingsDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun uiOverrideDao(): UiOverrideDao
    abstract fun situationDao(): SituationDao
    abstract fun reminderDao(): ReminderDao
    abstract fun hintDismissalDao(): HintDismissalDao
}
