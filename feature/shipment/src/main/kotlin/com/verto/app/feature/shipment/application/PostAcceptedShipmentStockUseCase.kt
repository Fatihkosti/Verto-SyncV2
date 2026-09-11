package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsInventoryPosting
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryPostingPort
import javax.inject.Inject

class PostAcceptedShipmentStockUseCase @Inject constructor(
    private val inventory: LogisticsInventoryPostingPort,
) {
    suspend operator fun invoke(posting: LogisticsInventoryPosting) {
        require(posting.postingId.isNotBlank()) { "postingId is required" }
        require(posting.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(posting.receivingBatchId.isNotBlank()) { "receivingBatchId is required" }
        require(posting.receivingLineId.isNotBlank()) { "receivingLineId is required" }
        require(posting.itemId.isNotBlank()) { "itemId is required" }
        require(posting.supplierId.isNotBlank()) { "supplierId is required" }
        require(posting.quantity > 0) { "Only positive accepted quantity can be posted" }
        require(posting.unitPrice.signum() >= 0) { "unitPrice must be non-negative" }
        inventory.postAcceptedStock(posting)
    }
}
