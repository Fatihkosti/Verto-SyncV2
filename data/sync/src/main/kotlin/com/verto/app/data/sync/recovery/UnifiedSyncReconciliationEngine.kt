package com.verto.app.data.sync.recovery

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncBootstrapStageEntity
import com.verto.app.data.sync.SyncScope
import com.verto.app.data.sync.UnifiedSyncBootstrapRemote
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Session 313 anti-entropy only. It never mutates the global revision cursor. */
enum class ReconciliationOutcome { CONVERGED, DEFERRED_PENDING_LOCAL_MUTATIONS, RECOVERY_REQUIRED }

@Singleton
class UnifiedSyncReconciliationEngine @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncBootstrapRemote,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }

    suspend fun reconcile(scope: SyncScope): ReconciliationOutcome {
        val dao = database.syncRecoveryDao()
        val recovery = dao.getRecoveryState(scope.scopeId)
        if (recovery == null || recovery.state != UnifiedSyncRecoveryEngine.STATE_READY || recovery.bootstrapSessionId.isNullOrBlank()) {
            mark(scope.scopeId, "RECOVERY_REQUIRED")
            return ReconciliationOutcome.RECOVERY_REQUIRED
        }
        val bootstrapSessionId = recovery.bootstrapSessionId!!
        if (hasPendingLocal(scope.organizationId)) {
            mark(scope.scopeId, "DEFERRED_PENDING_LOCAL_MUTATIONS")
            return ReconciliationOutcome.DEFERRED_PENDING_LOCAL_MUTATIONS
        }

        val stage = dao.listStage(scope.scopeId, bootstrapSessionId)
        val local = stage.groupBy { it.aggregateType to it.partitionKey }
        val seen = linkedSetOf<Pair<String, String>>()
        var token: String? = null
        var pages = 0
        do {
            val page = remote.reconciliationManifest(scope, token)
            if (page.scopeId != scope.scopeId) return mismatch(scope, "FAIL_BOOTSTRAP_SCOPE_MISMATCH")
            val manifest = page.manifest
            if (manifest != null) {
                if (manifest.scopeId != scope.scopeId || manifest.aggregateType !in UnifiedSyncRecoveryRegistry.byId) {
                    return mismatch(scope, "FAIL_RECONCILIATION_MANIFEST")
                }
                val key = manifest.aggregateType to manifest.partitionKey
                seen += key
                val rows = local[key].orEmpty()
                if (rows.size.toLong() != manifest.rowCount || localDigest(rows) != manifest.contentHashOrVersionDigest.lowercase()) {
                    return mismatch(scope, "RECONCILIATION_MISMATCH")
                }
            }
            token = page.nextPartitionToken
            pages++
            if (pages > MAX_MANIFEST_PAGES) return mismatch(scope, "FAIL_RECONCILIATION_MANIFEST")
        } while (token != null)

        // A local server-base partition that the server no longer exposes is also divergence.
        if ((local.keys - seen).isNotEmpty()) return mismatch(scope, "RECONCILIATION_MISMATCH")
        mark(scope.scopeId, "CONVERGED")
        return ReconciliationOutcome.CONVERGED
    }

    private suspend fun hasPendingLocal(organizationId: String): Boolean {
        val d = database.syncRecoveryDao()
        return d.unifiedOutboxDepth(organizationId) + d.partyOutboxDepth(organizationId) + d.financialOutboxDepth(organizationId) +
            d.inventoryStockOutboxDepth(organizationId) + d.inventoryCostOutboxDepth(organizationId) + d.optimalOutboxDepth(organizationId) +
            d.attachmentOutboxDepth(organizationId) > 0L
    }

    private suspend fun mismatch(scope: SyncScope, code: String): ReconciliationOutcome {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val d = database.syncRecoveryDao()
            val old = d.getRecoveryState(scope.scopeId)
            if (old != null) d.upsertRecoveryState(
                old.copy(state = UnifiedSyncRecoveryEngine.STATE_RECOVERY_REQUIRED, reason = RecoveryReason.RECONCILIATION_MISMATCH.name,
                    lastErrorCode = normalize(code), updatedAt = now, completedAt = null)
            )
            d.markReconciliation(scope.scopeId, "RECOVERY_REQUIRED", now)
        }
        return ReconciliationOutcome.RECOVERY_REQUIRED
    }

    private suspend fun mark(scopeId: String, status: String) {
        database.syncRecoveryDao().markReconciliation(scopeId, status, System.currentTimeMillis())
    }

    /** Mirrors v305 concat_ws + jsonb::text + string_agg ORDER BY aggregate_id. */
    internal fun localDigest(rows: List<SyncBootstrapStageEntity>): String {
        val body = rows.sortedBy { it.aggregateId }.joinToString("\n") { row ->
            val payload = json.parseToJsonElement(row.payloadJson)
            listOf(row.aggregateId, row.entityVersion?.toString() ?: "null", row.payloadVersion.toString(), postgresJsonbText(payload)).joinToString("|")
        }
        return sha256(body)
    }

    private fun postgresJsonbText(e: JsonElement): String = when (e) {
        is JsonObject -> e.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}", separator = ", ") { (k, v) ->
            "${JsonPrimitive(k)}: ${postgresJsonbText(v)}"
        }
        is JsonArray -> e.joinToString(prefix = "[", postfix = "]", separator = ", ") { postgresJsonbText(it) }
        is JsonNull -> "null"
        is JsonPrimitive -> e.toString()
        else -> e.toString()
    }

    private fun sha256(v: String): String = MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun normalize(v: String): String = v.uppercase().replace(Regex("[^A-Z0-9_]+"), "_").take(80)

    companion object { const val MAX_MANIFEST_PAGES = 512 }
}
