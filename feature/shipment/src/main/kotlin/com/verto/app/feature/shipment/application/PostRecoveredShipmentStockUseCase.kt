package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsInventoryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryPostingPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

/** Posts recovered goods with a distinct deterministic posting id and recovery-only landed cost. */
class PostRecoveredShipmentStockUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val inventory: LogisticsInventoryPostingPort,
    private val calculateRecoveryCost: CalculateRecoveryLandedCostUseCase,
) {
    suspend operator fun invoke(organizationId: String, shipmentId: String, recoveryId: String): List<LogisticsRecoveryPosting> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(recoveryId.isNotBlank()) { "recoveryId is required" }
        val aggregate = store.getShipment(organizationId, shipmentId) ?: error("Logistics shipment not found")
        require(aggregate.shipment.state == LogisticsShipmentState.CLOSED) { "Recovery inventory posting requires a closed shipment" }
        val recovery = aggregate.recoveries.singleOrNull { it.id == recoveryId } ?: error("Recovery not found")
        val recoveryLines = store.getRecoveryLines(organizationId, recoveryId)
        require(recoveryLines.isNotEmpty()) { "Recovery has no lines to post" }
        val shipmentLines = aggregate.lines.associateBy { it.id }
        val sources = aggregate.sources.associateBy { it.invoiceId }

        return recoveryLines.map { line ->
            store.getRecoveryPosting(organizationId, line.id) ?: run {
                val shipmentLine = shipmentLines[line.shipmentLineId] ?: error("Recovered shipment line not found")
                val supplierId = sources[shipmentLine.sourceInvoiceId]?.supplierId
                    ?: error("Recovered shipment source supplier not found")
                val posting = LogisticsRecoveryPosting(
                    organizationId = organizationId,
                    postingId = postingId(recoveryId, line.id),
                    recoveryId = recoveryId,
                    recoveryLineId = line.id,
                    shipmentId = shipmentId,
                    shipmentLineId = line.shipmentLineId,
                    quantity = line.recoveredQuantity,
                ).also(LogisticsValidation::validateRecoveryPosting)
                inventory.postAcceptedStock(
                    LogisticsInventoryPosting(
                        postingId = posting.postingId,
                        shipmentId = shipmentId,
                        receivingBatchId = "recovery:$recoveryId",
                        receivingLineId = line.id,
                        itemId = shipmentLine.inventoryItemId,
                        quantity = line.recoveredQuantity,
                        supplierId = supplierId,
                        unitPrice = calculateRecoveryCost.landedUnitPrice(line),
                        note = "Recovered missing goods • ${recovery.note}",
                    ),
                )
                store.saveRecoveryPosting(posting)
            }
        }
    }

    internal fun postingId(recoveryId: String, recoveryLineId: String): String =
        "logistics-recovery:$recoveryId:$recoveryLineId"
}
