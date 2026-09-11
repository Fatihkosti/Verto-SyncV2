package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.SyncConflictEntity
import com.verto.app.data.local.entity.SyncCursorEntity
import com.verto.app.data.local.entity.SyncInboxEntity
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.local.entity.SyncSequenceStateEntity
import java.security.MessageDigest

/**
 * Session 306 persistence primitives only. No network/worker/runtime wiring belongs here.
 *
 * Canonical future producer boundary (Session 307):
 * AppDatabase.withTransaction { domain write; enqueueMutation(...) }.
 *
 * Canonical future pull boundary (Session 308):
 * AppDatabase.withTransaction { insertInboxChecked(...); domain apply; advanceCursorOrThrow(...) }.
 * Nested Room transactions join the caller transaction, so sequence/outbox and inbox/cursor remain
 * representable inside one database commit without DataStore or network authority.
 */

interface UnifiedSyncOutboxDao {
    @Query(
        """
        SELECT MAX(version) FROM (
            SELECT entity_version AS version FROM sync_inbox
            WHERE organization_id = :organizationId AND aggregate_type = :aggregateType AND aggregate_id = :aggregateId
              AND entity_version IS NOT NULL
            UNION ALL
            SELECT acked_server_version AS version FROM sync_outbox
            WHERE organization_id = :organizationId AND aggregate_type = :aggregateType AND aggregate_id = :aggregateId
              AND acked_server_version IS NOT NULL
        )
        """
    )
    abstract suspend fun latestKnownServerVersion(organizationId: String, aggregateType: String, aggregateId: String): Long?

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
          AND state IN ('PENDING','LEASED','RETRY')
        """
    )
    abstract suspend fun countActiveOrderedMutations(organizationId: String, aggregateType: String, aggregateId: String): Int

    @Query("SELECT * FROM sync_outbox WHERE mutation_id = :mutationId LIMIT 1")
    abstract suspend fun getOutbox(mutationId: String): SyncOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOutboxRaw(entity: SyncOutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun ensureSequenceCounterRaw(entity: SyncSequenceStateEntity): Long

    @Query(
        """
        UPDATE sync_sequence_state
        SET last_value = last_value + 1, updated_at = :updatedAt
        WHERE organization_id = :organizationId
          AND counter_kind = :counterKind
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
        """
    )
    abstract suspend fun incrementSequenceCounterRaw(
        organizationId: String,
        counterKind: String,
        aggregateType: String,
        aggregateId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT * FROM sync_sequence_state
        WHERE organization_id = :organizationId
          AND counter_kind = :counterKind
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
        LIMIT 1
        """
    )
    abstract suspend fun readSequenceCounterRaw(
        organizationId: String,
        counterKind: String,
        aggregateType: String,
        aggregateId: String,
    ): SyncSequenceStateEntity?

    /**
     * Allocates both counters and inserts one immutable mutation in one Room transaction.
     * No sequence value is accepted from the caller and no value is derived from time/MAX(outbox).
     */
    @Transaction
    open suspend fun enqueueMutation(draft: UnifiedSyncMutationDraft, updatedAt: Long): SyncOutboxEntity {
        validateMutationDraft(draft)
        val fingerprint = semanticFingerprint(draft)
        getOutbox(draft.mutationId)?.let { existing ->
            require(existing.semanticFingerprint == fingerprint && existing.matches(draft)) {
                "FAIL_IDEMPOTENCY_CONFLICT"
            }
            return existing
        }

        val localSequence = allocateSequence(
            organizationId = draft.organizationId,
            counterKind = COUNTER_GLOBAL,
            aggregateType = "",
            aggregateId = "",
            updatedAt = updatedAt,
        )
        val aggregateSequence = allocateSequence(
            organizationId = draft.organizationId,
            counterKind = COUNTER_AGGREGATE,
            aggregateType = draft.aggregateType,
            aggregateId = draft.aggregateId,
            updatedAt = updatedAt,
        )
        val row = SyncOutboxEntity(
            mutationId = draft.mutationId,
            organizationId = draft.organizationId,
            aggregateType = draft.aggregateType,
            aggregateId = draft.aggregateId,
            operationType = draft.operationType,
            baseVersion = draft.baseVersion,
            localSequence = localSequence,
            aggregateSequence = aggregateSequence,
            payloadVersion = draft.payloadVersion,
            payloadJson = draft.payloadJson,
            semanticFingerprint = fingerprint,
            commandBatchId = draft.commandBatchId,
            commandOrder = draft.commandOrder,
            dependsOnMutationId = draft.dependsOnMutationId,
            createdAt = draft.createdAt,
        )
        check(insertOutboxRaw(row) != -1L) { "outbox insert failed" }
        return row
    }

    private suspend fun allocateSequence(
        organizationId: String,
        counterKind: String,
        aggregateType: String,
        aggregateId: String,
        updatedAt: Long,
    ): Long {
        ensureSequenceCounterRaw(
            SyncSequenceStateEntity(
                organizationId = organizationId,
                counterKind = counterKind,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                lastValue = 0L,
                updatedAt = updatedAt,
            )
        )
        check(incrementSequenceCounterRaw(organizationId, counterKind, aggregateType, aggregateId, updatedAt) == 1) {
            "sequence counter update failed"
        }
        return checkNotNull(readSequenceCounterRaw(organizationId, counterKind, aggregateType, aggregateId)).lastValue
    }

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE organization_id = :organizationId
          AND state IN ('PENDING','RETRY')
          AND next_attempt_at <= :now
        ORDER BY local_sequence ASC
        LIMIT :limit
        """
    )
    abstract suspend fun listEligibleOutboxRaw(
        organizationId: String,
        now: Long,
        limit: Int,
    ): List<SyncOutboxEntity>

    suspend fun listEligibleOutbox(organizationId: String, now: Long, limit: Int): List<SyncOutboxEntity> {
        require(limit in 1..MAX_WORKER_OPERATIONS_PER_RUN) { "outbox limit must be 1..500" }
        return listEligibleOutboxRaw(organizationId, now, limit)
    }

    @Query("SELECT * FROM sync_outbox WHERE mutation_id = :mutationId LIMIT 1")
    abstract suspend fun readDependency(mutationId: String): SyncOutboxEntity?

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'LEASED',
            attempt_count = attempt_count + 1,
            lease_owner = :leaseOwner,
            lease_token = :leaseToken,
            lease_scope_epoch = :scopeEpoch,
            lease_expires_at = :leaseExpiresAt,
            last_error_type = NULL,
            last_error_code = NULL
        WHERE mutation_id = :mutationId
          AND state = :expectedState
          AND state IN ('PENDING','RETRY')
        """
    )
    abstract suspend fun tryLease(
        mutationId: String,
        expectedState: String,
        leaseOwner: String,
        leaseToken: String,
        scopeEpoch: Long,
        leaseExpiresAt: Long,
    ): Int

    @Query(
        """UPDATE sync_outbox SET lease_expires_at=:leaseExpiresAt
           WHERE mutation_id=:mutationId AND state='LEASED'
             AND lease_token=:leaseToken AND lease_scope_epoch=:scopeEpoch"""
    )
    abstract suspend fun renewLease(
        mutationId: String,
        leaseToken: String,
        scopeEpoch: Long,
        leaseExpiresAt: Long,
    ): Int

    @Query(
        """
        UPDATE sync_outbox
        SET state = 'RETRY',
            last_error_type = :errorType,
            last_error_code = :errorCode,
            next_attempt_at = :nextAttemptAt,
            lease_owner = NULL,
            lease_token = NULL,
            lease_scope_epoch = NULL,
            lease_expires_at = NULL
        WHERE mutation_id = :mutationId
          AND state = 'LEASED'
          AND lease_token = :leaseToken
          AND lease_scope_epoch = :scopeEpoch
        """
    )
    abstract suspend fun markRetry(
        mutationId: String,
        leaseToken: String,
        scopeEpoch: Long,
        errorType: String?,
        errorCode: String?,
        nextAttemptAt: Long,
    ): Int

    @Query(
        """
        UPDATE sync_outbox
        SET state = :terminalState,
            acked_server_revision = :serverRevision,
            acked_server_version = :serverVersion,
            receipt_status = :receiptStatus,
            acked_at = :ackedAt,
            lease_owner = NULL,
            lease_token = NULL,
            lease_scope_epoch = NULL,
            lease_expires_at = NULL
        WHERE mutation_id = :mutationId
          AND state = 'LEASED'
          AND lease_token = :leaseToken
          AND lease_scope_epoch = :scopeEpoch
          AND semantic_fingerprint = :expectedSemanticFingerprint
        """
    )
    abstract suspend fun markTerminalRaw(
        mutationId: String,
        leaseToken: String,
        scopeEpoch: Long,
        expectedSemanticFingerprint: String,
        terminalState: String,
        serverRevision: Long?,
        serverVersion: Long?,
        receiptStatus: String?,
        ackedAt: Long?,
    ): Int

    suspend fun markTerminal(
        mutationId: String,
        leaseToken: String,
        scopeEpoch: Long,
        expectedSemanticFingerprint: String,
        terminalState: String,
        serverRevision: Long?,
        serverVersion: Long?,
        receiptStatus: String?,
        ackedAt: Long?,
    ): Int {
        require(terminalState in TERMINAL_OUTBOX_STATES) { "invalid terminal outbox state" }
        require(expectedSemanticFingerprint.isNotBlank()) { "semantic fingerprint is required for terminal ACK/CAS" }
        return markTerminalRaw(
            mutationId, leaseToken, scopeEpoch, expectedSemanticFingerprint, terminalState, serverRevision, serverVersion, receiptStatus, ackedAt
        )
    }

    /** M05: terminal dependency failures never remain runnable forever. */
    @Query(
        """
        UPDATE sync_outbox
        SET state = 'REQUIRES_REVIEW',
            last_error_type = 'DEPENDENCY',
            last_error_code = :errorCode,
            lease_owner = NULL,
            lease_token = NULL,
            lease_scope_epoch = NULL,
            lease_expires_at = NULL
        WHERE mutation_id = :mutationId
          AND semantic_fingerprint = :expectedSemanticFingerprint
          AND state IN ('PENDING','RETRY')
        """
    )
    abstract suspend fun markDependencyRequiresReview(
        mutationId: String,
        expectedSemanticFingerprint: String,
        errorCode: String,
    ): Int

    @Query(
        """
        UPDATE sync_outbox
        SET state = :terminalState,
            last_error_type = :errorType,
            last_error_code = :errorCode
        WHERE mutation_id = :mutationId
          AND semantic_fingerprint = :expectedSemanticFingerprint
          AND state IN ('PENDING','RETRY')
        """
    )
    abstract suspend fun markUnleasedTerminalRaw(
        mutationId: String,
        expectedSemanticFingerprint: String,
        terminalState: String,
        errorType: String,
        errorCode: String,
    ): Int

    suspend fun markUnleasedTerminal(
        mutationId: String,
        expectedSemanticFingerprint: String,
        terminalState: String,
        errorType: String,
        errorCode: String,
    ): Int {
        require(terminalState in setOf("REQUIRES_REVIEW", "REJECTED"))
        return markUnleasedTerminalRaw(mutationId, expectedSemanticFingerprint, terminalState, errorType, errorCode)
    }

    /** B11 explicit user resolution. These transitions never mutate the frozen request bytes. */
    @Query(
        """
        UPDATE sync_outbox SET state='SUPERSEDED_PENDING_PROOF',
            last_error_type='CONFLICT', last_error_code='REPLACEMENT_AWAITING_PROOF',
            lease_owner=NULL, lease_token=NULL, lease_scope_epoch=NULL, lease_expires_at=NULL
        WHERE mutation_id=:mutationId AND organization_id=:organizationId
          AND semantic_fingerprint=:expectedSemanticFingerprint AND state='REQUIRES_REVIEW'
        """
    )
    abstract suspend fun markReviewSupersededPendingProof(
        mutationId: String, organizationId: String, expectedSemanticFingerprint: String,
    ): Int

    @Query(
        """
        UPDATE sync_outbox SET state='SUPERSEDED_WITH_PROOF',
            last_error_type='CONFLICT_RESOLVED', last_error_code=:proofCode, acked_at=:resolvedAt,
            lease_owner=NULL, lease_token=NULL, lease_scope_epoch=NULL, lease_expires_at=NULL
        WHERE mutation_id=:mutationId AND organization_id=:organizationId
          AND semantic_fingerprint=:expectedSemanticFingerprint AND state='REQUIRES_REVIEW'
        """
    )
    abstract suspend fun markReviewSupersededWithProof(
        mutationId: String, organizationId: String, expectedSemanticFingerprint: String,
        proofCode: String, resolvedAt: Long,
    ): Int

    @Query(
        """
        UPDATE sync_outbox SET state='SUPERSEDED_WITH_PROOF',
            last_error_type='CONFLICT_RESOLVED', last_error_code=:proofCode, acked_at=:resolvedAt
        WHERE mutation_id=:mutationId AND organization_id=:organizationId
          AND semantic_fingerprint=:expectedSemanticFingerprint AND state='SUPERSEDED_PENDING_PROOF'
        """
    )
    abstract suspend fun proveSupersededMutation(
        mutationId: String, organizationId: String, expectedSemanticFingerprint: String,
        proofCode: String, resolvedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id=:organizationId AND state='REQUIRES_REVIEW'")
    abstract suspend fun countRequiresReview(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id=:organizationId AND state='REJECTED'")
    abstract suspend fun countRejected(organizationId: String): Long

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
          AND aggregate_sequence < :aggregateSequence
          AND state IN ('REQUIRES_REVIEW','REJECTED')
        """
    )
    abstract suspend fun countOlderTerminalBlockers(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        aggregateSequence: Long,
    ): Int


    /** Session 309: process-death recovery; wall clock is delivery metadata only. */
    @Query(
        """
        UPDATE sync_outbox
        SET state = 'RETRY',
            last_error_type = 'TRANSIENT_NETWORK',
            last_error_code = 'EXPIRED_LEASE_RECOVERED',
            next_attempt_at = :now,
            lease_owner = NULL,
            lease_token = NULL,
            lease_scope_epoch = NULL,
            lease_expires_at = NULL
        WHERE organization_id = :organizationId
          AND state = 'LEASED'
          AND lease_expires_at IS NOT NULL
          AND lease_expires_at <= :now
        """
    )
    abstract suspend fun recoverExpiredLeases(organizationId: String, now: Long): Int

    /** B11 replacement may jump only over local intents created after its superseded parent. */
    @Query(
        """
        SELECT parent.aggregate_sequence FROM sync_conflict c
        JOIN sync_outbox parent ON parent.mutation_id = c.mutation_id
        WHERE c.resolution_mutation_id = :replacementMutationId
          AND c.organization_id = :organizationId
          AND c.state = 'WAITING_REPLACEMENT_RECEIPT'
          AND parent.state = 'SUPERSEDED_PENDING_PROOF'
        LIMIT 1
        """
    )
    abstract suspend fun replacementParentSequence(
        organizationId: String,
        replacementMutationId: String,
    ): Long?

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
          AND aggregate_sequence < :aggregateSequence
          AND state = 'SUPERSEDED_PENDING_PROOF'
        """
    )
    abstract suspend fun countOlderSupersededPendingProof(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        aggregateSequence: Long,
    ): Int

    /** Same-aggregate delivery must not leapfrog an older active mutation. */
    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
          AND aggregate_sequence < :aggregateSequence
          AND state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW')
        """
    )
    abstract suspend fun countOlderActiveMutations(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        aggregateSequence: Long,
    ): Int

    /**
     * Session 309 authoritative-echo ACK. Deliberately ignores lease token because a server commit may
     * outlive the lease/response, but constrains immutable identity and active states. Must be called
     * from the Pull page Room transaction.
     */
    @Query(
        """
        UPDATE sync_outbox
        SET state = 'ACKNOWLEDGED',
            acked_server_revision = :serverRevision,
            acked_server_version = :serverVersion,
            receipt_status = 'APPLIED',
            acked_at = :ackedAt,
            last_error_type = NULL,
            last_error_code = NULL,
            lease_owner = NULL,
            lease_token = NULL,
            lease_scope_epoch = NULL,
            lease_expires_at = NULL
        WHERE mutation_id = :mutationId
          AND organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
          AND state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW')
        """
    )
    abstract suspend fun acknowledgeByAuthoritativeEcho(
        mutationId: String,
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        serverRevision: Long,
        serverVersion: Long?,
        ackedAt: Long,
    ): Int

}
