package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.ItemCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemCategoryDao {

    @Query("SELECT * FROM item_categories WHERE itemId = :itemId")
    fun getCategoriesForItem(itemId: String): Flow<List<ItemCategoryEntity>>

    @Query("SELECT * FROM item_categories")
    fun getAllItemCategories(): Flow<List<ItemCategoryEntity>>

    @Query("SELECT DISTINCT category FROM item_categories ORDER BY category ASC")
    fun getAllDistinctCategories(): Flow<List<String>>

    @Query("SELECT * FROM item_categories")
    suspend fun getAllItemCategoriesSync(): List<ItemCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(entity: ItemCategoryEntity)

    @Query("DELETE FROM item_categories WHERE itemId = :itemId")
    suspend fun deleteCategoriesForItem(itemId: String)

    /** SYNC-014.a: upsert دفعي للـ pull (REPLACE). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoriesFromRemote(items: List<ItemCategoryEntity>)

    /** SYNC-014.a: حذف تصنيفات بمعرّفات محددة (إزالة اليتيمة محلياً عند المزامنة). */
    @Query("DELETE FROM item_categories WHERE id IN (:ids)")
    suspend fun deleteCategoriesByIds(ids: List<String>)
}
