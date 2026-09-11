package com.verto.app.data.sync

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

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
        return rows.single().toContract().also {
            require(it.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY && it.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) {
                "CONTRACT_BLOCKED: server scope is not unified-sync V2"
            }
        }
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
    }
}
