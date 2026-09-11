package com.verto.app.data.sync.rollout

/**
 * Read-only boundary for Wave 2. Implementations may read authoritative Legacy/local and V2 server
 * projections, but this runner intentionally exposes no mutation/cursor/outbox capability.
 */
data class UnifiedSyncShadowDigest(
    val aggregateType: String,
    val scopeId: String,
    val serverRevisionRange: String,
    val rowCount: Int,
    val canonicalDigest: String,
)

interface UnifiedSyncShadowReadSource {
    suspend fun readAuthoritativeDigest(aggregateType: String, scopeId: String): UnifiedSyncShadowDigest
    suspend fun readV2Digest(aggregateType: String, scopeId: String): UnifiedSyncShadowDigest
}

class UnifiedSyncShadowPullRunner(
    private val source: UnifiedSyncShadowReadSource,
) {
    suspend fun compare(aggregateType: String, scopeId: String): UnifiedSyncShadowComparison {
        val authoritative = source.readAuthoritativeDigest(aggregateType, scopeId)
        val shadow = source.readV2Digest(aggregateType, scopeId)
        require(authoritative.aggregateType == shadow.aggregateType && authoritative.aggregateType == aggregateType) {
            "SCOPE_MISMATCH: shadow aggregate mismatch"
        }
        require(authoritative.scopeId == shadow.scopeId && authoritative.scopeId == scopeId) {
            "SCOPE_MISMATCH: shadow scope mismatch"
        }
        return UnifiedSyncShadowComparator.compareDigests(
            aggregateType = aggregateType,
            scopeId = scopeId,
            serverRevisionRange = shadow.serverRevisionRange,
            authoritativeRowCount = authoritative.rowCount,
            authoritativeDigest = authoritative.canonicalDigest,
            shadowRowCount = shadow.rowCount,
            shadowDigest = shadow.canonicalDigest,
        )
    }
}
