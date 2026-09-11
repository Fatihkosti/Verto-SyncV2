package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.LogisticsRouteAnalyticsUseCase
import com.verto.app.feature.shipment.application.model.ShipmentCardPhase
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalStatus
import com.verto.app.feature.shipment.application.model.LogisticsDelayReadModel
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import java.math.BigDecimal

internal fun LogisticsShipmentState.arabicLabel(): String = when (this) {
    LogisticsShipmentState.DRAFT -> "مسودة"
    LogisticsShipmentState.READY -> "جاهزة"
    LogisticsShipmentState.WAITING_DEPARTURE -> "بانتظار التحرك"
    LogisticsShipmentState.IN_TRANSIT -> "في الطريق"
    LogisticsShipmentState.AT_STATION -> "في المحطة"
    LogisticsShipmentState.CUSTOMS -> "في الجمارك"
    LogisticsShipmentState.ARRIVED -> "وصلت"
    LogisticsShipmentState.RECEIVING -> "قيد الاستلام"
    LogisticsShipmentState.PARTIAL -> "استلام جزئي"
    LogisticsShipmentState.RECEIVED -> "مستلمة"
    LogisticsShipmentState.CLOSED -> "مغلقة"
    LogisticsShipmentState.CANCELLED -> "ملغاة"
}

internal fun LogisticsShipment.group(): LogisticsCenterGroup = when (state) {
    LogisticsShipmentState.DRAFT,
    LogisticsShipmentState.READY,
    LogisticsShipmentState.WAITING_DEPARTURE,
    LogisticsShipmentState.AT_STATION,
    LogisticsShipmentState.CUSTOMS -> LogisticsCenterGroup.NEEDS_ACTION

    LogisticsShipmentState.IN_TRANSIT -> LogisticsCenterGroup.IN_TRANSIT

    LogisticsShipmentState.ARRIVED,
    LogisticsShipmentState.RECEIVING,
    LogisticsShipmentState.PARTIAL,
    LogisticsShipmentState.RECEIVED -> LogisticsCenterGroup.RECEIVING

    LogisticsShipmentState.CLOSED,
    LogisticsShipmentState.CANCELLED -> LogisticsCenterGroup.COMPLETED
}

internal fun LogisticsShipmentAggregate.isAtCustoms(): Boolean =
    shipment.state == LogisticsShipmentState.CUSTOMS ||
        milestones.any { it.customsStartedAt != null && it.customsCompletedAt == null }

internal fun LogisticsShipmentAggregate.nextValidAction(): LogisticsNextAction = when (shipment.state) {
    LogisticsShipmentState.DRAFT -> LogisticsNextAction.PREPARE
    LogisticsShipmentState.READY -> LogisticsNextAction.PREPARE_MOVEMENT
    LogisticsShipmentState.WAITING_DEPARTURE -> LogisticsNextAction.START_MOVEMENT
    LogisticsShipmentState.IN_TRANSIT -> {
        val next = milestones.sortedBy { it.order }.firstOrNull {
            it.type == LogisticsMilestoneType.DESTINATION && it.arrivedAt == null ||
                it.type != LogisticsMilestoneType.DESTINATION && it.departedAt == null
        }
        when {
            next == null -> LogisticsNextAction.NONE
            next.type == LogisticsMilestoneType.DESTINATION -> LogisticsNextAction.RECORD_DESTINATION_ARRIVAL
            next.arrivedAt == null && next.type == LogisticsMilestoneType.CUSTOMS -> LogisticsNextAction.RECORD_CUSTOMS_ARRIVAL
            next.arrivedAt != null && next.type == LogisticsMilestoneType.CUSTOMS -> LogisticsNextAction.RECORD_CUSTOMS_DEPARTURE
            next.arrivedAt == null -> LogisticsNextAction.RECORD_MILESTONE_ARRIVAL
            else -> LogisticsNextAction.RECORD_MILESTONE_DEPARTURE
        }
    }
    LogisticsShipmentState.AT_STATION -> {
        val destination = milestones.singleOrNull { it.type == LogisticsMilestoneType.DESTINATION }
        if (destination?.arrivedAt != null) LogisticsNextAction.START_RECEIVING else LogisticsNextAction.START_MOVEMENT
    }
    LogisticsShipmentState.CUSTOMS -> LogisticsNextAction.NONE
    LogisticsShipmentState.ARRIVED -> LogisticsNextAction.START_RECEIVING
    LogisticsShipmentState.RECEIVING,
    LogisticsShipmentState.PARTIAL -> LogisticsNextAction.RECEIVE_ANOTHER_BATCH

    LogisticsShipmentState.RECEIVED -> {
        val actualCost = costs.asSequence()
            .filter { it.status == LogisticsCostStatus.ACTUAL }
            .fold(BigDecimal.ZERO) { acc, cost -> acc.add(cost.baseCurrencyAmount) }
        if (actualCost.signum() > 0 && costAllocations.isNotEmpty()) {
            LogisticsNextAction.CLOSE
        } else {
            LogisticsNextAction.SETTLE_COSTS
        }
    }
    LogisticsShipmentState.CLOSED,
    LogisticsShipmentState.CANCELLED -> LogisticsNextAction.NONE
}

