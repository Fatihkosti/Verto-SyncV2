package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingBatch
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordLogisticsReceivingBatchCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsReceivingTransactionPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class RecordShipmentReceivingBatchUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val receivingTransaction: LogisticsReceivingTransactionPort,
) {
    suspend operator fun invoke(organizationId: String, command: RecordLogisticsReceivingBatchCommand): LogisticsReceivingBatch {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.batchId.isNotBlank()) { "batchId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.receivedAt >= 0L) { "receivedAt must be non-negative" }
        require(command.receivedByEmployeeId.isNotBlank()) { "receivedByEmployeeId is required" }
        require(command.receivedByEmployeeNameSnapshot.isNotBlank()) { "receivedByEmployeeNameSnapshot is required" }
        require(command.lines.isNotEmpty()) { "At least one receiving line is required" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) {
            return aggregate.receivingBatches.singleOrNull { it.requestId == command.requestId }
                ?: error("Processed receiving request has no persisted batch")
        }
        require(aggregate.shipment.state == LogisticsShipmentState.RECEIVING) { "Shipment must be RECEIVING" }
        require(command.lines.all { it.shipmentId == command.shipmentId }) { "Receiving line belongs to another shipment" }
        require(command.lines.map { it.shipmentLineId }.toSet().size == command.lines.size) { "Duplicate shipment line in batch" }

        val lineById = aggregate.lines.associateBy { it.id }
        val cumulativeBefore = aggregate.receivingBatches.flatMap { it.lines }
            .groupBy { it.shipmentLineId }
            .mapValues { (_, lines) -> lines.sumOf { it.receivedQuantity } }
        command.lines.forEach { line ->
            val sourceLine = lineById[line.shipmentLineId] ?: error("Shipment line not found")
            require(line.expectedQuantitySnapshot == sourceLine.expectedQuantity) { "expectedQuantitySnapshot mismatch" }
            LogisticsValidation.validateReceivingLine(line, cumulativeBefore[line.shipmentLineId] ?: 0)
        }

        val sourceByInvoice = aggregate.sources.associateBy { it.invoiceId }
        val postings = command.lines.filter { it.acceptedQuantity > 0 }.map { received ->
            val shipmentLine = lineById.getValue(received.shipmentLineId)
            val source = sourceByInvoice[shipmentLine.sourceInvoiceId] ?: error("Purchase source not found")
            LogisticsInventoryPosting(
                postingId = postingId(command.batchId, received.id),
                shipmentId = command.shipmentId,
                receivingBatchId = command.batchId,
                receivingLineId = received.id,
                itemId = shipmentLine.inventoryItemId,
                quantity = received.acceptedQuantity,
                supplierId = source.supplierId,
                unitPrice = shipmentLine.basePurchaseUnitPrice,
                note = "Accepted Logistics V2 receipt",
            ).also(::validatePosting)
        }

        val batch = LogisticsReceivingBatch(
            id = command.batchId,
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            requestId = command.requestId,
            receivedAt = command.receivedAt,
            receivedByEmployeeId = command.receivedByEmployeeId,
            receivedByEmployeeNameSnapshot = command.receivedByEmployeeNameSnapshot,
            lines = command.lines,
        )
        val receivedNow = command.lines.associate { it.shipmentLineId to it.receivedQuantity }
        val complete = aggregate.lines.all { line ->
            (cumulativeBefore[line.id] ?: 0) + (receivedNow[line.id] ?: 0) == line.expectedQuantity
        }
        val nextState = LogisticsShipmentState.RECEIVING
        val updated = aggregate.shipment
        val event = LogisticsEvent(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
            type = LogisticsEventType.RECEIVING_RECORDED, occurredAt = command.receivedAt,
            employeeId = command.receivedByEmployeeId,
            employeeNameSnapshot = command.receivedByEmployeeNameSnapshot,
            requestId = command.requestId,
            payload = mapOf("batchId" to command.batchId, "state" to nextState.name, "complete" to complete.toString()),
        )
        receivingTransaction.commitReceiving(
            postings = postings,
            batch = batch,
            updatedShipment = updated,
            event = event,
        )
        return batch
    }

    private fun validatePosting(posting: LogisticsInventoryPosting) {
        require(posting.postingId.isNotBlank()) { "postingId is required" }
        require(posting.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(posting.receivingBatchId.isNotBlank()) { "receivingBatchId is required" }
        require(posting.receivingLineId.isNotBlank()) { "receivingLineId is required" }
        require(posting.itemId.isNotBlank()) { "itemId is required" }
        require(posting.supplierId.isNotBlank()) { "supplierId is required" }
        require(posting.quantity > 0) { "Only positive accepted quantity can be posted" }
        require(posting.unitPrice.signum() >= 0) { "unitPrice must be non-negative" }
    }

    internal fun postingId(batchId: String, receivingLineId: String): String =
        "logistics-receipt:$batchId:$receivingLineId"
}
