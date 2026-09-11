package com.verto.app.feature.invoice.domain.port

import com.verto.app.feature.invoice.domain.model.InvoiceCashMovementType
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoicePayment
import com.verto.app.feature.invoice.domain.model.InvoiceInventoryRevaluationRecord
import com.verto.app.feature.invoice.domain.model.InvoicePurchaseStockCommand
import com.verto.app.feature.invoice.domain.model.InvoicePaymentAllocation
import com.verto.app.feature.invoice.domain.model.InvoiceDueInstallment
import com.verto.app.feature.invoice.domain.model.RealizedFxEvent
import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.invoice.domain.model.InvoiceWriteClaim
import com.verto.app.feature.invoice.domain.model.InvoiceWriteIdentity
import com.verto.app.feature.invoice.domain.model.InvoiceSaleStockMutation
import com.verto.app.feature.invoice.domain.model.InvoicePostingIdentity

interface InvoiceStorePort {
    suspend fun getInvoiceById(invoiceId: String): InvoiceRecord?
    suspend fun getInvoiceItems(invoiceId: String): List<InvoiceLine>
    suspend fun getTotalPaid(invoiceId: String): Double
    suspend fun getPayments(invoiceId: String): List<InvoicePayment>
    suspend fun getPaymentAllocations(invoiceId: String): List<InvoicePaymentAllocation>
    suspend fun getRealizedFxEvents(invoiceId: String): List<RealizedFxEvent>
    suspend fun getDueInstallments(invoiceId: String): List<InvoiceDueInstallment> = emptyList()
    suspend fun getReversalForPayment(originalPaymentId: String): InvoicePayment?
    suspend fun claimWrite(identity: InvoiceWriteIdentity): InvoiceWriteClaim
    suspend fun insertInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>): Pair<String, Int>
    suspend fun updateInvoice(invoice: InvoiceRecord)
    suspend fun updatePostedDescription(invoice: InvoiceRecord, expectedVersion: Int): Boolean
    suspend fun updateInvoiceWithItems(invoice: InvoiceRecord, items: List<InvoiceLine>)
    suspend fun addPayment(payment: InvoicePayment): String
    suspend fun addPaymentAllocation(allocation: InvoicePaymentAllocation)
    suspend fun addRealizedFxEvent(event: RealizedFxEvent)
    suspend fun replaceDueInstallments(invoiceId: String, installments: List<InvoiceDueInstallment>) = Unit
    suspend fun deletePayments(invoiceId: String)
    suspend fun markVoided(
        invoiceId: String,
        expectedVersion: Int,
        voidedAt: Long,
        reason: String,
        writeId: String,
    ): Boolean
}

interface InvoiceStockPort {
    suspend fun getAllItems(): List<InvoiceStockItem>
    suspend fun getItem(itemId: String): InvoiceStockItem?
    suspend fun saveItem(item: InvoiceStockItem)
    suspend fun deductStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        clientId: String,
        unitPrice: Double,
        allowNegativeStock: Boolean,
        sourceWriteId: String = "",
    ): Result<Unit>

    suspend fun addStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        supplierId: String,
        unitPrice: Double,
        sourceWriteId: String = "",
    ): Result<Unit>
    suspend fun receivePurchaseAtLatestPrice(command: InvoicePurchaseStockCommand): InvoiceInventoryRevaluationRecord?
    suspend fun deleteMovements(invoiceId: String)
    suspend fun reverseMovements(invoiceId: String, sourceWriteId: String = "")
}

interface InvoiceLinePostingStockPort {
    suspend fun deductPostingLine(mutation: InvoiceSaleStockMutation, identity: InvoicePostingIdentity): Result<Unit>
}

interface InvoiceCashPort {
    suspend fun onSaleCash(amount: Double, invoiceId: String, sourceWriteId: String = "")
    suspend fun onPurchaseCash(amount: Double, invoiceId: String, sourceWriteId: String = "")
    suspend fun onPaymentReceived(amount: Double, invoiceId: String, sourceWriteId: String = "")
    suspend fun recordMovement(
        type: InvoiceCashMovementType,
        amount: Double,
        referenceId: String,
        note: String = "",
        sourceWriteId: String = "",
    )
    suspend fun reverseMovement(
        type: InvoiceCashMovementType,
        amount: Double,
        referenceId: String,
        note: String = "",
        sourceWriteId: String = "",
    )
}

interface InvoiceTransactionPort {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

interface InvoiceAuthorizationPort {
    suspend fun canSave(isSale: Boolean, creatingNew: Boolean): Boolean
    suspend fun canPost(category: InvoiceCategory): Boolean
    suspend fun canEditDescription(category: InvoiceCategory): Boolean
    suspend fun canOverrideStock(): Boolean
    suspend fun canApproveExchangeRate(): Boolean
    suspend fun canManuallyAllocateLandedCost(): Boolean
    suspend fun canManageCommission(): Boolean = false
    suspend fun canOverridePurchaseVariance(): Boolean = false
    suspend fun canOverrideUnreceivedPurchasePayment(): Boolean = false
    suspend fun canVoid(category: InvoiceCategory): Boolean
}

interface InvoiceSettingsPort {
    suspend fun allowNegativeStock(): Boolean
    suspend fun functionalCurrencyCode(): String
}

interface InvoiceNumberPort {
    suspend fun allocate(): Int?
}


interface InvoiceSyncSchedulerPort {
    suspend fun requestSync(organizationId: String, userId: String)
}
