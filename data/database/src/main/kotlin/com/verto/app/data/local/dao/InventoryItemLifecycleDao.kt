package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryItemLifecycleDao {
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertItemRaw(item: InventoryItemEntity)

@Update
suspend fun updateItemRaw(item: InventoryItemEntity)

@Transaction
suspend fun insertItem(item: InventoryItemEntity) =
    insertItemRaw(item.withSearchKeys())

@Transaction
suspend fun updateItem(item: InventoryItemEntity) =
    updateItemRaw(item.withSearchKeys())

/** Session 308 REMOTE_APPLY archive: authoritative remote state must not create local dirty work. */
@Query("UPDATE inventory_items SET is_archived = 1, archived_at = :archivedAt, archived_by = :archivedBy, isDirty = 0, updatedAt = :changedAt WHERE id = :id")
suspend fun archiveItemFromRemote(id: String, archivedAt: Long?, archivedBy: String?, changedAt: Long): Int

@Query("UPDATE inventory_items SET is_archived = 1, archived_at = :archivedAt, archived_by = :archivedBy, isDirty = 1 WHERE id = :id AND is_archived = 0")
suspend fun archiveItem(id: String, archivedBy: String = "LOCAL_USER", archivedAt: Long = System.currentTimeMillis()): Int

@Query("UPDATE inventory_items SET is_archived = 0, archived_at = NULL, archived_by = NULL, isDirty = 1, updatedAt = :now WHERE id = :id AND is_archived = 1")
suspend fun reactivateItem(id: String, now: Long = System.currentTimeMillis()): Int

@Transaction
suspend fun deleteItem(id: String) {
    check(archiveItem(id) == 1) { "الصنف غير موجود أو مؤرشف مسبقاً" }
}

// SYNC-012: تغيّر الكمية/السعر محلياً ⇒ تعليم الصف متسخاً (يغطّي كل مسارات البيع/التعديل)
@Query("UPDATE inventory_items SET quantity = :newQty, updatedAt = :now, isDirty = 1 WHERE id = :id")
suspend fun updateQuantity(id: String, newQty: Int, now: Long = System.currentTimeMillis())

/** F245: compare-and-set stock deduction; row count is the concurrency contract. */
@Query(
    """
    UPDATE inventory_items
    SET quantity = quantity - :quantity, updatedAt = :now, isDirty = 1
    WHERE id = :id
      AND (:allowNegativeStock = 1 OR quantity >= :quantity)
    """
)
suspend fun deductQuantityConditional(
    id: String,
    quantity: Int,
    allowNegativeStock: Boolean,
    now: Long = System.currentTimeMillis(),
): Int

/** F245: arithmetic happens in SQLite, so callers never write a stale quantity snapshot. */
@Query(
    """
    UPDATE inventory_items
    SET quantity = quantity + :quantity, updatedAt = :now, isDirty = 1
    WHERE id = :id
    """
)
suspend fun addQuantityAtomic(
    id: String,
    quantity: Int,
    now: Long = System.currentTimeMillis(),
): Int

/** F247: quantity and latest purchase price are one SQLite mutation; stale snapshots cannot overwrite stock. */
@Query(
    """
    UPDATE inventory_items
    SET quantity = quantity + :quantity,
        buyPrice = :buyPrice,
        buy_price_minor = :buyPriceMinor,
        sellPrice = CASE WHEN :updateSellPrice = 1 THEN :sellPrice ELSE sellPrice END,
        sell_price_minor = CASE WHEN :updateSellPrice = 1 THEN :sellPriceMinor ELSE sell_price_minor END,
        updatedAt = :now,
        isDirty = 1
    WHERE id = :id
    """
)
suspend fun addQuantityAtLatestPurchasePriceAtomic(
    id: String,
    quantity: Int,
    buyPrice: Double,
    buyPriceMinor: Long,
    updateSellPrice: Boolean,
    sellPrice: Double,
    sellPriceMinor: Long,
    now: Long = System.currentTimeMillis(),
): Int

@Query(
    """
    UPDATE inventory_items
    SET buyPrice = :buyPrice,
        buy_price_minor = :buyPriceMinor,
        updatedAt = :now,
        isDirty = 1
    WHERE id = :id
    """
)
suspend fun updateLatestPurchasePriceAtomic(
    id: String,
    buyPrice: Double,
    buyPriceMinor: Long,
    now: Long = System.currentTimeMillis(),
): Int

}
