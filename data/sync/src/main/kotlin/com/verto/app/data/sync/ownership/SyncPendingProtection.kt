package com.verto.app.data.sync.ownership

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.SyncRepairV2Dao
import com.verto.app.data.local.entity.SyncPendingReferenceEntity
import javax.inject.Inject
import javax.inject.Singleton

data class ProtectedSyncKey(val type: String, val id: String) {
    init {
        require(type.isNotBlank()) { "protected type is required" }
        require(id.isNotBlank()) { "protected id is required" }
    }
}

data class PendingSourceRef(
    val organizationId: String,
    val owner: SyncSourceOwner,
    val sourceId: String,
)

/**
 * The only runtime answer to "may remote state replace this local row?".
 * A missing/unknown source behind an extant reference fails closed and remains protected.
 */
@Singleton
class SyncPendingProtection @Inject constructor(
    private val database: AppDatabase,
) {
    suspend fun capture(
        source: PendingSourceRef,
        protectedKeys: Collection<ProtectedSyncKey>,
        capturedGeneration: Long,
        capturedContentHash: String,
        dependencyKind: String,
    ) {
        require(database.inTransaction()) { "PENDING_REFERENCE_TRANSACTION_REQUIRED" }
        validateSource(source)
        require(source.owner != SyncSourceOwner.SERVER_ONLY) { "server-only data cannot own a local reference" }
        require(protectedKeys.isNotEmpty()) { "at least one protected key is required" }
        require(capturedGeneration >= 0L)
        requireSha256(capturedContentHash)
        require(dependencyKind.isNotBlank())

        val state = readState(database.unifiedSyncDao(), source)
            ?: throw IllegalStateException("BLOCKED_OWNER_UNPROVEN: ${source.owner.tableName}/${source.sourceId}")
        check(source.owner.isPending(state)) { "TERMINAL_SOURCE_CANNOT_CAPTURE_PENDING_REFERENCE" }
        val dao = database.unifiedSyncDao()
        val rows = protectedKeys.distinct().map { key ->
            SyncPendingReferenceEntity(
                organizationId = source.organizationId,
                sourceOwner = source.owner.tableName,
                sourceId = source.sourceId,
                protectedType = key.type,
                protectedId = key.id,
                capturedGeneration = capturedGeneration,
                capturedContentHash = capturedContentHash,
                dependencyKind = dependencyKind,
            )
        }
        dao.insertPendingReferences(rows)
        rows.forEach { expected ->
            val stored = dao.listPendingReferences(
                expected.organizationId,
                expected.protectedType,
                expected.protectedId,
            ).singleOrNull {
                it.sourceOwner == expected.sourceOwner && it.sourceId == expected.sourceId
            } ?: error("PENDING_REFERENCE_INSERT_FAILED")
            check(stored == expected) { "PENDING_REFERENCE_CONTENT_MISMATCH" }
        }
    }

    /** Cleanup is legal only after the real owner row proves a terminal state. */
    suspend fun releaseAfterTerminal(source: PendingSourceRef): Int {
        require(database.inTransaction()) { "PENDING_REFERENCE_TRANSACTION_REQUIRED" }
        validateSource(source)
        val state = readState(database.unifiedSyncDao(), source)
            ?: throw IllegalStateException("ORPHAN_PENDING_REFERENCE: ${source.owner.tableName}/${source.sourceId}")
        check(!source.owner.isPending(state)) { "PENDING_SOURCE_REFERENCE_CANNOT_BE_RELEASED" }
        val dao = database.unifiedSyncDao()
        val removed = dao.deleteSourcePendingReferences(source.organizationId, source.owner.tableName, source.sourceId)
        // Persist before the Room invalidation observer requests WorkManager wake-up after commit.
        dao.requestInboxApplyForOrganization(source.organizationId, System.currentTimeMillis())
        return removed
    }

    suspend fun isProtected(
        organizationId: String,
        key: ProtectedSyncKey,
        financialRootId: String? = null,
        projectionRoot: ProtectedSyncKey? = null,
    ): Boolean {
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        val dao = database.unifiedSyncDao()
        val references = dao.listPendingReferences(organizationId, key.type, key.id)
        for (reference in references) {
            val owner = runCatching { SyncSourceOwner.fromTable(reference.sourceOwner) }.getOrNull()
                ?: return true
            val state = readState(dao, PendingSourceRef(organizationId, owner, reference.sourceId))
            if (state == null || owner.isPending(state)) return true
        }

        // Explicit child DTOs have their own captured references AND a known projection owner.
        // An unrecognized key still fails closed rather than acquiring an invented default owner.
        if (projectionRoot != null) {
            check(PROJECTION_PARENT_TYPES[key.type] == projectionRoot.type) { "BLOCKED_OWNER_UNPROVEN: child projection root" }
            return isProtected(organizationId, projectionRoot) ||
                dao.hasUnconfirmedAttachmentAggregate(organizationId, key.type, key.id)
        }

        // Financial children share their existing invoice owner; they are not new registry owners.
        // A supplied root comes from the validated FULL DTO and is checked against any stored child.
        if (key.type in FINANCIAL_CHILD_TYPES) {
            val storedRoot = database.invoiceDao().readRemoteFinancialRoot(key.type, key.id)
            if (storedRoot != null && financialRootId != null && storedRoot != financialRootId) return true
            val root = financialRootId ?: storedRoot ?: if (key.type == "PAYMENT") key.id else return true
            return isProtected(organizationId, ProtectedSyncKey("INVOICE", root)) ||
                dao.hasUnconfirmedAttachmentAggregate(organizationId, key.type, key.id)
        }

        // Compatibility protection for pre-B04 rows which have not acquired content references yet.
        // New producers must call capture(); this fallback never guesses an owner for an unknown type.
        return hasLegacyOwnerProtection(dao, organizationId, key)
    }

    suspend fun findOrphans(organizationId: String, source: PendingSourceRef): Boolean {
        require(source.organizationId == organizationId)
        val refs = databaseReferenceList(source)
        return refs.isNotEmpty() && readState(database.unifiedSyncDao(), source) == null
    }

    suspend fun hasUnconfirmedWork(organizationId: String): Boolean {
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        val dao = database.unifiedSyncDao()
        return dao.countUnconfirmedUnified(organizationId) +
            dao.countUnconfirmedFinancial(organizationId) +
            dao.countUnconfirmedParty(organizationId) +
            dao.countUnconfirmedInventoryStock(organizationId) +
            dao.countUnconfirmedInventoryCost(organizationId) +
            dao.countUnconfirmedOptimal(organizationId) +
            dao.countUnconfirmedAttachment(organizationId) > 0L
    }

    private suspend fun databaseReferenceList(source: PendingSourceRef) =
        database.unifiedSyncDao().listSourcePendingReferences(
            source.organizationId,
            source.owner.tableName,
            source.sourceId,
        )

    private suspend fun hasLegacyOwnerProtection(
        dao: SyncRepairV2Dao,
        organizationId: String,
        key: ProtectedSyncKey,
    ): Boolean {
        val ownership = SyncOwnershipRegistry.byAggregateType[key.type]
            ?: throw UnknownSyncOwnerException(key.type)
        return when (ownership.sourceOwner) {
            SyncSourceOwner.UNIFIED -> dao.hasUnconfirmedUnifiedAggregate(organizationId, key.type, key.id)
            SyncSourceOwner.FINANCIAL -> dao.hasUnconfirmedFinancialAggregate(organizationId, key.id)
            SyncSourceOwner.PARTY_ROLE -> dao.hasUnconfirmedPartyRoleAggregate(organizationId, key.id.substringBefore(':'))
            SyncSourceOwner.INVENTORY_STOCK -> dao.hasUnconfirmedInventoryStockAggregate(organizationId, key.id)
            SyncSourceOwner.INVENTORY_COST -> dao.hasUnconfirmedInventoryCostAggregate(organizationId, key.id)
            SyncSourceOwner.OPTIMAL -> dao.hasUnconfirmedOptimalAggregate(organizationId, key.id)
            SyncSourceOwner.ATTACHMENT -> dao.hasUnconfirmedAttachmentAggregate(organizationId, key.type, key.id)
            SyncSourceOwner.SERVER_ONLY -> false
        } || dao.hasUnconfirmedAttachmentAggregate(organizationId, key.type, key.id)
    }

    private suspend fun readState(dao: SyncRepairV2Dao, source: PendingSourceRef): String? = when (source.owner) {
        SyncSourceOwner.UNIFIED -> dao.readUnifiedSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.FINANCIAL -> dao.readFinancialSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.PARTY_ROLE -> dao.readPartyRoleSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.INVENTORY_STOCK -> dao.readInventoryStockSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.INVENTORY_COST -> dao.readInventoryCostSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.OPTIMAL -> dao.readOptimalSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.ATTACHMENT -> dao.readAttachmentSourceState(source.organizationId, source.sourceId)
        SyncSourceOwner.SERVER_ONLY -> null
    }

    private fun validateSource(source: PendingSourceRef) {
        require(source.organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        require(source.sourceId.isNotBlank()) { "source id is required" }
        SyncSourceOwner.fromTable(source.owner.tableName)
    }

    private companion object {
        val PROJECTION_PARENT_TYPES = mapOf("CASH_DENOMINATION" to "CASH_RECONCILIATION",
            "PURCHASE_ORDER_LINE" to "PURCHASE_ORDER", "GOODS_RECEIPT_LINE" to "GOODS_RECEIPT",
            "PURCHASE_MATCH_LINE" to "PURCHASE_MATCH", "PRICE_LIST_ITEM" to "PRICE_LIST")
        val FINANCIAL_CHILD_TYPES = setOf("INVOICE_ITEM", "INVOICE_DUE_INSTALLMENT", "PAYMENT",
            "PAYMENT_ALLOCATION", "REALIZED_FX_EVENT", "INVOICE_RETURN", "INVOICE_RETURN_LINE",
            "INVOICE_RETURN_PAYMENT_ALLOCATION")
    }

    private fun requireSha256(value: String) {
        require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
            "lowercase SHA-256 required"
        }
    }
}
