package com.verto.app.feature.inventory.domain.port

import java.math.BigDecimal

/** Additive inventory-owned contract for idempotent Logistics V2 receiving. */
interface ReceiveShipmentStockPort {
    suspend fun receive(
        postingId: String,
        shipmentId: String,
        receivingBatchId: String,
        receivingLineId: String,
        itemId: String,
        quantity: Int,
        supplierId: String,
        unitPrice: BigDecimal,
        note: String = "",
    ): Result<Unit>
}
