package org.freeperiod.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY id") fun observeAll(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags ORDER BY id") suspend fun getAll(): List<TagEntity>
    @Insert suspend fun insert(tag: TagEntity): Long
    @Update suspend fun update(tag: TagEntity)
    @Query("UPDATE tags SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE tags SET archived = 1 WHERE id = :id") suspend fun archive(id: Long)
    @Query("DELETE FROM tags") suspend fun deleteAll()
}
