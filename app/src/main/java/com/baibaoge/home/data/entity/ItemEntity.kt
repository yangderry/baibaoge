package com.baibaoge.home.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 物品表 items（设计方案 4.2）
 * item_type: 1=食品 2=药品 3=衣物 4=其他；status: 1=在库 0=已归档
 * 时间字段均为毫秒时间戳
 */
@Entity(
    tableName = "items",
    indices = [
        Index("item_type"),
        Index("name"),
        Index(value = ["barcode"], unique = true),
        Index("expiry_date"),
        Index("location_id"),
        Index("status")
    ]
)
data class ItemEntity(
    /** 编码规则：类型前缀(F/M/C/O) + yyyyMMddHHmmss + 2位随机数 */
    @PrimaryKey
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "item_type") val itemType: Int,
    val name: String,
    val barcode: String? = null,
    @ColumnInfo(name = "purchase_date") val purchaseDate: Long? = null,
    @ColumnInfo(name = "produce_date") val produceDate: Long? = null,
    @ColumnInfo(name = "expiry_date") val expiryDate: Long? = null,
    val quantity: Int = 1,
    @ColumnInfo(name = "purchase_channel") val purchaseChannel: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "item_qr_path") val itemQrPath: String? = null,
    val status: Int = 1,
    /** 自定义标签，逗号分隔 */
    val tags: String? = null,
    @ColumnInfo(name = "create_time") val createTime: Long,
    @ColumnInfo(name = "update_time") val updateTime: Long
) {
    companion object {
        const val TYPE_FOOD = 1
        const val TYPE_MEDICINE = 2
        const val TYPE_CLOTHING = 3
        const val TYPE_OTHER = 4

        const val STATUS_IN_STOCK = 1
        const val STATUS_ARCHIVED = 0
    }
}
