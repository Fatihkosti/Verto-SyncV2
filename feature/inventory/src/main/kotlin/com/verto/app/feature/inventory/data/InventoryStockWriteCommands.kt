package com.verto.app.feature.inventory.data

import java.util.UUID

data class InventoryWriteMeta(
    val commandId: String = UUID.randomUUID().toString(),
    val note: String = "",
    val sourceLineId: String? = null,
    val postingGroupId: String? = null,
)

data class InventoryIssueRequest(
    val itemId: String,
    val quantity: Int,
    val invoiceId: String,
    val clientId: String,
    val unitPrice: Double,
    val allowNegativeStock: Boolean,
)

data class InventoryReceiveRequest(
    val itemId: String,
    val quantity: Int,
    val invoiceId: String,
    val supplierId: String,
    val unitPrice: Double,
)

data class ShipmentStockReceiptRequest(
    val postingId: String,
    val shipmentId: String,
    val receivingBatchId: String,
    val receivingLineId: String,
    val itemId: String,
    val quantity: Int,
)

data class InventoryReceiptDetails(
    val partyId: String,
    val unitPrice: Double,
    val note: String,
)

data class SalesReturnStockRequest(
    val itemId: String,
    val quantity: Int,
    val returnId: String,
    val returnLineId: String,
    val clientId: String,
    val historicalUnitCostMinor: Long,
)

data class PurchaseReturnStockTarget(
    val itemId: String,
    val quantity: Int,
    val supplierId: String,
    val originalUnitCostMinor: Long,
)

data class PurchaseReturnSource(
    val returnId: String,
    val returnLineId: String,
    val originalInvoiceId: String,
    val originalInvoiceItemId: String,
)

data class PurchaseReturnPolicy(
    val internationalPurchase: Boolean,
    val sourceStillValidForItem: Boolean,
)

data class InventoryWriteTiming(
    val occurredAt: Long,
    val commandId: String,
)

data class InventoryWriteActor(
    val actorId: String,
    val actorName: String,
)
