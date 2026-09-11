package com.verto.app.data.sync

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

interface UnifiedSyncBootstrapRemote {
    suspend fun resolveScope(): SyncScope
    suspend fun begin(scope: SyncScope): SyncBootstrapStart
    suspend fun pullPage(sessionId: String, pageToken: String, limit: Int): SyncBootstrapPage
    suspend fun reconciliationManifest(scope: SyncScope, partitionToken: String?): SyncReconciliationPage
}

@Singleton
class SupabaseUnifiedSyncBootstrapRemote @Inject constructor() : UnifiedSyncBootstrapRemote {
    override suspend fun resolveScope(): SyncScope {
        val rows = VertoSupabase.client.postgrest.rpc("verto_resolve_sync_scope_v2").decodeList<UnifiedSyncScopeWire>()
        require(rows.size == 1) { "FAIL_BOOTSTRAP_SCOPE_MISMATCH: expected exactly one trusted V2 scope" }
        val row = rows.single()
        return SyncScope(row.organizationId, row.syncPrincipalId, row.scopeId, row.contractFamily, row.contractVersion, row.scopeDefinitionVersion).also {
            require(it.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_BLOCKED: bootstrap requires V2 scope" }
        }
    }

    override suspend fun begin(scope: SyncScope): SyncBootstrapStart {
        UnifiedSyncContractRules.requireValidScope(scope)
        require(scope.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_BLOCKED: bootstrap requires V2 scope" }
        val w = VertoSupabase.client.postgrest.rpc(
            "verto_begin_sync_bootstrap_v2", UnifiedSyncBootstrapBeginRequestWire(scope.scopeId)
        ).decodeAs<UnifiedSyncBootstrapStartWire>()
        require(w.scopeId == scope.scopeId && w.contractFamily == scope.contractFamily && w.contractVersion == scope.contractVersion) {
            "FAIL_BOOTSTRAP_SCOPE_MISMATCH: bootstrap handshake identity mismatch"
        }
        require(w.bootstrapSessionId.isNotBlank() && w.baselineCursor.isNotBlank() && w.firstPageToken.isNotBlank()) {
            "FAIL_BOOTSTRAP_INCOMPLETE: bootstrap identity/cursor/page token missing"
        }
        require(w.snapshotRowCount >= 0) { "FAIL_BOOTSTRAP_INCOMPLETE: negative snapshot row count" }
        require(!w.snapshotDigestSha256.isNullOrBlank() && w.coverageAggregateTypes?.size == 35 &&
            w.highWatermark != null && w.deltaToken == w.baselineCursor) {
            "FAIL_BOOTSTRAP_SEAL_MISSING: V2 server seal incomplete"
        }
        return SyncBootstrapStart(
            scope = scope, bootstrapSessionId = w.bootstrapSessionId, baselineRevision = w.baselineRevision,
            baselineCursor = w.baselineCursor, expectedSnapshotRows = w.snapshotRowCount,
            expectedSnapshotDigest = w.snapshotDigestSha256, coverageAggregateTypes = w.coverageAggregateTypes,
            highWatermark = w.highWatermark, deltaToken = w.deltaToken,
            firstPageToken = w.firstPageToken, expiresAtEpochMillis = w.expiresAtEpochMillis,
        )
    }

    override suspend fun pullPage(sessionId: String, pageToken: String, limit: Int): SyncBootstrapPage {
        require(sessionId.isNotBlank() && pageToken.isNotBlank()) { "FAIL_BOOTSTRAP_INCOMPLETE: session/page token missing" }
        require(limit in 1..1_000) { "VALIDATION: bootstrap limit must be 1..1000" }
        val w = VertoSupabase.client.postgrest.rpc(
            "verto_pull_sync_bootstrap_page_v2", UnifiedSyncBootstrapPageRequestWire(sessionId, pageToken, limit)
        ).decodeAs<UnifiedSyncBootstrapPageWire>()
        return SyncBootstrapPage(
            w.bootstrapSessionId, w.baselineCursor,
            w.rows.map { SyncBootstrapRow(it.ordinal, it.aggregateType, it.aggregateId, it.entityVersion, it.payloadVersion, it.payload, it.partitionKey, it.isTombstone) },
            w.hasMore, w.nextPageToken, w.snapshotComplete,
        )
    }

    override suspend fun reconciliationManifest(scope: SyncScope, partitionToken: String?): SyncReconciliationPage {
        require(scope.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) { "CONTRACT_BLOCKED: reconciliation requires V2 scope" }
        val w = VertoSupabase.client.postgrest.rpc(
            "verto_get_reconciliation_manifest_v2", UnifiedSyncManifestRequestWire(scope.scopeId, partitionToken)
        ).decodeAs<UnifiedSyncManifestPageWire>()
        require(w.scopeId == scope.scopeId) { "FAIL_BOOTSTRAP_SCOPE_MISMATCH: reconciliation scope mismatch" }
        return SyncReconciliationPage(
            scopeId = w.scopeId,
            manifest = w.manifest?.let { SyncReconciliationManifest(it.aggregateType, it.partitionKey, it.rowCount, it.contentHashOrVersionDigest, it.manifestRevision, it.scopeId) },
            hasMore = w.hasMore,
            nextPartitionToken = w.nextPartitionToken,
        )
    }
}