internal fun LogisticsShipment.toCard(
    aggregate: LogisticsShipmentAggregate?,
    nowMillis: Long,
): LogisticsShipmentCardUi {
    val facts = LogisticsCardFacts(this, aggregate, nowMillis)
    return LogisticsShipmentCardUi(
        id = id,
        shipmentNumber = shipmentNumber,
        sourceLocation = sourceLocation,
        destinationLocation = destinationLocation,
        state = state,
        stateLabel = state.arabicLabel(),
        assigneeName = assignee?.employeeName,
        expectedArrivalAt = facts.eta ?: expectedArrivalAt,
        group = group(),
        nextAction = nextCardAction(aggregate),
        delayed = facts.delayed,
        atCustoms = facts.atCustoms,
        phase = facts.phase,
        suppliers = aggregate?.sources.orEmpty().map { it.supplierNameSnapshot }.distinct(),
        currentPackageCount = facts.cargo?.packageCount,
        currentWeightKg = facts.cargo?.weightKg,
        expectedDepartureAt = expectedDepartureAt,
        primaryLocation = facts.primaryLocation,
        primaryCustodian = facts.custodian,
        currentLegLabel = facts.currentLegLabel,
        movementLabel = facts.movementLabel,
        carrierName = facts.carrierName,
        expectedRemainingMillis = facts.expectedRemainingMillis,
        lastUpdateAt = facts.lastUpdate,
        actualArrivalAt = facts.actualArrival,
        acceptedQuantity = facts.accepted,
        missingQuantity = facts.missing,
        totalLogisticsCost = facts.totalCost,
        finalInventoryValue = facts.finalInventoryValue,
        hasRecovery = aggregate?.recoveries?.isNotEmpty() == true,
    )
}


private fun LogisticsShipment.nextCardAction(aggregate: LogisticsShipmentAggregate?): LogisticsNextAction = when (state) {
    LogisticsShipmentState.READY, LogisticsShipmentState.IN_TRANSIT, LogisticsShipmentState.AT_STATION, LogisticsShipmentState.CUSTOMS, LogisticsShipmentState.ARRIVED -> LogisticsNextAction.NONE
    else -> aggregate?.nextValidAction() ?: when (state) {
        LogisticsShipmentState.DRAFT -> LogisticsNextAction.PREPARE
        LogisticsShipmentState.RECEIVING, LogisticsShipmentState.PARTIAL -> LogisticsNextAction.RECEIVE_ANOTHER_BATCH
        LogisticsShipmentState.RECEIVED -> LogisticsNextAction.SETTLE_COSTS
        else -> LogisticsNextAction.NONE
    }
}

