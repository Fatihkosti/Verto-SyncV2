package com.verto.app.data.sync.push

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncConflictEntity
import com.verto.app.data.local.entity.SyncConflictReviewEvidenceEntity
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.sync.SyncReceiptStatus
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncConflictPolicy
import com.verto.app.data.sync.UnifiedSyncPushResponse
import com.verto.app.data.sync.sha256Utf8
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** B11: conflicts are captured for explicit review. This engine never chooses a business winner. */
@Singleton
class UnifiedSyncConflictEngine @Inject constructor(
    private val database: AppDatabase,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    suspend fun reconcile(
        outbox: SyncOutboxEntity,
        leaseToken: String,
        scopeEpoch: Long,
        response: UnifiedSyncPushResponse,
        now: Long,
    ): UnifiedSyncConflictOutcome {
        val receipt = response.receipt
        require(receipt.status == SyncReceiptStatus.CONFLICT) { "CONFLICT engine requires CONFLICT receipt" }
        require(receipt.mutationId == outbox.mutationId && receipt.aggregateId == outbox.aggregateId) {
            "SERVER_PROTOCOL_INCONSISTENCY: conflict receipt identity mismatch"
        }
        val serverVersion = requireNotNull(receipt.serverVersion) { "SERVER_PROTOCOL_INCONSISTENCY: missing serverVersion" }
        require(serverVersion > 0) { "SERVER_PROTOCOL_INCONSISTENCY: non-positive serverVersion" }
        val authoritative = requireNotNull(receipt.authoritativePayload) {
            "SERVER_PROTOCOL_INCONSISTENCY: missing authoritativePayload"
        }
        val conflictCode = receipt.conflictCode?.takeIf { it.isNotBlank() }
            ?: throw UnifiedSyncPushFailure("SERVER_PROTOCOL_INCONSISTENCY", "missing conflictCode")
        val requirement = response.resolutionRequirement
            ?: throw UnifiedSyncPushFailure("FAIL_CONFLICT_REQUIREMENT_UNKNOWN", "missing resolution requirement")
        val authoritativeJson = canonical(authoritative)
        val capability = UnifiedSyncAggregateRegistry.requireById(outbox.aggregateType).conflictPolicy
        val reviewState = if (capability == UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION) {
            "OPEN"
        } else {
            "DOMAIN_CORRECTION_REQUIRED"
        }
        val conflictId = deterministicConflictId(outbox.organizationId, outbox.mutationId, serverVersion, conflictCode)

        return database.withTransaction {
            val dao = database.unifiedSyncDao()
            val live = dao.getOutbox(outbox.mutationId)
            if (live == null || live.state != "LEASED" || live.leaseToken != leaseToken ||
                live.leaseScopeEpoch != scopeEpoch || live.semanticFingerprint != outbox.semanticFingerprint) {
                return@withTransaction UnifiedSyncConflictOutcome.STALE_LEASE_RESULT
            }
            require(live.organizationId == outbox.organizationId &&
                live.aggregateType == outbox.aggregateType && live.aggregateId == outbox.aggregateId) {
                "SERVER_PROTOCOL_INCONSISTENCY: live outbox identity drift"
            }

            val packet = dao.readMutationPacket(outbox.organizationId, outbox.mutationId)
            val proof = if (
                receipt.serverRevision != null && receipt.requestHash != null &&
                packet?.wireSha256 != null && receipt.requestHash == packet.wireSha256
            ) "PROVEN_CONFLICT" else "OUTCOME_UNKNOWN"

            val conflict = SyncConflictEntity(
                conflictId = conflictId,
                mutationId = outbox.mutationId,
                organizationId = outbox.organizationId,
                aggregateType = outbox.aggregateType,
                aggregateId = outbox.aggregateId,
                conflictCode = conflictCode,
                localPayloadVersion = outbox.payloadVersion,
                serverVersion = serverVersion,
                authoritativePayloadJson = authoritativeJson,
                resolutionRequirement = requirement.name,
                state = reviewState,
                requestHash = receipt.requestHash,
                resolutionMutationId = null,
                createdAt = now,
                resolvedAt = null,
            )
            dao.insertConflictChecked(conflict)
            dao.insertConflictEvidenceChecked(
                SyncConflictReviewEvidenceEntity(
                    conflictId = conflictId,
                    organizationId = outbox.organizationId,
                    mutationId = outbox.mutationId,
                    localPayloadJson = outbox.payloadJson,
                    localPayloadSha256 = sha256Utf8(outbox.payloadJson),
                    localSemanticFingerprint = outbox.semanticFingerprint,
                    localBaseVersion = outbox.baseVersion,
                    remotePayloadJson = authoritativeJson,
                    remotePayloadSha256 = sha256Utf8(authoritativeJson),
                    serverRevision = receipt.serverRevision,
                    serverVersion = serverVersion,
                    outcomeProof = proof,
                    createdAt = now,
                )
            )
            val terminal = dao.markTerminal(
                mutationId = outbox.mutationId,
                leaseToken = leaseToken,
                scopeEpoch = scopeEpoch,
                expectedSemanticFingerprint = outbox.semanticFingerprint,
                terminalState = "REQUIRES_REVIEW",
                serverRevision = receipt.serverRevision,
                serverVersion = serverVersion,
                receiptStatus = "CONFLICT",
                ackedAt = null,
            )
            if (terminal != 1) return@withTransaction UnifiedSyncConflictOutcome.STALE_LEASE_RESULT
            if (reviewState == "DOMAIN_CORRECTION_REQUIRED") {
                UnifiedSyncConflictOutcome.DOMAIN_CORRECTION_REQUIRED
            } else {
                UnifiedSyncConflictOutcome.REQUIRES_REVIEW
            }
        }
    }

    fun deterministicConflictId(
        organizationId: String,
        mutationId: String,
        serverVersion: Long,
        conflictCode: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val material = "verto-sync-conflict-v1\u0000$organizationId\u0000$mutationId\u0000$serverVersion\u0000$conflictCode"
        return digest.digest(material.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun canonical(payload: JsonObject): String = json.encodeToString(JsonObject.serializer(), payload)
}

enum class UnifiedSyncConflictOutcome {
    REQUIRES_REVIEW,
    DOMAIN_CORRECTION_REQUIRED,
    STALE_LEASE_RESULT,
}
