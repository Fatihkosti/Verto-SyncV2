package com.verto.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val UNIFIED_SYNC_CONTRACT_FAMILY: String = "verto-unified-sync"
const val UNIFIED_SYNC_CONTRACT_VERSION: Int = 1

@Serializable
enum class SyncMutationOperation {
    UPSERT,
    DELETE,
    COMMAND,
    ARCHIVE,
    VOID,
    REVERSE,
    CANCEL,
}

@Serializable
enum class SyncReceiptStatus {
    APPLIED,
    REPLAYED,
    NO_OP,
    CONFLICT,
    REJECTED,
    RETRYABLE,
}

@Serializable
enum class SyncProtocolErrorType {
    TRANSIENT,
    AUTH,
    VALIDATION,
    CONFLICT,
    CURSOR_EXPIRED,
    CONTRACT_UNSUPPORTED,
}

@Serializable
enum class SyncConflictResolutionRequirement {
    AUTO_REBASE_SAFE,
    SERVER_WINS_SAFE,
    LOCAL_RETRY_WITH_NEW_BASE,
    REQUIRES_REVIEW,
    REJECTED,
}

@Serializable
enum class SyncBootstrapState {
    NOT_STARTED,
    IN_PROGRESS,
    READY,
    RECOVERY_REQUIRED,
}

@Serializable
enum class SyncPullCoverage {
    GLOBAL_SCOPE,
    FILTERED_SCOPE,
}

@Serializable
data class SyncMutation(
    val mutationId: String,
    val organizationId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operationType: SyncMutationOperation,
    val baseVersion: Long? = null,
    val localSequence: Long,
    val aggregateSequence: Long,
    val payloadVersion: Int,
    val payload: JsonObject = JsonObject(emptyMap()),
    val createdAtEpochMillis: Long,
    val commandBatchId: String? = null,
    val commandOrder: Int? = null,
    val dependsOnMutationId: String? = null,
)

@Serializable
data class SyncChange(
    val revision: Long,
    val organizationId: String,
    val syncScopeId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operationType: SyncMutationOperation,
    val entityVersion: Long? = null,
    val payloadVersion: Int,
    val payload: JsonObject = JsonObject(emptyMap()),
    val originMutationId: String? = null,
    val transactionId: String,
    val transactionOrder: Int,
    val transactionSize: Int,
    val deletedAtEpochMillis: Long? = null,
    val changedAtEpochMillis: Long,
)

@Serializable
data class SyncReceipt(
    val status: SyncReceiptStatus,
    val mutationId: String,
    val aggregateId: String,
    val serverVersion: Long? = null,
    val serverRevision: Long? = null,
    val authoritativePayload: JsonObject? = null,
    val conflictCode: String? = null,
    val validationCode: String? = null,
    val transactionId: String? = null,
    val retryAfterEpochMillis: Long? = null,
    val requestHash: String? = null,
)

@Serializable
data class SyncConflict(
    val conflictId: String,
    val mutationId: String,
    val organizationId: String,
    val aggregateType: String,
    val aggregateId: String,
    val conflictCode: String,
    val localPayloadVersion: Int,
    val serverVersion: Long,
    val authoritativePayload: JsonObject,
    val resolutionRequirement: SyncConflictResolutionRequirement,
)

@Serializable
data class SyncScope(
    val organizationId: String,
    val syncPrincipalId: String,
    val scopeId: String,
    val contractFamily: String = UNIFIED_SYNC_CONTRACT_FAMILY,
    val contractVersion: Int = UNIFIED_SYNC_CONTRACT_VERSION,
    val scopeDefinitionVersion: Int,
)

@Serializable
data class SyncPrincipal(
    val syncPrincipalId: String,
    val organizationId: String,
    val visibilityVersion: Long,
)

@Serializable
data class BootstrapSession(
    val bootstrapSessionId: String,
    val scope: SyncScope,
    val baselineCursor: String,
    val snapshotPageToken: String? = null,
    val snapshotComplete: Boolean,
    val contractVersion: Int = UNIFIED_SYNC_CONTRACT_VERSION,
    val expiresAtEpochMillis: Long,
)

@Serializable
data class TransactionGroup(
    val transactionId: String,
    val transactionOrder: Int,
    val transactionSize: Int,
    val firstRevision: Long,
    val lastRevision: Long,
)

@Serializable
data class CommandBatch(
    val commandBatchId: String,
    val atomic: Boolean,
    val orderedMutationIds: List<String>,
)

