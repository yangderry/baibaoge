package com.baibaoge.home.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baibaoge.home.data.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("SELECT * FROM locations WHERE location_id = :id LIMIT 1")
    suspend fun getById(id: String): LocationEntity?

    @Query("SELECT * FROM locations WHERE location_name = :name LIMIT 1")
    suspend fun getByName(name: String): LocationEntity?

    @Query("SELECT * FROM locations ORDER BY create_time DESC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun count(): Int

    /** 列出所有地点及其在库物品数（联合查询） */
    @Query("""
        SELECT locations.*, (SELECT COUNT(*) FROM items WHERE items.location_id = locations.location_id AND items.status = 1) AS itemCount
        FROM locations
        ORDER BY create_time DESC
    """)
    fun observeWithItemCount(): Flow<List<LocationWithCount>>

    data class LocationWithCount(
        @Embedded val location: LocationEntity,
        val itemCount: Int
    )
}
