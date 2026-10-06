package com.baibaoge.home.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.baibaoge.home.data.dao.ItemDao
import com.baibaoge.home.data.dao.LocationDao
import com.baibaoge.home.data.dao.SyncLogDao
import com.baibaoge.home.data.dao.UserPinDao
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.data.entity.LocationEntity
import com.baibaoge.home.data.entity.SyncLogEntity
import com.baibaoge.home.data.entity.UserPinEntity

/** Room 数据库：单例 baobaoge_db（设计方案 4.2） */
@Database(
    entities = [ItemEntity::class, LocationEntity::class, UserPinEntity::class, SyncLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun locationDao(): LocationDao
    abstract fun userPinDao(): UserPinDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baobaoge_db"
                )
                    // 开发阶段：schema 变化时直接重建，避免旧库 identity hash 不匹配闪退
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }

        /** 从 NAS 恢复备份前：关闭当前连接并重置单例（下次 getInstance 重新打开） */
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
