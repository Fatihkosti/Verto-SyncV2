package com.verto.app.data.sync.pull

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.FinancialAggregateSnapshotV2
import com.verto.app.data.sync.SyncContractV2Codec
import com.verto.app.data.sync.toDtoV2
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.data.sync.pull.FinancialMaterializationContractV2.fail
import com.verto.app.data.sync.pull.FinancialMaterializationContractV2.requireContract
import com.verto.app.data.sync.pull.FinancialMaterializationContractV2.sha256
import javax.inject.Inject
import javax.inject.Singleton

/** Verifies existing facts AFTER all members of the Room group; never creates/post effects. */
@Singleton
class FinancialEffectVerifierV2 @Inject constructor(
    private val database: AppDatabase,
    private val pendingProtection: SyncPendingProtection,
) {
    internal suspend fun verify(scopeId: String, snapshot: FinancialAggregateSnapshotV2) {
        check(database.inTransaction()) { "REMOTE_APPLY_TRANSACTION_REQUIRED" }
        val dao = database.invoiceDao()
        for (ref in snapshot.effectReferences) {
            if (ref.factType == "PAYMENT") {
                val row = dao.readRemotePayment(ref.factId) ?: fail("BATCH_DEPENDENCY_MISSING", "payment fact")
                requireContract(row.invoiceId == snapshot.invoiceId && row.toDtoV2() == snapshot.payments.single { it.id == ref.factId },
                    "IMMUTABLE_FACT_CONFLICT", "payment fact content/root")
                requireContract(ref.businessIdentity == row.writeId.ifBlank { row.id } &&
                    ref.contentHash == sha256(SyncContractV2Codec.encode(row.toDtoV2())),
                    "IMMUTABLE_FACT_CONFLICT", "payment effect identity/hash")
                continue
            }
            requireContract(!pendingProtection.isProtected(snapshot.organizationId, ProtectedSyncKey(ref.factType, ref.factId)),
                "PENDING_LOCAL_MUTATION", "effect owner is not confirmed: ${ref.factType}")
            if (ref.factType == "INVENTORY_MOVEMENT") {
                val row = dao.readRemoteEffectMovement(ref.factId) ?: fail("BATCH_DEPENDENCY_MISSING", "inventory movement")
                requireContract(row.organizationId == snapshot.organizationId && row.invoiceId == snapshot.invoiceId &&
                    row.signedBaseQuantity != null && row.idempotencyKey == ref.businessIdentity,
                    "IMMUTABLE_FACT_CONFLICT", "inventory movement scope/identity")
                // This is the exact B06 factory projection; do not invent a second movement hash.
                val hash = sha256(listOf(row.id, row.itemId, row.signedBaseQuantity, row.unitPriceMinor,
                    row.sourceType, row.sourceId).joinToString("\u0000"))
                requireContract(hash == ref.contentHash, "IMMUTABLE_FACT_CONFLICT", "inventory movement effect hash")
                continue
            }
            // The remaining owners publish their own accepted business hash. B09 must not fabricate
            // that authority from a total, a local dirty flag or an unvalidated incoming JSON object.
            val authority = database.unifiedSyncDao().readEntityVersion(
                snapshot.organizationId, scopeId, ref.factType, ref.factId,
            ) ?: fail("BATCH_DEPENDENCY_MISSING", "${ref.factType} applied owner authority")
            requireContract(authority.appliedServerVersion != null && authority.appliedServerVersion!! > 0 &&
                !authority.tombstone && authority.appliedContentHash == ref.contentHash,
                "IMMUTABLE_FACT_CONFLICT", "${ref.factType} owner hash")
            val identity = when (ref.factType) {
                "INVENTORY_COST_REVISION" -> {
                    val row = dao.readRemoteEffectCost(ref.factId) ?: fail("BATCH_DEPENDENCY_MISSING", "inventory cost")
                    requireContract(row.organizationId == snapshot.organizationId, "SCOPE_MISMATCH", "cost effect")
                    row.idempotencyKey
                }
                "CASH_MOVEMENT" -> (dao.readRemoteEffectCash(ref.factId) ?: fail("BATCH_DEPENDENCY_MISSING", "cash fact")).id
                "CLIENT_CREDIT" -> {
                    val row = dao.readRemoteEffectCredit(ref.factId) ?: fail("BATCH_DEPENDENCY_MISSING", "credit fact")
                    requireContract(dao.hasRemotePartyReference(snapshot.organizationId, row.clientId, null),
                        "SCOPE_MISMATCH", "credit party")
                    row.id
                }
                "COMMISSION_PAYMENT" -> (dao.readRemoteEffectCommission(ref.factId) ?: fail("BATCH_DEPENDENCY_MISSING", "commission fact")).id
                else -> fail("CONTRACT_UNSUPPORTED", "effect owner ${ref.factType}")
            }
            requireContract(identity == ref.businessIdentity, "IMMUTABLE_FACT_CONFLICT", "${ref.factType} business identity")
        }
    }
}
