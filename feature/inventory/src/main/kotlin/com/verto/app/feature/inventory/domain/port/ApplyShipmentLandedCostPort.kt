package com.verto.app.feature.inventory.domain.port

import java.math.BigDecimal

/** Inventory-owned additive contract for changing only a specific Logistics V2 receipt movement cost. */
interface ApplyShipmentLandedCostPort {
    suspend fun apply(
        postingId: String,
        shipmentId: String,
        unitPrice: BigDecimal,
    ): Result<Unit>
}
