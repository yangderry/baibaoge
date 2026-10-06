package com.baibaoge.home.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 同步日志表 sync_log（设计方案 4.2） */
@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 1=自动 2=手动 */
    @ColumnInfo(name = "sync_type") val syncType: Int,
    /** upload / download */
    val direction: String,
    /** success / failed */
    val result: String,
    /** 备份文件版本标识 */
    @ColumnInfo(name = "file_version") val fileVersion: String? = null,
    /** 失败原因或备注 */
    val message: String? = null,
    @ColumnInfo(name = "sync_time") val syncTime: Long
)
