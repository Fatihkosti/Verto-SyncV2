package com.verto.app.feature.shipment.application

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class MarkLogisticsShipmentReadyUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val purchaseInvoices: LogisticsPurchaseInvoiceQueryPort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(requestId.isNotBlank()) { "requestId is required" }

        val aggregate = store.getShipment(organizationId, shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, requestId)) return aggregate.shipment

        LogisticsValidation.validateReady(aggregate)
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.READY)

        val linesByInvoice = aggregate.lines.groupBy { it.sourceInvoiceId }
        aggregate.sources.forEach { source ->
            val invoice = purchaseInvoices.getPurchaseInvoice(
                organizationId = organizationId,
                invoiceId = source.invoiceId,
                excludeShipmentId = shipmentId,
            ) ?: throw BusinessRuleFailureException("SHIPMENT_PURCHASE_INVOICE_UNAVAILABLE", target = source.invoiceId)
            val invoiceLines = invoice.lines.associateBy { it.invoiceItemId }
            val selectedLines = linesByInvoice[source.invoiceId].orEmpty()
            require(selectedLines.map { it.sourceInvoiceItemId }.toSet() == invoice.lines.map { it.invoiceItemId }.toSet()) {
                "v230 planning links the whole invoice; item-level partial allocation is not supported"
            }
            selectedLines.forEach { line ->
                val snapshot = invoiceLines[line.sourceInvoiceItemId]
                    ?: error("Purchase invoice line not found: ${line.sourceInvoiceItemId}")
                require(snapshot.inventoryItemId.isNotBlank()) {
                    "Purchase invoice line inventory identity is unresolved: ${line.sourceInvoiceItemId}"
                }
                require(line.inventoryItemId == snapshot.inventoryItemId) {
                    "Shipment line inventory identity no longer matches the purchase invoice line"
                }
                require(line.expectedQuantity == snapshot.remainingShippableQuantity) {
                    "v230 planning links the full remaining invoice line; partial item allocation is outside scope"
                }
            }
        }

        val readyAt = clock.now()
        val ready = aggregate.shipment.copy(state = LogisticsShipmentState.READY)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = shipmentId,
            type = LogisticsEventType.SHIPMENT_READY,
            occurredAt = readyAt,
            employeeId = ready.assignee?.employeeId,
            employeeNameSnapshot = ready.assignee?.employeeName,
            requestId = requestId,
            payload = mapOf("transportMode" to requireNotNull(ready.transportMode).name),
        )
        store.saveShipmentState(ready, event)
        return ready
    }
}
