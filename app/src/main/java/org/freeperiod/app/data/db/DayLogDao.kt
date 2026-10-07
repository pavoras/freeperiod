package org.freeperiod.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DayLogDao {
    @Transaction
    @Query("SELECT * FROM day_logs ORDER BY epochDay") fun observeWithTags(): Flow<List<DayLogWithTags>>
    @Transaction
    @Query("SELECT * FROM day_logs ORDER BY epochDay") suspend fun getWithTags(): List<DayLogWithTags>
    @Query("SELECT * FROM day_logs ORDER BY epochDay") suspend fun getAll(): List<DayLogEntity>
    @Query("SELECT * FROM day_tags ORDER BY epochDay, tagId") suspend fun getLinks(): List<DayTagEntity>
    @Upsert suspend fun upsert(log: DayLogEntity)
    @Insert suspend fun insertLinks(links: List<DayTagEntity>)
    @Query("DELETE FROM day_tags WHERE epochDay = :epochDay") suspend fun deleteLinks(epochDay: Long)
    @Query("DELETE FROM day_logs WHERE epochDay = :epochDay") suspend fun delete(epochDay: Long)
    @Query("DELETE FROM day_logs") suspend fun deleteAll()
}
