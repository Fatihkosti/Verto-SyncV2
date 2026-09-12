package com.verto.app.data.sync.pull

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncEntityVersionEntity
import com.verto.app.data.sync.*
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.data.sync.pull.FinancialMaterializationContractV2.fail
import com.verto.app.data.sync.pull.FinancialMaterializationContractV2.requireContract
import javax.inject.Inject
import javax.inject.Singleton

/** Transaction-local validation state, NOT an inbox/outbox, cursor, singleton buffer or retry queue. */
class FinancialApplyBatchV2 internal constructor(
    val organizationId: String,
    val scopeId: String,
    internal val owner: FinancialMaterializerV2,
) {
    internal var completed = false
    internal val writes = linkedMapOf<String, FinancialVersionWriteV2>()
    internal val inventoryReferenceWitnesses = hashSetOf<String>()
    internal val effectSnapshots = mutableListOf<FinancialAggregateSnapshotV2>()
}

internal data class FinancialVersionWriteV2(
    val initialScopeAuthority: SyncEntityVersionEntity?,
    val context: UnifiedRemoteMaterialization,
    val snapshot: FinancialAggregateSnapshotV2,
)

/**
 * B09 canonical financial REMOTE_APPLY. All nine domain tables are written selectively, then
 * existing owned effects and persisted content are verified. Only then is applied authority moved.
 * The caller commits all inbox members/APPLIED/checkpoint in the same outer Room transaction.
 * This class does not ACK an owner, enqueue intent, calculate stock/cash, or call legacy sync.
 */
