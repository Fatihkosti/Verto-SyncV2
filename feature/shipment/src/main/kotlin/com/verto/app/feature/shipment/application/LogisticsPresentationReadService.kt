package com.verto.app.feature.shipment.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.shipment.application.model.LogisticsActorRef
import com.verto.app.feature.shipment.application.model.LogisticsEmployeeRef
import com.verto.app.feature.shipment.application.model.LogisticsUnifiedReadRecord
import com.verto.app.feature.shipment.application.model.ShipmentClosedDetailReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedShortageReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedEmployeeReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedCostPaymentReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedAttachmentReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedRoutesReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedTimingReadModel
import com.verto.app.feature.shipment.application.model.ShipmentClosedValuesReadModel
import com.verto.app.feature.shipment.application.model.ShipmentEventReadRecord
import com.verto.app.feature.shipment.application.model.ShipmentPresentationSnapshot
import com.verto.app.feature.shipment.application.model.ShipmentTimelineItemKind
import com.verto.app.feature.shipment.application.model.ShipmentTimelineReadItem
import com.verto.app.feature.shipment.application.model.ShipmentTimelineTiming
import com.verto.app.feature.shipment.application.model.ShipmentTimelineState
import com.verto.app.feature.shipment.application.port.LogisticsPlanningReferencePort
import com.verto.app.feature.shipment.application.port.LogisticsUnifiedReadPort
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCountryNormalizer
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryCatalogItem
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsPurchaseInvoiceSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Application-service boundary used by Logistics presentation.
 * It is the single read path to low-level shipment ports and foreign-feature references.
 */
