package com.verto.app.data.local.dao

import com.verto.app.data.local.entity.SyncConflictEntity
import com.verto.app.data.local.entity.SyncCursorEntity
import com.verto.app.data.local.entity.SyncInboxEntity
import com.verto.app.data.local.entity.SyncOutboxEntity
import java.security.MessageDigest

internal const val MAX_MUTATION_PAYLOAD_BYTES = 524_288
internal const val MAX_WORKER_OPERATIONS_PER_RUN = 500
internal const val COUNTER_GLOBAL = "GLOBAL"
internal const val COUNTER_AGGREGATE = "AGGREGATE"
internal const val COUNTER_ORCHESTRATION_REQUESTED = "ORCHESTRATION_REQUESTED_GENERATION"
internal const val COUNTER_ORCHESTRATION_DRAINED = "ORCHESTRATION_DRAINED_GENERATION"
internal val UNIFIED_OPERATIONS = setOf("UPSERT", "DELETE", "COMMAND", "ARCHIVE", "VOID", "REVERSE", "CANCEL")
internal val TERMINAL_OUTBOX_STATES = setOf("ACKNOWLEDGED", "REQUIRES_REVIEW", "REJECTED")
internal val INBOX_STATES = setOf("RECEIVED", "READY", "APPLIED", "WAITING_LOCAL", "WAITING_DEPENDENCY", "REQUIRES_REVIEW")
internal val CURSOR_STATES = setOf("ACTIVE", "BOOTSTRAP_REQUIRED", "INVALIDATED")
internal val CONFLICT_REQUIREMENTS = setOf(
    "AUTO_REBASE_SAFE", "SERVER_WINS_SAFE", "LOCAL_RETRY_WITH_NEW_BASE", "REQUIRES_REVIEW", "REJECTED"
)
internal val CONFLICT_STATES = setOf(
    "OPEN", "DOMAIN_CORRECTION_REQUIRED", "WAITING_REPLACEMENT_RECEIPT",
    "RESOLVED_SERVER_ACCEPTED", "RESOLVED_REPLACEMENT_PROVED", "RESOLVED_MATCHING_ECHO",
    // Read compatibility for pre-B11 rows. New code never creates these automatic outcomes.
    "RESOLVED_SERVER_WINS", "RESOLVED_RETRY_QUEUED", "RESOLVED_REJECTED"
)

internal fun validateMutationDraft(draft: UnifiedSyncMutationDraft) {
    require(draft.mutationId.isNotBlank())
    require(draft.organizationId.isNotBlank())
    require(draft.aggregateType.isNotBlank())
    require(draft.aggregateId.isNotBlank())
    require(draft.operationType in UNIFIED_OPERATIONS) { "unsupported unified operation" }
    require(draft.payloadVersion > 0) { "payloadVersion must be positive" }
    require(draft.payloadJson.toByteArray(Charsets.UTF_8).size <= MAX_MUTATION_PAYLOAD_BYTES) {
        "CONTRACT_PAYLOAD_TOO_LARGE"
    }
    require(
        (draft.commandBatchId == null && draft.commandOrder == null) ||
            (!draft.commandBatchId.isNullOrBlank() && draft.commandOrder != null && draft.commandOrder >= 0)
    ) { "invalid command batch metadata" }
    require(draft.dependsOnMutationId == null || (draft.dependsOnMutationId.isNotBlank() && draft.dependsOnMutationId != draft.mutationId)) {
        "invalid mutation dependency"
    }
}

internal fun validateInbox(entity: SyncInboxEntity) {
    require(entity.scopeId.isNotBlank())
    require(entity.organizationId.isNotBlank())
    require(entity.serverRevision >= 0)
    require(entity.aggregateType.isNotBlank() && entity.aggregateId.isNotBlank())
    require(entity.operationType in UNIFIED_OPERATIONS)
    require(entity.payloadVersion > 0)
    require(entity.payloadJson.toByteArray(Charsets.UTF_8).size <= 2_097_152) { "CONTRACT_GROUP_TOO_LARGE" }
    require(entity.transactionId.isNotBlank())
    require(entity.transactionSize >= 1)
    require(entity.transactionOrder in 0 until entity.transactionSize)
    require(entity.contentFingerprint.isNotBlank())
    require(entity.applyState in INBOX_STATES)
}

