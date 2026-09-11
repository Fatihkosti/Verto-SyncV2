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

interface UnifiedSyncOrchestrationDao: UnifiedSyncOutboxDao {
    // ── Session 311 durable orchestration generation authority ───────────────────────────────

    @Query(
        """
        UPDATE sync_sequence_state
        SET last_value = :value, updated_at = :updatedAt
        WHERE organization_id = :organizationId
          AND counter_kind = :counterKind
          AND aggregate_type = ''
          AND aggregate_id = ''
          AND last_value <= :value
        """
    )
    abstract suspend fun setOrchestrationCounterRaw(
        organizationId: String,
        counterKind: String,
        value: Long,
        updatedAt: Long,
    ): Int

    @Transaction
    open suspend fun requestOrchestrationGeneration(organizationId: String, updatedAt: Long): Long {
        require(organizationId.isNotBlank()) { "organization id is required" }
        ensureSequenceCounterRaw(SyncSequenceStateEntity(organizationId, COUNTER_ORCHESTRATION_REQUESTED, "", "", 0L, updatedAt))
        ensureSequenceCounterRaw(SyncSequenceStateEntity(organizationId, COUNTER_ORCHESTRATION_DRAINED, "", "", 0L, updatedAt))
        check(incrementSequenceCounterRaw(organizationId, COUNTER_ORCHESTRATION_REQUESTED, "", "", updatedAt) == 1)
        return checkNotNull(readSequenceCounterRaw(organizationId, COUNTER_ORCHESTRATION_REQUESTED, "", "")).lastValue
    }

    @Transaction
    open suspend fun readOrchestrationGenerations(organizationId: String): OrchestrationGenerationState {
        val requested = readSequenceCounterRaw(organizationId, COUNTER_ORCHESTRATION_REQUESTED, "", "")?.lastValue ?: 0L
        val drained = readSequenceCounterRaw(organizationId, COUNTER_ORCHESTRATION_DRAINED, "", "")?.lastValue ?: 0L
        check(requested >= drained) { "FAIL_DRAINED_AHEAD_OF_REQUESTED" }
        return OrchestrationGenerationState(requested, drained)
    }

    /** CAS-like idle handshake: Room transaction serializes this check with request increments. */
    @Transaction
    open suspend fun markOrchestrationDrainedIfIdle(
        organizationId: String,
        observedRequested: Long,
        updatedAt: Long,
    ): Boolean {
        val state = readOrchestrationGenerations(organizationId)
        if (state.requested != observedRequested || state.drained > observedRequested) return false
        ensureSequenceCounterRaw(SyncSequenceStateEntity(organizationId, COUNTER_ORCHESTRATION_DRAINED, "", "", 0L, updatedAt))
        check(setOrchestrationCounterRaw(organizationId, COUNTER_ORCHESTRATION_DRAINED, observedRequested, updatedAt) == 1)
        return readOrchestrationGenerations(organizationId).let { it.requested == observedRequested && it.drained == observedRequested }
    }

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND state IN ('PENDING','RETRY')
          AND next_attempt_at <= :now
        """
    )
    abstract suspend fun countEligibleOutboxNow(organizationId: String, now: Long): Long

    /** M05: only work that can actually advance counts as immediate continuation work. */
    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox AS candidate
        WHERE candidate.organization_id = :organizationId
          AND candidate.state IN ('PENDING','RETRY')
          AND candidate.next_attempt_at <= :now
          AND (candidate.depends_on_mutation_id IS NULL OR EXISTS (
              SELECT 1 FROM sync_outbox AS dependency
              WHERE dependency.mutation_id = candidate.depends_on_mutation_id
                AND dependency.organization_id = candidate.organization_id
                AND dependency.state = 'ACKNOWLEDGED'
          ))
          AND NOT EXISTS (
              SELECT 1 FROM sync_outbox AS predecessor
              WHERE predecessor.organization_id = candidate.organization_id
                AND predecessor.aggregate_type = candidate.aggregate_type
                AND predecessor.aggregate_id = candidate.aggregate_id
                AND predecessor.aggregate_sequence < candidate.aggregate_sequence
                AND predecessor.state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW','REJECTED')
          )
        """
    )
    abstract suspend fun countRunnableOutboxNow(organizationId: String, now: Long): Long

    @Query(
        """
        SELECT MIN(next_attempt_at) FROM sync_outbox
        WHERE organization_id = :organizationId
          AND state = 'RETRY'
          AND next_attempt_at > :now
        """
    )
    abstract suspend fun nextEligibleRetryAt(organizationId: String, now: Long): Long?

    /** Session 308: active local mutations block remote overwrite; pull never ACKs them. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sync_outbox
            WHERE organization_id = :organizationId
              AND aggregate_type = :aggregateType
              AND aggregate_id = :aggregateId
              AND state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW')
        )
        """
    )
    abstract suspend fun hasActiveMutationForAggregate(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
    ): Boolean

}
