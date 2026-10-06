package com.baibaoge.home.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baibaoge.home.data.entity.UserPinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UserPinEntity)

    @Update
    suspend fun update(entity: UserPinEntity)

    @Query("SELECT * FROM user_pin WHERE id = 1 LIMIT 1")
    suspend fun get(): UserPinEntity?

    @Query("SELECT * FROM user_pin WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserPinEntity?>
}
