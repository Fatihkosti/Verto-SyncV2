package com.verto.app.data.sync

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class SyncRepairCapabilitiesRequest(
    @SerialName("p_organization_id") val organizationId: String,
)

@Serializable
private data class SyncRepairCapabilitiesWire(
    val contractFamily: String,
    val contractVersion: Int,
    val payloadVersions: JsonObject = JsonObject(emptyMap()),
    val maxGroupBytes: Long,
    val definitionFingerprint: String,
    val receiptHorizonDays: Int,
    val minAvailableRevision: Long,
    val legacyFence: JsonObject = JsonObject(emptyMap()),
    val storageCapabilities: JsonObject = JsonObject(emptyMap()),
)

interface UnifiedSyncPullRemote {
    suspend fun resolveScope(): SyncScope
    suspend fun pull(scope: SyncScope, afterCursor: String, limit: Int): SyncPullPage
}

@Singleton
class SupabaseUnifiedSyncPullRemote @Inject constructor() : UnifiedSyncPullRemote {
    override suspend fun resolveScope(): SyncScope {
        val rows = VertoSupabase.client.postgrest
            .rpc("verto_resolve_sync_scope_v2")
            .decodeList<UnifiedSyncScopeWire>()
        require(rows.size == 1) { "FAIL_SCOPE_MISMATCH: expected exactly one trusted V2 scope" }
        val scope = rows.single().toContract()
        require(scope.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY && scope.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) {
            "CONTRACT_BLOCKED: server scope is not unified-sync V2"
        }
        val capabilities = VertoSupabase.client.postgrest.rpc(
            function = "verto_sync_repair_capabilities_v1",
            parameters = SyncRepairCapabilitiesRequest(scope.organizationId),
        ).decodeAs<SyncRepairCapabilitiesWire>()
        require(capabilities.contractFamily == scope.contractFamily && capabilities.contractVersion == scope.contractVersion) {
            "CONTRACT_BLOCKED: capabilities contract mismatch"
        }
        require(capabilities.maxGroupBytes == MAX_GROUP_BYTES) {
            "CONTRACT_BLOCKED: maxGroupBytes mismatch"
        }
        require(capabilities.definitionFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "CONTRACT_BLOCKED: server definition fingerprint missing"
        }
        require(capabilities.receiptHorizonDays > 0 && capabilities.minAvailableRevision >= 0) {
            "CONTRACT_BLOCKED: receipt/cursor horizon unavailable"
        }
        return scope
    }

    override suspend fun pull(scope: SyncScope, afterCursor: String, limit: Int): SyncPullPage {
        require(scope.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_BLOCKED: pull requires V2 scope" }
        require(afterCursor.isNotBlank()) { "FAIL_CURSOR_STALL: opaque cursor must be nonblank" }
        require(limit in 1..MAX_PULL_PAGE_CHANGES) { "VALIDATION: soft limit must be 1..1000" }
        return VertoSupabase.client.postgrest.rpc(
            function = "verto_pull_sync_changes_v2",
            parameters = SyncInboxPullRequestV2(scope.scopeId, afterCursor, limit),
        ).decodeAs<SyncPullPage>()
    }

    private fun UnifiedSyncScopeWire.toContract() = SyncScope(
        organizationId = organizationId,
        syncPrincipalId = syncPrincipalId,
        scopeId = scopeId,
        contractFamily = contractFamily,
        contractVersion = contractVersion,
        scopeDefinitionVersion = scopeDefinitionVersion,
    )

    companion object {
        const val MAX_PULL_PAGE_CHANGES = 1_000
        const val MAX_GROUP_BYTES = 2_097_152L
    }
}
