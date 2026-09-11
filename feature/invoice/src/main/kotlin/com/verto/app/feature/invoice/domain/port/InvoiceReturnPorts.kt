package com.verto.app.feature.invoice.domain.port

import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceReturnAggregate
import com.verto.app.feature.invoice.domain.model.InvoiceReturnRecord

interface InvoiceReturnStorePort {
    suspend fun getByWriteId(organizationId: String, writeId: String): InvoiceReturnRecord?
    suspend fun getReturnedQuantity(originalInvoiceItemId: String): Int
    suspend fun getReturnedQuantityForInvoiceInventoryItem(invoiceId: String, inventoryItemId: String): Int
    suspend fun getAllocatedFunctionalForPayment(paymentId: String): Long
    suspend fun insertAggregate(aggregate: InvoiceReturnAggregate)
}

interface InvoiceReturnStockPort {
    suspend fun restoreSalesReturn(
        itemId: String,
        quantity: Int,
        returnId: String,
        returnLineId: String,
        clientId: String,
        historicalUnitCostMinor: Long,
        occurredAt: Long,
        writeId: String,
    )

    /**
     * Removes returned purchase stock. When the fully-returned source is still the latest valid
     * purchase/receipt source, the implementation recalculates currentBuyPrice from the newest
     * remaining valid source. A later purchase must therefore remain untouched.
     */
    suspend fun deductPurchaseReturn(
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
    )
}

interface InvoiceReturnCreditPort {
    suspend fun record(
        id: String,
        clientId: String,
        signedFunctionalAmountMinor: Long,
        note: String,
        sourceReference: String,
        occurredAt: Long,
        actorId: String,
        actorName: String,
    )
}

interface InvoiceReturnAuthorizationPort {
    suspend fun canCreate(category: InvoiceCategory): Boolean
}

/** Minor-unit boundary for F252. Legacy Double conversion is confined to the app adapter. */
interface InvoiceReturnCashPort {
    suspend fun cashOut(
        amountMinor: Long,
        returnId: String,
        writeId: String,
        note: String,
    )

    suspend fun cashIn(
        amountMinor: Long,
        returnId: String,
        writeId: String,
        note: String,
    )
}

interface InvoiceReturnOutboxPort {
    suspend fun append(aggregate: InvoiceReturnAggregate)
}
