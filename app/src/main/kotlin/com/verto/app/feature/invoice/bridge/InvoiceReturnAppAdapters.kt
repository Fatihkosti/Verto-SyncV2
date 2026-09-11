package com.verto.app.feature.invoice.bridge

import com.verto.app.data.local.dao.ClientCreditDao
import com.verto.app.data.local.dao.InvoiceReturnDao
import com.verto.app.data.local.entity.ClientCreditEntity
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceReturnLineEntity
import com.verto.app.data.local.entity.InvoiceReturnPaymentAllocationEntity
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceReturnAggregate
import com.verto.app.feature.invoice.domain.model.InvoiceReturnDocumentType
import com.verto.app.feature.invoice.domain.model.InvoiceReturnLineRecord
import com.verto.app.feature.invoice.domain.model.InvoiceReturnPaymentAllocationRecord
import com.verto.app.feature.invoice.domain.model.InvoiceReturnRecord
import com.verto.app.feature.invoice.domain.model.InvoiceReturnSettlementMode
import com.verto.app.feature.invoice.domain.port.InvoiceReturnAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnCreditPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnStorePort
import com.verto.app.feature.inventory.data.InventoryStockWriter
import com.verto.app.feature.inventory.data.InventoryWriteActor
import com.verto.app.feature.inventory.data.InventoryWriteTiming
import com.verto.app.feature.inventory.data.PurchaseReturnPolicy
import com.verto.app.feature.inventory.data.PurchaseReturnSource
import com.verto.app.feature.inventory.data.PurchaseReturnStockTarget
import com.verto.app.feature.inventory.data.SalesReturnStockRequest
import com.verto.app.money.Money
import com.verto.app.utils.CashRegisterManager
import javax.inject.Inject

class RoomInvoiceReturnStoreAdapter @Inject constructor(
    private val dao: InvoiceReturnDao,
) : InvoiceReturnStorePort {
    override suspend fun getByWriteId(organizationId: String, writeId: String): InvoiceReturnRecord? =
        dao.getByWriteId(organizationId, writeId)?.toDomain()

    override suspend fun getReturnedQuantity(originalInvoiceItemId: String): Int =
        dao.getReturnedQuantity(originalInvoiceItemId)

    override suspend fun getReturnedQuantityForInvoiceInventoryItem(invoiceId: String, inventoryItemId: String): Int =
        dao.getReturnedQuantityForInvoiceInventoryItem(invoiceId, inventoryItemId)

    override suspend fun getAllocatedFunctionalForPayment(paymentId: String): Long =
        dao.getAllocatedFunctionalForPayment(paymentId)

    override suspend fun insertAggregate(aggregate: InvoiceReturnAggregate) = dao.insertAggregate(
        document = aggregate.document.toEntity(),
        lines = aggregate.lines.map { it.toEntity() },
        allocations = aggregate.paymentAllocations.map { it.toEntity() },
    )
}

class InventoryInvoiceReturnStockAdapter @Inject constructor(
    private val inventory: InventoryStockWriter,
) : InvoiceReturnStockPort {
    override suspend fun restoreSalesReturn(
        itemId: String,
        quantity: Int,
        returnId: String,
        returnLineId: String,
        clientId: String,
        historicalUnitCostMinor: Long,
        occurredAt: Long,
        writeId: String,
    ) = inventory.returnStock(
        request = SalesReturnStockRequest(
            itemId, quantity, returnId, returnLineId, clientId, historicalUnitCostMinor,
        ),
        timing = InventoryWriteTiming(occurredAt, writeId),
    )

    override suspend fun deductPurchaseReturn(
        itemId: String,
        quantity: Int,
        returnId: String,
        returnLineId: String,
        supplierId: String,
        originalInvoiceId: String,
        originalInvoiceItemId: String,
        internationalPurchase: Boolean,
        originalUnitCostMinor: Long,
        sourceStillValidForItem: Boolean,
        occurredAt: Long,
        writeId: String,
        actorId: String,
        actorName: String,
    ) = inventory.deductPurchaseReturn(
        target = PurchaseReturnStockTarget(itemId, quantity, supplierId, originalUnitCostMinor),
        source = PurchaseReturnSource(returnId, returnLineId, originalInvoiceId, originalInvoiceItemId),
        policy = PurchaseReturnPolicy(internationalPurchase, sourceStillValidForItem),
        timing = InventoryWriteTiming(occurredAt, writeId),
        actor = InventoryWriteActor(actorId, actorName),
    )
}

