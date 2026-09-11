package com.verto.app.data.sync.conflict

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.UnifiedSyncMutationDraft
import com.verto.app.data.local.entity.SyncConflictResolutionAuditEntity
import com.verto.app.data.local.entity.SyncConflictReviewEvidenceEntity
import com.verto.app.data.sync.FrozenMutationStore
import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.SyncMutation
import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncConflictPolicy
import com.verto.app.data.sync.UnifiedSyncContractRules
import com.verto.app.data.sync.ownership.PendingSourceRef
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.data.sync.ownership.SyncSourceOwner
import com.verto.app.data.sync.pull.UnifiedSyncChangeApplier
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Identity written to the append-only conflict decision audit after authorization succeeds. */
data class SyncConflictDecisionActor(val userId: String, val role: String)

/** App/session layer supplies authorization; the data layer never assumes admin rights. */
fun interface SyncConflictDecisionAuthorizer {
    suspend fun requireAuthorized(organizationId: String): SyncConflictDecisionActor
}

data class SyncConflictFieldDiff(val path: String, val local: String, val remote: String)

data class SyncConflictReviewRecord(
    val conflictId: String,
    val aggregateType: String,
    val aggregateId: String,
    val reason: String,
    val state: String,
    val localBaseVersion: Long?,
    val localPayloadVersion: Int,
    val remoteVersion: Long,
    val localFingerprint: String,
    val remoteFingerprint: String,
    val localPreviewJson: String,
    val remotePreviewJson: String,
    val differences: List<SyncConflictFieldDiff>,
    val mutable: Boolean,
    val outcomeProven: Boolean,
    val resolutionMutationId: String?,
)

data class SyncConflictResolutionResult(val conflictId: String, val replacementMutationId: String? = null)

class SyncConflictResolutionException(val code: String, message: String) : IllegalStateException("$code: $message")

