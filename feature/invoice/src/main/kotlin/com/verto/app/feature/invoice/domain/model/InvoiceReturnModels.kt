package com.verto.app.feature.invoice.domain.model

import java.util.UUID

/** F252: immutable correction documents. */
enum class InvoiceReturnDocumentType {
    SALES_RETURN_CREDIT_NOTE,
    PURCHASE_RETURN_DEBIT_NOTE,
}

enum class InvoiceReturnSettlementMode {
    /** Keep the note as a party balance adjustment. */
    CREDIT_BALANCE,
    /** Settle the note immediately through the functional-currency cash register. */
    CASH_REFUND,
}

data class InvoiceReturnLineRequest(
    val originalInvoiceItemId: String,
    val quantity: Int,
)

data class CreateInvoiceReturnCommand(
    val originalInvoiceId: String,
    val lines: List<InvoiceReturnLineRequest>,
    val settlementMode: InvoiceReturnSettlementMode,
    val reason: String,
    val organizationId: String,
    /** Stable identity retained across retries/process recreation. */
    val writeId: String,
    val occurredAt: Long,
)

data class InvoiceReturnRecord(
    val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val originalInvoiceId: String,
    val clientId: String,
    val documentType: InvoiceReturnDocumentType,
    val settlementMode: InvoiceReturnSettlementMode,
    val transactionCurrencyCode: String,
    val functionalCurrencyCode: String,
    val transactionAmountMinor: Long,
    val functionalAmountMinor: Long,
    val reason: String,
    val occurredAt: Long,
    val recordedAt: Long,
    val createdBy: String,
    val createdByName: String,
    val writeId: String,
    val sourceVersion: Int = 1,
)

data class InvoiceReturnLineRecord(
    val id: String = UUID.randomUUID().toString(),
    val returnId: String,
    val originalInvoiceItemId: String,
    val inventoryItemId: String,
    val itemNameSnapshot: String,
    val quantity: Int,
    val unitTransactionAmountMinor: Long,
    val transactionAmountMinor: Long,
    val unitFunctionalAmountMinor: Long,
    val functionalAmountMinor: Long,
    val unitCostAtSaleMinor: Long,
    val historicalCostAmountMinor: Long,
    val originalPurchaseUnitCostMinor: Long,
)

data class InvoiceReturnPaymentAllocationRecord(
    val id: String = UUID.randomUUID().toString(),
    val returnId: String,
    val paymentId: String,
    val allocatedFunctionalAmountMinor: Long,
    val createdAt: Long,
)

data class InvoiceReturnAggregate(
    val document: InvoiceReturnRecord,
    val lines: List<InvoiceReturnLineRecord>,
    val paymentAllocations: List<InvoiceReturnPaymentAllocationRecord>,
)

data class InvoiceReturnResult(
    val returnId: String,
    val duplicate: Boolean,
    val documentType: InvoiceReturnDocumentType,
    val transactionAmountMinor: Long,
    val functionalAmountMinor: Long,
)