@Serializable
data class SyncPullPage(
    val changes: List<SyncChange>,
    val nextCursor: String,
    val hasMore: Boolean,
    val minAvailableRevision: Long? = null,
    val contractFamily: String = UNIFIED_SYNC_CONTRACT_FAMILY,
    val contractVersion: Int = UNIFIED_SYNC_CONTRACT_VERSION,
    val pageHighWatermark: Long? = null,
    val endsAtTransactionBoundary: Boolean,
    val scopeIdentity: SyncScope,
    val coverage: SyncPullCoverage = SyncPullCoverage.GLOBAL_SCOPE,
    val advancesGlobalCursor: Boolean = coverage == SyncPullCoverage.GLOBAL_SCOPE,
    // Mandatory at the B10 receive boundary; defaults keep older diagnostic fixtures decodable.
    val fromCursor: String? = null,
    val coveredThroughRevision: Long? = null,
    val groups: List<SyncInboxGroupManifestV2> = emptyList(),
)

@Serializable
data class SyncReconciliationManifest(
    val aggregateType: String,
    val partitionKey: String,
    val rowCount: Long,
    val contentHashOrVersionDigest: String,
    val manifestRevision: Long,
    val scopeId: String,
)

@Serializable
data class SyncRealtimeHint(
    val organizationId: String,
    val aggregateType: String? = null,
    val aggregateId: String? = null,
    val serverRevision: Long? = null,
)

@Serializable
data class SyncRetentionValue(
    val valueDays: Int? = null,
    val evidence: String,
    val rationale: String,
    val safetyRelation: String,
)

@Serializable
data class SyncRetentionContract(
    val changeLogRetentionDays: SyncRetentionValue,
    val receiptRetentionDays: SyncRetentionValue,
    val tombstoneRetentionDays: SyncRetentionValue,
    val deviceInactiveAfterDays: SyncRetentionValue,
    val localInboxDiagnosticRetentionDays: SyncRetentionValue,
    val localAckedOutboxDiagnosticRetentionDays: SyncRetentionValue,
    val supportedOfflineWindowDays: SyncRetentionValue,
    val supportedOldClientWindowDays: SyncRetentionValue,
)

@Serializable
data class SyncBudgetValue(
    val value: Long? = null,
    val unit: String,
    val evidence: String,
    val rationale: String,
)

@Serializable
data class SyncBudgetsContract(
    val maxMutationPayloadBytes: SyncBudgetValue,
    val maxPullPageBytes: SyncBudgetValue,
    val maxPullPageChanges: SyncBudgetValue,
    val maxPushBatchBytes: SyncBudgetValue,
    val maxPushBatchMutations: SyncBudgetValue,
    val maxTransactionGroupBytes: SyncBudgetValue,
    val maxWorkerRuntimeMillis: SyncBudgetValue,
    val maxWorkerOperationsPerRun: SyncBudgetValue,
    val maxBacklogBeforeDiagnosticWarning: SyncBudgetValue,
)

class SyncContractViolation(
    val code: String,
    message: String,
) : IllegalArgumentException("$code: $message")

object UnifiedSyncContractRules {
    fun requireValidMutation(mutation: SyncMutation) {
        requireNonBlank(mutation.mutationId, "mutationId")
        requireNonBlank(mutation.organizationId, "organizationId")
        requireKnownAggregate(mutation.aggregateType, mutation.payloadVersion)
        requireNonBlank(mutation.aggregateId, "aggregateId")
        requirePositive(mutation.localSequence, "localSequence")
        requirePositive(mutation.aggregateSequence, "aggregateSequence")
        requirePositive(mutation.payloadVersion, "payloadVersion")
        mutation.baseVersion?.let { if (it < 0L) fail("VALIDATION", "baseVersion cannot be negative") }
        mutation.commandOrder?.let { if (it < 0) fail("VALIDATION", "commandOrder cannot be negative") }
        mutation.commandBatchId?.let { requireNonBlank(it, "commandBatchId") }
        mutation.dependsOnMutationId?.let {
            requireNonBlank(it, "dependsOnMutationId")
            if (it == mutation.mutationId) fail("VALIDATION", "mutation cannot depend on itself")
        }
    }

    fun requireStableRetryIdentity(original: SyncMutation, retry: SyncMutation) {
        requireValidMutation(original)
        requireValidMutation(retry)
        if (original.mutationId != retry.mutationId) {
            fail("IDEMPOTENCY_ID_CHANGED", "retry must preserve mutationId")
        }
        if (original.copy(createdAtEpochMillis = retry.createdAtEpochMillis) != retry) {
            fail("IDEMPOTENCY_CONFLICT", "same mutationId cannot represent different semantic content")
        }
    }