@Singleton
class SyncConflictReviewService @Inject constructor(
    private val database: AppDatabase,
    private val frozenMutationStore: FrozenMutationStore,
    private val pendingProtection: SyncPendingProtection,
    private val applier: UnifiedSyncChangeApplier,
    private val authorizer: SyncConflictDecisionAuthorizer,
) {
    suspend fun listForOrganization(organizationId: String, limit: Int = 100): List<SyncConflictReviewRecord> {
        require(organizationId.isNotBlank())
        return database.unifiedSyncDao().listOpenConflicts(organizationId, limit).map { conflict ->
            val evidence = requireEvidence(conflict)
            check(evidence.organizationId == organizationId && evidence.mutationId == conflict.mutationId) {
                "SCOPE_MISMATCH"
            }
            val local = SyncConflictRedactor.redact(evidence.localPayloadJson)
            val remote = SyncConflictRedactor.redact(evidence.remotePayloadJson)
            SyncConflictReviewRecord(
                conflictId = conflict.conflictId,
                aggregateType = conflict.aggregateType,
                aggregateId = conflict.aggregateId,
                reason = conflict.conflictCode,
                state = conflict.state,
                localBaseVersion = evidence.localBaseVersion,
                localPayloadVersion = conflict.localPayloadVersion,
                remoteVersion = conflict.serverVersion,
                localFingerprint = evidence.localPayloadSha256,
                remoteFingerprint = evidence.remotePayloadSha256,
                localPreviewJson = SyncConflictRedactor.preview(local),
                remotePreviewJson = SyncConflictRedactor.preview(remote),
                differences = SyncConflictRedactor.diff(local, remote),
                mutable = SyncConflictResolutionPolicy.isGenericMutable(conflict.aggregateType),
                outcomeProven = evidence.outcomeProof == "PROVEN_CONFLICT",
                resolutionMutationId = conflict.resolutionMutationId,
            )
        }
    }

    /** B11.02: explicit server choice; only for generic versioned mutable aggregates. */
    suspend fun acceptServer(conflictId: String, expectedServerVersion: Long): SyncConflictResolutionResult {
        val initial = loadConflict(conflictId)
        val actor = authorizer.requireAuthorized(initial.first.organizationId)
        return database.withTransaction {
            val (conflict, evidence) = loadConflict(conflictId)
            val dao = database.unifiedSyncDao()
            SyncConflictResolutionPolicy.requireDecisionAllowed(
                aggregateType = conflict.aggregateType,
                state = conflict.state,
                outcomeProof = evidence.outcomeProof,
                evidenceServerVersion = evidence.serverVersion,
                displayedServerVersion = expectedServerVersion,
                latestKnownServerVersion = dao.latestKnownServerVersion(
                    conflict.organizationId, conflict.aggregateType, conflict.aggregateId,
                ),
            )
            val old = dao.getOutbox(conflict.mutationId)
                ?: throw SyncConflictResolutionException("CONFLICT_LOCAL_INTENT_MISSING", conflict.mutationId)
            check(old.organizationId == conflict.organizationId && old.state == "REQUIRES_REVIEW") {
                "CONFLICT_STATE_CHANGED"
            }
            val serverRevision = evidence.serverRevision
                ?: throw SyncConflictResolutionException("OUTCOME_UNKNOWN", "missing authoritative revision")
            val remote = json.parseToJsonElement(evidence.remotePayloadJson) as? JsonObject
                ?: throw SyncConflictResolutionException("CONFLICT_REMOTE_PAYLOAD_INVALID", conflictId)

            // C§8.7: resolving an older intent must not overwrite a newer local edit. In that case
            // only the old intent is superseded; the later intent/reference keeps current local state dirty.
            val newerLocalIntent = dao.countChangedProtectedKeys(
                conflict.organizationId, SyncSourceOwner.UNIFIED.tableName, old.mutationId,
            ) > 0 || dao.countLaterUnresolvedProtectedMutations(
                conflict.organizationId, SyncSourceOwner.UNIFIED.tableName, old.mutationId,
            ) > 0
            if (!newerLocalIntent) {
                // Accepting the extant server version materializes that exact version, never merged JSON.
                applier.apply(
                    SyncChange(
                        revision = serverRevision,
                        organizationId = conflict.organizationId,
                        syncScopeId = "conflict-review",
                        aggregateType = conflict.aggregateType,
                        aggregateId = conflict.aggregateId,
                        operationType = SyncMutationOperation.UPSERT,
                        entityVersion = expectedServerVersion,
                        payloadVersion = old.payloadVersion,
                        payload = remote,
                        originMutationId = conflict.mutationId,
                        transactionId = "conflict-review:${conflict.conflictId}",
                        transactionOrder = 0,
                        transactionSize = 1,
                        changedAtEpochMillis = System.currentTimeMillis(),
                    )
                )
            }
            val now = System.currentTimeMillis()
            check(dao.markReviewSupersededWithProof(
                old.mutationId, old.organizationId, old.semanticFingerprint, "USER_ACCEPTED_SERVER", now,
            ) == 1) { "CONFLICT_STATE_CHANGED" }
            check(dao.transitionConflict(
                conflict.conflictId, "OPEN", "RESOLVED_SERVER_ACCEPTED", null, now,
            ) == 1) { "CONFLICT_STATE_CHANGED" }
            dao.insertConflictDecision(
                audit(conflict.conflictId, old.mutationId, conflict.organizationId, evidence,
                    "ACCEPT_SERVER", actor, null,
                    if (newerLocalIntent) "AUTHORIZED_USER_DECISION_PRESERVE_NEWER_LOCAL" else "AUTHORIZED_USER_DECISION",
                    actor.userId,
                    "OPEN", "RESOLVED_SERVER_ACCEPTED", now)
            )
            pendingProtection.releaseAfterTerminal(PendingSourceRef(old.organizationId, SyncSourceOwner.UNIFIED, old.mutationId))
            SyncConflictResolutionResult(conflict.conflictId)
        }
    }

    /** B11.03: create a new immutable mutation on the exact displayed remote version. */
    suspend fun resendLocal(conflictId: String, expectedServerVersion: Long): SyncConflictResolutionResult {
        val initial = loadConflict(conflictId)
        val actor = authorizer.requireAuthorized(initial.first.organizationId)
        return database.withTransaction {
            val (conflict, evidence) = loadConflict(conflictId)
            val dao = database.unifiedSyncDao()
            SyncConflictResolutionPolicy.requireDecisionAllowed(
                aggregateType = conflict.aggregateType,
                state = conflict.state,
                outcomeProof = evidence.outcomeProof,
                evidenceServerVersion = evidence.serverVersion,
                displayedServerVersion = expectedServerVersion,
                latestKnownServerVersion = dao.latestKnownServerVersion(
                    conflict.organizationId, conflict.aggregateType, conflict.aggregateId,
                ),
            )
            val old = dao.getOutbox(conflict.mutationId)
                ?: throw SyncConflictResolutionException("CONFLICT_LOCAL_INTENT_MISSING", conflict.mutationId)
            check(old.organizationId == conflict.organizationId && old.state == "REQUIRES_REVIEW") {
                "CONFLICT_STATE_CHANGED"
            }
            if (old.commandBatchId != null) {
                throw SyncConflictResolutionException("DOMAIN_CORRECTION_REQUIRED", "batched command needs domain-specific correction")
            }
            val contract = UnifiedSyncAggregateRegistry.requireById(old.aggregateType)
            if (contract.payloadVersion != old.payloadVersion) {
                throw SyncConflictResolutionException("CONFLICT_LOCAL_SCHEMA_STALE", "payload version changed")
            }
            val payload = json.parseToJsonElement(old.payloadJson) as? JsonObject
                ?: throw SyncConflictResolutionException("CONFLICT_LOCAL_PAYLOAD_INVALID", old.mutationId)
            val operation = runCatching { SyncMutationOperation.valueOf(old.operationType) }
                .getOrElse { throw SyncConflictResolutionException("CONFLICT_LOCAL_OPERATION_INVALID", old.operationType) }
            val now = System.currentTimeMillis()
            check(dao.markReviewSupersededPendingProof(
                old.mutationId, old.organizationId, old.semanticFingerprint,
            ) == 1) { "CONFLICT_STATE_CHANGED" }

            val replacement = dao.enqueueMutation(
                UnifiedSyncMutationDraft(
                    mutationId = UUID.randomUUID().toString(),
                    organizationId = old.organizationId,
                    aggregateType = old.aggregateType,
                    aggregateId = old.aggregateId,
                    operationType = old.operationType,
                    baseVersion = expectedServerVersion,
                    payloadVersion = old.payloadVersion,
                    payloadJson = old.payloadJson,
                    commandBatchId = null,
                    commandOrder = null,
                    dependsOnMutationId = null,
                    createdAt = now,
                ),
                updatedAt = now,
            )
            UnifiedSyncContractRules.requireValidMutation(
                SyncMutation(
                    mutationId = replacement.mutationId,
                    organizationId = replacement.organizationId,
                    aggregateType = replacement.aggregateType,
                    aggregateId = replacement.aggregateId,
                    operationType = operation,
                    baseVersion = replacement.baseVersion,
                    localSequence = replacement.localSequence,
                    aggregateSequence = replacement.aggregateSequence,
                    payloadVersion = replacement.payloadVersion,
                    payload = payload,
                    createdAtEpochMillis = replacement.createdAt,
                )
            )
            frozenMutationStore.captureUnified(replacement, supersedesMutationId = old.mutationId, useUnresolvedPredecessor = false)
            check(dao.transitionConflict(
                conflict.conflictId, "OPEN", "WAITING_REPLACEMENT_RECEIPT", replacement.mutationId, null,
            ) == 1) { "CONFLICT_STATE_CHANGED" }
            dao.insertConflictDecision(
                audit(conflict.conflictId, old.mutationId, conflict.organizationId, evidence,
                    "RESEND_LOCAL", actor, replacement.mutationId, "AUTHORIZED_USER_DECISION", actor.userId,
                    "OPEN", "WAITING_REPLACEMENT_RECEIPT", now)
            )
            // Supersedes relation is durable in the packet, conflict row, and audit; old frozen bytes are untouched.
            dao.requestInboxApplyForOrganization(conflict.organizationId, now)
            SyncConflictResolutionResult(conflict.conflictId, replacement.mutationId)
        }
    }

    private suspend fun loadConflict(conflictId: String): Pair<com.verto.app.data.local.entity.SyncConflictEntity, SyncConflictReviewEvidenceEntity> {
        val dao = database.unifiedSyncDao()
        val conflict = dao.getConflict(conflictId)
            ?: throw SyncConflictResolutionException("CONFLICT_NOT_FOUND", conflictId)
        val evidence = requireEvidence(conflict)
        check(conflict.organizationId == evidence.organizationId && conflict.mutationId == evidence.mutationId) { "SCOPE_MISMATCH" }
        return conflict to evidence
    }

    /** B11 upgrade bridge: old OPEN reviews are converted to immutable evidence before display/action. */
    private suspend fun requireEvidence(
        conflict: com.verto.app.data.local.entity.SyncConflictEntity,
    ): SyncConflictReviewEvidenceEntity {
        database.unifiedSyncDao().getConflictEvidence(conflict.conflictId)?.let { return it }
        return database.withTransaction {
            val dao = database.unifiedSyncDao()
            dao.getConflictEvidence(conflict.conflictId)?.let { return@withTransaction it }
            val outbox = dao.getOutbox(conflict.mutationId)
                ?: throw SyncConflictResolutionException("CONFLICT_LOCAL_INTENT_MISSING", conflict.mutationId)
            val packet = dao.readMutationPacket(conflict.organizationId, conflict.mutationId)
            val proof = if (
                outbox.ackedServerRevision != null && conflict.requestHash != null &&
                packet?.wireSha256 != null && conflict.requestHash == packet.wireSha256
            ) "PROVEN_CONFLICT" else "OUTCOME_UNKNOWN"
            val evidence = SyncConflictReviewEvidenceEntity(
                conflictId = conflict.conflictId,
                organizationId = conflict.organizationId,
                mutationId = conflict.mutationId,
                localPayloadJson = outbox.payloadJson,
                localPayloadSha256 = com.verto.app.data.sync.sha256Utf8(outbox.payloadJson),
                localSemanticFingerprint = outbox.semanticFingerprint,
                localBaseVersion = outbox.baseVersion,
                remotePayloadJson = conflict.authoritativePayloadJson,
                remotePayloadSha256 = com.verto.app.data.sync.sha256Utf8(conflict.authoritativePayloadJson),
                serverRevision = outbox.ackedServerRevision,
                serverVersion = conflict.serverVersion,
                outcomeProof = proof,
                createdAt = conflict.createdAt,
            )
            dao.insertConflictEvidenceChecked(evidence)
            evidence
        }
    }

    private fun audit(
        conflictId: String,
        mutationId: String,
        organizationId: String,
        evidence: SyncConflictReviewEvidenceEntity,
        decisionType: String,
        actor: SyncConflictDecisionActor?,
        resolutionMutationId: String?,
        proofType: String,
        proofReference: String?,
        beforeState: String,
        afterState: String,
        now: Long,
    ) = SyncConflictResolutionAuditEntity(
        decisionId = UUID.randomUUID().toString(),
        conflictId = conflictId,
        organizationId = organizationId,
        mutationId = mutationId,
        decisionType = decisionType,
        actorId = actor?.userId,
        actorRole = actor?.role,
        localPayloadSha256 = evidence.localPayloadSha256,
        remotePayloadSha256 = evidence.remotePayloadSha256,
        expectedServerVersion = evidence.serverVersion,
        resolutionMutationId = resolutionMutationId,
        proofType = proofType,
        proofReference = proofReference,
        beforeState = beforeState,
        afterState = afterState,
        decidedAt = now,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }
    }
}

