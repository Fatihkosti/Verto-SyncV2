package com.verto.app.feature.inventory.data

import com.verto.app.feature.inventory.domain.port.ReverseShipmentReceiptsPort
import javax.inject.Inject

class RoomReverseShipmentReceiptsAdapter @Inject constructor(
    private val stockWriter: InventoryStockWriter,
) : ReverseShipmentReceiptsPort {
    override suspend fun reverse(shipmentId: String): Result<Unit> =
        stockWriter.reverseShipment(shipmentId)
}
