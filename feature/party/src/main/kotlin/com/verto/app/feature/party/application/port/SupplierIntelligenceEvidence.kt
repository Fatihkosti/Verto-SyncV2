package com.verto.app.feature.party.application.port

data class SupplierOrderLineEvidence(
    val id: String,
    val inventoryItemId: String?,
    val orderedQuantity: Int,
    val poUnitPriceMinor: Long,
)

data class SupplierReceiptEvidence(
    val id: String,
    val orderLineId: String,
    val receivedAt: Long,
    val receivedQuantity: Int,
    val acceptedQuantity: Int,
    val rejectedQuantity: Int,
    val unitCostMinor: Long,
)

data class SupplierPriceEvidence(
    val id: String,
    val orderLineId: String,
    val invoicedQuantity: Int,
    val poUnitPriceMinor: Long,
    val invoiceUnitPriceMinor: Long,
)

data class SupplierReturnEvidence(
    val id: String,
    val inventoryItemId: String,
    val quantity: Int,
    val occurredAt: Long,
)

data class SupplierOrderEvidence(
    val orderId: String,
    val supplierId: String,
    val currencyCode: String,
    val status: String,
    val createdAt: Long,
    val promisedDeliveryAt: Long?,
    val lines: List<SupplierOrderLineEvidence>,
    val receipts: List<SupplierReceiptEvidence>,
    val priceMatches: List<SupplierPriceEvidence>,
    val returns: List<SupplierReturnEvidence> = emptyList(),
)
