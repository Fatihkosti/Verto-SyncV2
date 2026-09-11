package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.utils.ErrorHumanizer
import com.verto.app.feature.shipment.application.CalculateShipmentLandedCostUseCase
import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.ResolveLogisticsOperationalStatusUseCase
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver

internal class LogisticsV2OperationalReads @javax.inject.Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val calculateLandedCost: CalculateShipmentLandedCostUseCase,
    private val resolveOperationalStatus: ResolveLogisticsOperationalStatusUseCase,
) {
    suspend fun organizationId(): String = readService.organizationId()

    suspend fun aggregate(shipmentId: String): LogisticsShipmentAggregate =
        readService.aggregate(shipmentId)

    suspend fun detail(shipmentId: String): LogisticsShipmentDetailUi {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val operationalStatus = resolveOperationalStatus(organizationId, shipmentId)
        val operationalDelay = resolveOperationalStatus.delay(organizationId, shipmentId)
        val presentation = readService.presentationSnapshot(organizationId, aggregate)
        val firstLeg = aggregate.legs.minByOrNull { it.sequence }
        val firstCarrier = firstLeg?.carrierPartnerId
            ?.takeIf(String::isNotBlank)
            ?.let { readService.partner(organizationId, it) }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate)
        val custody = aggregate.sources.map { source ->
            val position = positions.getValue(source.id)
            val holderId = position.holderId
            val phone = if (
                position.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER && holderId != null
            ) readService.partner(organizationId, holderId)?.phone else null
            LogisticsSourceCustodyUi(
                sourceId = source.id,
                invoiceNumber = source.invoiceNumberSnapshot,
                supplierName = source.supplierNameSnapshot,
                holderType = position.holderType,
                holderId = position.holderId,
                holderName = position.holderName,
                phone = phone,
                atFirstCarrier = firstCarrier != null &&
                    position.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER &&
                    position.holderId == firstCarrier.id,
            )
        }
        val carriers = readService.partners(organizationId).filter {
            it.role == LogisticsPartnerRole.CARRIER || it.role == LogisticsPartnerRole.FREIGHT_FORWARDER
        }
        return aggregate.toDetailUi(
            operationalStatus = operationalStatus,
            operationalDelay = operationalDelay,
            sourceCustody = custody,
            firstCarrierId = firstCarrier?.id,
            firstCarrierName = firstCarrier?.name,
            startReady = aggregate.sources.isNotEmpty() && custody.all { it.atFirstCarrier },
            availableCarriers = carriers,
            timeline = presentation.timeline,
            closedSummary = presentation.closed,
        )
    }

    suspend fun planning(shipmentId: String): LogisticsPlanningUiState.Content {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        require(aggregate.shipment.state == LogisticsShipmentState.DRAFT) { "التخطيط متاح للمسودة فقط" }

        val employees = readService.activeEmployees()
            .map { LogisticsEmployeeOption(it.id, it.name) }

        var purchaseInvoiceLoadError: String? = null
        val recentInvoiceIds = runCatching { readService.recentPurchaseInvoiceIds(0L, System.currentTimeMillis()) }
            .getOrElse { error ->
                purchaseInvoiceLoadError = ErrorHumanizer.humanize(error, "تحميل الفواتير الدولية")
                emptyList()
            }
        val purchaseOptions = mutableListOf<LogisticsPurchaseInvoiceOptionUi>()
        val invoiceIds = (recentInvoiceIds + aggregate.sources.map { it.invoiceId }).distinct()
        for (invoiceId in invoiceIds) {
            val snapshot = runCatching {
                readService.purchaseInvoice(
                    organizationId = organizationId,
                    invoiceId = invoiceId,
                    excludeShipmentId = shipmentId,
                )
            }.getOrElse { error ->
                purchaseInvoiceLoadError = ErrorHumanizer.humanize(error, "تحميل بعض الفواتير الدولية")
                null
            } ?: continue
            val remainingLines = snapshot.lines.filter { it.remainingShippableQuantity > 0 }
            if (remainingLines.isEmpty()) continue
            purchaseOptions += LogisticsPurchaseInvoiceOptionUi(
                source = LogisticsShipmentSource(
                    id = "ui-source:${snapshot.invoiceId}",
                    shipmentId = shipmentId,
                    invoiceId = snapshot.invoiceId,
                    supplierId = snapshot.supplierId,
                    supplierNameSnapshot = snapshot.supplierName,
                    invoiceNumberSnapshot = snapshot.invoiceNumber,
                    originalCurrency = snapshot.currency,
                    exchangeRateSnapshot = snapshot.exchangeRate,
                    plannedPackageCount = aggregate.sources.firstOrNull { it.invoiceId == snapshot.invoiceId }?.plannedPackageCount,
                    plannedWeightKg = aggregate.sources.firstOrNull { it.invoiceId == snapshot.invoiceId }?.plannedWeightKg,
                    expectedReadyAt = aggregate.sources.firstOrNull { it.invoiceId == snapshot.invoiceId }?.expectedReadyAt,
                ),
                lines = remainingLines.map { line ->
                    LogisticsShipmentLine(
                        id = "ui-line:${line.invoiceItemId}",
                        shipmentId = shipmentId,
                        sourceInvoiceId = snapshot.invoiceId,
                        sourceInvoiceItemId = line.invoiceItemId,
                        inventoryItemId = line.inventoryItemId,
                        itemNameSnapshot = line.itemName,
                        expectedQuantity = line.remainingShippableQuantity,
                        basePurchaseUnitPrice = line.unitPrice,
                    )
                },
                invoiceDate = snapshot.invoiceDate,
                totalAmount = snapshot.totalAmount,
            )
        }

        val routeMilestones = if (aggregate.milestones.isEmpty()) {
            listOf(
                LogisticsMilestone(
                    id = "ui-origin:$shipmentId",
                    shipmentId = shipmentId,
                    type = LogisticsMilestoneType.ORIGIN,
                    order = 0,
                    location = aggregate.shipment.sourceLocation,
                    plannedDepartureAt = aggregate.shipment.expectedDepartureAt,
                ),
                LogisticsMilestone(
                    id = "ui-destination:$shipmentId",
                    shipmentId = shipmentId,
                    type = LogisticsMilestoneType.DESTINATION,
                    order = 1,
                    location = aggregate.shipment.destinationLocation,
                    plannedArrivalAt = aggregate.shipment.expectedArrivalAt,
                ),
            )
        } else aggregate.milestones.sortedBy { it.order }.mapIndexed { index, milestone -> milestone.copy(order = index) }
        val carriers = readService.partners(organizationId).filter {
            it.role == LogisticsPartnerRole.CARRIER || it.role == LogisticsPartnerRole.FREIGHT_FORWARDER
        }
        val resolvedInvoiceLines = purchaseOptions.flatMap { it.lines }
            .associateBy { it.sourceInvoiceId to it.sourceInvoiceItemId }
        val identityReconciledLines = aggregate.lines.map { line ->
            val resolved = resolvedInvoiceLines[line.sourceInvoiceId to line.sourceInvoiceItemId]
            line.copy(inventoryItemId = resolved?.inventoryItemId ?: line.inventoryItemId)
        }
        val hasUnresolvedInventoryIdentity = purchaseOptions.any { option ->
            option.lines.any { it.inventoryItemId.isBlank() }
        } || identityReconciledLines.any { it.inventoryItemId.isBlank() }
        val inventoryCatalog = if (hasUnresolvedInventoryIdentity) readService.inventoryCatalog() else emptyList()
        return LogisticsPlanningUiState.Content(
            shipmentId = shipmentId,
            shipmentNumber = aggregate.shipment.shipmentNumber,
            initialDraft = LogisticsPlanningDraft(
                organizationId = organizationId,
                shipmentId = shipmentId,
                sourceLocation = aggregate.shipment.sourceLocation,
                destinationLocation = aggregate.shipment.destinationLocation,
                transportMode = aggregate.shipment.transportMode,
                assigneeId = aggregate.shipment.assignee?.employeeId.orEmpty(),
                assigneeName = aggregate.shipment.assignee?.employeeName.orEmpty(),
                expectedDepartureAt = aggregate.shipment.expectedDepartureAt,
                expectedArrivalAt = aggregate.shipment.expectedArrivalAt,
                transportDetails = aggregate.shipment.transportDetails,
                sources = aggregate.sources,
                lines = identityReconciledLines,
                milestones = routeMilestones,
                legs = aggregate.legs,
                carriers = carriers,
                costs = aggregate.costs,
                documents = aggregate.documents,
            ),
            employees = employees,
            purchaseInvoices = purchaseOptions,
            inventoryCatalog = inventoryCatalog,
            carriers = carriers,
            customsPlan = aggregate.customsPlan,
            customsPlanDocuments = aggregate.customsPlanDocuments,
            customsCheckpointSuggestions = readService.customsCheckpointSuggestions(),
            purchaseInvoiceLoadError = purchaseInvoiceLoadError,
        )
    }

    suspend fun receiving(shipmentId: String): LogisticsReceivingUiState.Content {
        val aggregate = aggregate(shipmentId)
        val receivedByLine = aggregate.receivingBatches.flatMap { it.lines }
            .groupBy { it.shipmentLineId }
            .mapValues { (_, lines) -> lines.sumOf { it.receivedQuantity } }
        val sourceByInvoice = aggregate.sources.associateBy { it.invoiceId }
        return LogisticsReceivingUiState.Content(
            shipmentId = shipmentId,
            shipmentNumber = aggregate.shipment.shipmentNumber,
            lines = aggregate.lines.map { line ->
                val source = sourceByInvoice[line.sourceInvoiceId]
                LogisticsReceivingLineDraft(
                    shipmentLineId = line.id,
                    sourceInvoiceId = line.sourceInvoiceId,
                    sourceInvoiceNumber = source?.invoiceNumberSnapshot.orEmpty(),
                    itemName = line.itemNameSnapshot,
                    expectedQuantity = line.expectedQuantity,
                    alreadyReceivedQuantity = receivedByLine[line.id] ?: 0,
                )
            }.filter { it.remainingBeforeBatch > 0 },
        )
    }

    suspend fun costs(shipmentId: String): LogisticsCostsUiState.Content {
        val aggregate = aggregate(shipmentId)
        val actualTotal = calculateLandedCost.totalActualCost(aggregate)
        val previewAllocations = if (
            aggregate.costAllocations.isEmpty() &&
            actualTotal.signum() > 0 &&
            aggregate.shipment.state == LogisticsShipmentState.RECEIVED
        ) {
            runCatching { calculateLandedCost(aggregate) }.getOrDefault(emptyList())
        } else aggregate.costAllocations

        return LogisticsCostsUiState.Content(
            shipmentId = aggregate.shipment.id,
            shipmentNumber = aggregate.shipment.shipmentNumber,
            settlement = LogisticsCostSettlementUi(
                costs = aggregate.costs,
                allocations = previewAllocations,
                actualTotal = actualTotal,
                settled = actualTotal.signum() == 0 || aggregate.costAllocations.isNotEmpty(),
                sources = aggregate.sources,
                milestones = aggregate.milestones.sortedBy { it.order },
                legs = aggregate.legs.sortedBy { it.sequence },
            ),
        )
    }
}
