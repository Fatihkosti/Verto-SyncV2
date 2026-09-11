package com.verto.app.data.sync.pull

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncCursorEntity
import com.verto.app.data.local.entity.SyncRecoveryStateEntity
import com.verto.app.data.sync.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

enum class UnifiedSyncPullOutcome {
    CAUGHT_UP, MORE_AVAILABLE, BOOTSTRAP_REQUIRED, RECOVERY_REQUIRED,
    WAITING_LOCAL_OR_DEPENDENCY, REQUIRES_REVIEW, WAITING_STORAGE_OR_REVIEW,
}

data class UnifiedSyncPullResult(
    val outcome: UnifiedSyncPullOutcome,
    val pagesCommitted: Int,
    val changesAccounted: Int,
    val pendingInboxGroups: Long = 0L,
)

class UnifiedSyncPullFailure(val code: String, detail: String, cause: Throwable? = null) :
    IllegalStateException("$code: $detail", cause)

/** B10 data plane: durable receive cursor first, independent atomic business-group apply second. */
@Singleton
class UnifiedSyncPullEngine @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncPullRemote,
    private val inbox: DurableInboxApplyCoordinator,
) {
    suspend fun pull(organizationId: String, pageSize: Int = DEFAULT_PAGE_SIZE,
        maxPagesPerInvocation: Int = DEFAULT_MAX_PAGES, maxChangesPerInvocation: Int = DEFAULT_MAX_CHANGES): UnifiedSyncPullResult {
        require(organizationId.isNotBlank()) { "SCOPE_MISMATCH: trusted organization required" }
        require(pageSize in 1..MAX_PAGE_SIZE && maxPagesPerInvocation > 0 && maxChangesPerInvocation > 0)
        val scope = resolveTrustedScope(organizationId)
        val cursor = try { locateActiveCursor(scope) } catch (failure: UnifiedSyncPullFailure) {
            if (failure.code != "CURSOR_STALE" && failure.code != "SCOPE_MISMATCH") throw failure
            persistRecoveryObligation(scope, "CURSOR_CORRUPT", STATE_RECOVERY_REQUIRED)
            return UnifiedSyncPullResult(UnifiedSyncPullOutcome.RECOVERY_REQUIRED, 0, 0)
        } ?: run {
            persistRecoveryObligation(scope, "INITIAL_BOOTSTRAP", STATE_NOT_STARTED)
            return UnifiedSyncPullResult(UnifiedSyncPullOutcome.BOOTSTRAP_REQUIRED, 0, 0)
        }
        if (cursor.state != CURSOR_ACTIVE) {
            persistRecoveryObligation(scope, "CURSOR_CORRUPT", STATE_RECOVERY_REQUIRED)
            return UnifiedSyncPullResult(UnifiedSyncPullOutcome.RECOVERY_REQUIRED, 0, 0)
        }
        cursor.lastAppliedChangeRevision?.let { revision ->
            if (!database.syncRecoveryDao().hasAppliedInboxAnchor(scope.scopeId, revision)) {
                markBootstrapRequired(scope)
                persistRecoveryObligation(scope, "LOCAL_ANCHOR_MISSING", STATE_RECOVERY_REQUIRED)
                return UnifiedSyncPullResult(UnifiedSyncPullOutcome.RECOVERY_REQUIRED, 0, 0)
            }
        }
        var pages = 0
        var received = 0
        var attemptedGroups = 0
        var attemptedChanges = 0
        val started = System.nanoTime()
        suspend fun drainRemaining(): DurableInboxApplyCoordinator.DrainResult {
            val remaining = (maxChangesPerInvocation - attemptedChanges).coerceAtLeast(0)
            val timeRemaining = (20_000L - (System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            return inbox.drain(scope, remaining, timeRemaining, attemptedGroups).also {
                attemptedGroups += it.attemptedGroups
                attemptedChanges = Math.addExact(attemptedChanges, it.attemptedChanges)
            }
        }
        var drained = drainRemaining()
        if (drained.continuationRequired) return UnifiedSyncPullResult(UnifiedSyncPullOutcome.MORE_AVAILABLE, pages, received, drained.pendingGroups)
        while (pages < maxPagesPerInvocation) {
            if (received >= maxChangesPerInvocation || attemptedChanges >= maxChangesPerInvocation ||
                (attemptedGroups > 0 && (System.nanoTime() - started) / 1_000_000L >= 20_000L)) break
            val limit = inbox.receiveLimit(scope, minOf(pageSize, (maxChangesPerInvocation - received).coerceAtLeast(1)))
            if (limit == 0) return UnifiedSyncPullResult(UnifiedSyncPullOutcome.WAITING_STORAGE_OR_REVIEW, pages, received, drained.pendingGroups)
            val current = checkNotNull(database.unifiedSyncDao().getCursor(scope.scopeId))
            val page = try { remote.pull(scope, current.receivedCursorToken, limit) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) {
                if (isCursorExpired(failure)) {
                    markBootstrapRequired(scope)
                    persistRecoveryObligation(scope, "CURSOR_EXPIRED", STATE_RECOVERY_REQUIRED)
                    return UnifiedSyncPullResult(UnifiedSyncPullOutcome.RECOVERY_REQUIRED, pages, received, drained.pendingGroups)
                }
                throw classifyRemoteFailure(failure)
            }
            val receipt = inbox.receive(scope, current.receivedCursorToken, page)
            if (receipt.storageWait) {
                drained = drainRemaining()
                return UnifiedSyncPullResult(UnifiedSyncPullOutcome.WAITING_STORAGE_OR_REVIEW, pages, received, drained.pendingGroups)
            }
            if (receipt.committed) { pages++; received += receipt.insertedChanges }
            drained = drainRemaining()
            if (drained.continuationRequired) return UnifiedSyncPullResult(UnifiedSyncPullOutcome.MORE_AVAILABLE, pages, received, drained.pendingGroups)
            if (!page.hasMore) return UnifiedSyncPullResult(outcome(drained), pages, received, drained.pendingGroups)
            // Soft budget: the first complete legal group may exceed 1000 and is never re-fetched.
            if (received >= maxChangesPerInvocation) break
        }
        database.withTransaction {
            val now = System.currentTimeMillis()
            database.unifiedSyncDao().requestInboxApply(scope.scopeId, scope.organizationId,
                now + DurableInboxPolicy.CONTINUATION_DELAY_MILLIS, now)
        }
        return UnifiedSyncPullResult(UnifiedSyncPullOutcome.MORE_AVAILABLE, pages, received, drained.pendingGroups)
    }

    private fun outcome(result: DurableInboxApplyCoordinator.DrainResult) = when {
        result.storageWait -> UnifiedSyncPullOutcome.WAITING_STORAGE_OR_REVIEW
        result.reviewGroups > 0L -> UnifiedSyncPullOutcome.REQUIRES_REVIEW
        result.pendingGroups > 0L -> UnifiedSyncPullOutcome.WAITING_LOCAL_OR_DEPENDENCY
        else -> UnifiedSyncPullOutcome.CAUGHT_UP
    }

    private suspend fun resolveTrustedScope(expectedOrganizationId: String): SyncScope {
        val scope = try {
            remote.resolveScope()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            throw classifyRemoteFailure(t)
        }
        UnifiedSyncContractRules.requireValidScope(scope)
        if (scope.organizationId != expectedOrganizationId) {
            throw UnifiedSyncPullFailure("SCOPE_MISMATCH", "resolved tenant differs from caller session")
        }
        return scope
    }

    private suspend fun locateActiveCursor(scope: SyncScope): SyncCursorEntity? {
        val dao = database.unifiedSyncDao()
        val current = dao.getCursor(scope.scopeId)
        if (current != null) {
            validateCursorIdentity(current, scope)
            return current
        }

        val prior = dao.getActiveCursorForPrincipal(
            scope.organizationId, scope.syncPrincipalId, scope.contractFamily, scope.contractVersion,
        )
        if (prior != null) {
            database.withTransaction {
                check(
                    dao.invalidateCursor(
                        prior.scopeId,
                        prior.organizationId,
                        prior.syncPrincipalId,
                        prior.contractFamily,
                        prior.contractVersion,
                        prior.scopeDefinitionVersion,
                        System.currentTimeMillis(),
                    ) == 1
                ) { "CURSOR_STALE: prior scope cursor could not be invalidated" }
            }
            return null
        }
        return null
    }

    private fun validateCursorIdentity(cursor: SyncCursorEntity, scope: SyncScope) {
        if (
            cursor.organizationId != scope.organizationId ||
            cursor.syncPrincipalId != scope.syncPrincipalId ||
            cursor.contractFamily != scope.contractFamily ||
            cursor.contractVersion != scope.contractVersion ||
            cursor.scopeDefinitionVersion != scope.scopeDefinitionVersion ||
            cursor.scopeId != scope.scopeId
        ) {
            throw UnifiedSyncPullFailure("SCOPE_MISMATCH", "local cursor identity differs from resolved server scope")
        }
        if (cursor.cursorToken.isBlank()) throw UnifiedSyncPullFailure("CURSOR_STALE", "opaque Room cursor is blank")
    }

    private suspend fun markBootstrapRequired(scope: SyncScope) {
        val dao = database.unifiedSyncDao()
        database.withTransaction {
            dao.markBootstrapRequired(
                scope.scopeId,
                scope.organizationId,
                scope.syncPrincipalId,
                scope.contractFamily,
                scope.contractVersion,
                scope.scopeDefinitionVersion,
                System.currentTimeMillis(),
            )
        }
    }

    private suspend fun persistRecoveryObligation(scope: SyncScope, reason: String, state: String) {
        val dao = database.syncRecoveryDao()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val old = dao.getRecoveryState(scope.scopeId)
            if (old != null && old.state == "IN_PROGRESS" && state == STATE_NOT_STARTED) return@withTransaction
            dao.upsertRecoveryState(
                old?.copy(state = state, reason = reason, lastErrorCode = null, updatedAt = now)
                    ?: SyncRecoveryStateEntity(
                        scopeId = scope.scopeId, organizationId = scope.organizationId, syncPrincipalId = scope.syncPrincipalId,
                        contractFamily = scope.contractFamily, contractVersion = scope.contractVersion,
                        scopeDefinitionVersion = scope.scopeDefinitionVersion, state = state, reason = reason,
                        bootstrapSessionId = null, baselineCursor = null, baselineRevision = null, nextPageToken = null,
                        expectedSnapshotRows = null, stagedSnapshotRows = 0L, recoveryGeneration = 0L, attemptCount = 0,
                        lastErrorCode = null, startedAt = null, updatedAt = now, completedAt = null,
                    )
            )
        }
    }

    private fun isCursorExpired(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.any { it.message?.contains("CURSOR_EXPIRED", ignoreCase = true) == true }

    private fun classifyRemoteFailure(t: Throwable): UnifiedSyncPullFailure {
        if (t is UnifiedSyncPullFailure) return t
        if (t is SyncContractViolation) return UnifiedSyncPullFailure(t.code, "remote contract violation", t)
        val message = t.message.orEmpty()
        val code = when {
            message.contains("401") || message.contains("403") || message.contains("auth", true) -> "AUTH"
            message.contains("CONTRACT_UNSUPPORTED") || message.contains("PGRST202") ||
                (message.contains("verto_pull_sync_changes_v2") && message.contains("does not exist", true)) -> "CONTRACT_UNSUPPORTED"
            message.contains("SCOPE_MISMATCH") -> "SCOPE_MISMATCH"
            message.contains("PAYLOAD_TOO_LARGE") -> "PAYLOAD_TOO_LARGE"
            else -> "TRANSIENT_NETWORK"
        }
        return UnifiedSyncPullFailure(code, "pull remote failure (${t::class.java.simpleName})", t)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 1_000
        const val MAX_PAGE_SIZE = 1_000
        const val DEFAULT_MAX_PAGES = 5
        const val DEFAULT_MAX_CHANGES = 1_000
        private const val CURSOR_ACTIVE = "ACTIVE"
        private const val STATE_NOT_STARTED = "NOT_STARTED"
        private const val STATE_RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    }
}