    fun requireValidScope(scope: SyncScope) {
        requireNonBlank(scope.organizationId, "scope.organizationId")
        requireNonBlank(scope.syncPrincipalId, "scope.syncPrincipalId")
        requireNonBlank(scope.scopeId, "scope.scopeId")
        if (scope.contractFamily != UNIFIED_SYNC_CONTRACT_FAMILY) {
            fail("CONTRACT_UNSUPPORTED", "unsupported contract family")
        }
        if (scope.contractVersion != UNIFIED_SYNC_CONTRACT_VERSION) {
            fail("CONTRACT_UNSUPPORTED", "unsupported contract version")
        }
        requirePositive(scope.scopeDefinitionVersion, "scope.scopeDefinitionVersion")
    }

    fun requireValidBootstrap(session: BootstrapSession) {
        requireNonBlank(session.bootstrapSessionId, "bootstrapSessionId")
        requireValidScope(session.scope)
        requireNonBlank(session.baselineCursor, "baselineCursor")
        if (session.contractVersion != UNIFIED_SYNC_CONTRACT_VERSION) {
            fail("CONTRACT_UNSUPPORTED", "bootstrap contract version mismatch")
        }
        if (session.expiresAtEpochMillis <= 0L) fail("VALIDATION", "bootstrap expiry must be positive")
    }

    fun requireValidTransactionGroup(group: TransactionGroup) {
        requireNonBlank(group.transactionId, "transactionId")
        if (group.transactionSize < 1) fail("VALIDATION", "transactionSize must be >= 1")
        if (group.transactionOrder !in 0 until group.transactionSize) {
            fail("INCOMPLETE_TRANSACTION_GROUP", "transactionOrder outside transactionSize")
        }
        requirePositive(group.firstRevision, "firstRevision")
        requirePositive(group.lastRevision, "lastRevision")
        if (group.firstRevision > group.lastRevision) {
            fail("INVALID_REVISION_ORDER", "firstRevision cannot exceed lastRevision")
        }
    }

    fun requireValidCommandBatch(batch: CommandBatch, mutations: Collection<SyncMutation>) {
        requireNonBlank(batch.commandBatchId, "commandBatchId")
        if (batch.orderedMutationIds.isEmpty()) fail("VALIDATION", "command batch cannot be empty")
        if (batch.orderedMutationIds.any { it.isBlank() } || batch.orderedMutationIds.toSet().size != batch.orderedMutationIds.size) {
            fail("VALIDATION", "command batch mutation ids must be unique and nonblank")
        }
        val byId = mutations.associateBy { it.mutationId }
        batch.orderedMutationIds.forEach { id ->
            val mutation = byId[id] ?: fail("VALIDATION", "command batch references unknown mutation $id")
            if (mutation.commandBatchId != batch.commandBatchId) {
                fail("VALIDATION", "mutation $id is not bound to command batch")
            }
        }
        val positions = batch.orderedMutationIds.withIndex().associate { it.value to it.index }
        mutations.forEach { mutation ->
            val dependency = mutation.dependsOnMutationId ?: return@forEach
            val dependentPosition = positions[mutation.mutationId] ?: return@forEach
            val dependencyPosition = positions[dependency]
                ?: fail("DEPENDENCY_NOT_ACKNOWLEDGED", "dependency $dependency is outside atomic batch")
            if (dependencyPosition >= dependentPosition) {
                fail("DEPENDENCY_ORDER_VIOLATION", "dependency must precede dependent mutation")
            }
        }
    }

    fun requireRevisionSequence(changes: List<SyncChange>) {
        var previous = 0L
        val fingerprints = mutableMapOf<Long, SyncChange>()
        changes.forEach { change ->
            requirePositive(change.revision, "revision")
            requireKnownAggregate(change.aggregateType, change.payloadVersion)
            requireNonBlank(change.organizationId, "change.organizationId")
            requireNonBlank(change.syncScopeId, "change.syncScopeId")
            requireNonBlank(change.aggregateId, "change.aggregateId")
            requireNonBlank(change.transactionId, "change.transactionId")
            if (change.transactionSize < 1 || change.transactionOrder !in 0 until change.transactionSize) {
                fail("INCOMPLETE_TRANSACTION_GROUP", "invalid transaction metadata")
            }
            if (change.revision < previous) fail("INVALID_REVISION_ORDER", "revision order moved backwards")
            fingerprints[change.revision]?.let { prior ->
                if (prior != change) fail("DUPLICATE_REVISION_CONTENT_MISMATCH", "same revision has divergent content")
            } ?: run { fingerprints[change.revision] = change }
            previous = change.revision
        }
    }

