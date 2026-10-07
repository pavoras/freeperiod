package org.freeperiod.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PeriodEntity::class, DayLogEntity::class, TagEntity::class,
    DayTagEntity::class, DomainSettingsEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FreePeriodDatabase : RoomDatabase() {
    abstract fun periodDao(): PeriodDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun tagDao(): TagDao
    abstract fun domainSettingsDao(): DomainSettingsDao
}
