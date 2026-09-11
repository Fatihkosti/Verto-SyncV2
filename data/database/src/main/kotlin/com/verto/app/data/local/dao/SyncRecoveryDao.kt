package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.SyncBootstrapStageEntity
import com.verto.app.data.local.entity.SyncHealthStateEntity
import com.verto.app.data.local.entity.SyncCursorEntity
import com.verto.app.data.local.entity.SyncRecoveryStateEntity
import com.verto.app.data.local.entity.SyncRecoveryProtectionManifestEntity

@Dao
interface SyncRecoveryDao {
    @Query("SELECT * FROM sync_recovery_state WHERE scope_id = :scopeId LIMIT 1")
    suspend fun getRecoveryState(scopeId: String): SyncRecoveryStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecoveryState(state: SyncRecoveryStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun installBootstrapCursor(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_bootstrap_stage WHERE scope_id = :scopeId AND bootstrap_session_id = :sessionId")
    suspend fun clearStage(scopeId: String, sessionId: String): Int

    @Query("DELETE FROM sync_bootstrap_stage WHERE scope_id = :scopeId AND bootstrap_session_id != :keepSessionId")
    suspend fun clearStaleStages(scopeId: String, keepSessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStage(row: SyncBootstrapStageEntity): Long

    @Query("SELECT * FROM sync_bootstrap_stage WHERE scope_id = :scopeId AND bootstrap_session_id = :sessionId AND aggregate_type = :aggregateType AND aggregate_id = :aggregateId LIMIT 1")
    suspend fun getStageIdentity(scopeId: String, sessionId: String, aggregateType: String, aggregateId: String): SyncBootstrapStageEntity?

    @Query("SELECT * FROM sync_bootstrap_stage WHERE scope_id = :scopeId AND bootstrap_session_id = :sessionId ORDER BY ordinal ASC")
    suspend fun listStage(scopeId: String, sessionId: String): List<SyncBootstrapStageEntity>

    @Query("SELECT COUNT(*) FROM sync_bootstrap_stage WHERE scope_id = :scopeId AND bootstrap_session_id = :sessionId")
    suspend fun countStage(scopeId: String, sessionId: String): Long

    @Query("UPDATE sync_bootstrap_stage SET promotion_state=:promotionState, wait_reason=:waitReason, applied_at=:appliedAt WHERE scope_id=:scopeId AND bootstrap_session_id=:sessionId AND ordinal=:ordinal")
    suspend fun markStagePromotion(scopeId: String, sessionId: String, ordinal: Long, promotionState: String, waitReason: String?, appliedAt: Long?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProtectionManifest(manifest: SyncRecoveryProtectionManifestEntity)

    @Query("SELECT * FROM sync_recovery_protection_manifest WHERE scope_id=:scopeId AND bootstrap_session_id=:sessionId LIMIT 1")
    suspend fun getProtectionManifest(scopeId: String, sessionId: String): SyncRecoveryProtectionManifestEntity?

    @Query("DELETE FROM sync_recovery_protection_manifest WHERE scope_id=:scopeId AND bootstrap_session_id != :keepSessionId")
    suspend fun clearStaleProtectionManifests(scopeId: String, keepSessionId: String): Int

    @Query("DELETE FROM sync_recovery_protection_manifest WHERE scope_id=:scopeId AND bootstrap_session_id=:sessionId")
    suspend fun clearProtectionManifest(scopeId: String, sessionId: String): Int

    @Query("SELECT MAX(ordinal) FROM sync_bootstrap_stage WHERE scope_id = :scopeId AND bootstrap_session_id = :sessionId")
    suspend fun maxStageOrdinal(scopeId: String, sessionId: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM sync_inbox WHERE scope_id = :scopeId AND server_revision = :revision AND apply_state = 'APPLIED')")
    suspend fun hasAppliedInboxAnchor(scopeId: String, revision: Long): Boolean

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id = :organizationId AND state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF')")
    suspend fun unifiedOutboxDepth(organizationId: String): Long

    @Query("""
        SELECT COUNT(*) FROM party_sync_outbox o
        WHERE o.state IN ('PENDING','RETRY')
          AND EXISTS (SELECT 1 FROM party_roles r WHERE r.party_id = o.aggregate_id AND r.organization_id = :organizationId)
    """)
    suspend fun partyOutboxDepth(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM financial_outbox WHERE organization_id = :organizationId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun financialOutboxDepth(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_stock_outbox WHERE organization_id = :organizationId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun inventoryStockOutboxDepth(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_cost_outbox WHERE organization_id = :organizationId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun inventoryCostOutboxDepth(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM optimal_outbox WHERE organization_id = :organizationId AND status NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun optimalOutboxDepth(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_attachment_transfer WHERE organization_id = :organizationId AND state NOT IN ('COMPLETED','CANCELLED')")
    suspend fun attachmentOutboxDepth(organizationId: String): Long

    @Query("SELECT MIN(created_at) FROM sync_outbox WHERE organization_id = :organizationId AND state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF')")
    suspend fun oldestUnifiedPendingAt(organizationId: String): Long?

    @Query("SELECT COALESCE(SUM(attempt_count),0) FROM sync_outbox WHERE organization_id = :organizationId AND state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF')")
    suspend fun unifiedRetryCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_conflict WHERE organization_id = :organizationId AND state IN ('OPEN','DOMAIN_CORRECTION_REQUIRED','WAITING_REPLACEMENT_RECEIPT')")
    suspend fun unifiedConflictCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_sync_conflicts WHERE organization_id = :organizationId AND status NOT IN ('RESOLVED','ACKNOWLEDGED')")
    suspend fun inventoryConflictCount(organizationId: String): Long

    @Query("""
        SELECT COUNT(*) FROM party_sync_conflicts c
        WHERE c.resolved = 0
          AND EXISTS (SELECT 1 FROM party_roles r WHERE r.party_id = c.aggregate_id AND r.organization_id = :organizationId)
    """)
    suspend fun partyConflictCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id = :organizationId AND state='REQUIRES_REVIEW'")
    suspend fun unifiedRequiresReviewCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id = :organizationId AND state='REJECTED'")
    suspend fun unifiedRejectedCount(organizationId: String): Long

    @Query("""
        SELECT COUNT(*) FROM party_sync_outbox o
        WHERE o.state='REQUIRES_REVIEW'
          AND EXISTS (SELECT 1 FROM party_roles r WHERE r.party_id=o.aggregate_id AND r.organization_id=:organizationId)
    """)
    suspend fun partyRequiresReviewCount(organizationId: String): Long

    @Query("""
        SELECT COUNT(*) FROM party_sync_outbox o
        WHERE o.state='REJECTED'
          AND EXISTS (SELECT 1 FROM party_roles r WHERE r.party_id=o.aggregate_id AND r.organization_id=:organizationId)
    """)
    suspend fun partyRejectedCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM financial_outbox WHERE organization_id=:organizationId AND sync_state='REQUIRES_REVIEW'")
    suspend fun financialRequiresReviewCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_stock_outbox WHERE organization_id=:organizationId AND sync_state='REQUIRES_REVIEW'")
    suspend fun inventoryStockRequiresReviewCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state='REQUIRES_REVIEW'")
    suspend fun inventoryCostRequiresReviewCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM optimal_outbox WHERE organization_id=:organizationId AND status='BLOCKED'")
    suspend fun optimalRequiresReviewCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id = :organizationId AND state IN ('REQUIRES_REVIEW','REJECTED')")
    suspend fun unifiedReviewCount(organizationId: String): Long

    @Query("SELECT COALESCE(SUM(retry_count),0) FROM party_sync_outbox o WHERE o.state IN ('PENDING','RETRY') AND EXISTS (SELECT 1 FROM party_roles r WHERE r.party_id=o.aggregate_id AND r.organization_id=:organizationId)")
    suspend fun partyRetryCount(organizationId: String): Long

    @Query("SELECT COALESCE(SUM(attempt_count),0) FROM financial_outbox WHERE organization_id=:organizationId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun financialRetryCount(organizationId: String): Long

    @Query("SELECT COALESCE(SUM(attempt_count),0) FROM inventory_stock_outbox WHERE organization_id=:organizationId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun inventoryStockRetryCount(organizationId: String): Long

    @Query("SELECT COALESCE(SUM(attempt_count),0) FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun inventoryCostRetryCount(organizationId: String): Long

    @Query("SELECT COALESCE(SUM(attempt_count),0) FROM optimal_outbox WHERE organization_id=:organizationId AND status NOT IN ('SYNCED','ACKNOWLEDGED')")
    suspend fun optimalRetryCount(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id=:organizationId AND state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF')")
    suspend fun activeUnifiedMutationCount(organizationId: String): Long

    @Query("SELECT * FROM sync_bootstrap_stage WHERE scope_id=:scopeId AND bootstrap_session_id=:sessionId AND aggregate_type=:aggregateType AND partition_key=:partitionKey ORDER BY aggregate_id ASC")
    suspend fun listStagePartition(scopeId: String, sessionId: String, aggregateType: String, partitionKey: String): List<SyncBootstrapStageEntity>

    @Query("UPDATE sync_health_state SET last_reconciliation_at=:at, last_reconciliation_status=:status, updated_at=:at WHERE scope_id=:scopeId")
    suspend fun markReconciliation(scopeId: String, status: String, at: Long): Int


    @Query("SELECT EXISTS(SELECT 1 FROM party_sync_outbox WHERE aggregate_id = :aggregateId AND state IN ('PENDING','RETRY'))")
    suspend fun hasActivePartyMutation(aggregateId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM financial_outbox WHERE organization_id = :organizationId AND aggregate_id = :aggregateId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED'))")
    suspend fun hasActiveFinancialMutation(organizationId: String, aggregateId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_stock_outbox WHERE organization_id = :organizationId AND movement_id = :aggregateId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED'))")
    suspend fun hasActiveInventoryStockMutation(organizationId: String, aggregateId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_cost_outbox WHERE organization_id = :organizationId AND cost_revision_id = :aggregateId AND sync_state NOT IN ('SYNCED','ACKNOWLEDGED'))")
    suspend fun hasActiveInventoryCostMutation(organizationId: String, aggregateId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM optimal_outbox WHERE organization_id = :organizationId AND aggregate_id = :aggregateId AND status NOT IN ('SYNCED','ACKNOWLEDGED'))")
    suspend fun hasActiveOptimalMutation(organizationId: String, aggregateId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM sync_attachment_transfer WHERE organization_id = :organizationId AND aggregate_type = :aggregateType AND aggregate_id = :aggregateId AND state NOT IN ('COMPLETED','CANCELLED'))")
    suspend fun hasActiveAttachmentIntent(organizationId: String, aggregateType: String, aggregateId: String): Boolean

    @Query("SELECT * FROM sync_health_state WHERE scope_id = :scopeId LIMIT 1")
    suspend fun getHealthState(scopeId: String): SyncHealthStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHealthState(state: SyncHealthStateEntity)

    @Query("UPDATE sync_health_state SET last_successful_push_at=:at, last_failure_category=NULL, last_failure_code=NULL, updated_at=:at WHERE organization_id=:organizationId")
    suspend fun markPushSuccess(organizationId: String, at: Long): Int

    @Query("UPDATE sync_health_state SET last_successful_pull_at=:at, last_failure_category=NULL, last_failure_code=NULL, updated_at=:at WHERE organization_id=:organizationId")
    suspend fun markPullSuccess(organizationId: String, at: Long): Int

    @Query("UPDATE sync_health_state SET last_failure_category=:category, last_failure_code=:code, updated_at=:at WHERE organization_id=:organizationId")
    suspend fun markFailure(organizationId: String, category: String, code: String, at: Long): Int
}
