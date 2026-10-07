package org.freeperiod.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {
    @Query("SELECT * FROM periods ORDER BY startEpochDay, id") fun observeAll(): Flow<List<PeriodEntity>>
    @Query("SELECT * FROM periods ORDER BY startEpochDay, id") suspend fun getAll(): List<PeriodEntity>
    @Query("SELECT * FROM periods WHERE id = :id") suspend fun get(id: Long): PeriodEntity?
    @Insert suspend fun insert(period: PeriodEntity): Long
    @Update suspend fun update(period: PeriodEntity)
    @Query("DELETE FROM periods WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM periods") suspend fun deleteAll()
}
