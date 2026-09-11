package com.verto.app.data.sync.recovery

import com.verto.app.data.local.entity.SyncBootstrapStageEntity
import com.verto.app.data.local.entity.SyncRecoveryStateEntity
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

/** B13 server/client snapshot-seal contract. Missing or divergent seal fields fail closed. */
object BootstrapSealPolicy {
    private val json = Json { ignoreUnknownKeys = false }
    private val requiredCoverage = UnifiedSyncAggregateRegistry.byId.keys.sorted()
    private val sha256 = Regex("^[0-9a-f]{64}$")

    fun verify(state: SyncRecoveryStateEntity, rows: List<SyncBootstrapStageEntity>) {
        val expectedRows = state.expectedSnapshotRows
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "expected snapshot row count missing")
        if (rows.size.toLong() != expectedRows) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "staged=${rows.size} expected=$expectedRows")
        }
        val expectedDigest = state.expectedSnapshotDigest?.lowercase()
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_MISSING", "snapshot digest missing")
        if (!sha256.matches(expectedDigest)) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_INVALID", "snapshot digest is not sha256")
        }
        val coverageRaw = state.expectedCoverageJson
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_MISSING", "coverage aggregate types missing")
        val coverage = runCatching {
            (json.parseToJsonElement(coverageRaw) as JsonArray).map { it.jsonPrimitive.content }.sorted()
        }.getOrElse {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_INVALID", "coverage aggregate types malformed", it)
        }
        if (coverage != requiredCoverage) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE", "coverage is not exactly 35/35")
        }
        val highWatermark = state.bootstrapHighWatermark
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_MISSING", "high watermark missing")
        if (highWatermark <= 0L || (state.baselineRevision != null && state.baselineRevision != highWatermark)) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_INVALID", "high watermark/baseline mismatch")
        }
        val deltaToken = state.bootstrapDeltaToken
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_MISSING", "delta token missing")
        if (deltaToken.isBlank() || deltaToken != state.baselineCursor) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_INVALID", "delta token/baseline cursor mismatch")
        }
        val actualDigest = snapshotDigest(rows)
        if (actualDigest != expectedDigest) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SEAL_DIGEST", "staged snapshot digest mismatch")
        }
    }

    /** Contract digest: stable ordinal + the canonical row fingerprint + tombstone bit. */
    fun snapshotDigest(rows: List<SyncBootstrapStageEntity>): String = sha256(
        rows.sortedBy { it.ordinal }.joinToString("\n") {
            "${it.ordinal}|${it.contentFingerprint}|${if (it.isTombstone) 1 else 0}"
        }
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
