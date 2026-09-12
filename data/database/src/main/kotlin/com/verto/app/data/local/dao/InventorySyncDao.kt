package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventorySyncDao : InventoryMovementDao, InventoryCostDao, InventoryMaintenanceDao {
/** SYNC-012: الأصناف المتسخة فقط (للرفع) + تصفير العلم بعد رفع ناجح. */
@Query("SELECT * FROM inventory_items WHERE isDirty = 1 AND is_archived = 0")
suspend fun getDirtyItemsSync(): List<InventoryItemEntity>

@Query("SELECT id FROM inventory_items WHERE isDirty = 1 AND is_archived = 1")
suspend fun getDirtyArchivedItemIds(): List<String>

@Query("UPDATE inventory_items SET isDirty = 0 WHERE id IN (:ids)")
suspend fun markItemsClean(ids: List<String>)

// ─── v259: local write gate / transactional outbox ─────────────────────

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun claimInventoryWrite(guard: InventoryWriteGuardEntity): Long

@Query("SELECT * FROM inventory_write_guards WHERE organization_id = :organizationId AND command_id = :commandId LIMIT 1")
suspend fun getInventoryWriteGuard(organizationId: String, commandId: String): InventoryWriteGuardEntity?

@Query("SELECT * FROM inventory_movements WHERE write_id = :writeId ORDER BY createdAt ASC, id ASC")
suspend fun getMovementsByWriteId(writeId: String): List<InventoryMovementEntity>

@Query("SELECT * FROM inventory_movements WHERE reverses_movement_id = :movementId LIMIT 1")
suspend fun getReversalForMovement(movementId: String): InventoryMovementEntity?

@Query(
    """
    UPDATE inventory_movements
    SET organization_id = :organizationId,
        command_id = :commandId,
        idempotency_key = :commandId || ':' || id,
        signed_base_quantity = CAST(quantityAfter AS INTEGER) - CAST(quantityBefore AS INTEGER),
        movement_kind = CASE
            WHEN source_type = 'OPENING_BALANCE' THEN 'OPENING_BALANCE'
            WHEN source_type IN ('INVOICE_VOID','SHIPMENT_REVERSAL') THEN 'REVERSAL'
            WHEN source_type = 'SHIPMENT_RECEIPT' THEN 'SHIPMENT_RECEIPT'
            WHEN source_type IN ('MANUAL_ADJUSTMENT','UNIT_CONVERSION') THEN 'MANUAL_ADJUSTMENT'
            WHEN source_type = 'INVOICE_RETURN' AND movementType = 'IN' THEN 'SALES_RETURN'
            WHEN source_type = 'INVOICE_RETURN' AND movementType = 'OUT' THEN 'PURCHASE_RETURN'
            WHEN source_type = 'INVOICE' AND movementType = 'OUT' THEN 'SALE'
            WHEN source_type IN ('INVOICE','GOODS_RECEIPT') AND movementType = 'IN' THEN 'PURCHASE'
            WHEN movementType = 'ADJUST' THEN 'MANUAL_ADJUSTMENT'
            WHEN movementType = 'IN' THEN 'PURCHASE'
            ELSE 'SALE'
        END,
        occurred_at = COALESCE(occurred_at, createdAt),
        recorded_at = COALESCE(recorded_at, :recordedAt),
        created_by = COALESCE(created_by, :actorId),
        contract_version = 2
    WHERE write_id = :commandId
    """
)
suspend fun canonicalizeMovementsForWrite(
    organizationId: String,
    commandId: String,
    actorId: String,
    recordedAt: Long,
): Int

@Insert
suspend fun insertInventoryStockOutbox(row: InventoryStockOutboxEntity)

@Query("SELECT * FROM inventory_stock_outbox WHERE organization_id = :organizationId AND command_id = :commandId ORDER BY created_at, id")
suspend fun getInventoryStockOutboxForCommand(organizationId: String, commandId: String): List<InventoryStockOutboxEntity>

@Query("SELECT * FROM inventory_stock_outbox WHERE organization_id = :organizationId AND sync_state IN ('PENDING','RETRY') AND next_attempt_at <= :now ORDER BY created_at, id LIMIT :limit")
suspend fun getPendingInventoryStockOutbox(organizationId: String, now: Long = System.currentTimeMillis(), limit: Int = 100): List<InventoryStockOutboxEntity>

@Query("SELECT * FROM inventory_movements WHERE id = :movementId LIMIT 1")
suspend fun getMovementById(movementId: String): InventoryMovementEntity?

@Query("UPDATE inventory_stock_outbox SET sync_state = 'ACKNOWLEDGED', ack_sequence = :serverSequence, acknowledged_at = :acknowledgedAt, last_error = NULL WHERE id = :id AND sync_state <> 'ACKNOWLEDGED'")
suspend fun acknowledgeInventoryStockOutboxRaw(id: String, serverSequence: Long, acknowledgedAt: Long): Int

@Query("UPDATE inventory_movements SET server_sequence=:serverSequence WHERE id=(SELECT movement_id FROM inventory_stock_outbox WHERE id=:outboxId) AND (server_sequence IS NULL OR server_sequence=:serverSequence)")
suspend fun acknowledgeInventoryMovementRaw(outboxId: String, serverSequence: Long): Int

@Transaction
suspend fun acknowledgeInventoryStockOutbox(id: String, serverSequence: Long, acknowledgedAt: Long = System.currentTimeMillis()): Int {
    check(acknowledgeInventoryMovementRaw(id, serverSequence) == 1) { "inventory movement acknowledgement mismatch" }
    return acknowledgeInventoryStockOutboxRaw(id, serverSequence, acknowledgedAt)
}

@Query("UPDATE inventory_stock_outbox SET sync_state = 'RETRY', attempt_count = attempt_count + 1, next_attempt_at = :nextAttemptAt, last_error = :error WHERE id = :id AND sync_state <> 'ACKNOWLEDGED'")
suspend fun retryInventoryStockOutbox(id: String, nextAttemptAt: Long, error: String): Int

@Query("UPDATE inventory_stock_outbox SET sync_state = 'REQUIRES_REVIEW', attempt_count = attempt_count + 1, last_error = :error WHERE id = :id AND sync_state <> 'ACKNOWLEDGED'")
suspend fun reviewInventoryStockOutbox(id: String, error: String): Int

@Query("SELECT COUNT(*) FROM inventory_stock_outbox WHERE organization_id=:organizationId AND sync_state IN ('PENDING','RETRY')")
suspend fun countInventoryStockBacklog(organizationId: String): Long

@Query("SELECT COUNT(*) FROM inventory_stock_outbox WHERE organization_id=:organizationId AND sync_state='REQUIRES_REVIEW'")
suspend fun countInventoryStockReview(organizationId: String): Long

@Query("SELECT MIN(next_attempt_at) FROM inventory_stock_outbox WHERE organization_id=:organizationId AND sync_state='RETRY' AND next_attempt_at>:now")
suspend fun nextInventoryStockRetryAt(organizationId: String, now: Long): Long?

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertInventorySyncConflict(conflict: InventorySyncConflictEntity): Long

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertPulledInventoryMovementRaw(movement: InventoryMovementEntity): Long

@Query("SELECT COALESCE((SELECT last_server_sequence FROM inventory_sync_cursors WHERE organization_id = :organizationId), 0)")
suspend fun getInventoryServerCursor(organizationId: String): Long

@Query("SELECT COALESCE((SELECT last_cost_sequence FROM inventory_sync_cursors WHERE organization_id = :organizationId), 0)")
suspend fun getInventoryCostServerCursor(organizationId: String): Long

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun putInventorySyncCursor(cursor: InventorySyncCursorEntity)

@Transaction
suspend fun applyPulledInventoryMovements(
    organizationId: String,
    movements: List<InventoryMovementEntity>,
    serverCursor: Long,
    now: Long = System.currentTimeMillis(),
) {
    require(serverCursor >= getInventoryServerCursor(organizationId)) { "inventory cursor regression" }
    movements.forEach { movement ->
        require(movement.organizationId == organizationId && (movement.serverSequence ?: 0L) <= serverCursor) {
            "inventory pull organization/cursor mismatch"
        }
        if (insertPulledInventoryMovementRaw(movement) == -1L) {
            val existing = checkNotNull(getMovementById(movement.id)) { "inventory movement disappeared during apply" }
            require(existing.copy(
                unitPrice = movement.unitPrice,
                recordedAt = movement.recordedAt,
                serverAcceptedAt = movement.serverAcceptedAt,
                serverSequence = movement.serverSequence,
                createdBy = movement.createdBy,
            ) == movement) { "immutable inventory movement conflict" }
            check(updateMovementServerAuthority(
                movement.id, organizationId, movement.unitPrice, movement.recordedAt,
                movement.serverAcceptedAt, checkNotNull(movement.serverSequence), movement.createdBy,
            ) == 1) { "inventory movement server authority mismatch" }
        }
    }
    putInventorySyncCursor(
        InventorySyncCursorEntity(organizationId, serverCursor, getInventoryCostServerCursor(organizationId), now)
    )
}

@Transaction
suspend fun applyPulledInventoryCostRevisions(
    organizationId: String,
    revisions: List<InventoryCostRevisionEntity>,
    costCursor: Long,
    now: Long = System.currentTimeMillis(),
) {
    require(costCursor >= getInventoryCostServerCursor(organizationId)) { "inventory cost cursor regression" }
    revisions.forEach { revision ->
        require(revision.organizationId == organizationId && (revision.costSequence ?: 0L) <= costCursor) {
            "inventory cost pull organization/cursor mismatch"
        }
        val inserted = insertCostRevisionOnce(revision)
        if (!inserted) {
            val existing = checkNotNull(getCostRevisionById(revision.costRevisionId)) {
                "inventory cost revision disappeared during apply"
            }
            require(existing.copy(
                costSequence = revision.costSequence,
                recordedAt = revision.recordedAt,
                createdBy = revision.createdBy,
            ) == revision) { "immutable inventory cost revision conflict" }
            check(updateCostServerAuthority(
                revision.costRevisionId, organizationId, checkNotNull(revision.costSequence),
                revision.recordedAt, revision.createdBy,
            ) == 1) { "inventory cost server authority mismatch" }
        }
    }
    putInventorySyncCursor(
        InventorySyncCursorEntity(organizationId, getInventoryServerCursor(organizationId), costCursor, now)
    )
}

@Query("""UPDATE inventory_movements
          SET unitPrice=:unitPrice, recorded_at=:recordedAt, server_accepted_at=:serverAcceptedAt,
              server_sequence=:serverSequence, created_by=:createdBy
          WHERE id=:movementId AND organization_id=:organizationId
            AND (server_sequence IS NULL OR server_sequence=:serverSequence)""")
suspend fun updateMovementServerAuthority(
    movementId: String,
    organizationId: String,
    unitPrice: Double,
    recordedAt: Long?,
    serverAcceptedAt: Long?,
    serverSequence: Long,
    createdBy: String?,
): Int

@Query("""UPDATE inventory_cost_revisions
          SET cost_sequence=:costSequence, recorded_at=:recordedAt, created_by=:createdBy
          WHERE cost_revision_id=:costRevisionId AND organization_id=:organizationId
            AND (cost_sequence IS NULL OR cost_sequence=:costSequence)""")
suspend fun updateCostServerAuthority(
    costRevisionId: String,
    organizationId: String,
    costSequence: Long,
    recordedAt: Long,
    createdBy: String,
): Int

@Query(
    """
    SELECT i.id AS itemId, i.name AS itemName, CAST(i.quantity AS INTEGER) AS baseQuantity,
           COALESCE((SELECT r.approved_inventory_cost_minor FROM inventory_cost_revisions r
                     WHERE r.item_id = i.id
                     ORDER BY CASE WHEN r.cost_sequence IS NULL THEN 0 ELSE 1 END,
                              r.cost_sequence DESC, r.approved_at DESC, r.cost_revision_id DESC LIMIT 1),
                    i.buy_price_minor) AS approvedUnitCostMinor,
           CAST(i.quantity AS INTEGER) * COALESCE((SELECT r.approved_inventory_cost_minor FROM inventory_cost_revisions r
                     WHERE r.item_id = i.id
                     ORDER BY CASE WHEN r.cost_sequence IS NULL THEN 0 ELSE 1 END,
                              r.cost_sequence DESC, r.approved_at DESC, r.cost_revision_id DESC LIMIT 1),
                    i.buy_price_minor) AS inventoryValueMinor
    FROM inventory_items i WHERE i.is_archived = 0 ORDER BY i.name
    """
)
fun observeCanonicalInventory(): Flow<List<InventoryCanonicalReadRow>>

@Query(
    """
    SELECT i.id AS itemId, CAST(i.quantity AS INTEGER) AS snapshotQuantity,
           COALESCE(SUM(m.signed_base_quantity), 0) AS ledgerQuantity,
           CAST(i.quantity AS INTEGER) - COALESCE(SUM(m.signed_base_quantity), 0) AS driftQuantity
    FROM inventory_items i
    LEFT JOIN inventory_movements m ON m.itemId = i.id AND m.contract_version >= 2
    GROUP BY i.id HAVING driftQuantity <> 0 ORDER BY ABS(driftQuantity) DESC
    """
)
suspend fun detectInventoryDrift(): List<InventoryDriftRow>

@Query(
    """
    SELECT
      (SELECT COUNT(*) FROM inventory_stock_outbox WHERE organization_id = :organizationId AND sync_state IN ('PENDING','RETRY')) AS pendingOutbox,
      (SELECT MIN(created_at) FROM inventory_stock_outbox WHERE organization_id = :organizationId AND sync_state IN ('PENDING','RETRY')) AS oldestOutboxAt,
      (SELECT COUNT(*) FROM inventory_sync_conflicts WHERE organization_id = :organizationId AND status = 'OPEN') AS openConflicts,
      (SELECT COUNT(*) FROM inventory_reconciliation_quarantine WHERE organization_id = :organizationId AND resolved_at IS NULL) AS openQuarantine,
      (SELECT COUNT(*) FROM (SELECT i.id FROM inventory_items i LEFT JOIN inventory_movements m ON m.itemId=i.id AND m.contract_version>=2 GROUP BY i.id HAVING CAST(i.quantity AS INTEGER) <> COALESCE(SUM(m.signed_base_quantity),0))) AS driftedItems
    """
)
fun observeInventoryOperationsMetrics(organizationId: String): Flow<InventoryOperationsMetrics>

@Query("SELECT itemId, -SUM(signed_base_quantity) AS soldBaseQuantity FROM inventory_movements WHERE movement_kind='SALE' AND occurred_at BETWEEN :from AND :to GROUP BY itemId ORDER BY soldBaseQuantity DESC")
suspend fun getFastMovers(from: Long, to: Long): List<InventoryFastMoverRow>
}
