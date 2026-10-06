package com.baibaoge.home.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baibaoge.home.data.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY sync_time DESC LIMIT 100")
    fun observeRecent(): Flow<List<SyncLogEntity>>
}