@Singleton
class FinancialMaterializerV2 @Inject constructor(
    private val database: AppDatabase,
    private val pendingProtection: SyncPendingProtection,
    private val effectVerifier: FinancialEffectVerifierV2,
) {
    /** Safe entry point for a single complete snapshot whose external facts already exist. */
    suspend fun materialize(context: UnifiedRemoteMaterialization) = database.withTransaction {
        val batch = beginBatch(context.organizationId, context.scopeId)
        apply(context, batch)
        completeBatch(batch)
    }

    internal fun beginBatch(organizationId: String, scopeId: String): FinancialApplyBatchV2 {
        check(database.inTransaction()) { "REMOTE_APPLY_TRANSACTION_REQUIRED" }
        requireContract(organizationId.isNotBlank() && scopeId.isNotBlank(), "SCOPE_MISMATCH", "financial batch")
        return FinancialApplyBatchV2(organizationId, scopeId, this)
    }

    internal suspend fun apply(context: UnifiedRemoteMaterialization, batch: FinancialApplyBatchV2) {
        checkBatch(batch)
        requireContract(context.organizationId == batch.organizationId && context.scopeId == batch.scopeId,
            "SCOPE_MISMATCH", "financial group member")
        val snapshot = FinancialMaterializationContractV2.decode(context)
        val dao = database.invoiceDao()
        val existing = dao.readRemoteInvoice(snapshot.invoiceId)
        requireContract(existing == null || existing.organizationId == snapshot.organizationId, "SCOPE_MISMATCH", "existing invoice")
        val local = if (existing == null) null else readPersisted(snapshot)
        protect(snapshot, existing?.isDirty == true)
        validateReferences(batch, snapshot)

        val previousWrite = batch.writes[snapshot.invoiceId]
        val scopedAuthority = previousWrite?.initialScopeAuthority ?: database.unifiedSyncDao().readEntityVersion(
            snapshot.organizationId, batch.scopeId, VERSION_FAMILY, snapshot.invoiceId,
        )
        val authorities = dao.readRemoteFinancialAuthorities(snapshot.organizationId, snapshot.invoiceId)
        val applied = previousWrite?.snapshot?.financialStreamVersion ?: authorities.mapNotNull { it.appliedServerVersion }.maxOrNull()
        requireContract(authorities.none { it.tombstone }, "IMMUTABLE_FACT_CONFLICT", "financial root has a tombstoned authority")
        requireContract(applied == null || snapshot.financialStreamVersion >= applied,
            "FINANCIAL_VERSION_CONFLICT", "older financial snapshot must not overwrite a newer invoice")
        val replay = applied == snapshot.financialStreamVersion
        if (replay) {
            val hashes = previousWrite?.let { listOf(it.snapshot.businessContentHash) }
                ?: authorities.filter { it.appliedServerVersion == applied }.map { it.appliedContentHash }
            requireContract(hashes.isNotEmpty() && hashes.all { it == snapshot.businessContentHash },
                "FINANCIAL_VERSION_CONFLICT", "same version has different/unproven financial content")
        } else if (!context.isBootstrap) {
            requireContract((snapshot.expectedFinancialStreamVersion ?: 0L) == (applied ?: 0L),
                "WAITING_DEPENDENCY", "financial expected applied version")
        }
        // A dependency may be materialized after its dependent was received. The financial
        // stream version remains the domain-order fence; the scope revision watermark must
        // stay monotonic rather than rejecting that valid dependency-order drain.
        // Clean legacy rows do not magically acquire authority. Only identical projections may be
        // adopted while filling proven missing facts; differing unversioned data waits for repair.
        if (existing != null && applied == null) {
            requireContract(existing.toDtoV2() == snapshot.header &&
                local!!.items.all { row -> snapshot.items.any { it == row } } &&
                local.dueInstallments.all { row -> snapshot.dueInstallments.any { it == row } },
                "FINANCIAL_AUTHORITY_MISSING", "unversioned local financial projection differs")
        }
        if (local != null) requireNoImplicitDeletion(local, snapshot)
        validateTombstones(snapshot, applied, replay, existing == null && context.isBootstrap)

        val header = snapshot.header.toRemoteEntityV2(imageUri = existing?.imageUri.orEmpty())
        if (existing == null) dao.insertRemoteInvoice(header)
        else if (existing != header) requireContract(dao.updateRemoteInvoice(header) == 1, "LOCAL_APPLY_FAILURE", "invoice update")

        // Explicit deletions precede sequence reallocation, but referenced lines are never removed.
        snapshot.explicitTombstones.forEach { tombstone ->
            when (tombstone.entityType) {
                "INVOICE_ITEM" -> if (dao.readRemoteInvoiceItem(tombstone.id) != null) requireContract(
                    dao.deleteRemoteInvoiceItem(snapshot.organizationId, snapshot.invoiceId, tombstone.id) == 1,
                    "IMMUTABLE_FACT_CONFLICT", "tombstoned item is referenced or belongs to another invoice")
                "INVOICE_DUE_INSTALLMENT" -> if (dao.readRemoteInvoiceDueInstallment(tombstone.id) != null) requireContract(
                    dao.deleteRemoteDueInstallment(snapshot.organizationId, snapshot.invoiceId, tombstone.id) == 1,
                    "IMMUTABLE_FACT_CONFLICT", "due tombstone identity")
            }
        }
        snapshot.items.forEach { dto ->
            val old = dao.readRemoteInvoiceItem(dto.id)
            requireContract(old == null || old.invoiceId == snapshot.invoiceId, "SCOPE_MISMATCH", "invoice item root")
            val row = dto.toRemoteEntityV2()
            if (old == null) dao.insertRemoteInvoiceItem(row)
            else if (old != row) requireContract(dao.updateRemoteInvoiceItem(row) == 1, "LOCAL_APPLY_FAILURE", "item update")
        }
        val oldDues = dao.getDueInstallments(snapshot.invoiceId).associateBy { it.id }
        val occupied = (oldDues.values.map { it.sequence } + snapshot.dueInstallments.map { it.sequence }).toMutableSet()
        var temporary = Int.MIN_VALUE
        snapshot.dueInstallments.forEach { dto ->
            val old = dao.readRemoteInvoiceDueInstallment(dto.id)
            requireContract(old == null || old.invoiceId == snapshot.invoiceId, "SCOPE_MISMATCH", "installment root")
            if (old != null && old.sequence != dto.sequence) {
                while (temporary in occupied) temporary = Math.incrementExact(temporary)
                requireContract(dao.stageRemoteDueSequence(snapshot.invoiceId, dto.id, temporary) == 1,
                    "LOCAL_APPLY_FAILURE", "temporary installment slot")
                occupied += temporary
            }
        }
        snapshot.dueInstallments.forEach { dto ->
            val old = dao.readRemoteInvoiceDueInstallment(dto.id)
            val row = dto.toRemoteEntityV2()
            if (old == null) dao.insertRemoteInvoiceDueInstallment(row)
            else if (old != row) requireContract(dao.updateRemoteInvoiceDueInstallment(row) == 1, "LOCAL_APPLY_FAILURE", "installment update")
        }
        FinancialMaterializationContractV2.paymentsInDependencyOrder(snapshot.payments).forEach { dto ->
            val old = dao.readRemotePayment(dto.id)
            if (old == null) dao.insertRemotePayment(dto.toRemoteEntityV2())
            else requireContract(old.toDtoV2() == dto, "IMMUTABLE_FACT_CONFLICT", "payment ${dto.id}")
        }
        snapshot.paymentAllocations.forEach { dto ->
            val old = dao.readRemotePaymentAllocation(dto.id)
            if (old == null) dao.insertRemotePaymentAllocation(dto.toRemoteEntityV2())
            else requireContract(old.toDtoV2() == dto, "IMMUTABLE_FACT_CONFLICT", "payment allocation ${dto.id}")
        }
        snapshot.realizedFxEvents.forEach { dto ->
            val old = dao.readRemoteRealizedFxEvent(dto.id)
            if (old == null) dao.insertRemoteRealizedFxEvent(dto.toRemoteEntityV2())
            else requireContract(old.toDtoV2() == dto, "IMMUTABLE_FACT_CONFLICT", "FX ${dto.id}")
        }
        snapshot.returnDocuments.forEach { dto ->
            val old = dao.readRemoteInvoiceReturnDocument(dto.id)
            if (old == null) dao.insertRemoteInvoiceReturnDocument(dto.toRemoteEntityV2())
            else requireContract(old.toDtoV2() == dto, "IMMUTABLE_FACT_CONFLICT", "return document ${dto.id}")
        }
        snapshot.returnLines.forEach { dto ->
            val old = dao.readRemoteInvoiceReturnLine(dto.id)
            if (old == null) dao.insertRemoteInvoiceReturnLine(dto.toRemoteEntityV2())
            else requireContract(old.toDtoV2() == dto, "IMMUTABLE_FACT_CONFLICT", "return line ${dto.id}")
        }
        snapshot.returnPaymentAllocations.forEach { dto ->
            val old = dao.readRemoteInvoiceReturnPaymentAllocation(dto.id)
            if (old == null) dao.insertRemoteInvoiceReturnPaymentAllocation(dto.toRemoteEntityV2())
            else requireContract(old.toDtoV2() == dto, "IMMUTABLE_FACT_CONFLICT", "return payment allocation ${dto.id}")
        }
        assertPersisted(snapshot)
        batch.effectSnapshots += snapshot
        batch.writes[snapshot.invoiceId] = FinancialVersionWriteV2(scopedAuthority, context, snapshot)
    }

    internal suspend fun completeBatch(batch: FinancialApplyBatchV2) {
        checkBatch(batch)
        for (snapshot in batch.effectSnapshots) effectVerifier.verify(batch.scopeId, snapshot)
        val versions = database.unifiedSyncDao()
        for ((invoiceId, write) in batch.writes) {
            assertPersisted(write.snapshot)
            val current = versions.readEntityVersion(batch.organizationId, batch.scopeId, VERSION_FAMILY, invoiceId)
            requireContract(current == write.initialScopeAuthority, "FINANCIAL_VERSION_CONFLICT", "authority changed during group")
            versions.recordAppliedVersion(
                organizationId = batch.organizationId, scopeId = batch.scopeId, versionFamily = VERSION_FAMILY,
                aggregateId = invoiceId, appliedVersion = write.snapshot.financialStreamVersion,
                appliedRevision = maxOf(
                    write.initialScopeAuthority?.lastAppliedRevision ?: write.context.revision,
                    write.context.revision,
                ),
                contentHash = write.snapshot.businessContentHash,
                tombstone = false, updatedAt = System.currentTimeMillis(),
            )
        }
        batch.completed = true
    }

    private suspend fun protect(snapshot: FinancialAggregateSnapshotV2, dirtyHeader: Boolean) {
        val org = snapshot.organizationId
        val root = snapshot.invoiceId
        requireContract(!dirtyHeader && !pendingProtection.isProtected(org, ProtectedSyncKey("INVOICE", root)),
            "PENDING_LOCAL_MUTATION", "financial root")
        val keys = buildList {
            snapshot.items.forEach { add(ProtectedSyncKey("INVOICE_ITEM", it.id)) }
            snapshot.dueInstallments.forEach { add(ProtectedSyncKey("INVOICE_DUE_INSTALLMENT", it.id)) }
            snapshot.payments.forEach { add(ProtectedSyncKey("PAYMENT", it.id)) }
            snapshot.paymentAllocations.forEach { add(ProtectedSyncKey("PAYMENT_ALLOCATION", it.id)) }
            snapshot.realizedFxEvents.forEach { add(ProtectedSyncKey("REALIZED_FX_EVENT", it.id)) }
            snapshot.returnDocuments.forEach { add(ProtectedSyncKey("INVOICE_RETURN", it.id)) }
            snapshot.returnLines.forEach { add(ProtectedSyncKey("INVOICE_RETURN_LINE", it.id)) }
            snapshot.returnPaymentAllocations.forEach { add(ProtectedSyncKey("INVOICE_RETURN_PAYMENT_ALLOCATION", it.id)) }
            snapshot.explicitTombstones.forEach { add(ProtectedSyncKey(it.entityType, it.id)) }
        }
        keys.forEach { key -> requireContract(!pendingProtection.isProtected(org, key, financialRootId = root),
            "PENDING_LOCAL_MUTATION", "financial child ${key.type}") }
        val dao = database.invoiceDao()
        requireContract(dao.getInvoiceItemsSync(root).none { it.isDirty } &&
            database.paymentDao().getPaymentsForInvoiceSync(root).none { it.isDirty },
            "PENDING_LOCAL_MUTATION", "dirty financial child")
    }

    private suspend fun validateReferences(batch: FinancialApplyBatchV2, s: FinancialAggregateSnapshotV2) {
        val dao = database.invoiceDao()
        val role = if (s.header.category == "SALE") "CUSTOMER" else "SUPPLIER"
        requireContract(dao.hasRemotePartyReference(s.organizationId, s.header.clientId, role),
            "WAITING_DEPENDENCY", "proven invoice party/role; no cash or named placeholder is created")
        val parties = s.payments.map { it.clientId } + listOfNotNull(s.header.commissionBeneficiaryClientId)
        parties.distinct().forEach { requireContract(dao.hasRemotePartyReference(s.organizationId, it, null),
            "WAITING_DEPENDENCY", "proven financial party") }
        // Empty inventoryItemId is the existing manual/unlinked-line representation. Preserve it;
        // nonempty identifiers require positive tenant proof, never a lookup/insert by itemName.
        (s.items.map { it.inventoryItemId } + s.returnLines.map { it.inventoryItemId }).filter { it.isNotEmpty() }.distinct().forEach {
            requireContract(it.isNotBlank() && (dao.hasRemoteInventoryReference(s.organizationId, batch.scopeId, it) ||
                (it in batch.inventoryReferenceWitnesses && database.inventoryDao().getItemByIdSync(it) != null)),
                "WAITING_DEPENDENCY", "proven inventory reference")
        }
        s.header.purchaseOrderId?.let { requireContract(it.isNotBlank() && dao.hasRemotePurchaseOrderReference(s.organizationId, it),
            "WAITING_DEPENDENCY", "purchase order reference") }
        s.header.shipmentId?.let { requireContract(it.isNotBlank() && dao.hasRemoteShipmentReference(s.organizationId, it),
            "WAITING_DEPENDENCY", "shipment reference") }
    }

    private suspend fun validateTombstones(s: FinancialAggregateSnapshotV2, applied: Long?, replay: Boolean, freshBootstrap: Boolean) {
        val dao = database.invoiceDao()
        for (tombstone in s.explicitTombstones) {
            requireContract(replay || freshBootstrap || tombstone.previousVersion == applied,
                "FINANCIAL_VERSION_CONFLICT", "tombstone previous aggregate version")
            val root = dao.readRemoteFinancialRoot(tombstone.entityType, tombstone.id)
            requireContract(root == null || root == s.invoiceId, "SCOPE_MISMATCH", "tombstone child owner")
            requireContract(root != null || replay || freshBootstrap, "SOURCE_DATA_MISSING", "unproven tombstone target")
            if (tombstone.entityType == "INVOICE_ITEM") requireContract(!dao.remoteInvoiceItemHasProtectedReference(tombstone.id),
                "IMMUTABLE_FACT_CONFLICT", "return/purchase-match references tombstoned invoice item")
        }
    }

    private fun requireNoImplicitDeletion(local: FinancialAggregateSnapshotV2, incoming: FinancialAggregateSnapshotV2) {
        fun allPresent(old: List<String>, current: List<String>, type: String) {
            val tombstones = incoming.explicitTombstones.filter { it.entityType == type }.map { it.id }.toSet()
            requireContract(old.all { it in current || it in tombstones }, "FINANCIAL_FULL_SNAPSHOT_OMISSION",
                "$type omitted without a legal explicit tombstone; local rows were preserved")
        }
        allPresent(local.items.map { it.id }, incoming.items.map { it.id }, "INVOICE_ITEM")
        allPresent(local.dueInstallments.map { it.id }, incoming.dueInstallments.map { it.id }, "INVOICE_DUE_INSTALLMENT")
        allPresent(local.payments.map { it.id }, incoming.payments.map { it.id }, "PAYMENT")
        allPresent(local.paymentAllocations.map { it.id }, incoming.paymentAllocations.map { it.id }, "PAYMENT_ALLOCATION")
        allPresent(local.realizedFxEvents.map { it.id }, incoming.realizedFxEvents.map { it.id }, "REALIZED_FX_EVENT")
        allPresent(local.returnDocuments.map { it.id }, incoming.returnDocuments.map { it.id }, "INVOICE_RETURN")
        allPresent(local.returnLines.map { it.id }, incoming.returnLines.map { it.id }, "INVOICE_RETURN_LINE")
        allPresent(local.returnPaymentAllocations.map { it.id }, incoming.returnPaymentAllocations.map { it.id }, "INVOICE_RETURN_PAYMENT_ALLOCATION")
    }

    internal suspend fun readPersisted(template: FinancialAggregateSnapshotV2): FinancialAggregateSnapshotV2 {
        val invoiceId = template.invoiceId
        val returns = database.invoiceReturnDao().getForInvoice(invoiceId).sortedBy { it.id }
        return template.copy(
            header = (database.invoiceDao().readRemoteInvoice(invoiceId) ?: fail("LOCAL_APPLY_FAILURE", "missing invoice")).toDtoV2(),
            items = database.invoiceDao().getInvoiceItemsSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            dueInstallments = database.invoiceDao().getDueInstallments(invoiceId).sortedWith(compareBy<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity> { it.sequence }.thenBy { it.id }).map { it.toDtoV2() },
            payments = database.paymentDao().getPaymentsForInvoiceSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            paymentAllocations = database.paymentDao().getPaymentAllocationsForInvoiceSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            realizedFxEvents = database.paymentDao().getRealizedFxEventsForInvoiceSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            returnDocuments = returns.map { it.toDtoV2() },
            returnLines = returns.flatMap { database.invoiceReturnDao().getLines(it.id) }.sortedBy { it.id }.map { it.toDtoV2() },
            returnPaymentAllocations = returns.flatMap { database.invoiceReturnDao().getPaymentAllocations(it.id) }.sortedBy { it.id }.map { it.toDtoV2() },
        )
    }

    private suspend fun assertPersisted(snapshot: FinancialAggregateSnapshotV2) {
        val persisted = readPersisted(snapshot)
        requireContract(persisted == snapshot && SyncContractV2Codec.financialBusinessHash(persisted) == snapshot.businessContentHash &&
            FinancialMaterializationContractV2.minorTotals(persisted) == FinancialMaterializationContractV2.minorTotals(snapshot),
            "LOCAL_APPLY_FAILURE", "financial field/hash/Minor parity")
    }

    private fun checkBatch(batch: FinancialApplyBatchV2) {
        check(database.inTransaction() && batch.owner === this && !batch.completed) { "REMOTE_APPLY_TRANSACTION_REQUIRED" }
    }

    companion object { const val VERSION_FAMILY = "FINANCIAL_INVOICE" }
}
