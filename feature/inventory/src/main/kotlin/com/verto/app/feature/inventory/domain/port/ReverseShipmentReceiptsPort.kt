package com.verto.app.feature.inventory.domain.port

/** Inventory-owned boundary used only by exceptional permanent Logistics V2 deletion. */
interface ReverseShipmentReceiptsPort {
    suspend fun reverse(shipmentId: String): Result<Unit>
}
