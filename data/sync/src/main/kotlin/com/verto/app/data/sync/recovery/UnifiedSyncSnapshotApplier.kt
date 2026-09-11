package com.verto.app.data.sync.recovery

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncBootstrapStageEntity
import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.SyncScope
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncDeletePolicy
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.data.sync.pull.SyncSnapshotMaterialization
import com.verto.app.data.sync.pull.UnifiedSyncChangeApplier
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** B13 snapshot promotion boundary. Caller owns the one Room cutover transaction. */
@Singleton
class UnifiedSyncSnapshotApplier @Inject constructor(
    private val database: AppDatabase,
    private val domainApplier: UnifiedSyncChangeApplier,
    private val pendingProtection: SyncPendingProtection,
) {
    private val json = Json { ignoreUnknownKeys = false }

    suspend fun materializeAndPrune(scope: SyncScope, sessionId: String, baselineRevision: Long?) {
        check(database.inTransaction()) { "REMOTE_APPLY_TRANSACTION_REQUIRED" }
        val revision = requireNotNull(baselineRevision?.takeIf { it > 0L }) {
            "BOOTSTRAP_BASELINE_REVISION_MISSING"
        }
        val financialBatch = domainApplier.beginFinancialBatch(scope.organizationId, scope.scopeId)
        val recoveryDao = database.syncRecoveryDao()
        val rows = recoveryDao.listStage(scope.scopeId, sessionId)
        val ruleOrder = UnifiedSyncRecoveryRegistry.all.withIndex().associate { it.value.aggregateType to it.index }
        val ordered = rows.sortedWith(compareBy<SyncBootstrapStageEntity>({ ruleOrder[it.aggregateType] ?: Int.MAX_VALUE }, { it.ordinal }))
        val db = database.openHelper.writableDatabase
        val before = OutboxIdentityDigest.compute(db, scope.organizationId)

        for (row in ordered) {
            val contract = UnifiedSyncAggregateRegistry.requireById(row.aggregateType)
            require(row.payloadVersion == contract.payloadVersion) { "FAIL_BOOTSTRAP_PAYLOAD_VERSION: ${row.aggregateType}" }

            // This is deliberately repeated inside the promotion transaction. A write committed after
            // the staging inventory but before promotion is therefore still protected.
            if (shouldPreservePending(scope, row)) {
                check(recoveryDao.markStagePromotion(
                    scope.scopeId, sessionId, row.ordinal, STATE_WAITING_LOCAL, "PENDING_LOCAL_MUTATION", null,
                ) == 1) { "FAIL_RECOVERY_STAGE_STATE" }
                continue
            }

            if (row.isTombstone && contract.deletePolicy !in setOf(
                    UnifiedSyncDeletePolicy.VERSIONED_DELETE, UnifiedSyncDeletePolicy.TOMBSTONE,
                )
            ) {
                throw UnifiedSyncRecoveryFailure(
                    "FAIL_BOOTSTRAP_TOMBSTONE_POLICY", "${row.aggregateType} does not permit bootstrap tombstone",
                )
            }
            if (row.isTombstone && row.entityVersion == null) {
                throw UnifiedSyncRecoveryFailure(
                    "FAIL_BOOTSTRAP_TOMBSTONE_VERSION_MISSING", "${row.aggregateType}/${row.aggregateId}",
                )
            }

            val payload = json.parseToJsonElement(row.payloadJson).jsonObject
            domainApplier.applySnapshot(
                SyncSnapshotMaterialization(
                    scopeId = scope.scopeId,
                    organizationId = scope.organizationId,
                    aggregateType = row.aggregateType,
                    aggregateId = row.aggregateId,
                    entityVersion = row.entityVersion,
                    payloadVersion = row.payloadVersion,
                    payload = payload,
                    revision = revision,
                    operationType = if (row.isTombstone) SyncMutationOperation.DELETE else SyncMutationOperation.UPSERT,
                    deletedAtEpochMillis = if (row.isTombstone) System.currentTimeMillis() else null,
                ), financialBatch,
            )

            // Financial FULL materializers own their entity-version writes. Every other server-versioned
            // row records applied authority only after the domain apply succeeded, in this same tx.
            val appliedEntityVersion = row.entityVersion
            if (row.aggregateType !in FINANCIAL_TYPES && appliedEntityVersion != null) {
                database.unifiedSyncDao().recordAppliedVersion(
                    organizationId = scope.organizationId,
                    scopeId = scope.scopeId,
                    versionFamily = row.aggregateType,
                    aggregateId = row.aggregateId,
                    appliedVersion = appliedEntityVersion,
                    appliedRevision = revision,
                    contentHash = sha256Utf8(row.payloadJson),
                    tombstone = row.isTombstone,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            check(recoveryDao.markStagePromotion(
                scope.scopeId, sessionId, row.ordinal, STATE_APPLIED, null, System.currentTimeMillis(),
            ) == 1) { "FAIL_RECOVERY_STAGE_STATE" }
        }
        domainApplier.completeFinancialBatch(financialBatch)

        // No absence-pruning in B13. Financial/inventory rows are never deleted merely because a
        // sealed snapshot omits them; explicit scoped tombstones are the only hard-delete input.
        val after = OutboxIdentityDigest.compute(db, scope.organizationId)
        check(before == after) { "FAIL_RECOVERY_OUTBOX_LOSS: pending outbox identity digest changed during cutover" }
    }

    private suspend fun shouldPreservePending(scope: SyncScope, row: SyncBootstrapStageEntity): Boolean =
        pendingProtection.isProtected(scope.organizationId, ProtectedSyncKey(row.aggregateType, row.aggregateId))

    private fun sha256Utf8(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        val FINANCIAL_TYPES = setOf("INVOICE", "PAYMENT")
        const val STATE_WAITING_LOCAL = "WAITING_LOCAL"
        const val STATE_APPLIED = "APPLIED"
    }
}
