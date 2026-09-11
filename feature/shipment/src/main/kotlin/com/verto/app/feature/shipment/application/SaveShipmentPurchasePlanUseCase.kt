package com.verto.app.feature.shipment.application

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.feature.shipment.domain.model.LogisticsPurchaseInvoiceSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

data class SaveShipmentPurchasePlanCommand(
    val shipmentId: String,
    val sources: List<LogisticsShipmentSource>,
    val lines: List<LogisticsShipmentLine>,
)

/** Local-only application boundary for Screen 2. */
class SaveShipmentPurchasePlanUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val purchaseInvoices: LogisticsPurchaseInvoiceQueryPort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(organizationId: String, command: SaveShipmentPurchasePlanCommand): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        LogisticsValidation.validatePurchasePlanDraft(command.sources, command.lines, null, null, null)
        val current = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        require(current.shipment.state == LogisticsShipmentState.DRAFT) { "Purchase planning can be saved only while shipment is DRAFT" }
        val requestedByInvoice = command.sources.associateBy { it.invoiceId }
        require(requestedByInvoice.size == command.sources.size) { "Duplicate purchase sources are not allowed" }
        val snapshots = requestedByInvoice.keys.associateWith { invoiceId ->
            purchaseInvoices.getPurchaseInvoice(organizationId, invoiceId, current.shipment.id)
                ?: throw BusinessRuleFailureException("SHIPMENT_PURCHASE_INVOICE_UNAVAILABLE", target = invoiceId)
        }
        val persistedSources = snapshotSources(current, command.sources, snapshots)
        val persistedLines = snapshotLines(current, command.lines, persistedSources, snapshots)
        val updated = current.shipment.copy(
            expectedDepartureAt = null,
            expectedArrivalAt = null,
            transportDetails = null,
        )
        store.savePurchasePlan(updated, persistedSources, persistedLines)
        return updated
    }

    private fun snapshotSources(
        current: LogisticsShipmentAggregate,
        requested: List<LogisticsShipmentSource>,
        snapshots: Map<String, LogisticsPurchaseInvoiceSnapshot>,
    ): List<LogisticsShipmentSource> {
        val existingIds = current.sources.associate { it.invoiceId to it.id }
        return requested.map { source ->
            val invoice = snapshots.getValue(source.invoiceId)
            validateInternationalMoneySnapshot(invoice)
            require(source.supplierId == invoice.supplierId) { "Invoice ${source.invoiceId} does not belong to selected supplier ${source.supplierId}" }
            current.sources.firstOrNull { it.invoiceId == invoice.invoiceId }?.let { existing ->
                require(existing.supplierId == invoice.supplierId) { "Existing invoice supplier cannot change silently" }
            }
            LogisticsShipmentSource(
                id = existingIds[invoice.invoiceId] ?: identities.newId(), shipmentId = current.shipment.id,
                invoiceId = invoice.invoiceId, supplierId = invoice.supplierId, supplierNameSnapshot = invoice.supplierName,
                invoiceNumberSnapshot = invoice.invoiceNumber, originalCurrency = invoice.currency,
                exchangeRateSnapshot = invoice.exchangeRate,
                plannedPackageCount = source.plannedPackageCount,
                plannedWeightKg = source.plannedWeightKg,
                expectedReadyAt = source.expectedReadyAt,
            ).also(LogisticsValidation::validateSource)
        }
    }

    private fun snapshotLines(
        current: LogisticsShipmentAggregate,
        requested: List<LogisticsShipmentLine>,
        sources: List<LogisticsShipmentSource>,
        snapshots: Map<String, LogisticsPurchaseInvoiceSnapshot>,
    ): List<LogisticsShipmentLine> {
        require(requested.map { it.sourceInvoiceId to it.sourceInvoiceItemId }.distinct().size == requested.size) { "Duplicate purchase invoice lines are not allowed" }
        require(sources.all { source -> requested.any { it.sourceInvoiceId == source.invoiceId } }) { "Every selected invoice must contribute its full snapshot" }
        sources.forEach { source ->
            val expectedItems = snapshots.getValue(source.invoiceId).lines.map { it.invoiceItemId }.toSet()
            val selectedItems = requested.filter { it.sourceInvoiceId == source.invoiceId }.map { it.sourceInvoiceItemId }.toSet()
            require(selectedItems == expectedItems) { "v230 planning links the whole invoice; item-level allocation is outside scope" }
        }
        val sourceIds = sources.map { it.invoiceId }.toSet()
        val existingIds = current.lines.associate { (it.sourceInvoiceId to it.sourceInvoiceItemId) to it.id }
        return requested.map { line ->
            require(line.sourceInvoiceId in sourceIds) { "Shipment line references an invoice outside the selected sources" }
            val invoice = snapshots.getValue(line.sourceInvoiceId)
            val invoiceLine = invoice.lines.singleOrNull { it.invoiceItemId == line.sourceInvoiceItemId }
                ?: error("Purchase invoice line not found: ${line.sourceInvoiceItemId}")
            require(line.expectedQuantity == invoiceLine.remainingShippableQuantity) {
                "v230 planning links the full remaining invoice line; partial item allocation is outside scope"
            }
            LogisticsShipmentLine(
                id = existingIds[invoice.invoiceId to invoiceLine.invoiceItemId] ?: identities.newId(), shipmentId = current.shipment.id,
                sourceInvoiceId = invoice.invoiceId, sourceInvoiceItemId = invoiceLine.invoiceItemId,
                inventoryItemId = invoiceLine.inventoryItemId, itemNameSnapshot = invoiceLine.itemName,
                expectedQuantity = line.expectedQuantity, basePurchaseUnitPrice = invoiceLine.unitPrice, hsCode = line.hsCode,
            ).also(LogisticsValidation::validateLine)
        }
    }

    private fun validateInternationalMoneySnapshot(invoice: LogisticsPurchaseInvoiceSnapshot) {
        val currency = requireNotNull(invoice.currency) { "International purchase invoice currency is required" }
        val rate = requireNotNull(invoice.exchangeRate) { "International purchase invoice exchange rate is required" }
        LogisticsCurrencyPolicy.requireRate(currency, rate, invoice.invoiceDate.takeIf { it > 0L })
        require(invoice.lines.all { it.unitPrice.signum() >= 0 }) { "Functional purchase-unit prices must be non-negative" }
    }

}
