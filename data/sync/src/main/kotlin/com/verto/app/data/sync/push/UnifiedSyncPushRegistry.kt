package com.verto.app.data.sync.push

import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.UnifiedSyncAggregateRecord
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncDeletePolicy
import com.verto.app.data.sync.UnifiedStrongerBridgeRegistry
import com.verto.app.data.sync.StrongerBridgeRuntimeState
import javax.inject.Inject
import javax.inject.Singleton

enum class UnifiedSyncPushCoverageStatus {
    PUSH_READY_SHADOW,
    PRESERVED_STRONGER_PARTY_BRIDGE,
    SERVER_OWNED_NO_CLIENT_PUSH,
    STRONGER_BRIDGE_READY_SHADOW,
    SERVER_AUTHORITATIVE_NO_CLIENT_PUSH,
    PRESERVED_EXISTING_STRONGER_RUNTIME,
    SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE,
    BLOCKED_WITH_EVIDENCE,
}

class UnifiedSyncPushFailure(
    val code: String,
    detail: String,
    cause: Throwable? = null,
) : IllegalStateException("$code: $detail", cause)

@Singleton
class UnifiedSyncPushRegistry @Inject constructor() {
    fun record(aggregateType: String): UnifiedSyncAggregateRecord =
        UnifiedSyncAggregateRegistry.findById(aggregateType)
            ?: throw UnifiedSyncPushFailure("CONTRACT_UNSUPPORTED", "unknown aggregate=$aggregateType")

    fun coverageStatus(aggregateType: String): UnifiedSyncPushCoverageStatus {
        val record = record(aggregateType)
        return when {
            record.modernizationSession == 310 -> when (UnifiedStrongerBridgeRegistry.requireRecord(aggregateType).runtimeState) {
                StrongerBridgeRuntimeState.SERVER_AUTHORITATIVE_NO_CLIENT_PUSH -> UnifiedSyncPushCoverageStatus.SERVER_AUTHORITATIVE_NO_CLIENT_PUSH
                StrongerBridgeRuntimeState.PRESERVED_EXISTING_STRONGER_RUNTIME -> UnifiedSyncPushCoverageStatus.PRESERVED_EXISTING_STRONGER_RUNTIME
                else -> UnifiedSyncPushCoverageStatus.STRONGER_BRIDGE_READY_SHADOW
            }
            aggregateType == "NOTIFICATION" -> UnifiedSyncPushCoverageStatus.SERVER_OWNED_NO_CLIENT_PUSH
            aggregateType == "PARTY_ROLE" -> UnifiedSyncPushCoverageStatus.PRESERVED_STRONGER_PARTY_BRIDGE
            aggregateType == "TEAM_OBSERVATION" -> UnifiedSyncPushCoverageStatus.PUSH_READY_SHADOW
            record.modernizationSession == 307 -> UnifiedSyncPushCoverageStatus.SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE
            else -> UnifiedSyncPushCoverageStatus.BLOCKED_WITH_EVIDENCE
        }
    }

    fun validateGenericMutation(
        aggregateType: String,
        payloadVersion: Int,
        operation: SyncMutationOperation,
    ) {
        val record = record(aggregateType)
        if (record.modernizationSession == 310) {
            throw UnifiedSyncPushFailure("STRONGER_BRIDGE_REQUIRED", "$aggregateType must use its Session 310 stronger durable authority")
        }
        if (aggregateType == "NOTIFICATION" || record.deletePolicy == UnifiedSyncDeletePolicy.SERVER_OWNED) {
            throw UnifiedSyncPushFailure("FAIL_NOTIFICATION_CLIENT_PUSH", "server-owned aggregate cannot be client pushed")
        }
        if (aggregateType == "PARTY_ROLE") {
            throw UnifiedSyncPushFailure("DEFERRED_STRONGER_AGGREGATE", "PARTY_ROLE must use party_sync_outbox bridge")
        }
        if (payloadVersion != record.payloadVersion) {
            throw UnifiedSyncPushFailure("CONTRACT_UNSUPPORTED", "payloadVersion=$payloadVersion expected=${record.payloadVersion}")
        }
        val allowed = allowedOperations(record)
        if (operation !in allowed) {
            throw UnifiedSyncPushFailure("VALIDATION", "operation=$operation violates ${record.deletePolicy} for $aggregateType")
        }
    }

    fun validateStrongerMutation(
        aggregateType: String,
        payloadVersion: Int,
        operation: SyncMutationOperation,
    ) {
        val record = record(aggregateType)
        if (record.modernizationSession != 310) {
            throw UnifiedSyncPushFailure("CONTRACT_UNSUPPORTED", "$aggregateType is not a Session 310 stronger aggregate")
        }
        val bridge = UnifiedStrongerBridgeRegistry.requireRecord(aggregateType)
        if (bridge.runtimeState == StrongerBridgeRuntimeState.SERVER_AUTHORITATIVE_NO_CLIENT_PUSH) {
            throw UnifiedSyncPushFailure("SERVER_AUTHORITATIVE_NO_CLIENT_PUSH", "$aggregateType has no client push authority")
        }
        if (payloadVersion != record.payloadVersion) {
            throw UnifiedSyncPushFailure("CONTRACT_UNSUPPORTED", "payloadVersion=$payloadVersion expected=${record.payloadVersion}")
        }
        if (operation == SyncMutationOperation.DELETE || operation == SyncMutationOperation.UPSERT) {
            throw UnifiedSyncPushFailure("FAIL_OWNER310_GENERIC_MUTATION", "$aggregateType requires COMMAND/VOID/REVERSE through its stronger bridge")
        }
        if (operation == SyncMutationOperation.VOID && record.deletePolicy != UnifiedSyncDeletePolicy.VOID_OR_REVERSE) {
            throw UnifiedSyncPushFailure("VALIDATION", "$aggregateType does not allow VOID")
        }
        if (operation == SyncMutationOperation.REVERSE && record.deletePolicy !in setOf(UnifiedSyncDeletePolicy.VOID_OR_REVERSE, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE)) {
            throw UnifiedSyncPushFailure("VALIDATION", "$aggregateType does not allow REVERSE")
        }
    }

    fun allowedOperations(record: UnifiedSyncAggregateRecord): Set<SyncMutationOperation> = when (record.deletePolicy) {
        UnifiedSyncDeletePolicy.VERSIONED_DELETE,
        UnifiedSyncDeletePolicy.TOMBSTONE -> setOf(SyncMutationOperation.UPSERT, SyncMutationOperation.DELETE)
        UnifiedSyncDeletePolicy.ARCHIVE -> setOf(SyncMutationOperation.UPSERT, SyncMutationOperation.ARCHIVE)
        UnifiedSyncDeletePolicy.CANCEL_STATE_TRANSITION -> setOf(
            SyncMutationOperation.UPSERT,
            SyncMutationOperation.COMMAND,
            SyncMutationOperation.CANCEL,
        )
        UnifiedSyncDeletePolicy.NO_CLIENT_DELETE -> when (record.id) {
            "ORGANIZATION_SETTINGS" -> setOf(SyncMutationOperation.UPSERT)
            else -> setOf(SyncMutationOperation.UPSERT, SyncMutationOperation.COMMAND)
        }
        UnifiedSyncDeletePolicy.VOID_OR_REVERSE -> setOf(
            SyncMutationOperation.UPSERT,
            SyncMutationOperation.COMMAND,
            SyncMutationOperation.VOID,
            SyncMutationOperation.REVERSE,
        )
        UnifiedSyncDeletePolicy.SERVER_OWNED -> emptySet()
    }
}