class RoomInvoiceReturnCreditAdapter @Inject constructor(
    private val dao: ClientCreditDao,
) : InvoiceReturnCreditPort {
    override suspend fun record(
        id: String,
        clientId: String,
        signedFunctionalAmountMinor: Long,
        note: String,
        sourceReference: String,
        occurredAt: Long,
        actorId: String,
        actorName: String,
    ) {
        val inserted = dao.insert(
            ClientCreditEntity(
                id = id,
                clientId = clientId,
                amount = Money.ofMinor(signedFunctionalAmountMinor).toLegacyDouble(),
                amountMinor = signedFunctionalAmountMinor,
                note = note,
                sourcePaymentId = sourceReference,
                createdAt = occurredAt,
                employeeId = actorId,
                employeeName = actorName,
                isDirty = true,
            )
        )
        check(inserted != -1L) { "invoice return credit identity already exists" }
    }
}

class CashRegisterInvoiceReturnCashAdapter @Inject constructor(
    private val cashRegisterManager: CashRegisterManager,
) : InvoiceReturnCashPort {
    override suspend fun cashOut(amountMinor: Long, returnId: String, writeId: String, note: String) =
        cashRegisterManager.onInvoiceReturnCashOutMinor(
            amountMinor = amountMinor,
            returnId = returnId,
            writeId = writeId,
            note = note,
        )

    override suspend fun cashIn(amountMinor: Long, returnId: String, writeId: String, note: String) =
        cashRegisterManager.onInvoiceReturnCashInMinor(
            amountMinor = amountMinor,
            returnId = returnId,
            writeId = writeId,
            note = note,
        )
}

class PermissionInvoiceReturnAuthorizationAdapter @Inject constructor(
    private val permissionProvider: PermissionProvider,
) : InvoiceReturnAuthorizationPort {
    override suspend fun canCreate(category: InvoiceCategory): Boolean = when (category) {
        InvoiceCategory.SALE -> permissionProvider.canNow { it.salesEdit }
        InvoiceCategory.PURCHASE -> permissionProvider.canNow { it.purchasesEdit }
    }
}

private fun InvoiceReturnDocumentEntity.toDomain() = InvoiceReturnRecord(
    id = id,
    organizationId = organizationId,
    originalInvoiceId = originalInvoiceId,
    clientId = clientId,
    documentType = InvoiceReturnDocumentType.valueOf(documentType),
    settlementMode = InvoiceReturnSettlementMode.valueOf(settlementMode),
    transactionCurrencyCode = transactionCurrencyCode,
    functionalCurrencyCode = functionalCurrencyCode,
    transactionAmountMinor = transactionAmountMinor,
    functionalAmountMinor = functionalAmountMinor,
    reason = reason,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    createdBy = createdBy,
    createdByName = createdByName,
    writeId = writeId,
    sourceVersion = sourceVersion,
)

private fun InvoiceReturnRecord.toEntity() = InvoiceReturnDocumentEntity(
    id = id,
    organizationId = organizationId,
    originalInvoiceId = originalInvoiceId,
    clientId = clientId,
    documentType = documentType.name,
    settlementMode = settlementMode.name,
    transactionCurrencyCode = transactionCurrencyCode,
    functionalCurrencyCode = functionalCurrencyCode,
    transactionAmountMinor = transactionAmountMinor,
    functionalAmountMinor = functionalAmountMinor,
    reason = reason,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    createdBy = createdBy,
    createdByName = createdByName,
    writeId = writeId,
    sourceVersion = sourceVersion,
)

private fun InvoiceReturnLineRecord.toEntity() = InvoiceReturnLineEntity(
    id = id,
    returnId = returnId,
    originalInvoiceItemId = originalInvoiceItemId,
    inventoryItemId = inventoryItemId,
    itemNameSnapshot = itemNameSnapshot,
    quantity = quantity,
    unitTransactionAmountMinor = unitTransactionAmountMinor,
    transactionAmountMinor = transactionAmountMinor,
    unitFunctionalAmountMinor = unitFunctionalAmountMinor,
    functionalAmountMinor = functionalAmountMinor,
    unitCostAtSaleMinor = unitCostAtSaleMinor,
    historicalCostAmountMinor = historicalCostAmountMinor,
    originalPurchaseUnitCostMinor = originalPurchaseUnitCostMinor,
)

private fun InvoiceReturnPaymentAllocationRecord.toEntity() = InvoiceReturnPaymentAllocationEntity(
    id = id,
    returnId = returnId,
    paymentId = paymentId,
    allocatedFunctionalAmountMinor = allocatedFunctionalAmountMinor,
    createdAt = createdAt,
)