class LogisticsPresentationReadService @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val sessionReader: SessionReader,
    private val purchaseInvoices: LogisticsPurchaseInvoiceQueryPort,
    private val inventoryIdentities: LogisticsInventoryIdentityPort,
    private val planningReferences: LogisticsPlanningReferencePort,
    private val unifiedRead: LogisticsUnifiedReadPort,
) {
    fun observeOrganizationId(): Flow<String> = sessionReader.organizationId

    suspend fun actor(): LogisticsActorRef = combine(sessionReader.userId, sessionReader.userName) { id, name ->
        LogisticsActorRef(id.trim(), name.trim())
    }.first().also { require(it.id.isNotBlank() && it.name.isNotBlank()) { "هوية الموظف الحالي غير متاحة" } }

    suspend fun organizationId(): String = sessionReader.organizationId.first().trim().also {
        require(it.isNotBlank()) { "Organization is unavailable" }
    }

    suspend fun aggregate(shipmentId: String): LogisticsShipmentAggregate =
        store.getShipment(organizationId(), shipmentId) ?: error("لم يتم العثور على الشحنة")

    suspend fun aggregate(organizationId: String, shipmentId: String): LogisticsShipmentAggregate? =
        store.getShipment(organizationId, shipmentId)

    suspend fun partner(organizationId: String, partnerId: String?): LogisticsPartner? =
        partnerId?.let { store.getPartner(organizationId, it) }

    suspend fun partners(organizationId: String): List<LogisticsPartner> = store.listPartners(organizationId)

    suspend fun routeTemplates(organizationId: String): List<LogisticsRouteTemplate> = store.listRouteTemplates(organizationId)

    suspend fun routeTemplate(organizationId: String, templateId: String): LogisticsRouteTemplate? =
        store.getRouteTemplate(organizationId, templateId)

    suspend fun purchaseInvoice(
        organizationId: String,
        invoiceId: String,
        excludeShipmentId: String?,
    ): LogisticsPurchaseInvoiceSnapshot? = purchaseInvoices.getPurchaseInvoice(
        organizationId = organizationId,
        invoiceId = invoiceId,
        excludeShipmentId = excludeShipmentId,
    )

    suspend fun inventoryCatalog(): List<LogisticsInventoryCatalogItem> = inventoryIdentities.listCatalog()

    suspend fun activeEmployees(): List<LogisticsEmployeeRef> = planningReferences.activeEmployees()

    /**
     * v235 free-text country suggestions. No global country catalogue is exposed to the user.
     * Ranking is frequency first, then most-recent shipment usage.
     */
    suspend fun countrySuggestions(): List<String> {
        val organizationId = organizationId()
        data class Usage(var count: Int = 0, var lastUsedAt: Long = Long.MIN_VALUE, var displayName: String = "")
        val usage = linkedMapOf<String, Usage>()
        store.observeShipments(organizationId).first().forEach { shipment ->
            listOf(shipment.originLocationDetails, shipment.destinationLocationDetails).forEach { location ->
                val display = LogisticsCountryNormalizer.displayName(location?.countryNameSnapshot.orEmpty())
                val key = LogisticsCountryNormalizer.key(display)
                if (key.isNotBlank()) {
                    val current = usage.getOrPut(key) { Usage(displayName = display) }
                    current.count += 1
                    current.lastUsedAt = maxOf(current.lastUsedAt, shipment.createdAt)
                    if (display.isNotBlank()) current.displayName = display
                }
            }
        }
        return usage.values
            .sortedWith(compareByDescending<Usage> { it.count }.thenByDescending { it.lastUsedAt }.thenBy { it.displayName })
            .map { it.displayName }
            .take(50)
    }


    /** v238 free-text customs checkpoint suggestions, ranked by recent shipment usage. */
    suspend fun customsCheckpointSuggestions(): List<String> {
        val organizationId = organizationId()
        val checkpoints = mutableListOf<String>()
        store.observeShipments(organizationId).first()
            .sortedByDescending { it.createdAt }
            .forEach { shipment ->
                store.getCustomsPlan(organizationId, shipment.id)?.checkpointName?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(checkpoints::add)
            }
        return checkpoints.distinct().take(30)
    }

    suspend fun recentPurchaseInvoiceIds(fromInclusive: Long, toInclusive: Long): List<String> =
        planningReferences.recentPurchaseInvoiceIds(fromInclusive, toInclusive)

    fun observeUnified(organizationId: String): Flow<List<LogisticsUnifiedReadRecord>> =
        unifiedRead.observe(organizationId)

    suspend fun shipmentEvents(organizationId: String, shipmentId: String): List<ShipmentEventReadRecord> =
        unifiedRead.events(organizationId, shipmentId)

    suspend fun presentationSnapshot(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
    ): ShipmentPresentationSnapshot {
        val timeline = timeline(aggregate)
        val closed = if (aggregate.shipment.state == LogisticsShipmentState.CLOSED) {
            closedDetail(aggregate, shipmentEvents(organizationId, aggregate.shipment.id))
        } else null
        return ShipmentPresentationSnapshot(timeline = timeline, closed = closed)
    }

    private fun timeline(aggregate: LogisticsShipmentAggregate): List<ShipmentTimelineReadItem> {
        val milestones = aggregate.milestones.sortedBy { it.order }
        val legsByOrigin = aggregate.legs.groupBy { it.fromMilestoneId }
        val consumedLegIds = mutableSetOf<String>()
        val rows = mutableListOf<ShipmentTimelineReadItem>()
        milestones.forEach { milestone ->
            rows += milestone.toTimelineItem()
            legsByOrigin[milestone.id].orEmpty()
                .sortedWith(compareBy({ it.sequence }, { legStateSort(it.status) }, { it.id }))
                .forEach { leg ->
                    consumedLegIds += leg.id
                    val destination = milestones.firstOrNull { it.id == leg.toMilestoneId }
                    rows += ShipmentTimelineReadItem(
                        id = leg.id,
                        kind = ShipmentTimelineItemKind.LEG,
                        title = "${milestone.placeLabel()} → ${destination?.placeLabel() ?: leg.toMilestoneId}",
                        detail = buildString {
                            append(leg.mode.name)
                            if (leg.planKind == LogisticsPlanKind.UNPLANNED) append(" • UNPLANNED")
                            if (leg.status == LogisticsLegStatus.SUPERSEDED) append(" • SUPERSEDED")
                        },
                        state = leg.timelineState(),
                        timing = ShipmentTimelineTiming(
                            planKind = leg.planKind,
                            sequence = leg.sequence * 2 + 1,
                            occurredAt = leg.actualArrivalAt ?: leg.actualDepartureAt ?: leg.supersededAt,
                            plannedAt = leg.plannedArrivalAt ?: leg.plannedDepartureAt,
                        ),
                    )
                }
        }
        aggregate.legs.filterNot { it.id in consumedLegIds }
            .sortedWith(compareBy({ it.sequence }, { it.id }))
            .forEach { leg ->
                rows += ShipmentTimelineReadItem(
                    id = leg.id,
                    kind = ShipmentTimelineItemKind.LEG,
                    title = "مرحلة ${leg.sequence + 1}",
                    detail = if (leg.status == LogisticsLegStatus.SUPERSEDED) "SUPERSEDED" else leg.mode.name,
                    state = leg.timelineState(),
                    timing = ShipmentTimelineTiming(
                        planKind = leg.planKind,
                        sequence = leg.sequence * 2 + 1,
                        occurredAt = leg.actualArrivalAt ?: leg.actualDepartureAt ?: leg.supersededAt,
                        plannedAt = leg.plannedArrivalAt ?: leg.plannedDepartureAt,
                    ),
                )
            }
        return rows.sortedWith(compareBy({ it.sequence }, { it.kind.ordinal }, { it.id }))
    }

    private fun LogisticsMilestone.toTimelineItem(): ShipmentTimelineReadItem {
        val completed = if (type == LogisticsMilestoneType.DESTINATION) arrivedAt != null else departedAt != null
        val active = arrivedAt != null && !completed
        return ShipmentTimelineReadItem(
            id = id,
            kind = ShipmentTimelineItemKind.MILESTONE,
            title = placeLabel(),
            detail = buildString {
                append(type.name)
                if (planKind == LogisticsPlanKind.UNPLANNED) append(" • UNPLANNED")
            },
            state = when {
                completed -> ShipmentTimelineState.COMPLETED
                active -> ShipmentTimelineState.ACTIVE
                else -> ShipmentTimelineState.PLANNED
            },
            timing = ShipmentTimelineTiming(
                planKind = planKind,
                sequence = order * 2,
                occurredAt = departedAt ?: arrivedAt,
                plannedAt = plannedArrivalAt ?: plannedDepartureAt,
            ),
        )
    }

    private fun closedDetail(
        aggregate: LogisticsShipmentAggregate,
        events: List<ShipmentEventReadRecord>,
    ): ShipmentClosedDetailReadModel {
        val lineById = aggregate.lines.associateBy { it.id }
        val acceptedByLine = aggregate.receivingBatches.flatMap { it.lines }
            .groupBy { it.shipmentLineId }
            .mapValues { (_, rows) -> rows.sumOf { it.acceptedQuantity } }
        val acceptedValue = acceptedByLine.entries.fold(BigDecimal.ZERO) { sum, (lineId, quantity) ->
            val price = lineById[lineId]?.basePurchaseUnitPrice ?: BigDecimal.ZERO
            sum + price.multiply(BigDecimal.valueOf(quantity.toLong()))
        }
        val missingValue = aggregate.shortages.fold(BigDecimal.ZERO) { sum, shortage ->
            sum + shortage.quantity.basePurchaseUnitPriceSnapshot.multiply(
                BigDecimal.valueOf(shortage.quantity.remainingMissingQuantity.toLong()),
            )
        }
        val recoveredValue = aggregate.recoveryLines.fold(BigDecimal.ZERO) { sum, line ->
            sum + line.economics.basePurchaseUnitPriceSnapshot.multiply(BigDecimal.valueOf(line.recoveredQuantity.toLong()))
        }
        val shipmentCostTotal = aggregate.costs.filter {
            it.status == LogisticsCostStatus.ACTUAL && it.recoveryId == null
        }.fold(BigDecimal.ZERO) { sum, cost -> sum + cost.baseCurrencyAmount }
        val recoveryCostTotal = aggregate.costs.filter {
            it.status == LogisticsCostStatus.ACTUAL && it.recoveryId != null
        }.fold(BigDecimal.ZERO) { sum, cost -> sum + cost.baseCurrencyAmount }
        val settledShipmentAllocation = aggregate.costAllocations.fold(BigDecimal.ZERO) { sum, allocation -> sum + allocation.amount }
        val recoveryAllocation = aggregate.recoveryLines.fold(BigDecimal.ZERO) { sum, line ->
            sum + line.economics.allocatedRecoveryCost
        }
        val actualStart = aggregate.shipment.startedAt ?: aggregate.legs.mapNotNull { it.actualDepartureAt }.minOrNull()
        val actualEnd = aggregate.milestones.filter { it.type == LogisticsMilestoneType.DESTINATION }
            .mapNotNull { it.arrivedAt }.maxOrNull()
            ?: aggregate.receivingBatches.maxOfOrNull { it.receivedAt }
        val plannedStart = aggregate.shipment.expectedDepartureAt
            ?: aggregate.legs.mapNotNull { it.plannedDepartureAt }.minOrNull()
        val plannedEnd = aggregate.shipment.expectedArrivalAt
            ?: aggregate.legs.mapNotNull { it.plannedArrivalAt }.maxOrNull()
        val delayedLeg = aggregate.legs.mapNotNull { leg ->
            val planned = leg.plannedArrivalAt ?: return@mapNotNull null
            val actual = leg.actualArrivalAt ?: return@mapNotNull null
            (actual - planned).takeIf { it > 0L }?.let { delay -> leg to delay }
        }.maxByOrNull { it.second }
        val milestonesById = aggregate.milestones.associateBy { it.id }
        val paymentsByCost = aggregate.payments.groupBy { it.costId }
        val documentsById = aggregate.documents.associateBy { it.id }
        val settlementsByShortage = aggregate.shortageSettlements.groupBy { it.shortageId }
        return ShipmentClosedDetailReadModel(
            suppliers = aggregate.sources.map { it.supplierNameSnapshot }.distinct(),
            invoices = aggregate.sources.map { it.invoiceNumberSnapshot }.distinct(),
            routes = ShipmentClosedRoutesReadModel(
                planned = aggregate.milestones.filter { it.planKind == LogisticsPlanKind.PLANNED }
                    .sortedBy { it.order }.map { it.placeLabel() },
                actual = aggregate.milestones.filter { it.arrivedAt != null || it.departedAt != null }
                    .sortedBy { it.order }.map { it.placeLabel() },
            ),
            timing = ShipmentClosedTimingReadModel(
                plannedStartAt = plannedStart,
                plannedEndAt = plannedEnd,
                actualStartAt = actualStart,
                actualEndAt = actualEnd,
                delayLocation = delayedLeg?.first?.toMilestoneId?.let { milestonesById[it]?.placeLabel() },
                delayReason = delayedLeg?.first?.note?.takeIf { it.isNotBlank() }
                    ?: delayedLeg?.first?.toMilestoneId?.let { milestonesById[it]?.note?.takeIf(String::isNotBlank) },
                delayMillis = delayedLeg?.second ?: 0L,
            ),
            employee = ShipmentClosedEmployeeReadModel(
                employeeId = aggregate.shipment.assignee?.employeeId
                    ?: aggregate.assignments.maxByOrNull { it.assignedAt }?.employeeId,
                employeeName = aggregate.shipment.assignee?.employeeName
                    ?: aggregate.assignments.maxByOrNull { it.assignedAt }?.employeeNameSnapshot,
            ),
            values = ShipmentClosedValuesReadModel(
                accepted = acceptedValue,
                missing = missingValue,
                recovered = recoveredValue,
                shipmentCost = shipmentCostTotal,
                recoveryCost = recoveryCostTotal,
                finalInventory = acceptedValue + settledShipmentAllocation + recoveredValue + recoveryAllocation,
            ),
            costsAndPayments = aggregate.costs.sortedBy { it.id }.map { cost ->
                val payments = paymentsByCost[cost.id].orEmpty()
                val paid = payments.filter { it.paidAt != null }.fold(BigDecimal.ZERO) { sum, payment -> sum + payment.amount }
                val proof = payments.asSequence().mapNotNull { it.proofDocumentId }.mapNotNull(documentsById::get).firstOrNull()
                ShipmentClosedCostPaymentReadModel(
                    costId = cost.id,
                    description = cost.description.ifBlank { cost.type.name },
                    costAmount = cost.amount,
                    costCurrency = cost.currency,
                    baseAmount = cost.baseCurrencyAmount,
                    paymentState = cost.paymentState.name,
                    paidAmount = paid,
                    paidAt = payments.mapNotNull { it.paidAt }.maxOrNull(),
                    proofDocumentName = proof?.displayName,
                )
            },
            shortages = aggregate.shortages.sortedBy { it.detectedAt }.map { shortage ->
                val settlements = settlementsByShortage[shortage.identity.id].orEmpty()
                ShipmentClosedShortageReadModel(
                    shortageId = shortage.identity.id,
                    itemName = lineById[shortage.identity.shipmentLineId]?.itemNameSnapshot ?: shortage.identity.shipmentLineId,
                    originalMissingQuantity = shortage.quantity.originalMissingQuantity,
                    remainingMissingQuantity = shortage.quantity.remainingMissingQuantity,
                    status = shortage.status.name,
                    settlement = settlements.joinToString(" + ") { it.type.name }.ifBlank { null },
                )
            },
            attachments = aggregate.documents.sortedByDescending { it.createdAt }.map { document ->
                ShipmentClosedAttachmentReadModel(
                    documentId = document.id,
                    displayName = document.displayName,
                    type = document.type.name,
                    employeeName = document.employeeNameSnapshot,
                    createdAt = document.createdAt,
                )
            },
            events = events.sortedWith(compareByDescending<ShipmentEventReadRecord> { it.occurredAt }.thenBy { it.id }),
        )
    }

    private fun LogisticsMilestone.placeLabel(): String = placeName.ifBlank { location }

    private fun com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg.timelineState(): ShipmentTimelineState = when (status) {
        LogisticsLegStatus.ARRIVED -> ShipmentTimelineState.COMPLETED
        LogisticsLegStatus.IN_TRANSIT -> ShipmentTimelineState.ACTIVE
        LogisticsLegStatus.PLANNED -> ShipmentTimelineState.PLANNED
        LogisticsLegStatus.SUPERSEDED -> ShipmentTimelineState.SUPERSEDED
        LogisticsLegStatus.CANCELLED -> ShipmentTimelineState.CANCELLED
    }

    private fun legStateSort(status: LogisticsLegStatus): Int = when (status) {
        LogisticsLegStatus.SUPERSEDED -> 0
        LogisticsLegStatus.CANCELLED -> 1
        LogisticsLegStatus.ARRIVED -> 2
        LogisticsLegStatus.IN_TRANSIT -> 3
        LogisticsLegStatus.PLANNED -> 4
    }
}
