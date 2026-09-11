package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryCostDao {
@Insert
suspend fun insertMovement(movement: InventoryMovementEntity)

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertCanonicalMovementRaw(movement: InventoryMovementEntity): Long

/** v257 idempotent identity gate; v259 will route all quantity writes through the stock writer. */
@Transaction
suspend fun insertCanonicalMovementOnce(movement: InventoryMovementEntity): Boolean {
    movement.requireCanonicalInventoryContract()
    return insertCanonicalMovementRaw(movement) != -1L
}

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertCostRevisionRaw(revision: InventoryCostRevisionEntity): Long

@Transaction
suspend fun insertCostRevisionOnce(revision: InventoryCostRevisionEntity): Boolean {
    revision.requireCanonicalCostContract()
    return insertCostRevisionRaw(revision) != -1L
}

@Query("SELECT * FROM inventory_cost_revisions WHERE item_id = :itemId ORDER BY CASE WHEN cost_sequence IS NULL THEN 0 ELSE 1 END, cost_sequence DESC, approved_at DESC, cost_revision_id DESC")
suspend fun getCostRevisionsForItem(itemId: String): List<InventoryCostRevisionEntity>

@Query("SELECT * FROM inventory_cost_revisions WHERE command_id = :commandId ORDER BY recorded_at, cost_revision_id")
suspend fun getCostRevisionsByCommandId(commandId: String): List<InventoryCostRevisionEntity>

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertInventoryCostOutbox(row: InventoryCostOutboxEntity): Long

@Query("SELECT * FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state IN ('PENDING','RETRY') AND next_attempt_at<=:now ORDER BY created_at,id LIMIT :limit")
suspend fun getPendingInventoryCostOutbox(organizationId: String, now: Long, limit: Int = 100): List<InventoryCostOutboxEntity>

@Query("SELECT * FROM inventory_cost_revisions WHERE cost_revision_id=:revisionId LIMIT 1")
suspend fun getCostRevisionById(revisionId: String): InventoryCostRevisionEntity?

@Query("UPDATE inventory_cost_outbox SET sync_state='ACKNOWLEDGED',ack_sequence=:sequence,acknowledged_at=:now,last_error=NULL WHERE id=:id AND sync_state<>'ACKNOWLEDGED'")
suspend fun acknowledgeInventoryCostOutboxRaw(id: String, sequence: Long, now: Long): Int

@Query("UPDATE inventory_cost_revisions SET cost_sequence=:sequence WHERE cost_revision_id=(SELECT cost_revision_id FROM inventory_cost_outbox WHERE id=:outboxId) AND (cost_sequence IS NULL OR cost_sequence=:sequence)")
suspend fun acknowledgeInventoryCostRevisionRaw(outboxId: String, sequence: Long): Int

@Transaction
suspend fun acknowledgeInventoryCostOutbox(id: String, sequence: Long, now: Long = System.currentTimeMillis()): Int {
    check(acknowledgeInventoryCostRevisionRaw(id, sequence) == 1) { "inventory cost sequence mismatch" }
    return acknowledgeInventoryCostOutboxRaw(id, sequence, now)
}

@Query("UPDATE inventory_cost_outbox SET sync_state='RETRY',attempt_count=attempt_count+1,next_attempt_at=:nextAttemptAt,last_error=:error WHERE id=:id AND sync_state<>'ACKNOWLEDGED'")
suspend fun retryInventoryCostOutbox(id: String, nextAttemptAt: Long, error: String): Int

@Query("UPDATE inventory_cost_outbox SET sync_state='REQUIRES_REVIEW',attempt_count=attempt_count+1,last_error=:error WHERE id=:id AND sync_state<>'ACKNOWLEDGED'")
suspend fun reviewInventoryCostOutbox(id: String, error: String): Int

@Query("SELECT COUNT(*) FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state IN ('PENDING','RETRY')")
suspend fun countInventoryCostBacklog(organizationId: String): Long

@Query("SELECT COUNT(*) FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state='REQUIRES_REVIEW'")
suspend fun countInventoryCostReview(organizationId: String): Long

@Query("SELECT MIN(next_attempt_at) FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state='RETRY' AND next_attempt_at>:now")
suspend fun nextInventoryCostRetryAt(organizationId: String, now: Long): Long?

@Query("SELECT * FROM inventory_cost_revisions WHERE organization_id = :organizationId AND source_type = :sourceType AND source_id = :sourceId ORDER BY approved_at, cost_revision_id")
suspend fun getCostRevisionsForSource(organizationId: String, sourceType: String, sourceId: String): List<InventoryCostRevisionEntity>

@Query("SELECT * FROM inventory_cost_revisions WHERE organization_id = :organizationId AND reverses_cost_revision_id = :revisionId LIMIT 1")
suspend fun getCostReversal(organizationId: String, revisionId: String): InventoryCostRevisionEntity?
}
