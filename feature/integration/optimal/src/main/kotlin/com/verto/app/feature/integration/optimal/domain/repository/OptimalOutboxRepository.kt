package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxLease
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssue
import kotlinx.coroutines.flow.Flow

interface OptimalOutboxRepository {
    fun observeIssues(organizationId: String): Flow<List<OptimalSyncIssue>>

    suspend fun retryIssue(
        organizationId: String,
        eventId: String,
        retriedAt: Long,
    ): Boolean

    suspend fun recoverExpiredLeases(
        organizationId: String,
        now: Long,
    ): Int

    suspend fun claimNext(
        organizationId: String,
        leaseOwner: String,
        leaseToken: String,
        now: Long,
        leaseDurationMillis: Long,
    ): OptimalOutboxLease?

    suspend fun renewLease(
        lease: OptimalOutboxLease,
        now: Long,
        leaseDurationMillis: Long,
    ): Boolean

    suspend fun markSynced(
        lease: OptimalOutboxLease,
        remoteId: String?,
        remoteVersion: Long?,
        syncedAt: Long,
    ): Boolean

    suspend fun markFailed(
        lease: OptimalOutboxLease,
        reason: String,
        failedAt: Long,
        nextAttemptAt: Long,
    ): Boolean

    suspend fun markBlocked(
        lease: OptimalOutboxLease,
        reason: String,
        remoteId: String?,
        remoteVersion: Long?,
        blockedAt: Long,
    ): Boolean
}
