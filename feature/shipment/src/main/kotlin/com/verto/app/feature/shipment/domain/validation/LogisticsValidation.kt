package com.verto.app.feature.shipment.domain.validation

import com.verto.app.feature.shipment.domain.model.LogisticsCountryNormalizer
import com.verto.app.feature.shipment.domain.model.CreateLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsLocation
import com.verto.app.feature.shipment.domain.model.LogisticsRecovery
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryLine
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsTransportDetails
import com.verto.app.feature.shipment.domain.model.LogisticsTransportMode
import java.math.BigDecimal
import com.verto.app.core.error.BusinessRuleFailureException

object LogisticsValidation {
    private val zero = BigDecimal.ZERO
    fun validateCreate(command: CreateLogisticsShipmentCommand) {
        requireText(command.organizationId, "organizationId")
        requireText(command.shipmentNumber, "shipmentNumber")
        requireText(command.sourceLocation, "sourceLocation")
        requireText(command.destinationLocation, "destinationLocation")
        requireText(command.requestId, "requestId")
        require(command.createdAt >= 0L) { "createdAt must be non-negative" }
    }
    fun validateReady(aggregate: LogisticsShipmentAggregate) {
        val shipment = aggregate.shipment
        require(shipment.state == LogisticsShipmentState.DRAFT) {
            "Only DRAFT shipment can become READY"
        }
        requireText(shipment.sourceLocation, "shipment.sourceLocation")
        requireText(shipment.destinationLocation, "shipment.destinationLocation")
        require(aggregate.sources.isNotEmpty()) { "At least one purchase source is required" }
        require(aggregate.lines.isNotEmpty()) { "At least one shipment line is required" }
        requireNotNull(shipment.assignee) { "assignee is required before READY" }.also { assignee ->
            requireText(assignee.employeeId, "assignee.employeeId")
            requireText(assignee.employeeName, "assignee.employeeName")
        }
        aggregate.sources.forEach(::validateSource)
        aggregate.lines.forEach(::validateLine)
        validateSourceLineOwnership(aggregate)
        validatePurchasePlanReady(aggregate)
        require(aggregate.legs.isNotEmpty()) { "At least one route leg is required before READY" }
        validatePlanningRoute(shipment, aggregate.milestones, aggregate.legs)
        val derivedMode = deriveTransportMode(aggregate.legs)
        require(shipment.transportMode == derivedMode) {
            "Shipment transportMode must match route legs"
        }
        shipment.transportDetails?.let(::validateTransportDetails)
    }
    fun validateStart(aggregate: LogisticsShipmentAggregate) {
        val shipment = aggregate.shipment
        require(shipment.state == LogisticsShipmentState.READY) { "Shipment must be READY before start" }
        require(shipment.transportMode != null) { "transportMode is required before start" }
        requireNotNull(shipment.assignee) { "assignee is required before start" }.also { assignee ->
            requireText(assignee.employeeId, "assignee.employeeId")
            requireText(assignee.employeeName, "assignee.employeeName")
        }
        require(aggregate.sources.isNotEmpty()) { "At least one purchase source is required before start" }
        require(aggregate.lines.isNotEmpty()) { "At least one shipment line is required before start" }
        aggregate.sources.forEach(::validateSource)
        aggregate.lines.forEach(::validateLine)
        validateSourceLineOwnership(aggregate)
        require(aggregate.legs.isNotEmpty()) { "At least one route leg is required before start" }
        validateRoute(shipment, aggregate.milestones, aggregate.legs)
        require(shipment.transportMode == deriveTransportMode(aggregate.legs)) {
            "Shipment transportMode must match route legs"
        }
        shipment.transportDetails?.let(::validateTransportDetails)
    }
    fun validateRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) {
        require(milestones.size >= 2) { "Route requires origin and destination milestones" }
        require(milestones.all { it.shipmentId == shipment.id }) { "Route milestone belongs to another shipment" }
        require(legs.all { it.shipmentId == shipment.id && it.organizationId == shipment.organizationId }) {
            "Route leg belongs to another shipment or organization"
        }
        validateMilestones(milestones)
        val ordered = milestones.sortedBy { it.order }
        require(ordered.map { it.order } == ordered.indices.toList()) {
            "Milestone order must start at 0 and be contiguous"
        }
        require(ordered.first().type == LogisticsMilestoneType.ORIGIN) { "First milestone must be ORIGIN" }
        require(ordered.last().type == LogisticsMilestoneType.DESTINATION) { "Last milestone must be DESTINATION" }
        require(ordered.count { it.type == LogisticsMilestoneType.ORIGIN } == 1) { "Route requires exactly one origin" }
        require(ordered.count { it.type == LogisticsMilestoneType.DESTINATION } == 1) {
            "Route requires exactly one destination"
        }
        require(ordered.first().location.trim() == shipment.sourceLocation.trim()) {
            "Origin milestone must remain anchored to shipment source"
        }
        require(ordered.last().location.trim() == shipment.destinationLocation.trim()) {
            "Destination milestone must remain anchored to shipment destination"
        }
        require(ordered.drop(1).dropLast(1).none {
            it.type == LogisticsMilestoneType.ORIGIN || it.type == LogisticsMilestoneType.DESTINATION
        }) { "Intermediate milestones must be TRANSIT or CUSTOMS" }
        require(legs.size == ordered.size - 1) { "Every adjacent milestone pair requires exactly one leg" }
        val orderedLegs = legs.sortedBy { it.sequence }
        require(orderedLegs.map { it.sequence } == orderedLegs.indices.toList()) {
            "Leg sequence must start at 0 and be contiguous"
        }
        orderedLegs.forEachIndexed { index, leg ->
            validateLeg(leg)
            require(leg.fromMilestoneId == ordered[index].id && leg.toMilestoneId == ordered[index + 1].id) {
                "Leg sequence must connect each milestone to the next milestone"
            }
            require(ordered[index].order < ordered[index + 1].order) { "Leg must move forward through route order" }
        }
    }
    /** v230 planning route: topology/timing only. Carrier and operational cargo are intentionally deferred to v231. */
    fun validatePlanningRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) {
        require(milestones.size >= 2) { "Route requires origin and destination milestones" }
        require(milestones.all { it.shipmentId == shipment.id }) { "Route milestone belongs to another shipment" }
        require(legs.all { it.shipmentId == shipment.id && it.organizationId == shipment.organizationId }) {
            "Route leg belongs to another shipment or organization"
        }
        validateMilestones(milestones)
        val ordered = milestones.sortedBy { it.order }
        require(ordered.map { it.order } == ordered.indices.toList()) { "Milestone order must start at 0 and be contiguous" }
        require(ordered.first().type == LogisticsMilestoneType.ORIGIN) { "First milestone must be ORIGIN" }
        require(ordered.last().type == LogisticsMilestoneType.DESTINATION) { "Last milestone must be DESTINATION" }
        require(ordered.count { it.type == LogisticsMilestoneType.ORIGIN } == 1) { "Route requires exactly one origin" }
        require(ordered.count { it.type == LogisticsMilestoneType.DESTINATION } == 1) { "Route requires exactly one destination" }
        require(ordered.first().location.trim() == shipment.sourceLocation.trim()) { "Origin milestone must remain anchored to shipment source" }
        require(ordered.last().location.trim() == shipment.destinationLocation.trim()) { "Destination milestone must remain anchored to shipment destination" }
        require(ordered.drop(1).dropLast(1).none { it.type == LogisticsMilestoneType.ORIGIN || it.type == LogisticsMilestoneType.DESTINATION }) {
            "Intermediate milestones must be TRANSIT or CUSTOMS"
        }
        require(ordered.count { it.type == LogisticsMilestoneType.CUSTOMS } <= 1) { "Only one customs station is allowed" }
        ordered.filter { it.type == LogisticsMilestoneType.CUSTOMS }.forEach { customs ->
            require((customs.expectedStayDays ?: 0) > 0) { "Customs station requires a positive expected duration" }
        }
        require(legs.size == ordered.size - 1) { "Every adjacent milestone pair requires exactly one leg" }
        val orderedLegs = legs.sortedBy { it.sequence }
        require(orderedLegs.map { it.sequence } == orderedLegs.indices.toList()) { "Leg sequence must start at 0 and be contiguous" }
        orderedLegs.forEachIndexed { index, leg ->
            validatePlanningLeg(leg)
            require(leg.fromMilestoneId == ordered[index].id && leg.toMilestoneId == ordered[index + 1].id) {
                "Leg sequence must connect each milestone to the next milestone"
            }
        }
        val concreteModes = orderedLegs.map { it.mode }.filterNot { it == LogisticsLegTransportMode.UNSPECIFIED }.toSet()
        val hasUnspecified = orderedLegs.any { it.mode == LogisticsLegTransportMode.UNSPECIFIED }
        require(!(hasUnspecified && concreteModes.isNotEmpty())) { "Route must be either unified or mixed, not partially assigned" }
    }

    fun validatePlanningLeg(leg: LogisticsShipmentLeg) {
        requireText(leg.id, "leg.id")
        requireText(leg.organizationId, "leg.organizationId")
        requireText(leg.shipmentId, "leg.shipmentId")
        require(leg.sequence >= 0) { "leg sequence must be non-negative" }
        requireText(leg.fromMilestoneId, "leg.fromMilestoneId")
        requireText(leg.toMilestoneId, "leg.toMilestoneId")
        require(leg.fromMilestoneId != leg.toMilestoneId) { "leg milestones must be different" }
        val expectedTransitMinutes = leg.expectedTransitMinutes ?: leg.expectedTransitDays?.times(24 * 60)
        require((expectedTransitMinutes ?: 0) > 0) { "expected transit duration is required and must be positive" }
        leg.expectedTransitDays?.let { require(it > 0) { "expectedTransitDays must be positive when present" } }
        leg.expectedTransitMinutes?.let { require(it > 0) { "expectedTransitMinutes must be positive when present" } }
        require(leg.carrierPartnerId.isBlank()) { "Actual carrier is execution data and must not be captured during planning" }
        require(leg.packageCount == null && leg.weightKg == null) { "Actual cargo count/weight are execution data during planning" }
        require(leg.plannedDepartureAt == null && leg.plannedArrivalAt == null && leg.actualDepartureAt == null && leg.actualArrivalAt == null) {
            "Planning stores expected duration, not execution timestamps"
        }
        leg.plannedCarrierPartnerId?.let { requireText(it, "leg.plannedCarrierPartnerId") }
        leg.plannedCarrierNameSnapshot?.let { requireText(it, "leg.plannedCarrierNameSnapshot") }
        leg.plannedRepresentativeNameSnapshot?.let { requireText(it, "leg.plannedRepresentativeNameSnapshot") }
        leg.plannedRepresentativePhoneSnapshot?.let { requireText(it, "leg.plannedRepresentativePhoneSnapshot") }
        leg.plannedPackageCount?.let { require(it > 0) { "plannedPackageCount must be positive" } }
        leg.plannedWeightKg?.let { require(it > zero) { "plannedWeightKg must be positive" } }
        leg.plannedCost?.let { plannedCost ->
            require(plannedCost.amount >= zero) { "planned cost amount must be non-negative" }
            requireText(plannedCost.currency, "leg.plannedCost.currency")
            require(plannedCost.exchangeRate > zero) { "planned cost exchange rate must be positive" }
            require(plannedCost.baseCurrencyAmount >= zero) { "planned base currency amount must be non-negative" }
        }
        leg.plannedProof?.let { proof ->
            requireText(proof.privateUri, "leg.plannedProof.privateUri")
            requireText(proof.displayName, "leg.plannedProof.displayName")
            requireText(proof.mimeType, "leg.plannedProof.mimeType")
        }
        require(leg.representativeNameSnapshot == null && leg.representativePhoneSnapshot == null) { "Representative is execution data" }
        require(leg.roadVehicleNumber == null && leg.roadDriverName == null && leg.roadDriverPhone == null) { "Road execution details are not planning data" }
        require(leg.seaContainerNumber == null && leg.seaBillOfLading == null && leg.seaVesselReference == null) { "Sea execution details are not planning data" }
        require(leg.airWaybillNumber == null && leg.airFlightReference == null) { "Air execution details are not planning data" }
    }

    fun deriveTransportMode(legs: List<LogisticsShipmentLeg>): LogisticsTransportMode {
        require(legs.isNotEmpty()) { "At least one leg is required" }
        val modes = legs.map { it.mode }.toSet()
        if (LogisticsLegTransportMode.UNSPECIFIED in modes || modes.size > 1) return LogisticsTransportMode.MULTIMODAL
        return when (modes.single()) {
            LogisticsLegTransportMode.ROAD -> LogisticsTransportMode.ROAD
            LogisticsLegTransportMode.SEA -> LogisticsTransportMode.SEA
            LogisticsLegTransportMode.AIR -> LogisticsTransportMode.AIR
            LogisticsLegTransportMode.UNSPECIFIED -> LogisticsTransportMode.MULTIMODAL
        }
    }
    fun requireHandlingTransition(
        milestone: LogisticsMilestone,
        target: LogisticsMilestoneHandlingStatus,
    ) {
        val allowed = when (milestone.handlingStatus) {
            LogisticsMilestoneHandlingStatus.PENDING -> LogisticsMilestoneHandlingStatus.ARRIVED
            LogisticsMilestoneHandlingStatus.ARRIVED -> LogisticsMilestoneHandlingStatus.UNLOADED
            LogisticsMilestoneHandlingStatus.UNLOADED -> LogisticsMilestoneHandlingStatus.LOADED
            LogisticsMilestoneHandlingStatus.LOADED -> LogisticsMilestoneHandlingStatus.DEPARTED
            LogisticsMilestoneHandlingStatus.DEPARTED -> null
        }
        require(target == allowed) {
            "Milestone handling transition ${milestone.handlingStatus} -> $target is not allowed"
        }
        if (milestone.type == LogisticsMilestoneType.DESTINATION) {
            require(target != LogisticsMilestoneHandlingStatus.LOADED && target != LogisticsMilestoneHandlingStatus.DEPARTED) {
                "Destination milestone stops after unloading"
            }
        }
    }
    fun validatePurchasePlanDraft(
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
        expectedDepartureAt: Long?,
        expectedArrivalAt: Long?,
        transportDetails: LogisticsTransportDetails?,
    ) {
        validateTimestampPair(expectedDepartureAt, expectedArrivalAt, "shipment")
        transportDetails?.packageCount?.let { require(it >= 0) { "packageCount must be non-negative" } }
        transportDetails?.weightKg?.let { require(it > zero) { "weightKg must be positive" } }
        require(sources.map { it.invoiceId }.distinct().size == sources.size) { "Duplicate purchase sources" }
        require(lines.map { it.sourceInvoiceId to it.sourceInvoiceItemId }.distinct().size == lines.size) {
            "Duplicate purchase invoice lines"
        }
        val invoiceIds = sources.map { it.invoiceId }.toSet()
        require(lines.all { it.sourceInvoiceId in invoiceIds }) {
            "Every shipment line must reference a selected purchase source"
        }
        if (sources.isNotEmpty()) {
            require(sources.all { source -> lines.any { it.sourceInvoiceId == source.invoiceId } }) {
                "Every selected purchase source must contribute a shipment line"
            }
        }
        sources.forEach(::validateSource)
        lines.forEach(::validateLine)
    }
    fun validatePurchasePlanReady(aggregate: LogisticsShipmentAggregate) {
        require(aggregate.sources.isNotEmpty()) { "At least one purchase source is required before READY" }
        require(aggregate.lines.isNotEmpty()) { "At least one shipment line is required before READY" }
        require(aggregate.sources.all { source -> aggregate.lines.any { it.sourceInvoiceId == source.invoiceId } }) {
            "Every selected purchase source must contribute its full invoice snapshot"
        }
    }
    fun validateSource(source: LogisticsShipmentSource) {
        requireText(source.id, "source.id")
        requireText(source.shipmentId, "source.shipmentId")
        requireText(source.invoiceId, "source.invoiceId")
        requireText(source.supplierId, "source.supplierId")
        requireText(source.supplierNameSnapshot, "source.supplierNameSnapshot")
        requireText(source.invoiceNumberSnapshot, "source.invoiceNumberSnapshot")
        source.originalCurrency?.let { requireText(it, "source.originalCurrency") }
        source.exchangeRateSnapshot?.let { rate ->
            require(rate > zero) { "source exchange rate must be positive" }
        }
        source.plannedPackageCount?.let { require(it > 0) { "source plannedPackageCount must be positive" } }
        source.plannedWeightKg?.let { require(it > zero) { "source plannedWeightKg must be positive" } }
        source.expectedReadyAt?.let { require(it >= 0L) { "source expectedReadyAt must be non-negative" } }
    }
    fun validateLine(line: LogisticsShipmentLine) {
        requireText(line.id, "line.id")
        requireText(line.shipmentId, "line.shipmentId")
        requireText(line.sourceInvoiceId, "line.sourceInvoiceId")
        requireText(line.sourceInvoiceItemId, "line.sourceInvoiceItemId")
        if (line.inventoryItemId.isBlank()) {
            throw BusinessRuleFailureException("SHIPMENT_INVENTORY_LINK_REQUIRED", target = "line.inventoryItemId")
        }
        requireText(line.itemNameSnapshot, "line.itemNameSnapshot")
        require(line.expectedQuantity > 0) { "expectedQuantity must be positive" }
        require(line.basePurchaseUnitPrice >= zero) { "basePurchaseUnitPrice must be non-negative" }
    }
    fun validateReceivingLine(
        line: LogisticsReceivingLine,
        cumulativeReceivedBeforeBatch: Int,
    ) {
        require(cumulativeReceivedBeforeBatch >= 0) { "cumulativeReceivedBeforeBatch must be non-negative" }
        require(line.expectedQuantitySnapshot > 0) { "expectedQuantitySnapshot must be positive" }
        require(line.receivedQuantity >= 0) { "receivedQuantity must be non-negative" }
        require(line.acceptedQuantity >= 0) { "acceptedQuantity must be non-negative" }
        require(line.damagedQuantity >= 0) { "damagedQuantity must be non-negative" }
        require(line.rejectedQuantity >= 0) { "rejectedQuantity must be non-negative" }
        require(line.quarantinedQuantity >= 0) { "quarantinedQuantity must be non-negative" }
        val partition = listOf(
            line.acceptedQuantity,
            line.damagedQuantity,
            line.rejectedQuantity,
            line.quarantinedQuantity,
        ).fold(0L) { total, quantity -> total + quantity }
        require(partition == line.receivedQuantity.toLong()) {
            "accepted + damaged + rejected + quarantined must equal received"
        }
        val cumulative = cumulativeReceivedBeforeBatch.toLong() + line.receivedQuantity.toLong()
        require(cumulative <= line.expectedQuantitySnapshot.toLong()) {
            "cumulative received quantity cannot exceed expected quantity"
        }
    }
    fun missingQuantity(
        expectedQuantity: Int,
        cumulativeReceived: Int,
    ): Int {
        require(expectedQuantity >= 0) { "expectedQuantity must be non-negative" }
        require(cumulativeReceived >= 0) { "cumulativeReceived must be non-negative" }
        require(cumulativeReceived <= expectedQuantity) { "cumulativeReceived cannot exceed expectedQuantity" }
        return expectedQuantity - cumulativeReceived
    }
    fun validateCost(cost: LogisticsCost) {
        requireText(cost.id, "cost.id"); requireText(cost.organizationId, "cost.organizationId"); requireText(cost.shipmentId, "cost.shipmentId"); requireText(cost.currency, "cost.currency")
        require(cost.amount > zero) { "cost amount must be positive" }; require(cost.exchangeRateSnapshot > zero) { "exchange rate must be positive" }; require(cost.baseCurrencyAmount > zero) { "base currency amount must be positive" }
        require(cost.amount.multiply(cost.exchangeRateSnapshot).compareTo(cost.baseCurrencyAmount) == 0) { "cost base currency amount must equal amount × exchange rate snapshot" }; requireScopesAtMostOne("cost", cost.legId, cost.milestoneId, cost.sourceId, cost.recoveryId)
        cost.cashPostedBaseAmount?.let { require(it >= zero) { "cash posted base amount must be non-negative" }; require(it.stripTrailingZeros().scale() <= 0) { "cash posted base amount must be a whole base-currency unit" } }
        cost.cashPostedAt?.let { require(it >= 0L) { "cash posted timestamp must be non-negative" } }; cost.requestId?.let { requireText(it, "cost.requestId") }
        when (cost.paymentState) { LogisticsCostPaymentState.UNPAID -> require(cost.cashReference == null && cost.cashPostedBaseAmount == null && cost.cashPostedAt == null) { "UNPAID cost cannot contain committed cash facts" }
            LogisticsCostPaymentState.POSTING -> { require(!cost.cashReference.isNullOrBlank()) { "cost.cashReference is required" }; require(cost.cashPostedBaseAmount == null && cost.cashPostedAt == null) { "POSTING cost cannot be marked as already posted" } }
            LogisticsCostPaymentState.PAID -> { require(!cost.cashReference.isNullOrBlank()) { "cost.cashReference is required" }; requireNotNull(cost.cashPostedBaseAmount) { "PAID cost requires cash posted base amount" }; require(cost.cashPostedBaseAmount > zero) { "PAID cost cash amount must be positive" }; requireNotNull(cost.cashPostedAt) { "PAID cost requires cash posted timestamp" } }
            LogisticsCostPaymentState.ADJUSTING -> { require(!cost.cashReference.isNullOrBlank()) { "cost.cashReference is required" }; requireNotNull(cost.cashPostedBaseAmount) { "ADJUSTING cost requires previous posted amount" }; require(cost.cashPostedBaseAmount > zero) { "ADJUSTING previous posted amount must be positive" }; requireNotNull(cost.cashPostedAt) { "ADJUSTING cost requires previous posted timestamp" } }
            LogisticsCostPaymentState.REVERSED -> { require(!cost.cashReference.isNullOrBlank()) { "cost.cashReference is required" }; requireNotNull(cost.cashPostedAt) { "REVERSED cost requires cash history" } }
        }
    }
    fun validateDocument(document: LogisticsDocument) {
        requireText(document.id, "document.id")
        requireText(document.organizationId, "document.organizationId")
        requireText(document.shipmentId, "document.shipmentId")
        requireScopesAtMostOne(
            "document",
            document.sourceId,
            document.milestoneId,
            document.legId,
            document.handoffId,
            document.costId,
            document.recoveryId,
        )
    }
    fun validatePartner(partner: LogisticsPartner) {
        requireText(partner.id, "partner.id")
        requireText(partner.organizationId, "partner.organizationId")
        requireText(partner.name, "partner.name")
        validateOptionalPhone(partner.phone, "partner.phone")
    }
    fun validateLeg(leg: LogisticsShipmentLeg) {
        requireText(leg.id, "leg.id")
        requireText(leg.organizationId, "leg.organizationId")
        requireText(leg.shipmentId, "leg.shipmentId")
        require(leg.sequence >= 0) { "leg sequence must be non-negative" }
        requireText(leg.fromMilestoneId, "leg.fromMilestoneId")
        requireText(leg.toMilestoneId, "leg.toMilestoneId")
        require(leg.fromMilestoneId != leg.toMilestoneId) { "leg milestones must be different" }
        requireText(leg.carrierPartnerId, "leg.carrierPartnerId")
        validateOptionalPhone(leg.roadDriverPhone, "leg.roadDriverPhone")
        validateOptionalPhone(leg.representativePhoneSnapshot, "leg.representativePhoneSnapshot")
        leg.expectedTransitDays?.let { require(it > 0) { "expectedTransitDays must be positive" } }
        leg.packageCount?.let { require(it >= 0) { "leg packageCount must be non-negative" } }
        leg.weightKg?.let { require(it > zero) { "leg weightKg must be positive" } }
        leg.supersededAt?.let { require(it >= 0L) { "supersededAt must be non-negative" } }
        if (leg.supersededByLegId != null) requireText(leg.supersededByLegId, "leg.supersededByLegId")
        validateTimestampPair(leg.plannedDepartureAt, leg.plannedArrivalAt, "planned leg")
        validateTimestampPair(leg.actualDepartureAt, leg.actualArrivalAt, "actual leg")
        when (leg.mode) {
            LogisticsLegTransportMode.ROAD -> {
                require(leg.seaContainerNumber == null && leg.seaBillOfLading == null && leg.seaVesselReference == null) {
                    "ROAD leg cannot contain SEA fields"
                }
                require(leg.airWaybillNumber == null && leg.airFlightReference == null) {
                    "ROAD leg cannot contain AIR fields"
                }
            }
            LogisticsLegTransportMode.SEA -> {
                require(leg.roadVehicleNumber == null && leg.roadDriverName == null && leg.roadDriverPhone == null) {
                    "SEA leg cannot contain ROAD fields"
                }
                require(leg.airWaybillNumber == null && leg.airFlightReference == null) {
                    "SEA leg cannot contain AIR fields"
                }
            }
            LogisticsLegTransportMode.AIR -> {
                require(leg.roadVehicleNumber == null && leg.roadDriverName == null && leg.roadDriverPhone == null) {
                    "AIR leg cannot contain ROAD fields"
                }
                require(leg.seaContainerNumber == null && leg.seaBillOfLading == null && leg.seaVesselReference == null) {
                    "AIR leg cannot contain SEA fields"
                }
            }
            LogisticsLegTransportMode.UNSPECIFIED -> error("UNSPECIFIED transport mode is planning-only and must be resolved before execution")
        }
    }
    fun validateCustodyHandoff(handoff: LogisticsCustodyHandoff) {
        requireText(handoff.id, "handoff.id")
        requireText(handoff.organizationId, "handoff.organizationId")
        requireText(handoff.shipmentId, "handoff.shipmentId")
        requireText(handoff.fromHolderNameSnapshot, "handoff.fromHolderNameSnapshot")
        requireText(handoff.toHolderNameSnapshot, "handoff.toHolderNameSnapshot")
        requireText(handoff.requestId, "handoff.requestId")
        require(handoff.transferredAt >= 0L) { "handoff.transferredAt must be non-negative" }
        require(handoff.receivedAt >= handoff.transferredAt) {
            "handoff.receivedAt cannot be before transferredAt"
        }
        handoff.handoverPackageCount?.let { require(it >= 0) { "handoverPackageCount must be non-negative" } }
        handoff.receivedPackageCount?.let { require(it >= 0) { "receivedPackageCount must be non-negative" } }
        handoff.handoverWeightKg?.let { require(it > zero) { "handoverWeightKg must be positive" } }
        handoff.receivedWeightKg?.let { require(it > zero) { "receivedWeightKg must be positive" } }
        require(handoff.openedPackageCount >= 0) { "openedPackageCount must be non-negative" }
        require(handoff.damagedPackageCount >= 0) { "damagedPackageCount must be non-negative" }
        handoff.receivedPackageCount?.let { received ->
            require(handoff.openedPackageCount <= received) { "openedPackageCount cannot exceed received package count" }
            require(handoff.damagedPackageCount <= received) { "damagedPackageCount cannot exceed received package count" }
        }
    }
    fun validateConfirmedCustodyHandoff(handoff: LogisticsCustodyHandoff) {
        validateCustodyHandoff(handoff); val sent = requireNotNull(handoff.handoverPackageCount) { "handoverPackageCount is required" }; val received = requireNotNull(handoff.receivedPackageCount) { "receivedPackageCount is required" }
        if (sent != received) require(!handoff.packageChangeNote.isNullOrBlank()) { "handoff discrepancy note is required when package counts differ" }
    }
    fun validateCargoRepack(previous: LogisticsCargoSnapshot, updated: LogisticsCargoSnapshot, reason: LogisticsPackageChangeReason, note: String) {
        require(previous.packageCount >= 0 && updated.packageCount >= 0) { "package counts must be non-negative" }; require(previous.packageCount != updated.packageCount) { "repack must change package count" }; require(reason != LogisticsPackageChangeReason.CORRECTION) { "CORRECTION is not a physical repack reason" }; require(note.isNotBlank()) { "repack note is required" }
        previous.weightKg?.let { require(it > zero) { "previousWeightKg must be positive" } }; updated.weightKg?.let { require(it > zero) { "newWeightKg must be positive" } }
    }
    fun validateMilestones(milestones: List<LogisticsMilestone>, customsHostMilestoneId: String? = null) {
        require(milestones.count { it.type == LogisticsMilestoneType.DESTINATION } <= 1) {
            "Only one destination milestone is allowed"
        }
        milestones.forEach { milestone ->
            require(milestone.order >= 0) { "milestone order must be non-negative" }
            requireText(milestone.location, "milestone.location")
            milestone.expectedStayDays?.let { require(it >= 0) { "expectedStayDays must be non-negative" } }
            validateOptionalPhone(milestone.customsBrokerPhoneSnapshot, "milestone.customsBrokerPhoneSnapshot")
            validateCustomsFacts(milestone, customsHostMilestoneId)
            val hasStructuredLocation = milestone.countryCode.isNotBlank() || milestone.city.isNotBlank() ||
                milestone.countryNameSnapshot.isNotBlank()
            if (hasStructuredLocation) {
                validateLocation(
                    LogisticsLocation(
                        countryCode = milestone.countryCode,
                        countryNameSnapshot = milestone.countryNameSnapshot,
                        city = milestone.city,
                        placeName = milestone.placeName,
                    ),
                )
            }
            milestone.plannedArrivalAt?.let { require(it >= 0L) { "planned arrival must be non-negative" } }
            milestone.plannedDepartureAt?.let { require(it >= 0L) { "planned departure must be non-negative" } }
            if (milestone.plannedArrivalAt != null && milestone.plannedDepartureAt != null) {
                require(milestone.plannedDepartureAt >= milestone.plannedArrivalAt) {
                    "planned milestone departure cannot be before arrival"
                }
            }
            milestone.arrivedAt?.let { require(it >= 0L) { "milestone arrival must be non-negative" } }
            if (milestone.unloadedAt != null) {
                require(milestone.arrivedAt != null && milestone.unloadedAt >= milestone.arrivedAt) {
                    "milestone unload cannot be before arrival"
                }
            }
            if (milestone.loadedAt != null) {
                val lowerBound = milestone.unloadedAt ?: milestone.arrivedAt
                require(lowerBound != null && milestone.loadedAt >= lowerBound) {
                    "milestone load cannot be before unload/arrival"
                }
            }
            if (milestone.departedAt != null) {
                val lowerBound = milestone.loadedAt ?: milestone.unloadedAt ?: milestone.arrivedAt
                require(lowerBound != null && milestone.departedAt >= lowerBound) {
                    "milestone departure cannot be before handling"
                }
            }
        }
    }
    private fun validateCustomsFacts(milestone: LogisticsMilestone, customsHostMilestoneId: String?) {
        val customsFactsAllowed = milestone.type == LogisticsMilestoneType.CUSTOMS || milestone.id == customsHostMilestoneId
        if (!customsFactsAllowed) {
            require(milestone.customsBrokerPartnerId == null && milestone.customsBrokerNameSnapshot.isNullOrBlank() &&
                milestone.customsBrokerPhoneSnapshot.isNullOrBlank() && milestone.customsStartedAt == null &&
                milestone.customsCompletedAt == null) { "Customs facts are allowed only on legacy CUSTOMS milestones or the designated customs host station" }
            return
        }
        milestone.customsStartedAt?.let { started ->
            require(milestone.arrivedAt != null && started >= milestone.arrivedAt) {
                "customs start cannot be before station arrival"
            }
        }
        milestone.customsCompletedAt?.let { completed ->
            val started = requireNotNull(milestone.customsStartedAt) { "customs completion requires customs start" }
            require(completed >= started) { "customs completion cannot be before customs start" }
        }
    }

    fun validateLocation(location: LogisticsLocation) {
        require(LogisticsCountryNormalizer.isInternalKey(location.countryCode)) {
            "countryCode must be a canonical country key or legacy ISO code"
        }
        requireText(location.countryNameSnapshot, "location.countryNameSnapshot")
        requireText(location.city, "location.city")
        requireText(location.placeName, "location.placeName")
    }
    fun validateShortage(shortage: LogisticsShortage) {
        with(shortage.identity) {
            requireText(organizationId, "shortage.organizationId")
            requireText(id, "shortage.id")
            requireText(shipmentId, "shortage.shipmentId")
            requireText(shipmentLineId, "shortage.shipmentLineId")
        }
        with(shortage.quantity) {
            require(originalMissingQuantity > 0) { "originalMissingQuantity must be positive" }
            require(remainingMissingQuantity >= 0) { "remainingMissingQuantity must be non-negative" }
            require(remainingMissingQuantity <= originalMissingQuantity) {
                "remainingMissingQuantity cannot exceed originalMissingQuantity"
            }
            require(basePurchaseUnitPriceSnapshot >= zero) { "purchase price snapshot must be non-negative" }
        }
        val expectedStatus = if (shortage.quantity.remainingMissingQuantity == 0) com.verto.app.feature.shipment.domain.model.LogisticsShortageStatus.RECOVERED else if (shortage.quantity.remainingMissingQuantity < shortage.quantity.originalMissingQuantity) com.verto.app.feature.shipment.domain.model.LogisticsShortageStatus.PARTIALLY_RECOVERED else com.verto.app.feature.shipment.domain.model.LogisticsShortageStatus.OPEN
        require(shortage.status == expectedStatus) { "shortage status must match remaining quantity" }
        require(shortage.detectedAt >= 0L) { "shortage.detectedAt must be non-negative" }
        requireText(shortage.note, "shortage.note"); requireText(shortage.requestId, "shortage.requestId")
    }
    fun validateRecovery(recovery: LogisticsRecovery) {
        requireText(recovery.organizationId, "recovery.organizationId"); requireText(recovery.id, "recovery.id"); requireText(recovery.shipmentId, "recovery.shipmentId")
        require(recovery.recoveredAt >= 0L) { "recovery.recoveredAt must be non-negative" }
        requireText(recovery.employee.employeeId, "recovery.employee.employeeId"); requireText(recovery.employee.employeeName, "recovery.employee.employeeName")
        requireText(recovery.note, "recovery.note"); requireText(recovery.requestId, "recovery.requestId")
    }
    fun validateRecoveryLine(line: LogisticsRecoveryLine) {
        requireText(line.organizationId, "recoveryLine.organizationId")
        requireText(line.id, "recoveryLine.id")
        requireText(line.recoveryId, "recoveryLine.recoveryId")
        requireText(line.shortageId, "recoveryLine.shortageId")
        requireText(line.shipmentLineId, "recoveryLine.shipmentLineId")
        require(line.recoveredQuantity > 0) { "recoveredQuantity must be positive" }
        require(line.economics.basePurchaseUnitPriceSnapshot >= zero) { "purchase price snapshot must be non-negative" }
        require(line.economics.allocatedRecoveryCost >= zero) { "allocatedRecoveryCost must be non-negative" }
    }
    fun validateRecoveryPosting(posting: LogisticsRecoveryPosting) {
        requireText(posting.organizationId, "recoveryPosting.organizationId")
        requireText(posting.postingId, "recoveryPosting.postingId")
        requireText(posting.recoveryId, "recoveryPosting.recoveryId")
        requireText(posting.recoveryLineId, "recoveryPosting.recoveryLineId")
        requireText(posting.shipmentId, "recoveryPosting.shipmentId")
        requireText(posting.shipmentLineId, "recoveryPosting.shipmentLineId")
        require(posting.quantity > 0) { "recovery posting quantity must be positive" }
    }
    fun validateTransportDetails(details: LogisticsTransportDetails) {
        details.weightKg?.let { require(it > zero) { "weightKg must be positive" } }
        details.volumeM3?.let { require(it >= zero) { "volumeM3 must be non-negative" } }
        details.packageCount?.let { require(it >= 0) { "packageCount must be non-negative" } }
        details.palletCount?.let { require(it >= 0) { "palletCount must be non-negative" } }
    }
    private fun validateSourceLineOwnership(aggregate: LogisticsShipmentAggregate) {
        val shipmentId = aggregate.shipment.id
        require(aggregate.sources.all { it.shipmentId == shipmentId }) {
            "All sources must belong to the shipment"
        }
        require(aggregate.lines.all { it.shipmentId == shipmentId }) {
            "All lines must belong to the shipment"
        }
        val sourceInvoices = aggregate.sources.map { it.invoiceId }.toSet()
        require(aggregate.lines.all { it.sourceInvoiceId in sourceInvoices }) {
            "Every shipment line must reference one of the shipment purchase sources"
        }
    }
    private fun requireScopesAtMostOne(owner: String, vararg scopes: String?) {
        scopes.filterNotNull().forEach { requireText(it, "$owner.scopeId") }
        require(scopes.count { it != null } <= 1) {
            "$owner can be linked to only one scope"
        }
    }
    private fun validateOptionalPhone(value: String?, field: String) {
        if (value.isNullOrBlank()) return
        require(value.all { it in '0'..'9' }) { "$field must contain digits only" }
    }
    private fun validateTimestampPair(departure: Long?, arrival: Long?, label: String) {
        departure?.let { require(it >= 0L) { "$label departure must be non-negative" } }
        arrival?.let { require(it >= 0L) { "$label arrival must be non-negative" } }
        if (departure != null && arrival != null) {
            require(arrival >= departure) { "$label arrival cannot be before departure" }
        }
    }
    private fun requireText(value: String, field: String) { require(value.isNotBlank()) { "$field is required" } }
}
