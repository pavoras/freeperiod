package org.freeperiod.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainSettingsDao {
    @Query("SELECT * FROM domain_settings WHERE id = 0") fun observe(): Flow<DomainSettingsEntity?>
    @Query("SELECT * FROM domain_settings WHERE id = 0") suspend fun get(): DomainSettingsEntity?
    @Upsert suspend fun upsert(settings: DomainSettingsEntity)
}
