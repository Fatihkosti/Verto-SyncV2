package com.verto.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** F253 transport contract. Server SQL is docs/sql/v253_purchase_cycle.sql. */
@Serializable
data class PurchaseOrderDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("purchase_scope") val purchaseScope: String,
    @SerialName("currency_code") val currencyCode: String,
    val status: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("promised_delivery_at") val promisedDeliveryAt: Long? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_by_name") val createdByName: String,
    @SerialName("closed_at") val closedAt: Long? = null,
    @SerialName("close_reason") val closeReason: String? = null,
    val note: String = "",
    @SerialName("write_id") val writeId: String,
)

@Serializable
data class PurchaseOrderLineDto(
    val id: String,
    /** Input-only tenant proof for the security-definer push RPC; server row derives tenant from PO. */
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    @SerialName("line_number") val lineNumber: Int,
    @SerialName("inventory_item_id") val inventoryItemId: String? = null,
    @SerialName("item_name_snapshot") val itemNameSnapshot: String,
    @SerialName("ordered_quantity") val orderedQuantity: Int,
    @SerialName("unit_price_minor") val unitPriceMinor: Long,
)

@Serializable
data class GoodsReceiptDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    @SerialName("receipt_number") val receiptNumber: String,
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("received_by") val receivedBy: String,
    @SerialName("received_by_name") val receivedByName: String,
    val note: String = "",
    @SerialName("write_id") val writeId: String,
)

@Serializable
data class GoodsReceiptLineDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("goods_receipt_id") val goodsReceiptId: String,
    @SerialName("purchase_order_line_id") val purchaseOrderLineId: String,
    @SerialName("inventory_item_id") val inventoryItemId: String? = null,
    @SerialName("received_quantity") val receivedQuantity: Int,
    @SerialName("accepted_quantity") val acceptedQuantity: Int,
    @SerialName("rejected_quantity") val rejectedQuantity: Int,
    @SerialName("unit_cost_minor") val unitCostMinor: Long,
)

@Serializable
data class PurchaseCycleAttachmentDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("owner_type") val ownerType: String,
    @SerialName("owner_id") val ownerId: String,
    val uri: String,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("created_at") val createdAt: Long,
    @SerialName("write_id") val writeId: String,
)

@Serializable
data class PurchaseInvoiceMatchDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    val status: String,
    @SerialName("quantity_variance_units") val quantityVarianceUnits: Int,
    @SerialName("price_variance_minor") val priceVarianceMinor: Long,
    @SerialName("quantity_tolerance_units") val quantityToleranceUnits: Int,
    @SerialName("price_tolerance_minor") val priceToleranceMinor: Long,
    @SerialName("invoice_amount_minor") val invoiceAmountMinor: Long,
    @SerialName("payable_amount_minor") val payableAmountMinor: Long,
    @SerialName("variance_reason") val varianceReason: String? = null,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("approved_by_name") val approvedByName: String? = null,
    @SerialName("matched_at") val matchedAt: Long,
    @SerialName("write_id") val writeId: String,
)

@Serializable
data class PurchaseInvoiceMatchLineDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("match_id") val matchId: String,
    @SerialName("invoice_item_id") val invoiceItemId: String,
    @SerialName("purchase_order_line_id") val purchaseOrderLineId: String,
    @SerialName("ordered_quantity") val orderedQuantity: Int,
    @SerialName("accepted_quantity") val acceptedQuantity: Int,
    @SerialName("invoiced_quantity") val invoicedQuantity: Int,
    @SerialName("po_unit_price_minor") val poUnitPriceMinor: Long,
    @SerialName("invoice_unit_price_minor") val invoiceUnitPriceMinor: Long,
    @SerialName("quantity_variance_units") val quantityVarianceUnits: Int,
    @SerialName("price_variance_minor") val priceVarianceMinor: Long,
    @SerialName("payable_amount_minor") val payableAmountMinor: Long,
)

@Serializable
data class PurchaseInvoiceReceiptAllocationDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("match_line_id") val matchLineId: String,
    @SerialName("goods_receipt_line_id") val goodsReceiptLineId: String,
    @SerialName("allocated_quantity") val allocatedQuantity: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("write_id") val writeId: String,
)

@Serializable
data class PurchasePaymentOverrideDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("payment_request_id") val paymentRequestId: String,
    @SerialName("requested_amount_minor") val requestedAmountMinor: Long,
    @SerialName("payable_before_override_minor") val payableBeforeOverrideMinor: Long,
    val reason: String,
    @SerialName("approved_by") val approvedBy: String,
    @SerialName("approved_by_name") val approvedByName: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class PurchaseCyclePrePushRequest(
    @SerialName("p_purchase_orders") val purchaseOrders: List<PurchaseOrderDto>,
    @SerialName("p_purchase_order_lines") val purchaseOrderLines: List<PurchaseOrderLineDto>,
    @SerialName("p_goods_receipts") val goodsReceipts: List<GoodsReceiptDto>,
    @SerialName("p_goods_receipt_lines") val goodsReceiptLines: List<GoodsReceiptLineDto>,
    @SerialName("p_attachments") val attachments: List<PurchaseCycleAttachmentDto>,
)

@Serializable
data class PurchaseCyclePostPushRequest(
    @SerialName("p_matches") val matches: List<PurchaseInvoiceMatchDto>,
    @SerialName("p_match_lines") val matchLines: List<PurchaseInvoiceMatchLineDto>,
    @SerialName("p_allocations") val allocations: List<PurchaseInvoiceReceiptAllocationDto>,
    @SerialName("p_payment_overrides") val paymentOverrides: List<PurchasePaymentOverrideDto>,
)
