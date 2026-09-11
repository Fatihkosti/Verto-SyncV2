package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.OptimalOutboxEntity
import com.verto.app.data.local.entity.OptimalOutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimalOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: OptimalOutboxEntity)

    @Query(
        """
        SELECT * FROM optimal_outbox
        WHERE organization_id = :organizationId AND event_id = :eventId
        LIMIT 1
        """,
    )
    suspend fun get(
        organizationId: String,
        eventId: String,
    ): OptimalOutboxEntity?

    @Query(
        """
        SELECT * FROM optimal_outbox
        WHERE organization_id = :organizationId AND idempotency_key = :idempotencyKey
        LIMIT 1
        """,
    )
    suspend fun getByIdempotencyKey(
        organizationId: String,
        idempotencyKey: String,
    ): OptimalOutboxEntity?

    @Query(
        """
        SELECT * FROM optimal_outbox
        WHERE organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
        ORDER BY sequence DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForAggregate(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
    ): OptimalOutboxEntity?

    @Query(
        """
        SELECT * FROM optimal_outbox
        WHERE organization_id = :organizationId
        ORDER BY created_at ASC, aggregate_type ASC, aggregate_id ASC, sequence ASC
        """,
    )
    fun observeForOrganization(organizationId: String): Flow<List<OptimalOutboxEntity>>

    @Query(
        """
        SELECT * FROM optimal_outbox
        WHERE organization_id = :organizationId AND status = :status
        ORDER BY created_at ASC, aggregate_type ASC, aggregate_id ASC, sequence ASC
        LIMIT :limit
        """,
    )
    suspend fun getByStatus(
        organizationId: String,
        status: OptimalOutboxStatus,
        limit: Int,
    ): List<OptimalOutboxEntity>

    @Query(
        """
        SELECT * FROM optimal_outbox
        WHERE organization_id = :organizationId
          AND (
              status IN ('FAILED', 'BLOCKED')
              OR (status IN ('PENDING', 'SYNCING') AND last_error IS NOT NULL)
          )
        ORDER BY updated_at DESC, created_at DESC, event_id ASC
        """,
    )
    fun observeVisibleIssues(organizationId: String): Flow<List<OptimalOutboxEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM optimal_outbox
        WHERE organization_id = :organizationId
          AND (
              status IN ('FAILED', 'BLOCKED')
              OR (status IN ('PENDING', 'SYNCING') AND last_error IS NOT NULL)
          )
        """,
    )
    fun observeIssueCount(organizationId: String): Flow<Int>

    /**
     * Requeues the same durable event. The row, sequence, payload, and idempotency key are preserved.
     * Tenant scoping and eligible-state CAS prevent cross-organization or duplicate retries.
     */
    @Query(
        """
        UPDATE optimal_outbox
        SET status = 'PENDING',
            next_attempt_at = :retriedAt,
            lease_owner = NULL,
            lease_token = NULL,
            lease_expires_at = NULL,
            updated_at = :retriedAt
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status IN ('FAILED', 'BLOCKED')
        """,
    )
    suspend fun retryVisibleIssue(
        organizationId: String,
        eventId: String,
        retriedAt: Long,
    ): Int

    @Query(
        """
        SELECT COALESCE(MAX(sequence), 0) + 1 FROM optimal_outbox
        WHERE organization_id = :organizationId
          AND aggregate_type = :aggregateType
          AND aggregate_id = :aggregateId
        """,
    )
    suspend fun nextSequence(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
    ): Long

    /**
     * Returns only the first unresolved event of each aggregate. A later event cannot be claimed
     * while an earlier LOCAL_ONLY, PENDING, SYNCING, FAILED, or BLOCKED event exists for that same tenant aggregate.
     */
    @Query(
        """
        SELECT candidate.*
        FROM optimal_outbox AS candidate
        WHERE candidate.organization_id = :organizationId
          AND candidate.status IN ('PENDING', 'FAILED')
          AND (candidate.next_attempt_at IS NULL OR candidate.next_attempt_at <= :now)
          AND NOT EXISTS (
              SELECT 1
              FROM optimal_outbox AS predecessor
              WHERE predecessor.organization_id = candidate.organization_id
                AND predecessor.aggregate_type = candidate.aggregate_type
                AND predecessor.aggregate_id = candidate.aggregate_id
                AND predecessor.sequence < candidate.sequence
                AND predecessor.status IN ('LOCAL_ONLY', 'PENDING', 'SYNCING', 'FAILED', 'BLOCKED')
          )
        ORDER BY candidate.created_at ASC,
                 candidate.aggregate_type ASC,
                 candidate.aggregate_id ASC,
                 candidate.sequence ASC
        LIMIT :limit
        """,
    )
    suspend fun findClaimableAggregateHeads(
        organizationId: String,
        now: Long,
        limit: Int,
    ): List<OptimalOutboxEntity>

    @Query("SELECT COUNT(*) FROM optimal_outbox WHERE organization_id=:organizationId AND status IN ('PENDING','FAILED','SYNCING')")
    suspend fun countSyncBacklog(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM optimal_outbox WHERE organization_id=:organizationId AND status='BLOCKED'")
    suspend fun countBlockedReview(organizationId: String): Long

    @Query("SELECT MIN(next_attempt_at) FROM optimal_outbox WHERE organization_id=:organizationId AND status='FAILED' AND next_attempt_at>:now")
    suspend fun nextRetryAt(organizationId: String, now: Long): Long?

    /** Atomic claim. The predecessor check is repeated to close the select/update race. */
    @Query(
        """
        UPDATE optimal_outbox
        SET status = 'SYNCING',
            attempt_count = attempt_count + 1,
            last_attempt_at = :now,
            next_attempt_at = NULL,
            lease_owner = :leaseOwner,
            lease_token = :leaseToken,
            lease_expires_at = :leaseExpiresAt,
            updated_at = :now
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status = :expectedStatus
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
          AND NOT EXISTS (
              SELECT 1
              FROM optimal_outbox AS predecessor
              WHERE predecessor.organization_id = :organizationId
                AND predecessor.aggregate_type = optimal_outbox.aggregate_type
                AND predecessor.aggregate_id = optimal_outbox.aggregate_id
                AND predecessor.sequence < optimal_outbox.sequence
                AND predecessor.status IN ('LOCAL_ONLY', 'PENDING', 'SYNCING', 'FAILED', 'BLOCKED')
          )
        """,
    )
    suspend fun claim(
        organizationId: String,
        eventId: String,
        expectedStatus: OptimalOutboxStatus,
        leaseOwner: String,
        leaseToken: String,
        now: Long,
        leaseExpiresAt: Long,
    ): Int

    /** A crashed worker cannot leave an event in SYNCING forever. */
    @Query(
        """
        UPDATE optimal_outbox
        SET status = 'FAILED',
            last_error = :reason,
            next_attempt_at = :now,
            lease_owner = NULL,
            lease_token = NULL,
            lease_expires_at = NULL,
            updated_at = :now
        WHERE organization_id = :organizationId
          AND status = 'SYNCING'
          AND (lease_expires_at IS NULL OR lease_expires_at <= :now)
        """,
    )
    suspend fun recoverExpiredLeases(
        organizationId: String,
        now: Long,
        reason: String,
    ): Int

    @Query(
        """
        UPDATE optimal_outbox
        SET lease_expires_at = :leaseExpiresAt,
            updated_at = :now
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status = 'SYNCING'
          AND lease_owner = :leaseOwner
          AND lease_token = :leaseToken
        """,
    )
    suspend fun renewLease(
        organizationId: String,
        eventId: String,
        leaseOwner: String,
        leaseToken: String,
        now: Long,
        leaseExpiresAt: Long,
    ): Int

    @Query(
        """
        UPDATE optimal_outbox
        SET status = 'SYNCED',
            remote_id = :remoteId,
            remote_version = :remoteVersion,
            synced_at = :syncedAt,
            next_attempt_at = NULL,
            lease_owner = NULL,
            lease_token = NULL,
            lease_expires_at = NULL,
            updated_at = :syncedAt
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status = 'SYNCING'
          AND lease_owner = :leaseOwner
          AND lease_token = :leaseToken
        """,
    )
    suspend fun markSynced(
        organizationId: String,
        eventId: String,
        leaseOwner: String,
        leaseToken: String,
        remoteId: String?,
        remoteVersion: Long?,
        syncedAt: Long,
    ): Int

    @Query(
        """
        UPDATE optimal_outbox
        SET status = 'FAILED',
            last_error = :reason,
            next_attempt_at = :nextAttemptAt,
            lease_owner = NULL,
            lease_token = NULL,
            lease_expires_at = NULL,
            updated_at = :failedAt
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status = 'SYNCING'
          AND lease_owner = :leaseOwner
          AND lease_token = :leaseToken
        """,
    )
    suspend fun markFailed(
        organizationId: String,
        eventId: String,
        leaseOwner: String,
        leaseToken: String,
        reason: String,
        failedAt: Long,
        nextAttemptAt: Long,
    ): Int

    @Query(
        """
        UPDATE optimal_outbox
        SET status = 'BLOCKED',
            last_error = :reason,
            remote_id = COALESCE(:remoteId, remote_id),
            remote_version = COALESCE(:remoteVersion, remote_version),
            next_attempt_at = NULL,
            lease_owner = NULL,
            lease_token = NULL,
            lease_expires_at = NULL,
            updated_at = :blockedAt
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status = 'SYNCING'
          AND lease_owner = :leaseOwner
          AND lease_token = :leaseToken
        """,
    )
    suspend fun markBlocked(
        organizationId: String,
        eventId: String,
        leaseOwner: String,
        leaseToken: String,
        reason: String,
        remoteId: String?,
        remoteVersion: Long?,
        blockedAt: Long,
    ): Int

    /** Compatibility CAS retained for existing callers/tests; new sync code uses lease-token CAS. */
    @Query(
        """
        UPDATE optimal_outbox
        SET status = :newStatus,
            attempt_count = :attemptCount,
            last_error = :lastError,
            updated_at = :updatedAt
        WHERE organization_id = :organizationId
          AND event_id = :eventId
          AND status = :expectedStatus
        """,
    )
    suspend fun compareAndSetStatus(
        organizationId: String,
        eventId: String,
        expectedStatus: OptimalOutboxStatus,
        newStatus: OptimalOutboxStatus,
        attemptCount: Int,
        lastError: String?,
        updatedAt: Long,
    ): Int
}
