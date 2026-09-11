package com.verto.app.feature.integration.optimal.data

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.OptimalOutboxDao
import com.verto.app.data.local.entity.OptimalOutboxEntity
import com.verto.app.data.local.entity.OptimalOutboxStatus
import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxEvent
import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxLease
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncState
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssue
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.repository.OptimalOutboxRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomOptimalOutboxRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: OptimalOutboxDao,
    private val issueMapper: OptimalSyncIssueMapper,
    private val operationGuard: OptimalOperationGuard,
) : OptimalOutboxRepository {

    override fun observeIssues(organizationId: String): Flow<List<OptimalSyncIssue>> {
        requireOrganization(organizationId)
        return dao.observeVisibleIssues(organizationId).map { rows -> rows.map(issueMapper::map) }
    }

    override suspend fun retryIssue(
        organizationId: String,
        eventId: String,
        retriedAt: Long,
    ): Boolean {
        requireOrganization(organizationId)
        require(eventId.isNotBlank()) { "eventId is required" }
        val access = operationGuard.check(
            operation = OptimalOperation.RETRY_SYNC,
            layer = OptimalGuardLayer.REPOSITORY,
            details = "organizationId=$organizationId,eventId=$eventId",
        )
        if (access != OptimalAccessDecision.Granted) return false
        return dao.retryVisibleIssue(
            organizationId = organizationId,
            eventId = eventId,
            retriedAt = retriedAt,
        ) == 1
    }

    override suspend fun recoverExpiredLeases(
        organizationId: String,
        now: Long,
    ): Int {
        requireOrganization(organizationId)
        return dao.recoverExpiredLeases(
            organizationId = organizationId,
            now = now,
            reason = EXPIRED_LEASE_REASON,
        )
    }

    override suspend fun claimNext(
        organizationId: String,
        leaseOwner: String,
        leaseToken: String,
        now: Long,
        leaseDurationMillis: Long,
    ): OptimalOutboxLease? {
        requireOrganization(organizationId)
        require(leaseOwner.isNotBlank()) { "leaseOwner is required" }
        require(leaseToken.isNotBlank()) { "leaseToken is required" }
        require(leaseDurationMillis > 0L) { "leaseDurationMillis must be positive" }
        val expiresAt = Math.addExact(now, leaseDurationMillis)

        return database.withTransaction {
            dao.recoverExpiredLeases(
                organizationId = organizationId,
                now = now,
                reason = EXPIRED_LEASE_REASON,
            )
            dao.findClaimableAggregateHeads(
                organizationId = organizationId,
                now = now,
                limit = CLAIM_CANDIDATE_WINDOW,
            ).firstNotNullOfOrNull { candidate ->
                val claimed = dao.claim(
                    organizationId = organizationId,
                    eventId = candidate.eventId,
                    expectedStatus = candidate.status,
                    leaseOwner = leaseOwner,
                    leaseToken = leaseToken,
                    now = now,
                    leaseExpiresAt = expiresAt,
                )
                if (claimed != 1) return@firstNotNullOfOrNull null
                val row = checkNotNull(dao.get(organizationId, candidate.eventId)) {
                    "Claimed Outbox event disappeared"
                }
                check(row.status == OptimalOutboxStatus.SYNCING)
                check(row.leaseOwner == leaseOwner && row.leaseToken == leaseToken)
                OptimalOutboxLease(
                    event = row.toDomain(),
                    owner = leaseOwner,
                    token = leaseToken,
                    expiresAt = expiresAt,
                )
            }
        }
    }

    override suspend fun renewLease(
        lease: OptimalOutboxLease,
        now: Long,
        leaseDurationMillis: Long,
    ): Boolean {
        require(leaseDurationMillis > 0L) { "leaseDurationMillis must be positive" }
        val expiresAt = Math.addExact(now, leaseDurationMillis)
        return dao.renewLease(
            organizationId = lease.event.organizationId,
            eventId = lease.event.eventId,
            leaseOwner = lease.owner,
            leaseToken = lease.token,
            now = now,
            leaseExpiresAt = expiresAt,
        ) == 1
    }

    override suspend fun markSynced(
        lease: OptimalOutboxLease,
        remoteId: String?,
        remoteVersion: Long?,
        syncedAt: Long,
    ): Boolean = dao.markSynced(
        organizationId = lease.event.organizationId,
        eventId = lease.event.eventId,
        leaseOwner = lease.owner,
        leaseToken = lease.token,
        remoteId = remoteId?.trim()?.takeIf { it.isNotEmpty() },
        remoteVersion = remoteVersion,
        syncedAt = syncedAt,
    ) == 1

    override suspend fun markFailed(
        lease: OptimalOutboxLease,
        reason: String,
        failedAt: Long,
        nextAttemptAt: Long,
    ): Boolean {
        require(nextAttemptAt >= failedAt) { "nextAttemptAt cannot precede failedAt" }
        return dao.markFailed(
            organizationId = lease.event.organizationId,
            eventId = lease.event.eventId,
            leaseOwner = lease.owner,
            leaseToken = lease.token,
            reason = reason.trim().ifBlank { "REMOTE_OPERATION_FAILED" }.take(MAX_REASON_LENGTH),
            failedAt = failedAt,
            nextAttemptAt = nextAttemptAt,
        ) == 1
    }

    override suspend fun markBlocked(
        lease: OptimalOutboxLease,
        reason: String,
        remoteId: String?,
        remoteVersion: Long?,
        blockedAt: Long,
    ): Boolean = dao.markBlocked(
        organizationId = lease.event.organizationId,
        eventId = lease.event.eventId,
        leaseOwner = lease.owner,
        leaseToken = lease.token,
        reason = reason.trim().ifBlank { "REMOTE_CONFLICT_BLOCKED" }.take(MAX_REASON_LENGTH),
        remoteId = remoteId?.trim()?.takeIf { it.isNotEmpty() },
        remoteVersion = remoteVersion,
        blockedAt = blockedAt,
    ) == 1

    private fun requireOrganization(organizationId: String) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
    }

    private companion object {
        const val CLAIM_CANDIDATE_WINDOW = 32
        const val MAX_REASON_LENGTH = 400
        const val EXPIRED_LEASE_REASON = "SYNC_LEASE_EXPIRED_RECOVERED"
    }
}

private fun OptimalOutboxEntity.toDomain(): OptimalOutboxEvent = OptimalOutboxEvent(
    organizationId = organizationId,
    eventId = eventId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    operation = operation,
    payloadJson = payloadJson,
    payloadVersion = payloadVersion,
    idempotencyKey = idempotencyKey,
    sequence = sequence,
    aggregateVersion = sequence,
    state = OptimalSyncState.valueOf(status.name),
    attemptCount = attemptCount,
    lastError = lastError,
    lastAttemptAt = lastAttemptAt,
    nextAttemptAt = nextAttemptAt,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    syncedAt = syncedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
