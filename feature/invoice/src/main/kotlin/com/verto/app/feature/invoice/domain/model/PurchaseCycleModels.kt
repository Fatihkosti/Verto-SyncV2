package com.verto.app.feature.invoice.domain.model

import com.verto.app.money.Money
import java.util.UUID

enum class PurchaseOrderStatus { OPEN, PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED }
enum class PurchaseInvoiceMatchStatus { MATCHED, WITHIN_TOLERANCE, OVERRIDDEN }
enum class PurchaseAttachmentOwnerType { PURCHASE_ORDER, GOODS_RECEIPT }

data class PurchaseOrderLineDraft(
    val inventoryItemId: String? = null,
    val itemName: String,
    val orderedQuantity: Int,
    val unitPrice: Money,
)

data class PurchaseCycleAttachmentDraft(
    val uri: String,
    val mimeType: String,
    val displayName: String,
)

data class CreatePurchaseOrderCommand(
    val organizationId: String = "",
    val orderNumber: String,
    val supplierId: String,
    val purchaseScope: PurchaseScope,
    val currencyCode: String,
    val lines: List<PurchaseOrderLineDraft>,
    val attachments: List<PurchaseCycleAttachmentDraft> = emptyList(),
    val note: String = "",
    val writeId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Supplier-confirmed delivery commitment; null means not captured/reliable. */
    val promisedDeliveryAt: Long? = null,
)

data class PurchaseOrderRecord(
    val id: String,
    val organizationId: String,
    val orderNumber: String,
    val supplierId: String,
    val purchaseScope: PurchaseScope,
    val currencyCode: String,
    val status: PurchaseOrderStatus,
    val createdAt: Long,
    val promisedDeliveryAt: Long? = null,
    val createdBy: String,
    val createdByName: String,
    val closedAt: Long? = null,
    val closeReason: String? = null,
    val note: String = "",
    val writeId: String,
)

data class PurchaseOrderLineRecord(
    val id: String,
    val purchaseOrderId: String,
    val lineNumber: Int,
    val inventoryItemId: String? = null,
    val itemName: String,
    val orderedQuantity: Int,
    val unitPriceMinor: Long,
)

data class RecordGoodsReceiptLineCommand(
    val purchaseOrderLineId: String,
    val receivedQuantity: Int,
    val acceptedQuantity: Int,
    val rejectedQuantity: Int = receivedQuantity - acceptedQuantity,
    /** If null the immutable PO price is used for initial stock recognition. */
    val unitCostMinor: Long? = null,
)

data class RecordGoodsReceiptCommand(
    val organizationId: String = "",
    val purchaseOrderId: String,
    val receiptNumber: String,
    val lines: List<RecordGoodsReceiptLineCommand>,
    val attachments: List<PurchaseCycleAttachmentDraft> = emptyList(),
    val note: String = "",
    val writeId: String = UUID.randomUUID().toString(),
    val receivedAt: Long = System.currentTimeMillis(),
)

data class GoodsReceiptRecord(
    val id: String,
    val organizationId: String,
    val purchaseOrderId: String,
    val receiptNumber: String,
    val receivedAt: Long,
    val receivedBy: String,
    val receivedByName: String,
    val note: String,
    val writeId: String,
)

data class GoodsReceiptLineRecord(
    val id: String,
    val goodsReceiptId: String,
    val purchaseOrderLineId: String,
    val inventoryItemId: String?,
    val receivedQuantity: Int,
    val acceptedQuantity: Int,
    val rejectedQuantity: Int,
    val unitCostMinor: Long,
)

data class PurchaseOrderResult(
    val purchaseOrderId: String,
    val duplicate: Boolean,
    /** Advisory warnings such as a historically stronger supplier for one of the requested items. */
    val recommendationWarnings: List<String> = emptyList(),
)

data class GoodsReceiptResult(
    val goodsReceiptId: String,
    val duplicate: Boolean,
    val orderStatus: PurchaseOrderStatus,
)

data class ClosePurchaseOrderCommand(
    val purchaseOrderId: String,
    val reason: String,
    val closedAt: Long = System.currentTimeMillis(),
)

data class PurchaseInvoiceCandidateLine(
    val invoiceItemId: String,
    val inventoryItemId: String,
    val itemName: String,
    val quantity: Int,
    val unitPriceMinor: Long,
)

data class PurchaseInvoiceMatchLineRecord(
    val id: String,
    val matchId: String,
    val invoiceItemId: String,
    val purchaseOrderLineId: String,
    val orderedQuantity: Int,
    val acceptedQuantity: Int,
    val invoicedQuantity: Int,
    val poUnitPriceMinor: Long,
    val invoiceUnitPriceMinor: Long,
    val quantityVarianceUnits: Int,
    val priceVarianceMinor: Long,
    val payableAmountMinor: Long,
)

data class PurchaseInvoiceMatchRecord(
    val id: String,
    val organizationId: String,
    val invoiceId: String,
    val purchaseOrderId: String,
    val status: PurchaseInvoiceMatchStatus,
    val quantityVarianceUnits: Int,
    val priceVarianceMinor: Long,
    val quantityToleranceUnits: Int,
    val priceToleranceMinor: Long,
    val invoiceAmountMinor: Long,
    val payableAmountMinor: Long,
    val varianceReason: String?,
    val approvedBy: String?,
    val approvedByName: String?,
    val matchedAt: Long,
    val writeId: String,
)

data class PurchaseInvoiceMatchAssessment(
    val match: PurchaseInvoiceMatchRecord,
    val lines: List<PurchaseInvoiceMatchLineRecord>,
    val hasVariance: Boolean,
    val exceedsTolerance: Boolean,
)


data class PurchasePaymentOverrideRecord(
    val id: String,
    val organizationId: String,
    val invoiceId: String,
    val paymentRequestId: String,
    val requestedAmountMinor: Long,
    val payableBeforeOverrideMinor: Long,
    val reason: String,
    val approvedBy: String,
    val approvedByName: String,
    val createdAt: Long,
)

data class LinkPurchaseOrderToShipmentCommand(
    val organizationId: String = "",
    val purchaseOrderId: String,
    val shipmentId: String,
    val writeId: String = UUID.randomUUID().toString(),
    val addedAt: Long = System.currentTimeMillis(),
)
