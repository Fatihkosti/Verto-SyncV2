package com.verto.app.data.sync.push

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.PartySyncConflictEntity
import com.verto.app.data.local.entity.PartySyncOutboxEntity
import com.verto.app.data.sync.SyncMutation
import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.SyncReceiptStatus
import com.verto.app.data.sync.UnifiedSyncPushRemote
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException

/**
 * Session 309 bridge for the one owner-307 aggregate that intentionally stays on party_sync_outbox.
 * It has at-least-once delivery without a local lease; server mutation idempotency is the duplicate guard.
 */
@Singleton
class UnifiedSyncPartyPushBridge @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncPushRemote,
) {
    private val json = Json { explicitNulls = true }

    suspend fun pushAvailable(
        organizationId: String,
        maxMutationsPerInvocation: Int = 50,
    ): UnifiedSyncPartyPushResult {
        require(organizationId.isNotBlank()) { "SCOPE_MISMATCH: organizationId required" }
        require(maxMutationsPerInvocation in 1..500)
        val rows = database.partyRoleDao().getPendingPartyRoleOperations(
            System.currentTimeMillis(),
            maxMutationsPerInvocation,
        )
        var acknowledged = 0
        var retried = 0
        var review = 0
        var rejected = 0
        for (row in rows) {
            val mutation = toMutation(row, organizationId)
            val response = try {
                remote.apply(mutation)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = generateSequence(t) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
                if (message.contains("401") || message.contains("403") || message.contains("AUTH", true)) {
                    database.partyRoleDao().markPartyRoleRetry(row.operationId, "AUTHENTICATION", System.currentTimeMillis() + 60_000L)
                    throw UnifiedSyncPushFailure("AUTHENTICATION", "party push authentication blocked; intent preserved", t)
                }
                val updated = database.partyRoleDao().markPartyRoleRetry(
                    row.operationId,
                    "TRANSIENT_NETWORK",
                    System.currentTimeMillis() + 5_000L,
                )
                if (updated == 1) retried += 1
                continue
            }
            when (response.receipt.status) {
                SyncReceiptStatus.APPLIED,
                SyncReceiptStatus.REPLAYED,
                SyncReceiptStatus.NO_OP -> {
                    if (database.partyRoleDao().markPartyRoleTerminal(row.operationId, "ACKNOWLEDGED") == 1) acknowledged += 1
                }
                SyncReceiptStatus.RETRYABLE -> {
                    if (database.partyRoleDao().markPartyRoleRetry(
                            row.operationId,
                            "SERVER_RETRYABLE",
                            response.receipt.retryAfterEpochMillis ?: (System.currentTimeMillis() + 5_000L),
                        ) == 1
                    ) retried += 1
                }
                SyncReceiptStatus.CONFLICT -> {
                    val payload = requireNotNull(response.receipt.authoritativePayload) {
                        "SERVER_PROTOCOL_INCONSISTENCY: Party conflict missing authoritative payload"
                    }
                    database.partyRoleDao().insertSyncConflict(
                        PartySyncConflictEntity(
                            id = conflictId(row, response.receipt.serverVersion ?: 0L, response.receipt.conflictCode.orEmpty()),
                            aggregateType = "ROLE",
                            aggregateId = row.aggregateId,
                            localPayload = row.payloadJson,
                            remotePayload = payload.toString(),
                            baseRevision = row.baseRevision,
                            serverRevision = response.receipt.serverVersion ?: 0L,
                            reason = response.receipt.conflictCode ?: "CONFLICT",
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    if (database.partyRoleDao().markPartyRoleTerminal(row.operationId, "REQUIRES_REVIEW") == 1) review += 1
                }
                SyncReceiptStatus.REJECTED -> {
                    if (database.partyRoleDao().markPartyRoleTerminal(row.operationId, "REJECTED") == 1) rejected += 1
                }
            }
        }
        val nowAfter = System.currentTimeMillis()
        return UnifiedSyncPartyPushResult(
            attempted = rows.size, acknowledged = acknowledged, retried = retried, requiresReview = review, rejected = rejected,
            backlog = database.partyRoleDao().countPartyRoleBacklog(),
            immediateMore = rows.size >= maxMutationsPerInvocation,
            nextEligibleAt = database.partyRoleDao().nextPartyRoleRetryAt(nowAfter),
        )
    }

    private suspend fun toMutation(row: PartySyncOutboxEntity, organizationId: String): SyncMutation {
        require(row.aggregateType == "ROLE") { "DEFERRED_STRONGER_AGGREGATE: only ROLE belongs to the v309 Party bridge" }
        val payload = runCatching { json.parseToJsonElement(row.payloadJson) as JsonObject }
            .getOrElse { throw UnifiedSyncPushFailure("VALIDATION", "Party payload must be JSON object", it) }
        val role = payload["role"]?.jsonPrimitive?.content.orEmpty()
        require(role.isNotBlank()) { "VALIDATION: Party ROLE payload is missing role" }
        // party_sync_outbox predates tenant column; verify the referenced normalized role belongs to the trusted org.
        require(database.partyRoleDao().getRole(row.aggregateId, organizationId, role) != null) {
            "SCOPE_MISMATCH: Party operation cannot be proven to belong to trusted organization"
        }
        val localDiagnosticSequence = row.createdAt.coerceAtLeast(1L)
        return SyncMutation(
            mutationId = row.operationId,
            organizationId = organizationId,
            aggregateType = "PARTY_ROLE",
            aggregateId = "${row.aggregateId}:$role",
            operationType = SyncMutationOperation.UPSERT,
            baseVersion = row.baseRevision,
            localSequence = localDiagnosticSequence,
            aggregateSequence = localDiagnosticSequence,
            payloadVersion = row.payloadVersion,
            payload = payload,
            createdAtEpochMillis = row.createdAt,
        )
    }

    private fun conflictId(row: PartySyncOutboxEntity, serverVersion: Long, code: String): String {
        val bytes = "verto-party-role-conflict-v309\u0000${row.operationId}\u0000$serverVersion\u0000$code"
            .toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

data class UnifiedSyncPartyPushResult(
    val attempted: Int,
    val acknowledged: Int,
    val retried: Int,
    val requiresReview: Int,
    val rejected: Int,
    val backlog: Long,
    val immediateMore: Boolean,
    val nextEligibleAt: Long?,
)
