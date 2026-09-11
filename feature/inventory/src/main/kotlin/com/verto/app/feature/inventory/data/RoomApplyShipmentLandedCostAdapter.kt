package com.verto.app.feature.inventory.data

import com.verto.app.feature.inventory.domain.port.ApplyShipmentLandedCostPort
import java.math.BigDecimal
import com.verto.app.money.Money
import javax.inject.Inject

class RoomApplyShipmentLandedCostAdapter @Inject constructor(
    private val stockWriter: InventoryStockWriter,
) : ApplyShipmentLandedCostPort {
    override suspend fun apply(
        postingId: String,
        shipmentId: String,
        unitPrice: BigDecimal,
    ): Result<Unit> = stockWriter.applyShipmentLandedCost(
        postingId = postingId,
        shipmentId = shipmentId,
        unitPriceMinor = Money.fromMajor(unitPrice).amountMinor,
    )
}
