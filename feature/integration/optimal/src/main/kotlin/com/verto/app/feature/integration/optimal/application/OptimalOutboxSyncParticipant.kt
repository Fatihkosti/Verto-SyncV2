package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxExecutionResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncRunSummary
import com.verto.app.feature.integration.optimal.domain.port.OptimalLeaseTokenFactory
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutboxEventExecutor
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScope
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncSessionGuard
import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import com.verto.app.feature.integration.optimal.domain.repository.OptimalOutboxRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import javax.inject.Singleton

/** Consumes durable aggregate heads using the ordering and lease rules established in v76. */
@Singleton
class OptimalOutboxSyncParticipant @Inject constructor(
    private val repository: OptimalOutboxRepository,
    private val executor: OptimalOutboxEventExecutor,
    private val clock: OptimalClock,
    private val tokenFactory: OptimalLeaseTokenFactory,
    private val recoveryPolicy: OptimalSyncRecoveryPolicy,
    private val sessionGuard: OptimalSyncSessionGuard,
) {
    suspend fun syncOrganization(
        scope: OptimalSyncScope,
        workerId: String,
        maxEvents: Int = DEFAULT_BATCH_SIZE,
    ): OptimalSyncRunSummary {
        require(workerId.isNotBlank()) { "workerId is required" }
        require(maxEvents in 1..MAX_BATCH_SIZE) { "maxEvents must be in 1..$MAX_BATCH_SIZE" }
        if (!sessionGuard.isActive(scope)) return OptimalSyncRunSummary(stoppedBySessionChange = true)

        var summary = OptimalSyncRunSummary(
            recoveredLeases = repository.recoverExpiredLeases(
                organizationId = scope.organizationId,
                now = clock.nowMillis(),
            ),
        )
        repeat(maxEvents) {
            if (!sessionGuard.isActive(scope)) {
                return summary.copy(stoppedBySessionChange = true)
            }
            val now = clock.nowMillis()
            val lease = repository.claimNext(
                organizationId = scope.organizationId,
                leaseOwner = workerId,
                leaseToken = tokenFactory.create(),
                now = now,
                leaseDurationMillis = recoveryPolicy.leaseDurationMillis,
            ) ?: return summary
            summary = summary.copy(claimed = summary.claimed + 1)

            if (!sessionGuard.isActive(scope)) {
                val stoppedAt = clock.nowMillis()
                check(
                    repository.markFailed(
                        lease = lease,
                        reason = SESSION_CHANGED_REASON,
                        failedAt = stoppedAt,
                        nextAttemptAt = stoppedAt,
                    ),
                ) { "Outbox lease lost while stopping stale session work" }
                return summary.copy(
                    failed = summary.failed + 1,
                    stoppedBySessionChange = true,
                )
            }

            val result = try {
                executor.execute(lease.event)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                OptimalOutboxExecutionResult.Failed(recoveryPolicy.sanitizeFailure(failure))
            }
            val completedAt = clock.nowMillis()
            when (result) {
                is OptimalOutboxExecutionResult.Synced -> {
                    check(
                        repository.markSynced(
                            lease = lease,
                            remoteId = result.remoteId,
                            remoteVersion = result.remoteVersion,
                            syncedAt = completedAt,
                        ),
                    ) { "Outbox lease lost before successful acknowledgement" }
                    summary = summary.copy(synced = summary.synced + 1)
                }

                is OptimalOutboxExecutionResult.Failed -> {
                    val reason = result.reason.trim().ifBlank { "REMOTE_OPERATION_FAILED" }.take(400)
                    check(
                        repository.markFailed(
                            lease = lease,
                            reason = reason,
                            failedAt = completedAt,
                            nextAttemptAt = recoveryPolicy.nextAttemptAt(
                                attemptCount = lease.event.attemptCount,
                                failedAt = completedAt,
                            ),
                        ),
                    ) { "Outbox lease lost before failure acknowledgement" }
                    summary = summary.copy(failed = summary.failed + 1)
                }


                is OptimalOutboxExecutionResult.Blocked -> {
                    val reason = result.reason.trim().ifBlank { "REMOTE_CONFLICT_BLOCKED" }.take(400)
                    check(
                        repository.markBlocked(
                            lease = lease,
                            reason = reason,
                            remoteId = result.remoteId,
                            remoteVersion = result.remoteVersion,
                            blockedAt = completedAt,
                        ),
                    ) { "Outbox lease lost before blocked acknowledgement" }
                    summary = summary.copy(blocked = summary.blocked + 1)
                }
            }
        }
        return summary
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val MAX_BATCH_SIZE = 500
        const val SESSION_CHANGED_REASON = "SYNC_SESSION_CHANGED"
    }
}