/** Pure fail-closed policy used by both decision paths and JVM tests. */
object SyncConflictResolutionPolicy {
    fun isGenericMutable(aggregateType: String): Boolean =
        UnifiedSyncAggregateRegistry.requireById(aggregateType).conflictPolicy == UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION

    fun requireDecisionAllowed(
        aggregateType: String,
        state: String,
        outcomeProof: String,
        evidenceServerVersion: Long,
        displayedServerVersion: Long,
        latestKnownServerVersion: Long?,
    ) {
        if (!isGenericMutable(aggregateType)) {
            throw SyncConflictResolutionException("DOMAIN_CORRECTION_REQUIRED", aggregateType)
        }
        if (state != "OPEN") throw SyncConflictResolutionException("CONFLICT_STATE_CHANGED", state)
        if (outcomeProof != "PROVEN_CONFLICT") {
            throw SyncConflictResolutionException("OUTCOME_UNKNOWN", "old mutation outcome is not proven")
        }
        if (evidenceServerVersion != displayedServerVersion ||
            (latestKnownServerVersion != null && latestKnownServerVersion != displayedServerVersion)) {
            throw SyncConflictResolutionException("CONFLICT_REMOTE_VERSION_CHANGED", "displayed remote version is stale")
        }
    }
}

/** Pure B11 projection: redact secrets before UI and expose deterministic differing field paths. */
object SyncConflictRedactor {
    private val json = Json { explicitNulls = true; prettyPrint = true }
    private val secret = Regex("(?i)(password|passcode|secret|token|authorization|api[_-]?key|otp|pin|private[_-]?uri|signed[_-]?url|refresh[_-]?token)")

