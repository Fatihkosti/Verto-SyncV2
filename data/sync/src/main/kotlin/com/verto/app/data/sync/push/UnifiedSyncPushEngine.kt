package com.verto.app.data.sync.push

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.sync.SyncMutation
import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.SyncReceiptStatus
import com.verto.app.data.sync.FrozenAckOutcome
import com.verto.app.data.sync.FrozenMutationStore
import com.verto.app.data.sync.UnifiedSyncPushRemote
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Session 309 bounded at-least-once sender. Scheduling remains owned by Session 311. */
@Singleton
class UnifiedSyncPushEngine @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncPushRemote,
    private val registry: UnifiedSyncPushRegistry,
    private val conflictEngine: UnifiedSyncConflictEngine,
    private val frozenMutationStore: FrozenMutationStore,
) {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    suspend fun pushAvailable(
        organizationId: String,
        scopeEpoch: Long,
        maxMutationsPerInvocation: Int = DEFAULT_MAX_MUTATIONS,
    ): UnifiedSyncPushRunResult {
        require(organizationId.isNotBlank()) { "SCOPE_MISMATCH: organizationId must be trusted and nonblank" }
        require(scopeEpoch > 0L) { "SCOPE_MISMATCH: scopeEpoch must be positive" }
        require(maxMutationsPerInvocation in 1..MAX_MUTATIONS) { "VALIDATION: max mutations must be 1..$MAX_MUTATIONS" }

        val now = System.currentTimeMillis()
        database.unifiedSyncDao().recoverExpiredLeases(organizationId, now)
        val candidates = database.unifiedSyncDao().listEligibleOutbox(
            organizationId = organizationId,
            now = now,
            limit = minOf(MAX_SCAN, maxMutationsPerInvocation * SCAN_MULTIPLIER),
        )

        var sent = 0
        var acknowledged = 0
        var retried = 0
        var review = 0
        var rejected = 0
        var stale = 0
        var blocked = 0

        for (row in candidates) {
            if (sent >= maxMutationsPerInvocation) break
            val eligibility = eligibility(row)
            if (eligibility != null) {
                if (eligibility in TERMINAL_DEPENDENCY_CODES) {
                    val changed = database.unifiedSyncDao().markDependencyRequiresReview(
                        row.mutationId, row.semanticFingerprint, eligibility,
                    )
                    if (changed == 1) review += 1 else stale += 1
                } else {
                    blocked += 1
                }
                continue
            }

            try {
                UnifiedFinancialOwner310Route.prepare(row, registry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val code = (failure as? UnifiedSyncPushFailure)?.code ?: "LOCAL_MATERIALIZATION_FAILED"
                val changed = database.unifiedSyncDao().markUnleasedTerminal(
                    mutationId = row.mutationId,
                    expectedSemanticFingerprint = row.semanticFingerprint,
                    terminalState = "REQUIRES_REVIEW",
                    errorType = "LOCAL_CONTRACT",
                    errorCode = code,
                )
                if (changed == 1) review += 1 else stale += 1
                continue
            }

            val frozen = try {
                frozenMutationStore.prepareOnce(organizationId, row.mutationId, System.currentTimeMillis())
            } catch (failure: IllegalStateException) {
                if (failure.message == "PREDECESSOR_NOT_ACKNOWLEDGED") {
                    blocked += 1
                    continue
                }
                throw failure
            }

            val leaseToken = UUID.randomUUID().toString()
            val leased = database.unifiedSyncDao().tryLease(
                mutationId = row.mutationId,
                expectedState = row.state,
                leaseOwner = LEASE_OWNER,
                leaseToken = leaseToken,
                scopeEpoch = scopeEpoch,
                leaseExpiresAt = System.currentTimeMillis() + LEASE_MILLIS,
            )
            if (leased != 1) continue
            sent += 1

            
            val response = try {
                frozenMutationStore.recordDispatch(frozen, organizationId, System.currentTimeMillis())
                applyWithLeaseRenewal(row.mutationId, leaseToken, scopeEpoch, frozen.wireJson, frozen.wireSha256)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val code = classifyRemoteFailure(t)
                when (code) {
                    "AUTHENTICATION" -> {
                        database.unifiedSyncDao().markRetry(
                            row.mutationId,
                            leaseToken,
                            scopeEpoch,
                            "AUTHENTICATION",
                            code,
                            System.currentTimeMillis() + AUTH_RECHECK_MILLIS,
                        )
                        throw UnifiedSyncPushFailure("AUTHENTICATION", "transport auth blocked; immutable intent preserved", t)
                    }
                    "RATE_LIMITED", "TRANSIENT_NETWORK" -> {
                        val changed = database.unifiedSyncDao().markRetry(
                            row.mutationId,
                            leaseToken,
                            scopeEpoch,
                            code,
                            code,
                            System.currentTimeMillis() + retryDelayMillis(row.attemptCount + 1),
                        )
                        if (changed == 1) retried += 1 else stale += 1
                    }
                    else -> {
                        val terminal = database.unifiedSyncDao().markTerminal(
                            row.mutationId, leaseToken, scopeEpoch, row.semanticFingerprint, "REJECTED", null, null, "REJECTED", System.currentTimeMillis(),
                        )
                        if (terminal == 1) rejected += 1 else stale += 1
                    }
                }
                continue
            }

            val receipt = response.receipt
            if (receipt.mutationId != row.mutationId || receipt.aggregateId != row.aggregateId) {
                val terminal = database.unifiedSyncDao().markTerminal(
                    row.mutationId, leaseToken, scopeEpoch, row.semanticFingerprint, "REQUIRES_REVIEW", receipt.serverRevision,
                    receipt.serverVersion, "REJECTED", null,
                )
                if (terminal == 1) review += 1 else stale += 1
                continue
            }
            // B11 must durably expose CONFLICT with an unproven/missing request hash as OUTCOME_UNKNOWN;
            // do not short-circuit it into a generic invisible review row.
            if (receipt.status != SyncReceiptStatus.RETRYABLE &&
                receipt.status != SyncReceiptStatus.CONFLICT &&
                receipt.requestHash.isNullOrBlank()) {
                val terminal = database.unifiedSyncDao().markTerminal(
                    row.mutationId, leaseToken, scopeEpoch, row.semanticFingerprint, "REQUIRES_REVIEW", receipt.serverRevision,
                    receipt.serverVersion, "REJECTED", null,
                )
                if (terminal == 1) review += 1 else stale += 1
                continue
            }

            when (receipt.status) {
                SyncReceiptStatus.APPLIED,
                SyncReceiptStatus.REPLAYED,
                SyncReceiptStatus.NO_OP -> {
                    when (frozenMutationStore.acknowledgeUnified(
                        organizationId = organizationId,
                        mutationId = row.mutationId,
                        leaseToken = leaseToken,
                        scopeEpoch = scopeEpoch,
                        expectedSemanticFingerprint = row.semanticFingerprint,
                        receiptMutationId = receipt.mutationId,
                        receiptRequestHash = receipt.requestHash,
                        serverRevision = receipt.serverRevision,
                        serverVersion = receipt.serverVersion,
                        receiptStatus = receipt.status.name,
                        acknowledgedAt = System.currentTimeMillis(),
                    )) {
                        FrozenAckOutcome.ACKNOWLEDGED_CURRENT,
                        FrozenAckOutcome.ACKNOWLEDGED_LOCAL_CHANGED -> acknowledged += 1
                        FrozenAckOutcome.STALE_LEASE,
                        FrozenAckOutcome.RECEIPT_MISMATCH -> stale += 1
                    }
                }

                SyncReceiptStatus.RETRYABLE -> {
                    val changed = database.unifiedSyncDao().markRetry(
                        row.mutationId,
                        leaseToken,
                        scopeEpoch,
                        "TRANSIENT_NETWORK",
                        "SERVER_RETRYABLE",
                        receipt.retryAfterEpochMillis ?: (System.currentTimeMillis() + retryDelayMillis(row.attemptCount + 1)),
                    )
                    if (changed == 1) retried += 1 else stale += 1
                }

                SyncReceiptStatus.REJECTED -> {
                    val terminal = database.unifiedSyncDao().markTerminal(
                        row.mutationId,
                        leaseToken,
                        scopeEpoch,
                        row.semanticFingerprint,
                        "REJECTED",
                        receipt.serverRevision,
                        receipt.serverVersion,
                        "REJECTED",
                        System.currentTimeMillis(),
                    )
                    if (terminal == 1) rejected += 1 else stale += 1
                }

                SyncReceiptStatus.CONFLICT -> {
                    when (conflictEngine.reconcile(row, leaseToken, scopeEpoch, response, System.currentTimeMillis())) {
                        UnifiedSyncConflictOutcome.REQUIRES_REVIEW,
                        UnifiedSyncConflictOutcome.DOMAIN_CORRECTION_REQUIRED -> review += 1
                        UnifiedSyncConflictOutcome.STALE_LEASE_RESULT -> stale += 1
                    }
                }
            }
        }

        val backlog = database.unifiedSyncDao().countBacklog(organizationId)
        val eligibleNow = database.unifiedSyncDao().countRunnableOutboxNow(organizationId, System.currentTimeMillis())
        val nextEligibleAt = database.unifiedSyncDao().nextEligibleRetryAt(organizationId, System.currentTimeMillis())
        return UnifiedSyncPushRunResult(
            outcome = if (eligibleNow > 0) UnifiedSyncPushOutcome.MORE_AVAILABLE else UnifiedSyncPushOutcome.CAUGHT_UP,
            sent = sent,
            acknowledged = acknowledged,
            retried = retried,
            requiresReview = review,
            rejected = rejected,
            staleLeaseResults = stale,
            blocked = blocked,
            backlog = backlog,
            eligibleNow = eligibleNow,
            nextEligibleAt = nextEligibleAt,
        )
    }

    private suspend fun eligibility(row: SyncOutboxEntity): String? {
        val dao = database.unifiedSyncDao()
        row.dependsOnMutationId?.let { dependencyId ->
            val dependency = dao.readDependency(dependencyId)
                ?: return "DEPENDENCY_MISSING"
            if (dependency.organizationId != row.organizationId) {
                throw UnifiedSyncPushFailure("SCOPE_MISMATCH", "cross-tenant mutation dependency")
            }
            when (dependency.state) {
                "ACKNOWLEDGED" -> Unit
                "PENDING", "LEASED", "RETRY" -> return "DEPENDENCY_NOT_ACKNOWLEDGED"
                "REQUIRES_REVIEW", "REJECTED" -> return "DEPENDENCY_FAILED"
                else -> return "DEPENDENCY_FAILED"
            }
        }
        val replacementParentSequence = dao.replacementParentSequence(row.organizationId, row.mutationId)
        if (replacementParentSequence == null && dao.countOlderSupersededPendingProof(
                row.organizationId, row.aggregateType, row.aggregateId, row.aggregateSequence,
            ) > 0
        ) return "SUPERSEDED_PARENT_AWAITING_REPLACEMENT_PROOF"
        val orderingBarrier = replacementParentSequence ?: row.aggregateSequence
        if (dao.countOlderTerminalBlockers(
                row.organizationId,
                row.aggregateType,
                row.aggregateId,
                orderingBarrier,
            ) > 0
        ) return "PREDECESSOR_REQUIRES_REVIEW"
        if (dao.countOlderActiveMutations(
                row.organizationId,
                row.aggregateType,
                row.aggregateId,
                orderingBarrier,
            ) > 0
        ) return "AGGREGATE_SEQUENCE_BLOCKED"
        return null
    }

    private fun SyncOutboxEntity.toMutation(operation: SyncMutationOperation): SyncMutation {
        val payload = runCatching { json.parseToJsonElement(payloadJson) as JsonObject }
            .getOrElse { throw UnifiedSyncPushFailure("VALIDATION", "payload_json must be a JSON object", it) }
        return SyncMutation(
            mutationId = mutationId,
            organizationId = organizationId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            operationType = operation,
            baseVersion = baseVersion,
            localSequence = localSequence,
            aggregateSequence = aggregateSequence,
            payloadVersion = payloadVersion,
            payload = payload,
            createdAtEpochMillis = createdAt,
            commandBatchId = commandBatchId,
            commandOrder = commandOrder,
            dependsOnMutationId = dependsOnMutationId,
        )
    }

    private fun parseOperation(raw: String): SyncMutationOperation =
        runCatching { SyncMutationOperation.valueOf(raw) }
            .getOrElse { throw UnifiedSyncPushFailure("CONTRACT_UNSUPPORTED", "unknown operation=$raw") }

    private fun classifyRemoteFailure(t: Throwable): String {
        val message = generateSequence(t) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
        return when {
            message.contains("401") || message.contains("403") || message.contains("AUTH", true) -> "AUTHENTICATION"
            message.contains("429") || message.contains("too many requests", true) -> "RATE_LIMITED"
            message.contains("SCOPE_MISMATCH", true) -> "SCOPE_MISMATCH"
            message.contains("CONTRACT_UNSUPPORTED", true) -> "CONTRACT_UNSUPPORTED"
            message.contains("VALIDATION", true) -> "VALIDATION"
            message.contains("IDEMPOTENCY_CONFLICT", true) -> "IDEMPOTENCY_CONFLICT"
            message.contains("SERVER_PROTOCOL_INCONSISTENCY", true) -> "SERVER_PROTOCOL_INCONSISTENCY"
            else -> "TRANSIENT_NETWORK"
        }
    }

    private suspend fun applyWithLeaseRenewal(
        mutationId: String,
        leaseToken: String,
        scopeEpoch: Long,
        wireJson: String,
        wireSha256: String,
    ): com.verto.app.data.sync.UnifiedSyncPushResponse = coroutineScope {
        val response = async { remote.applyFrozen(wireJson, wireSha256, leaseToken) }
        val renewal = launch {
            while (isActive) {
                delay(LEASE_RENEWAL_MILLIS)
                val renewed = database.unifiedSyncDao().renewLease(
                    mutationId, leaseToken, scopeEpoch, System.currentTimeMillis() + LEASE_MILLIS,
                )
                if (renewed != 1) return@launch
            }
        }
        try {
            response.await()
        } finally {
            renewal.cancelAndJoin()
        }
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 8)
        return (BASE_RETRY_MILLIS * (1L shl shift)).coerceAtMost(MAX_RETRY_MILLIS)
    }

    companion object {
        const val DEFAULT_MAX_MUTATIONS = 50
        const val MAX_MUTATIONS = 500
        private const val MAX_SCAN = 500
        private const val SCAN_MULTIPLIER = 4
        internal const val LEASE_MILLIS = 120_000L
        internal const val LEASE_RENEWAL_MILLIS = 30_000L
        private const val LEASE_OWNER = "unified-sync-push-v309"
        private const val BASE_RETRY_MILLIS = 1_000L
        private const val MAX_RETRY_MILLIS = 300_000L
        private const val AUTH_RECHECK_MILLIS = 60_000L
        private val TERMINAL_DEPENDENCY_CODES = setOf(
            "DEPENDENCY_MISSING", "DEPENDENCY_FAILED", "PREDECESSOR_REQUIRES_REVIEW",
        )
    }
}

enum class UnifiedSyncPushOutcome { CAUGHT_UP, MORE_AVAILABLE }

data class UnifiedSyncPushRunResult(
    val outcome: UnifiedSyncPushOutcome,
    val sent: Int,
    val acknowledged: Int,
    val retried: Int,
    val requiresReview: Int,
    val rejected: Int,
    val staleLeaseResults: Int,
    val blocked: Int,
    val backlog: Long,
    val eligibleNow: Long,
    val nextEligibleAt: Long?,
)
