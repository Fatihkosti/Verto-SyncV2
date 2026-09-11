package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryMaintenanceDao {
// ── المخزون الراكد ────────────────────────────────
@Query("""
    SELECT * FROM inventory_items
    WHERE isService = 0 AND is_archived = 0
      AND quantity > 0
      AND id NOT IN (
          SELECT DISTINCT itemId FROM inventory_movements
          WHERE movement_kind = 'SALE'
            AND COALESCE(occurred_at, createdAt) >= :sinceTimestamp
      )
    ORDER BY name ASC
""")
suspend fun getSlowMovingItemsSync(sinceTimestamp: Long): List<InventoryItemEntity>

// ── مزامنة ─────────────────────────────────────────
@Query("SELECT DISTINCT itemId FROM inventory_movements WHERE invoiceId IN (:invoiceIds) OR source_id IN (:invoiceIds)")
suspend fun getItemIdsTouchedByInvoices(invoiceIds: List<String>): List<String>

@Query("SELECT * FROM inventory_movements")
suspend fun getAllMovementsSync(): List<InventoryMovementEntity>

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertMovementIgnore(movement: InventoryMovementEntity)

// ── إعادة التعيين ─────────────────────────────────
@Query("UPDATE inventory_items SET is_archived=1,archived_at=:now,archived_by=:actor,isDirty=1 WHERE is_archived=0")
suspend fun archiveAllItems(actor: String = "ADMIN_RESET", now: Long = System.currentTimeMillis()): Int

@Query("DELETE FROM inventory_items")
suspend fun deleteAllItems()

@Query("DELETE FROM inventory_movements")
suspend fun deleteAllMovements()

@Query("DELETE FROM inventory_cost_revisions") suspend fun deleteAllCostRevisions()
@Query("DELETE FROM inventory_stock_outbox") suspend fun deleteAllInventoryStockOutbox()
@Query("DELETE FROM inventory_cost_outbox") suspend fun deleteAllInventoryCostOutbox()
@Query("DELETE FROM inventory_write_guards") suspend fun deleteAllInventoryWriteGuards()
@Query("DELETE FROM inventory_sync_conflicts") suspend fun deleteAllInventoryConflicts()
@Query("DELETE FROM inventory_sync_cursors") suspend fun deleteAllInventoryCursors()

/** Explicit backup-restore boundary only; business correction paths must append reversals. */
@Transaction
suspend fun clearInventoryForBackupRestore() {
    deleteAllInventoryStockOutbox()
    deleteAllInventoryCostOutbox()
    deleteAllInventoryWriteGuards()
    deleteAllInventoryConflicts()
    deleteAllInventoryCursors()
    deleteAllCostRevisions()
    deleteAllMovements()
    deleteAllItems()
}

// ── وحدات — مساعدة للسحب التلقائي ───────────────

/** يجلب صنف الوحدة (كرتونة...) المرتبط بصنف قطعة معين */
@Query("SELECT * FROM inventory_items WHERE linkedUnitItemId = :pieceId AND isUnitItem = 1 AND is_archived = 0 LIMIT 1")
suspend fun getUnitItemByLinkedPieceId(pieceId: String): InventoryItemEntity?
}
