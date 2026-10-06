package com.baibaoge.home.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** PIN 认证表 user_pin（设计方案 4.2）：id=1 单条记录 */
@Entity(tableName = "user_pin")
data class UserPinEntity(
    @PrimaryKey val id: Int = 1,
    /** PIN 码加盐 SHA-256 哈希值 */
    @ColumnInfo(name = "pin_hash") val pinHash: String,
    /** 随机盐值 */
    val salt: String,
    /** 是否启用指纹：0=否 1=是 */
    @ColumnInfo(name = "fingerprint_enabled") val fingerprintEnabled: Int = 0
)