    fun redact(raw: String): JsonElement = redactElement(Json.parseToJsonElement(raw))

    fun preview(value: JsonElement, maxChars: Int = 4_000): String {
        val rendered = json.encodeToString(JsonElement.serializer(), value)
        return if (rendered.length <= maxChars) rendered else rendered.take(maxChars) + "\n…[truncated]"
    }

    fun diff(local: JsonElement, remote: JsonElement, limit: Int = 50): List<SyncConflictFieldDiff> {
        val out = mutableListOf<SyncConflictFieldDiff>()
        collectDiff("$", local, remote, out, limit)
        return out
    }

    private fun redactElement(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            if (secret.containsMatchIn(key)) JsonPrimitive("[REDACTED]") else redactElement(child)
        })
        is JsonArray -> JsonArray(value.map(::redactElement))
        else -> value
    }

    private fun collectDiff(path: String, a: JsonElement?, b: JsonElement?, out: MutableList<SyncConflictFieldDiff>, limit: Int) {
        if (out.size >= limit || a == b) return
        if (a is JsonObject && b is JsonObject) {
            (a.keys + b.keys).toSortedSet().forEach { key ->
                if (out.size < limit) collectDiff("$path.$key", a[key], b[key], out, limit)
            }
            return
        }
        val av = a?.let { compact(it) } ?: "<missing>"
        val bv = b?.let { compact(it) } ?: "<missing>"
        out += SyncConflictFieldDiff(path, av, bv)
    }

    private fun compact(value: JsonElement): String = when (value) {
        JsonNull -> "null"
        is JsonPrimitive -> value.toString().take(180)
        else -> value.toString().take(180)
    }
}