    fun requireCompleteTransactionGroups(changes: List<SyncChange>) {
        changes.groupBy { it.transactionId }.forEach { (transactionId, group) ->
            val declaredSizes = group.map { it.transactionSize }.distinct()
            if (declaredSizes.size != 1) fail("INCOMPLETE_TRANSACTION_GROUP", "$transactionId has inconsistent size")
            val size = declaredSizes.single()
            val orders = group.map { it.transactionOrder }.toSet()
            if (group.size != size || orders != (0 until size).toSet()) {
                fail("INCOMPLETE_TRANSACTION_GROUP", "$transactionId is split or missing members")
            }
        }
    }

    fun requireValidPullPage(page: SyncPullPage, expectedScope: SyncScope) {
        requireValidScope(expectedScope)
        requireValidScope(page.scopeIdentity)
        if (page.contractFamily != UNIFIED_SYNC_CONTRACT_FAMILY || page.contractVersion != UNIFIED_SYNC_CONTRACT_VERSION) {
            fail("CONTRACT_UNSUPPORTED", "pull contract mismatch")
        }
        if (page.scopeIdentity != expectedScope) fail("SCOPE_MISMATCH", "pull response belongs to another scope/principal")
        requireNonBlank(page.nextCursor, "nextCursor")
        if (!page.endsAtTransactionBoundary) fail("INCOMPLETE_TRANSACTION_GROUP", "page does not end at transaction boundary")
        if (page.coverage == SyncPullCoverage.FILTERED_SCOPE && page.advancesGlobalCursor) {
            fail("FILTERED_GLOBAL_CURSOR_ADVANCE", "filtered pull cannot advance global cursor")
        }
        requireRevisionSequence(page.changes)
        requireCompleteTransactionGroups(page.changes)
        if (page.changes.any { it.organizationId != expectedScope.organizationId || it.syncScopeId != expectedScope.scopeId }) {
            fail("SCOPE_MISMATCH", "change is outside requested organization/scope")
        }
        page.pageHighWatermark?.let { high ->
            if (high < 0L) fail("VALIDATION", "page high-watermark cannot be negative")
            if (page.changes.any { it.revision > high }) fail("VALIDATION", "change exceeds page high-watermark")
        }
    }

    fun requireOpaqueCursor(cursor: String) {
        requireNonBlank(cursor, "cursor")
    }

    fun requireRealtimeHintOnly(hint: SyncRealtimeHint) {
        requireNonBlank(hint.organizationId, "hint.organizationId")
        hint.serverRevision?.let { requirePositive(it, "hint.serverRevision") }
    }

    fun requireValidManifest(manifest: SyncReconciliationManifest) {
        requireKnownAggregate(manifest.aggregateType, UnifiedSyncAggregateRegistry.requireById(manifest.aggregateType).payloadVersion)
        requireNonBlank(manifest.partitionKey, "partitionKey")
        if (manifest.rowCount < 0L) fail("VALIDATION", "rowCount cannot be negative")
        requireNonBlank(manifest.contentHashOrVersionDigest, "contentHashOrVersionDigest")
        requirePositive(manifest.manifestRevision, "manifestRevision")
        requireNonBlank(manifest.scopeId, "scopeId")
    }

    internal fun requireKnownAggregate(aggregateType: String, payloadVersion: Int) {
        val aggregate = UnifiedSyncAggregateRegistry.findById(aggregateType)
            ?: fail("CONTRACT_UNSUPPORTED", "unknown aggregate $aggregateType")
        if (payloadVersion != aggregate.payloadVersion) {
            fail("CONTRACT_UNSUPPORTED", "unsupported payload version $payloadVersion for $aggregateType")
        }
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) fail("VALIDATION", "$field must be nonblank")
    }

    private fun requirePositive(value: Long, field: String) {
        if (value <= 0L) fail("VALIDATION", "$field must be positive")
    }

    private fun requirePositive(value: Int, field: String) {
        if (value <= 0) fail("VALIDATION", "$field must be positive")
    }

    private fun fail(code: String, message: String): Nothing = throw SyncContractViolation(code, message)
}