private class LogisticsCardFacts(
    private val shipment: LogisticsShipment,
    private val aggregate: LogisticsShipmentAggregate?,
    private val nowMillis: Long,
) {
    val atCustoms = aggregate?.isAtCustoms() == true
    val delayed = shipment.expectedArrivalAt?.let { eta ->
        shipment.state !in setOf(LogisticsShipmentState.CLOSED, LogisticsShipmentState.CANCELLED, LogisticsShipmentState.RECEIVED) && eta < nowMillis
    } == true
    val phase = when (shipment.state) {
        LogisticsShipmentState.DRAFT, LogisticsShipmentState.READY, LogisticsShipmentState.WAITING_DEPARTURE -> ShipmentCardPhase.BEFORE_MOVEMENT
        LogisticsShipmentState.CLOSED, LogisticsShipmentState.CANCELLED -> ShipmentCardPhase.CLOSED
        else -> ShipmentCardPhase.IN_MOVEMENT
    }
    private val milestones = aggregate?.milestones.orEmpty().associateBy { it.id }
    private val activeLeg = aggregate?.legs?.filter { it.status == LogisticsLegStatus.IN_TRANSIT }?.minByOrNull { it.sequence }
    private val currentStation = aggregate?.milestones?.sortedBy { it.order }?.firstOrNull { it.arrivedAt != null && it.departedAt == null }
    private val nextLeg = activeLeg ?: aggregate?.legs?.filter { it.status == LogisticsLegStatus.PLANNED }?.minByOrNull { it.sequence }
    private val from = nextLeg?.let { milestones[it.fromMilestoneId] }
    private val to = nextLeg?.let { milestones[it.toMilestoneId] }
    val cargo = aggregate?.let(LogisticsCustodyResolver::currentCargoSnapshot)
    val custodian = aggregate?.let { LogisticsCustodyResolver.currentForAllSources(it).values.toList() }
        .orEmpty().distinctBy { Triple(it.holderType, it.holderId, it.holderName) }.let {
            when (it.size) { 0 -> null; 1 -> it.single().holderName; else -> "عدة جهات" }
        }
    val carrierName = nextLeg?.carrierPartnerId?.let { carrierId ->
        aggregate?.custodyHandoffs.orEmpty().asReversed().firstNotNullOfOrNull { handoff ->
            when (carrierId) { handoff.toHolderId -> handoff.toHolderNameSnapshot; handoff.fromHolderId -> handoff.fromHolderNameSnapshot; else -> null }
        } ?: carrierId
    }
    val accepted = aggregate?.receivingBatches.orEmpty().flatMap { it.lines }.sumOf { it.acceptedQuantity }
    val missing = aggregate?.shortages.orEmpty().sumOf { it.quantity.remainingMissingQuantity }
    val totalCost = aggregate?.costs.orEmpty().filter { it.status == LogisticsCostStatus.ACTUAL }
        .fold(BigDecimal.ZERO) { sum, cost -> sum + cost.baseCurrencyAmount }
    val actualArrival = aggregate?.milestones.orEmpty().filter { it.type == LogisticsMilestoneType.DESTINATION }.mapNotNull { it.arrivedAt }.maxOrNull()
    val eta = activeLeg?.plannedArrivalAt ?: shipment.expectedArrivalAt
    val expectedRemainingMillis = eta?.let { (it - nowMillis).coerceAtLeast(0L) }
    val primaryLocation = when {
        activeLeg != null -> to?.let { "في الطريق إلى ${it.placeName.ifBlank { it.location }}" } ?: "في الطريق"
        currentStation != null -> currentStation.placeName.ifBlank { currentStation.location }
        else -> shipment.sourceLocation
    }
    val currentLegLabel = if (from != null && to != null) "${from.placeName.ifBlank { from.location }} → ${to.placeName.ifBlank { to.location }}" else null
    val movementLabel = if (activeLeg != null) "في الطريق" else if (currentStation != null) "في المحطة" else shipment.state.arabicLabel()
    val lastUpdate = aggregate?.let { agg ->
        buildList {
            add(agg.shipment.createdAt); agg.shipment.startedAt?.let(::add)
            agg.milestones.forEach { it.arrivedAt?.let(::add); it.departedAt?.let(::add) }
            agg.legs.forEach { it.actualDepartureAt?.let(::add); it.actualArrivalAt?.let(::add); it.supersededAt?.let(::add) }
            agg.custodyHandoffs.forEach { add(it.receivedAt) }; agg.receivingBatches.forEach { add(it.receivedAt) }; agg.recoveries.forEach { add(it.recoveredAt) }
        }.maxOrNull()
    }
    val finalInventoryValue = aggregate?.let { agg ->
        val lineById = agg.lines.associateBy { it.id }
        val acceptedValue = agg.receivingBatches.flatMap { it.lines }.fold(BigDecimal.ZERO) { sum, line ->
            sum + (lineById[line.shipmentLineId]?.basePurchaseUnitPrice ?: BigDecimal.ZERO).multiply(BigDecimal.valueOf(line.acceptedQuantity.toLong()))
        }
        val settled = agg.costAllocations.fold(BigDecimal.ZERO) { sum, allocation -> sum + allocation.amount }
        val recovered = agg.recoveryLines.fold(BigDecimal.ZERO) { sum, line ->
            sum + line.economics.basePurchaseUnitPriceSnapshot.multiply(BigDecimal.valueOf(line.recoveredQuantity.toLong())) + line.economics.allocatedRecoveryCost
        }
        acceptedValue + settled + recovered
    } ?: BigDecimal.ZERO
}

