package com.verto.app.data.local.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.SyncConflictEntity
import com.verto.app.data.local.entity.SyncConflictResolutionAuditEntity
import com.verto.app.data.local.entity.SyncConflictReviewEvidenceEntity

/** Durable B11 conflict-review persistence. Business resolution stays outside the DAO. */
interface UnifiedSyncConflictDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConflictRaw(entity: SyncConflictEntity): Long

    @Query("SELECT * FROM sync_conflict WHERE mutation_id = :mutationId LIMIT 1")
    suspend fun getConflictByMutationId(mutationId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflict WHERE conflict_id = :conflictId LIMIT 1")
    suspend fun getConflict(conflictId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflict WHERE resolution_mutation_id = :mutationId LIMIT 1")
    suspend fun getConflictByResolutionMutationId(mutationId: String): SyncConflictEntity?

    @Query(
        """
        SELECT * FROM sync_conflict
        WHERE organization_id = :organizationId
          AND state IN ('OPEN','DOMAIN_CORRECTION_REQUIRED','WAITING_REPLACEMENT_RECEIPT')
        ORDER BY created_at, conflict_id
        LIMIT :limit
        """
    )
    suspend fun listOpenConflictsRaw(organizationId: String, limit: Int): List<SyncConflictEntity>

    suspend fun listOpenConflicts(organizationId: String, limit: Int): List<SyncConflictEntity> {
        require(limit in 1..MAX_WORKER_OPERATIONS_PER_RUN)
        return listOpenConflictsRaw(organizationId, limit)
    }

    /** Duplicate authoritative receipt is a no-op; divergent replay fails closed. */
    @Transaction
    suspend fun insertConflictChecked(entity: SyncConflictEntity): UnifiedConflictInsertResult {
        validateConflict(entity)
        val inserted = insertConflictRaw(entity)
        if (inserted != -1L) return UnifiedConflictInsertResult.INSERTED
        val existing = checkNotNull(getConflictByMutationId(entity.mutationId))
        require(existing.sameImmutableConflict(entity)) { "FAIL_DIVERGENT_CONFLICT_REPLAY" }
        return UnifiedConflictInsertResult.DUPLICATE
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConflictEvidenceRaw(entity: SyncConflictReviewEvidenceEntity): Long

    @Query("SELECT * FROM sync_conflict_review_evidence WHERE conflict_id=:conflictId LIMIT 1")
    suspend fun getConflictEvidence(conflictId: String): SyncConflictReviewEvidenceEntity?

    @Transaction
    suspend fun insertConflictEvidenceChecked(entity: SyncConflictReviewEvidenceEntity) {
        validateConflictEvidence(entity)
        val inserted = insertConflictEvidenceRaw(entity)
        if (inserted != -1L) return
        val stored = checkNotNull(getConflictEvidence(entity.conflictId))
        require(stored == entity) { "FAIL_DIVERGENT_CONFLICT_EVIDENCE_REPLAY" }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConflictDecision(entity: SyncConflictResolutionAuditEntity)

    @Query("SELECT * FROM sync_conflict_resolution_audit WHERE conflict_id=:conflictId ORDER BY decided_at,decision_id")
    suspend fun listConflictDecisions(conflictId: String): List<SyncConflictResolutionAuditEntity>

    @Query(
        """
        UPDATE sync_conflict
        SET state=:newState,
            resolution_mutation_id=:resolutionMutationId,
            resolved_at=:resolvedAt
        WHERE conflict_id=:conflictId AND state=:expectedState
        """
    )
    suspend fun transitionConflict(
        conflictId: String,
        expectedState: String,
        newState: String,
        resolutionMutationId: String?,
        resolvedAt: Long?,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF')
        """
    )
    suspend fun countBacklog(organizationId: String): Long
}
