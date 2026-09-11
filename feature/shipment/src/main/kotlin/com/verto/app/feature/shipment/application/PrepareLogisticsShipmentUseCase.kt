package com.verto.app.feature.shipment.application

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsTransportDetails
import com.verto.app.feature.shipment.domain.model.PrepareLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.port.AssigneeDirectoryPort
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class PrepareLogisticsShipmentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val purchaseInvoices: LogisticsPurchaseInvoiceQueryPort,
    private val assignees: AssigneeDirectoryPort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: PrepareLogisticsShipmentCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }

        val existing = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return existing.shipment
        require(existing.shipment.state == LogisticsShipmentState.DRAFT) {
            "Draft planning can be saved only while shipment is DRAFT"
        }

        require(command.sources.isNotEmpty()) { "At least one purchase source is required" }
        require(command.lines.isNotEmpty()) { "At least one shipment line is required" }

        val employeeId = command.assignee.employeeId.trim()
        require(employeeId.isNotBlank()) { "assignee.employeeId is required" }
        val employee = assignees.getEmployee(organizationId, employeeId)
            ?: error("Assigned employee was not found in this organization")
        require(employee.active) { "Assigned employee is inactive" }
        require(employee.employeeName.isNotBlank()) { "Assigned employee name is required" }
        val assignee = LogisticsAssigneeSnapshot(employee.employeeId, employee.employeeName)

        require(command.sources.all { it.invoiceId.isNotBlank() }) { "source.invoiceId is required" }
        require(command.lines.all { it.sourceInvoiceId.isNotBlank() && it.sourceInvoiceItemId.isNotBlank() }) {
            "Shipment line purchase references are required"
        }
        require(command.lines.map { it.sourceInvoiceId to it.sourceInvoiceItemId }.distinct().size == command.lines.size) {
            "Duplicate purchase invoice lines are not allowed"
        }

        val sourceRequests = command.sources.associateBy { source -> source.invoiceId }
        require(sourceRequests.size == command.sources.size) { "Duplicate purchase sources are not allowed" }

        val snapshots = sourceRequests.keys.associateWith { invoiceId ->
            purchaseInvoices.getPurchaseInvoice(
                organizationId = organizationId,
                invoiceId = invoiceId,
                excludeShipmentId = existing.shipment.id,
            ) ?: throw BusinessRuleFailureException("SHIPMENT_PURCHASE_INVOICE_UNAVAILABLE", target = invoiceId)
        }

        val existingSourceIds = existing.sources.associate { it.invoiceId to it.id }
        val persistedSources = command.sources.map { requested ->
            val invoice = snapshots.getValue(requested.invoiceId)
            validateInternationalMoneySnapshot(invoice)
            require(requested.supplierId == invoice.supplierId) {
                "Invoice ${requested.invoiceId} does not belong to selected supplier ${requested.supplierId}"
            }
            existing.sources.firstOrNull { it.invoiceId == invoice.invoiceId }?.let { persisted ->
                require(persisted.supplierId == invoice.supplierId) { "Existing invoice supplier cannot change silently" }
            }
            LogisticsShipmentSource(
                id = existingSourceIds[invoice.invoiceId] ?: identities.newId(),
                shipmentId = existing.shipment.id,
                invoiceId = invoice.invoiceId,
                supplierId = invoice.supplierId,
                supplierNameSnapshot = invoice.supplierName,
                invoiceNumberSnapshot = invoice.invoiceNumber,
                originalCurrency = invoice.currency,
                exchangeRateSnapshot = invoice.exchangeRate,
            ).also(LogisticsValidation::validateSource)
        }

        require(command.sources.all { source -> command.lines.any { it.sourceInvoiceId == source.invoiceId } }) {
            "Every selected purchase source must contribute its full invoice snapshot"
        }
        command.sources.forEach { source ->
            val invoice = snapshots.getValue(source.invoiceId)
            val selectedItems = command.lines.filter { it.sourceInvoiceId == source.invoiceId }.map { it.sourceInvoiceItemId }.toSet()
            require(selectedItems == invoice.lines.map { it.invoiceItemId }.toSet()) {
                "v230 planning links the whole invoice; item-level partial allocation is outside scope"
            }
        }

        val existingLineIds = existing.lines.associate { (it.sourceInvoiceId to it.sourceInvoiceItemId) to it.id }
        val persistedLines = command.lines.map { requested ->
            require(requested.expectedQuantity > 0) { "expectedQuantity must be positive" }
            require(requested.sourceInvoiceId in snapshots) {
                "Shipment line references a purchase invoice outside the selected sources"
            }
            val invoice = snapshots.getValue(requested.sourceInvoiceId)
            val invoiceLine = invoice.lines.singleOrNull { it.invoiceItemId == requested.sourceInvoiceItemId }
                ?: error("Purchase invoice line not found: ${requested.sourceInvoiceItemId}")
            require(invoiceLine.remainingShippableQuantity > 0) {
                "Purchase invoice line has no remaining shippable quantity"
            }
            require(requested.expectedQuantity == invoiceLine.remainingShippableQuantity) {
                "v230 planning links the full remaining invoice line; partial allocation is outside scope"
            }
            LogisticsShipmentLine(
                id = existingLineIds[invoice.invoiceId to invoiceLine.invoiceItemId] ?: identities.newId(),
                shipmentId = existing.shipment.id,
                sourceInvoiceId = invoice.invoiceId,
                sourceInvoiceItemId = invoiceLine.invoiceItemId,
                inventoryItemId = invoiceLine.inventoryItemId,
                itemNameSnapshot = invoiceLine.itemName,
                expectedQuantity = requested.expectedQuantity,
                basePurchaseUnitPrice = invoiceLine.unitPrice,
                hsCode = requested.hsCode,
            ).also(LogisticsValidation::validateLine)
        }

        val transportDetails = command.transportDetails.normalizedGeneralDetails()
        transportDetails?.let(LogisticsValidation::validateTransportDetails)

        val preparedAt = clock.now()
        val plannedShipment = existing.shipment.copy(
            state = LogisticsShipmentState.DRAFT,
            assignee = assignee,
            expectedDepartureAt = command.expectedDepartureAt,
            expectedArrivalAt = command.expectedArrivalAt,
            transportDetails = transportDetails,
        )

        val activeAssignment = existing.assignments.singleOrNull { it.endedAt == null }
        val assignment = if (activeAssignment?.employeeId == assignee.employeeId) {
            activeAssignment.copy(employeeNameSnapshot = assignee.employeeName)
        } else {
            LogisticsAssignment(
                id = identities.newId(),
                shipmentId = plannedShipment.id,
                employeeId = assignee.employeeId,
                employeeNameSnapshot = assignee.employeeName,
                assignedAt = preparedAt,
            )
        }
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = plannedShipment.id,
            type = LogisticsEventType.SHIPMENT_PLAN_UPDATED,
            occurredAt = preparedAt,
            employeeId = assignee.employeeId,
            employeeNameSnapshot = assignee.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "sourceCount" to persistedSources.size.toString(),
                "lineCount" to persistedLines.size.toString(),
            ),
        )

        store.savePlanning(
            plannedShipment,
            persistedSources,
            persistedLines,
            assignment,
            event,
        )
        return plannedShipment
    }

    private fun LogisticsTransportDetails?.normalizedGeneralDetails(): LogisticsTransportDetails? {
        if (this == null) return null
        val normalized = copy(
            incotermCode = incotermCode?.trim()?.takeIf { it.isNotBlank() },
            containerNumber = null,
            billOrAirwayNumber = null,
            vesselOrFlightReference = null,
            insuranceReference = insuranceReference?.trim()?.takeIf { it.isNotBlank() },
        )
        val empty = normalized.incotermCode == null &&
            normalized.weightKg == null &&
            normalized.volumeM3 == null &&
            normalized.packageCount == null &&
            normalized.palletCount == null &&
            normalized.insuranceReference == null
        return normalized.takeUnless { empty }
    }

    private fun validateInternationalMoneySnapshot(invoice: com.verto.app.feature.shipment.domain.model.LogisticsPurchaseInvoiceSnapshot) {
        val currency = requireNotNull(invoice.currency) { "International purchase invoice currency is required" }
        val rate = requireNotNull(invoice.exchangeRate) { "International purchase invoice exchange rate is required" }
        LogisticsCurrencyPolicy.requireRate(currency, rate, invoice.invoiceDate.takeIf { it > 0L })
        require(invoice.lines.all { it.unitPrice.signum() >= 0 }) { "Functional purchase-unit prices must be non-negative" }
    }

}
