package org.freeperiod.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY id") fun observeAll(): Flow<List<CustomCategoryEntity>>
    @Query("SELECT * FROM custom_categories ORDER BY id") suspend fun getAll(): List<CustomCategoryEntity>
    @Insert suspend fun insert(category: CustomCategoryEntity): Long
    @Update suspend fun update(category: CustomCategoryEntity)
    @Query("DELETE FROM custom_categories") suspend fun deleteAll()
}

@Dao
interface UiOverrideDao {
    @Query("SELECT * FROM ui_overrides ORDER BY `key`") fun observeAll(): Flow<List<UiOverrideEntity>>
    @Query("SELECT * FROM ui_overrides ORDER BY `key`") suspend fun getAll(): List<UiOverrideEntity>
    @Upsert suspend fun upsert(override: UiOverrideEntity)
    @Query("DELETE FROM ui_overrides WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM ui_overrides") suspend fun deleteAll()
}

@Dao
interface SituationDao {
    @Query("SELECT * FROM situation WHERE id = 0") fun observe(): Flow<SituationEntity?>
    @Query("SELECT * FROM situation WHERE id = 0") suspend fun get(): SituationEntity?
    @Upsert suspend fun upsert(situation: SituationEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY id") fun observeAll(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders ORDER BY id") suspend fun getAll(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: Long): ReminderEntity?
    @Insert suspend fun insert(reminder: ReminderEntity): Long
    @Update suspend fun update(reminder: ReminderEntity)
    @Query("UPDATE reminders SET lastDeliveredDate = :epochDay WHERE id = :id") suspend fun markDelivered(id: Long, epochDay: Long)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM reminders") suspend fun deleteAll()
}

@Dao
interface HintDismissalDao {
    @Query("SELECT startPeriodId FROM hint_dismissals ORDER BY startPeriodId") fun observeAll(): Flow<List<Long>>
    @Query("SELECT startPeriodId FROM hint_dismissals ORDER BY startPeriodId") suspend fun getAll(): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(dismissal: HintDismissalEntity)
    @Query("DELETE FROM hint_dismissals") suspend fun deleteAll()
}
