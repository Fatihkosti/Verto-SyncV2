package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** F253: optional PO header. Financial posting remains in invoices/payments. */
@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(
            entity = PartyIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("supplier_id"),
        Index(value = ["organization_id", "order_number"], unique = true, name = "index_purchase_orders_org_number"),
        Index(value = ["organization_id", "write_id"], unique = true, name = "index_purchase_orders_org_write"),
        Index(value = ["organization_id", "status"], name = "index_purchase_orders_org_status"),
        Index(value = ["supplier_id", "promised_delivery_at"], name = "index_purchase_orders_supplier_promised_delivery"),
    ],
)
data class PurchaseOrderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "order_number") val orderNumber: String,
    @ColumnInfo(name = "supplier_id") val supplierId: String,
    @ColumnInfo(name = "purchase_scope") val purchaseScope: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "promised_delivery_at") val promisedDeliveryAt: Long? = null,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "created_by_name") val createdByName: String,
    @ColumnInfo(name = "closed_at") val closedAt: Long? = null,
    @ColumnInfo(name = "close_reason") val closeReason: String? = null,
    val note: String = "",
    @ColumnInfo(name = "write_id") val writeId: String,
)

@Entity(
    tableName = "purchase_order_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("purchase_order_id"),
        Index(value = ["purchase_order_id", "line_number"], unique = true, name = "index_purchase_order_lines_order_number"),
        Index(value = ["purchase_order_id", "inventory_item_id"], name = "index_purchase_order_lines_inventory"),
    ],
)
data class PurchaseOrderLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "purchase_order_id") val purchaseOrderId: String,
    @ColumnInfo(name = "line_number") val lineNumber: Int,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String? = null,
    @ColumnInfo(name = "item_name_snapshot") val itemNameSnapshot: String,
    @ColumnInfo(name = "ordered_quantity") val orderedQuantity: Int,
    @ColumnInfo(name = "unit_price_minor") val unitPriceMinor: Long,
)

/** One immutable posted GRN event. Partial receiving creates another GRN. */
@Entity(
    tableName = "goods_receipts",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("purchase_order_id"),
        Index(value = ["organization_id", "receipt_number"], unique = true, name = "index_goods_receipts_org_number"),
        Index(value = ["organization_id", "write_id"], unique = true, name = "index_goods_receipts_org_write"),
    ],
)
data class GoodsReceiptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "purchase_order_id") val purchaseOrderId: String,
    @ColumnInfo(name = "receipt_number") val receiptNumber: String,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "received_by") val receivedBy: String,
    @ColumnInfo(name = "received_by_name") val receivedByName: String,
    val note: String = "",
    @ColumnInfo(name = "write_id") val writeId: String,
)

@Entity(
    tableName = "goods_receipt_lines",
    foreignKeys = [
        ForeignKey(
            entity = GoodsReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["goods_receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PurchaseOrderLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_line_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("goods_receipt_id"),
        Index("purchase_order_line_id"),
        Index(value = ["goods_receipt_id", "purchase_order_line_id"], unique = true, name = "index_goods_receipt_line_identity"),
    ],
)
data class GoodsReceiptLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goods_receipt_id") val goodsReceiptId: String,
    @ColumnInfo(name = "purchase_order_line_id") val purchaseOrderLineId: String,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String? = null,
    @ColumnInfo(name = "received_quantity") val receivedQuantity: Int,
    @ColumnInfo(name = "accepted_quantity") val acceptedQuantity: Int,
    @ColumnInfo(name = "rejected_quantity") val rejectedQuantity: Int,
    @ColumnInfo(name = "unit_cost_minor") val unitCostMinor: Long,
)

/** Attachments are metadata only; the actual file remains owned by the attachment/storage layer. */
@Entity(
    tableName = "purchase_cycle_attachments",
    indices = [
        Index(value = ["organization_id", "owner_type", "owner_id"], name = "index_purchase_cycle_attachments_owner"),
        Index(value = ["organization_id", "write_id", "uri"], unique = true, name = "index_purchase_cycle_attachments_identity"),
    ],
)
data class PurchaseCycleAttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "owner_type") val ownerType: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val uri: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)

