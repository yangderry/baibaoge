package com.baibaoge.home.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 存放地点表 locations（设计方案 4.2） */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["location_name"], unique = true)]
)
data class LocationEntity(
    /** 编码规则：L + 时间戳 + 2位随机数 */
    @PrimaryKey
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "qr_path") val qrPath: String? = null,
    /** 父地点 ID，预留多级层级 */
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "create_time") val createTime: Long
)