internal fun validateConflict(entity: SyncConflictEntity) {
    require(entity.conflictId.isNotBlank())
    require(entity.mutationId.isNotBlank())
    require(entity.organizationId.isNotBlank())
    require(entity.aggregateType.isNotBlank() && entity.aggregateId.isNotBlank())
    require(entity.conflictCode.isNotBlank())
    require(entity.localPayloadVersion > 0)
    require(entity.serverVersion > 0)
    require(entity.authoritativePayloadJson.isNotBlank())
    require(entity.resolutionRequirement in CONFLICT_REQUIREMENTS) { "FAIL_CONFLICT_REQUIREMENT_UNKNOWN" }
    require(entity.state in CONFLICT_STATES) { "invalid conflict state" }
}


internal fun validateConflictEvidence(entity: com.verto.app.data.local.entity.SyncConflictReviewEvidenceEntity) {
    require(entity.conflictId.isNotBlank() && entity.organizationId.isNotBlank() && entity.mutationId.isNotBlank())
    require(entity.localPayloadJson.isNotBlank() && entity.remotePayloadJson.isNotBlank())
    require(entity.localPayloadSha256.matches(Regex("[0-9a-f]{64}")))
    require(entity.remotePayloadSha256.matches(Regex("[0-9a-f]{64}")))
    require(entity.localSemanticFingerprint.isNotBlank())
    require(entity.serverVersion > 0)
    require(entity.outcomeProof in setOf("PROVEN_CONFLICT", "OUTCOME_UNKNOWN"))
}

internal fun validateCursor(entity: SyncCursorEntity) {
    require(entity.scopeId.isNotBlank())
    require(entity.organizationId.isNotBlank())
    require(entity.syncPrincipalId.isNotBlank())
    require(entity.contractFamily.isNotBlank())
    require(entity.contractVersion > 0 && entity.scopeDefinitionVersion > 0)
    require(entity.cursorToken.isNotBlank())
    require(entity.state in CURSOR_STATES)
}

internal fun semanticFingerprint(draft: UnifiedSyncMutationDraft): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val fields = listOf(
        draft.mutationId, draft.organizationId, draft.aggregateType, draft.aggregateId, draft.operationType,
        draft.baseVersion?.toString() ?: "<null>", draft.payloadVersion.toString(), draft.payloadJson,
        draft.commandBatchId ?: "<null>", draft.commandOrder?.toString() ?: "<null>",
        draft.dependsOnMutationId ?: "<null>", draft.createdAt.toString(),
    )
    fields.forEach { value ->
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(':'.code.toByte())
        digest.update(bytes)
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun SyncOutboxEntity.matches(draft: UnifiedSyncMutationDraft): Boolean =
    mutationId == draft.mutationId &&
        organizationId == draft.organizationId &&
        aggregateType == draft.aggregateType &&
        aggregateId == draft.aggregateId &&
        operationType == draft.operationType &&
        baseVersion == draft.baseVersion &&
        payloadVersion == draft.payloadVersion &&
        payloadJson == draft.payloadJson &&
        commandBatchId == draft.commandBatchId &&
        commandOrder == draft.commandOrder &&
        dependsOnMutationId == draft.dependsOnMutationId &&
        createdAt == draft.createdAt

internal fun SyncConflictEntity.sameImmutableConflict(other: SyncConflictEntity): Boolean =
    conflictId == other.conflictId &&
        mutationId == other.mutationId &&
        organizationId == other.organizationId &&
        aggregateType == other.aggregateType &&
        aggregateId == other.aggregateId &&
        conflictCode == other.conflictCode &&
        localPayloadVersion == other.localPayloadVersion &&
        serverVersion == other.serverVersion &&
        authoritativePayloadJson == other.authoritativePayloadJson &&
        resolutionRequirement == other.resolutionRequirement &&
        requestHash == other.requestHash

internal fun SyncInboxEntity.sameImmutableContent(other: SyncInboxEntity): Boolean =
    scopeId == other.scopeId &&
        organizationId == other.organizationId &&
        serverRevision == other.serverRevision &&
        aggregateType == other.aggregateType &&
        aggregateId == other.aggregateId &&
        operationType == other.operationType &&
        entityVersion == other.entityVersion &&
        payloadVersion == other.payloadVersion &&
        payloadJson == other.payloadJson &&
        originMutationId == other.originMutationId &&
        transactionId == other.transactionId &&
        transactionOrder == other.transactionOrder &&
        transactionSize == other.transactionSize &&
        deletedAt == other.deletedAt &&
        changedAt == other.changedAt &&
        contentFingerprint == other.contentFingerprint

