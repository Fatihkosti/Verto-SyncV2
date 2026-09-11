package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsShortageIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageQuantity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageStatus
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class ReconcileShipmentShortageUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    data class MissingLine(val shipmentLineId: String, val missingQuantity: Int, val note: String)
    data class Command(val shipmentId: String, val detectedAt: Long, val lines: List<MissingLine>, val requestId: String)

    suspend operator fun invoke(organizationId: String, command: Command): LogisticsShipment {
        validateCommand(organizationId, command)
        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment
        require(aggregate.shipment.state == LogisticsShipmentState.RECEIVING) {
            "Shortage reconciliation requires RECEIVING or PARTIAL state"
        }

        val receivingByLine = aggregate.receivingBatches.flatMap { it.lines }.groupBy { it.shipmentLineId }
        val existingByLine = aggregate.shortages.associateBy { it.identity.shipmentLineId }
        val requestedByLine = command.lines.associateBy { it.shipmentLineId }
        val context = ReconciliationContext(organizationId, command)
        val shortages = aggregate.lines.mapNotNull { line ->
            shortageForLine(context, line, receivingByLine[line.id].orEmpty(), existingByLine[line.id], requestedByLine[line.id])
        }
        require(shortages.size == command.lines.size) { "Shortage input contains a line that does not belong to this shipment" }
        val persistedByLine = shortages.map { store.upsertShortage(it) }.associateBy { it.identity.shipmentLineId }
        requireReconciled(aggregate, receivingByLine, existingByLine + persistedByLine)

        val reconciled = aggregate.shipment
        store.saveShipmentState(reconciled, reconciliationEvent(organizationId, command, reconciled, shortages))
        return reconciled
    }

    private fun validateCommand(organizationId: String, command: Command) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.detectedAt >= 0L) { "detectedAt must be non-negative" }
        require(command.lines.isNotEmpty()) { "At least one missing line is required" }
        require(command.lines.map { it.shipmentLineId }.toSet().size == command.lines.size) { "Duplicate shortage line" }
        command.lines.forEach { line ->
            require(line.shipmentLineId.isNotBlank()) { "shipmentLineId is required" }
            require(line.missingQuantity > 0) { "missingQuantity must be positive" }
            require(line.note.isNotBlank()) { "Shortage note is required" }
        }
    }

    private fun shortageForLine(
        context: ReconciliationContext,
        line: LogisticsShipmentLine,
        received: List<com.verto.app.feature.shipment.domain.model.LogisticsReceivingLine>,
        existing: LogisticsShortage?,
        input: MissingLine?,
    ): LogisticsShortage? {
        val organizationId = context.organizationId
        val command = context.command
        val accounted = received.sumOf { classifiedQuantity(it) }
        require(accounted <= line.expectedQuantity.toLong()) { "Received classifications exceed expected quantity for line ${line.id}" }
        val expectedMissing = line.expectedQuantity.toLong() - accounted
        if (expectedMissing == 0L) {
            require(input == null) { "A fully accounted line cannot be marked missing" }
            return null
        }
        require(expectedMissing <= Int.MAX_VALUE) { "Missing quantity exceeds supported range" }
        requireNotNull(input) { "Every unresolved shipment line must be reconciled as missing" }
        require(input.missingQuantity.toLong() == expectedMissing) { "Missing quantity must equal the unresolved expected quantity" }
        validateImmutableSnapshot(existing, input, line)
        return LogisticsShortage(
            identity = LogisticsShortageIdentity(organizationId, existing?.identity?.id ?: identities.newId(), command.shipmentId, line.id),
            quantity = LogisticsShortageQuantity(
                existing?.quantity?.originalMissingQuantity ?: input.missingQuantity,
                input.missingQuantity,
                existing?.quantity?.basePurchaseUnitPriceSnapshot ?: line.basePurchaseUnitPrice,
            ),
            status = LogisticsShortageStatus.OPEN,
            detectedAt = existing?.detectedAt ?: command.detectedAt,
            note = input.note.trim(),
            requestId = lineRequestId(command.requestId, line.id),
        ).also(LogisticsValidation::validateShortage)
    }

    private fun validateImmutableSnapshot(existing: LogisticsShortage?, input: MissingLine, line: LogisticsShipmentLine) {
        if (existing == null) return
        require(existing.quantity.originalMissingQuantity == input.missingQuantity) { "Existing shortage original quantity cannot be rewritten" }
        require(existing.quantity.basePurchaseUnitPriceSnapshot == line.basePurchaseUnitPrice) { "Existing shortage purchase price snapshot cannot be rewritten" }
    }

    private fun requireReconciled(
        aggregate: LogisticsShipmentAggregate,
        receivingByLine: Map<String, List<com.verto.app.feature.shipment.domain.model.LogisticsReceivingLine>>,
        shortagesByLine: Map<String, LogisticsShortage>,
    ) {
        aggregate.lines.forEach { line ->
            val classified = receivingByLine[line.id].orEmpty().sumOf { classifiedQuantity(it) }
            val missing = shortagesByLine[line.id]?.quantity?.remainingMissingQuantity?.toLong() ?: 0L
            require(classified + missing == line.expectedQuantity.toLong()) { "Shipment line is not fully reconciled" }
        }
    }

    private fun classifiedQuantity(line: com.verto.app.feature.shipment.domain.model.LogisticsReceivingLine): Long =
        line.acceptedQuantity.toLong() + line.damagedQuantity + line.rejectedQuantity + line.quarantinedQuantity

    private fun reconciliationEvent(
        organizationId: String,
        command: Command,
        shipment: LogisticsShipment,
        shortages: List<LogisticsShortage>,
    ) = LogisticsEvent(
        id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
        type = LogisticsEventType.EXCEPTION_RECORDED, occurredAt = command.detectedAt,
        employeeId = shipment.assignee?.employeeId, employeeNameSnapshot = shipment.assignee?.employeeName,
        requestId = command.requestId,
        payload = mapOf(
            "kind" to "SHORTAGE_RECONCILED",
            "shortageCount" to shortages.size.toString(),
            "missingQuantity" to shortages.sumOf { it.quantity.remainingMissingQuantity }.toString(),
        ),
    )

    private data class ReconciliationContext(val organizationId: String, val command: Command)

    internal fun lineRequestId(requestId: String, shipmentLineId: String): String = "$requestId:shortage:$shipmentLineId"
}
