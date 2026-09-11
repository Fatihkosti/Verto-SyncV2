package com.verto.app.feature.invoice.domain.port

import com.verto.app.feature.invoice.domain.model.GoodsReceiptLineRecord
import com.verto.app.feature.invoice.domain.model.GoodsReceiptRecord
import com.verto.app.feature.invoice.domain.model.PurchaseCycleAttachmentDraft
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceCandidateLine
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchAssessment
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderLineRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderStatus
import com.verto.app.feature.invoice.domain.model.PurchasePaymentOverrideRecord
import com.verto.app.feature.invoice.domain.model.PurchaseScope


data class PurchaseSupplierRecommendation(
    val inventoryItemId: String,
    val bestSupplierId: String?,
    val scoreBps: Int?,
    val orderCount: Int?,
)

/** Advisory boundary only; purchase creation must not fail when recommendation evidence is sparse. */
interface PurchaseSupplierRecommendationPort {
    suspend fun recommendationForItem(inventoryItemId: String): PurchaseSupplierRecommendation
}

interface PurchaseCycleStorePort {
    suspend fun getOrder(orderId: String): PurchaseOrderRecord?
    suspend fun getOrderByWriteId(organizationId: String, writeId: String): PurchaseOrderRecord?
    suspend fun getOrderLines(orderId: String): List<PurchaseOrderLineRecord>
    suspend fun insertOrder(order: PurchaseOrderRecord, lines: List<PurchaseOrderLineRecord>, attachments: List<PurchaseCycleAttachmentDraft>)

    suspend fun getReceiptByWriteId(organizationId: String, writeId: String): GoodsReceiptRecord?
    suspend fun getReceiptLines(receiptId: String): List<GoodsReceiptLineRecord>
    suspend fun getAcceptedQuantity(orderLineId: String): Int
    suspend fun getMatchedInvoicedQuantity(orderLineId: String): Int
    suspend fun getResolvedInventoryItemId(orderLineId: String): String?
    suspend fun insertReceipt(receipt: GoodsReceiptRecord, lines: List<GoodsReceiptLineRecord>, attachments: List<PurchaseCycleAttachmentDraft>)
    suspend fun updateOrderStatus(orderId: String, status: PurchaseOrderStatus, closedAt: Long? = null, closeReason: String? = null)

    suspend fun insertInvoiceMatch(assessment: PurchaseInvoiceMatchAssessment)
    suspend fun getInvoiceMatch(invoiceId: String): PurchaseInvoiceMatchRecord?
    suspend fun insertPaymentOverride(record: PurchasePaymentOverrideRecord)
    suspend fun shipmentExists(organizationId: String, shipmentId: String): Boolean
    suspend fun linkOrderToShipment(organizationId: String, orderId: String, shipmentId: String, writeId: String, addedAt: Long)
}

/** Invoice-owned integration boundary for three-way match. */
interface PurchaseCycleInvoicePort {
    suspend fun assessSupplierInvoice(
        organizationId: String,
        purchaseOrderId: String,
        supplierId: String,
        purchaseScope: PurchaseScope,
        invoiceId: String,
        invoiceAmountMinor: Long,
        lines: List<PurchaseInvoiceCandidateLine>,
        quantityToleranceUnits: Int,
        priceToleranceMinor: Long,
        varianceReason: String?,
        approvedBy: String?,
        approvedByName: String?,
        writeId: String,
        matchedAt: Long,
    ): PurchaseInvoiceMatchAssessment

    suspend fun persistSupplierInvoiceMatch(assessment: PurchaseInvoiceMatchAssessment)

    suspend fun recordPaymentOverride(record: PurchasePaymentOverrideRecord)
}