/** Immutable three-way-match decision for one posted supplier invoice. */
@Entity(
    tableName = "purchase_invoice_matches",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["invoice_id"], unique = true, name = "index_purchase_invoice_matches_invoice"),
        Index("purchase_order_id"),
        Index(value = ["organization_id", "status"], name = "index_purchase_invoice_matches_org_status"),
        Index(value = ["organization_id", "matched_at"], name = "index_purchase_invoice_matches_org_matched_at"),
    ],
)
data class PurchaseInvoiceMatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "purchase_order_id") val purchaseOrderId: String,
    val status: String,
    @ColumnInfo(name = "quantity_variance_units") val quantityVarianceUnits: Int,
    @ColumnInfo(name = "price_variance_minor") val priceVarianceMinor: Long,
    @ColumnInfo(name = "quantity_tolerance_units") val quantityToleranceUnits: Int,
    @ColumnInfo(name = "price_tolerance_minor") val priceToleranceMinor: Long,
    @ColumnInfo(name = "invoice_amount_minor") val invoiceAmountMinor: Long,
    @ColumnInfo(name = "payable_amount_minor") val payableAmountMinor: Long,
    @ColumnInfo(name = "variance_reason") val varianceReason: String? = null,
    @ColumnInfo(name = "approved_by") val approvedBy: String? = null,
    @ColumnInfo(name = "approved_by_name") val approvedByName: String? = null,
    @ColumnInfo(name = "matched_at") val matchedAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)

@Entity(
    tableName = "purchase_invoice_match_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoiceMatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InvoiceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PurchaseOrderLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_line_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("match_id"),
        Index("invoice_item_id"),
        Index("purchase_order_line_id"),
        Index(value = ["match_id", "purchase_order_line_id"], unique = true, name = "index_purchase_invoice_match_line_identity"),
    ],
)
data class PurchaseInvoiceMatchLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "invoice_item_id") val invoiceItemId: String,
    @ColumnInfo(name = "purchase_order_line_id") val purchaseOrderLineId: String,
    @ColumnInfo(name = "ordered_quantity") val orderedQuantity: Int,
    @ColumnInfo(name = "accepted_quantity") val acceptedQuantity: Int,
    @ColumnInfo(name = "invoiced_quantity") val invoicedQuantity: Int,
    @ColumnInfo(name = "po_unit_price_minor") val poUnitPriceMinor: Long,
    @ColumnInfo(name = "invoice_unit_price_minor") val invoiceUnitPriceMinor: Long,
    @ColumnInfo(name = "quantity_variance_units") val quantityVarianceUnits: Int,
    @ColumnInfo(name = "price_variance_minor") val priceVarianceMinor: Long,
    @ColumnInfo(name = "payable_amount_minor") val payableAmountMinor: Long,
)

/** Documented, permission-gated exception for paying beyond currently accepted quantity. */
/** Append-only allocation proving which accepted GRN quantity backs each matched invoice line. */
@Entity(
    tableName = "purchase_invoice_receipt_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoiceMatchLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_line_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = GoodsReceiptLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["goods_receipt_line_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("match_line_id"),
        Index("goods_receipt_line_id"),
        Index(value = ["organization_id", "match_line_id", "goods_receipt_line_id"], unique = true, name = "index_purchase_invoice_receipt_allocation_identity"),
    ],
)
data class PurchaseInvoiceReceiptAllocationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "match_line_id") val matchLineId: String,
    @ColumnInfo(name = "goods_receipt_line_id") val goodsReceiptLineId: String,
    @ColumnInfo(name = "allocated_quantity") val allocatedQuantity: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)

@Entity(
    tableName = "purchase_payment_overrides",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("invoice_id"),
        Index(value = ["organization_id", "payment_request_id"], unique = true, name = "index_purchase_payment_override_request"),
    ],
)
data class PurchasePaymentOverrideEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "payment_request_id") val paymentRequestId: String,
    @ColumnInfo(name = "requested_amount_minor") val requestedAmountMinor: Long,
    @ColumnInfo(name = "payable_before_override_minor") val payableBeforeOverrideMinor: Long,
    val reason: String,
    @ColumnInfo(name = "approved_by") val approvedBy: String,
    @ColumnInfo(name = "approved_by_name") val approvedByName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)


/** F253: an INTERNATIONAL purchase order may be a logistics shipment source before supplier invoice arrival. */
@Entity(
    tableName = "purchase_order_shipment_sources",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("purchase_order_id"),
        Index(value = ["organization_id", "shipment_id", "purchase_order_id"], unique = true, name = "index_purchase_order_shipment_identity"),
        Index(value = ["organization_id", "write_id"], unique = true, name = "index_purchase_order_shipment_write"),
    ],
)
data class PurchaseOrderShipmentSourceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "purchase_order_id") val purchaseOrderId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "write_id") val writeId: String,
)
