package com.baibaoge.home.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baibaoge.home.data.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity)

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("SELECT * FROM items WHERE item_id = :id LIMIT 1")
    suspend fun getById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE item_id = :id LIMIT 1")
    fun observeById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE barcode = :barcode AND status = 1 LIMIT 1")
    suspend fun getInStockByBarcode(barcode: String): ItemEntity?

    /** 在库物品列表（按更新时间倒序） */
    @Query("SELECT * FROM items WHERE status = 1 ORDER BY update_time DESC")
    fun observeInStock(): Flow<List<ItemEntity>>

    /** 历史档案（已归档） */
    @Query("SELECT * FROM items WHERE status = 0 ORDER BY update_time DESC")
    fun observeArchived(): Flow<List<ItemEntity>>

    /** 名称模糊搜索（在库） */
    @Query("SELECT * FROM items WHERE status = 1 AND name LIKE '%' || :keyword || '%' ORDER BY update_time DESC")
    fun searchInStock(keyword: String): Flow<List<ItemEntity>>

    /** 按物品类型筛选（在库） */
    @Query("SELECT * FROM items WHERE status = 1 AND item_type = :type ORDER BY update_time DESC")
    fun observeInStockByType(type: Int): Flow<List<ItemEntity>>

    /** 按类型+名称模糊搜索（在库） */
    @Query("SELECT * FROM items WHERE status = 1 AND item_type = :type AND name LIKE '%' || :keyword || '%' ORDER BY update_time DESC")
    fun searchInStockByType(type: Int, keyword: String): Flow<List<ItemEntity>>

    /** 名称模糊搜索（在库，不分类型） */
    @Query("SELECT * FROM items WHERE status = 1 AND name LIKE '%' || :keyword || '%' ORDER BY update_time DESC")
    fun searchInStockAll(keyword: String): Flow<List<ItemEntity>>

    /** 某地点下的在库物品 */
    @Query("SELECT * FROM items WHERE status = 1 AND location_id = :locationId ORDER BY update_time DESC")
    fun observeInStockByLocation(locationId: String): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM items WHERE status = 1 AND location_id = :locationId")
    suspend fun countInStockByLocation(locationId: String): Int

    /** 临期查询：在库且到期日期不晚于 deadline */
    @Query("SELECT * FROM items WHERE status = 1 AND expiry_date IS NOT NULL AND expiry_date <= :deadline ORDER BY expiry_date ASC")
    fun observeExpiringBefore(deadline: Long): Flow<List<ItemEntity>>

    /** 临期检查用：全部在库且有到期日期的物品 */
    @Query("SELECT * FROM items WHERE status = 1 AND expiry_date IS NOT NULL AND expiry_date > 0")
    suspend fun getInStockWithExpiry(): List<ItemEntity>

    @Query("UPDATE items SET quantity = :quantity, update_time = :time WHERE item_id = :id")
    suspend fun updateQuantity(id: String, quantity: Int, time: Long)

    @Query("UPDATE items SET status = :status, update_time = :time WHERE item_id = :id")
    suspend fun updateStatus(id: String, status: Int, time: Long)
}
