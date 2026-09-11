package com.verto.app.feature.inventory.data

import com.verto.app.feature.inventory.domain.port.ReceiveShipmentStockPort
import java.math.BigDecimal
import com.verto.app.money.Money
import javax.inject.Inject

class RoomReceiveShipmentStockAdapter @Inject constructor(
    private val stockWriter: InventoryStockWriter,
) : ReceiveShipmentStockPort {
    override suspend fun receive(
        postingId: String,
        shipmentId: String,
        receivingBatchId: String,
        receivingLineId: String,
        itemId: String,
        quantity: Int,
        supplierId: String,
        unitPrice: BigDecimal,
        note: String,
    ): Result<Unit> = stockWriter.receiveShipment(
        request = ShipmentStockReceiptRequest(
            postingId, shipmentId, receivingBatchId, receivingLineId, itemId, quantity,
        ),
        details = InventoryReceiptDetails(
            partyId = supplierId,
            unitPrice = Money.fromMajor(unitPrice).toLegacyDouble(),
            note = note,
        ),
    )
}