internal fun logisticsCenterState(
    access: LogisticsV2Access,
    shipments: List<LogisticsShipment>,
    nowMillis: Long,
    aggregatesById: Map<String, LogisticsShipmentAggregate> = emptyMap(),
): LogisticsCenterUiState {
    if (!access.canView) return LogisticsCenterUiState.PermissionDenied
    val cards = shipments.map { shipment ->
        shipment.toCard(aggregate = aggregatesById[shipment.id], nowMillis = nowMillis)
    }
    val groups = LogisticsCenterGroup.entries.associateWith { group -> cards.filter { it.group == group } }
    val metrics = LogisticsCenterMetrics(
        active = shipments.count { it.state != LogisticsShipmentState.CLOSED && it.state != LogisticsShipmentState.CANCELLED },
        delayed = cards.count { it.delayed },
        atCustoms = cards.count { it.atCustoms },
        waitingReceiving = shipments.count {
            it.state == LogisticsShipmentState.AT_STATION ||
                it.state == LogisticsShipmentState.ARRIVED ||
                it.state == LogisticsShipmentState.RECEIVING ||
                it.state == LogisticsShipmentState.PARTIAL
        },
    )
    val analytics = LogisticsRouteAnalyticsUseCase()(aggregatesById.values, nowMillis)
    return LogisticsCenterUiState.Content(metrics = metrics, groups = groups, analytics = analytics)
}

internal fun LogisticsShipmentAggregate.toDetailUi(
    operationalStatus: LogisticsOperationalStatus? = null,
    operationalDelay: LogisticsDelayReadModel? = null,
    sourceCustody: List<LogisticsSourceCustodyUi> = emptyList(),
    firstCarrierId: String? = null,
    firstCarrierName: String? = null,
    startReady: Boolean = false,
    availableCarriers: List<LogisticsPartner> = emptyList(),
    timeline: List<com.verto.app.feature.shipment.application.model.ShipmentTimelineReadItem> = emptyList(),
    closedSummary: com.verto.app.feature.shipment.application.model.ShipmentClosedDetailReadModel? = null,
): LogisticsShipmentDetailUi {
    val expected = lines.sumOf { it.expectedQuantity }
    val receivingLines = receivingBatches.flatMap { it.lines }
    val received = receivingLines.sumOf { it.receivedQuantity }
    val accepted = receivingLines.sumOf { it.acceptedQuantity }
    val actualTotal = costs.asSequence()
        .filter { it.status == LogisticsCostStatus.ACTUAL }
        .fold(BigDecimal.ZERO) { acc, cost -> acc.add(cost.baseCurrencyAmount) }
    val currentMilestone = milestones.sortedBy { it.order }.firstOrNull { it.departedAt == null }
    return LogisticsShipmentDetailUi(
        aggregate = this,
        nextAction = nextValidAction(),
        currentMilestone = currentMilestone,
        operationalStatus = operationalStatus,
        operationalDelay = operationalDelay,
        sourceCustody = sourceCustody,
        firstCarrierId = firstCarrierId,
        firstCarrierName = firstCarrierName,
        startReady = startReady,
        availableCarriers = availableCarriers,
        receivedQuantity = received,
        expectedQuantity = expected,
        acceptedQuantity = accepted,
        actualCostTotal = actualTotal,
        costSettled = actualTotal.signum() == 0 || costAllocations.isNotEmpty(),
        timeline = timeline,
        closedSummary = closedSummary,
    )
}

internal fun LogisticsShipment.toHeaderDraft(): LogisticsShipmentHeaderDraft = LogisticsShipmentHeaderDraft(
    shipmentId = id,
    shipmentNumber = shipmentNumber,
    origin = LogisticsDefinitionLocationDraft(
        countryName = originLocationDetails?.countryNameSnapshot.orEmpty(),
        city = originLocationDetails?.city.orEmpty().ifBlank { sourceLocation.toLogisticsPlanningPlace().city },
    ),
    destination = LogisticsDefinitionLocationDraft(
        countryName = destinationLocationDetails?.countryNameSnapshot.orEmpty(),
        city = destinationLocationDetails?.city.orEmpty().ifBlank { destinationLocation.toLogisticsPlanningPlace().city },
    ),
    employeeId = assignee?.employeeId.orEmpty(),
    employeeName = assignee?.employeeName.orEmpty(),
)
