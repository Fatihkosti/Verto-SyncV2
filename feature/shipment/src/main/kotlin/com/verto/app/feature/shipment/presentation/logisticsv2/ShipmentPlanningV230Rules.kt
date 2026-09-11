package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsCountryNormalizer
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplateStop
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import java.math.BigDecimal
import java.util.UUID

internal fun LogisticsPlanningDraft.addWholeInvoice(option: LogisticsPurchaseInvoiceOptionUi): LogisticsPlanningDraft {
    val withoutOld = removeInvoice(option.id)
    return withoutOld.copy(
        sources = withoutOld.sources + option.source,
        lines = withoutOld.lines + option.lines,
    )
}

internal fun LogisticsPlanningDraft.removeInvoice(invoiceId: String): LogisticsPlanningDraft = copy(
    sources = sources.filterNot { it.invoiceId == invoiceId },
    lines = lines.filterNot { it.sourceInvoiceId == invoiceId },
)

internal fun LogisticsPlanningDraft.removeSupplier(supplierId: String): LogisticsPlanningDraft {
    val invoiceIds = sources.filter { it.supplierId == supplierId }.map { it.invoiceId }.toSet()
    return copy(
        sources = sources.filterNot { it.supplierId == supplierId },
        lines = lines.filterNot { it.sourceInvoiceId in invoiceIds },
    )
}

internal fun LogisticsPlanningDraft.updateInvoicePlanningMetadata(
    invoiceId: String,
    packageCount: Int? = sources.firstOrNull { it.invoiceId == invoiceId }?.plannedPackageCount,
    weightKg: BigDecimal? = sources.firstOrNull { it.invoiceId == invoiceId }?.plannedWeightKg,
    expectedReadyAt: Long? = sources.firstOrNull { it.invoiceId == invoiceId }?.expectedReadyAt,
): LogisticsPlanningDraft = copy(
    sources = sources.map { source ->
        if (source.invoiceId == invoiceId) source.copy(
            plannedPackageCount = packageCount,
            plannedWeightKg = weightKg,
            expectedReadyAt = expectedReadyAt,
        ) else source
    },
)

internal data class LogisticsPurchaseInvoiceValidation(
    val packageCountError: String? = null,
    val weightError: String? = null,
    val readyDateError: String? = null,
) {
    val isValid: Boolean get() = packageCountError == null && weightError == null && readyDateError == null
}

internal fun LogisticsShipmentSource.v236Validation(): LogisticsPurchaseInvoiceValidation = LogisticsPurchaseInvoiceValidation(
    packageCountError = if (plannedPackageCount?.let { it > 0 } == true) null else "أدخل عدد الكراتين",
    weightError = if (plannedWeightKg?.signum() == 1) null else "أدخل الوزن",
    readyDateError = if (expectedReadyAt?.let { it > 0L } == true) null else "حدد تاريخ الجاهزية",
)

internal fun List<LogisticsPurchaseInvoiceOptionUi>.filterV236Invoices(query: String): List<LogisticsPurchaseInvoiceOptionUi> {
    val needle = query.trim()
    if (needle.isBlank()) return this
    return filter { option ->
        option.source.invoiceNumberSnapshot.contains(needle, ignoreCase = true) ||
            option.readinessLabel.contains(needle, ignoreCase = true)
    }
}

internal val LogisticsPlanningDraft.v236PlannedPackageTotal: Int
    get() = sources.sumOf { it.plannedPackageCount ?: 0 }

internal val LogisticsPlanningDraft.v236PlannedWeightTotal: BigDecimal
    get() = sources.fold(BigDecimal.ZERO) { total, source -> total + (source.plannedWeightKg ?: BigDecimal.ZERO) }

internal fun LogisticsPlanningDraft.isV230RouteReady(workspace: LogisticsRouteWorkspaceSnapshot): Boolean {
    val milestones = workspace.milestones.sortedBy { it.order }
    val legs = workspace.legs.sortedBy { it.sequence }
    if (milestones.size < 2 || legs.size != milestones.size - 1) return false
    if (milestones.first().type != LogisticsMilestoneType.ORIGIN || milestones.last().type != LogisticsMilestoneType.DESTINATION) return false
    if (milestones.first().location != sourceLocation.toLogisticsPlanningPlace().city ||
        milestones.last().location != destinationLocation.toLogisticsPlanningPlace().city
    ) return false
    if (milestones.count { it.type == LogisticsMilestoneType.CUSTOMS } > 1) return false
    if (legs.any { it.expectedTransitDays == null || it.expectedTransitDays <= 0 }) return false
    val customs = milestones.singleOrNull { it.type == LogisticsMilestoneType.CUSTOMS }
    if (customs != null && (customs.expectedStayDays == null || customs.expectedStayDays <= 0)) return false
    return when (workspace.routeTransportPlanKind) {
        LogisticsRouteTransportPlanKind.UNIFIED -> legs.all { it.mode == workspace.unifiedTransportMode }
        LogisticsRouteTransportPlanKind.MIXED -> legs.all { it.mode == LogisticsLegTransportMode.UNSPECIFIED }
    } && legs.all { it.carrierPartnerId.isBlank() && it.packageCount == null && it.weightKg == null }
}

internal fun LogisticsRouteWorkspaceSnapshot.withIntermediateMilestone(
    draft: LogisticsPlanningDraft,
    name: String,
): LogisticsRouteWorkspaceSnapshot {
    val ordered = milestones.sortedBy { it.order }
    if (ordered.size < 2) return this
    val next = ordered.dropLast(1) + LogisticsMilestone(
        id = "ui-stop:${UUID.randomUUID()}", shipmentId = draft.shipmentId,
        type = LogisticsMilestoneType.TRANSIT, order = ordered.lastIndex,
        location = name.trim(), placeName = name.trim(),
    ) + ordered.last()
    return withMilestonesAndReconciledLegs(draft, next)
}

internal fun LogisticsRouteWorkspaceSnapshot.withoutIntermediateMilestone(
    draft: LogisticsPlanningDraft,
    milestoneId: String,
): LogisticsRouteWorkspaceSnapshot = withMilestonesAndReconciledLegs(
    draft,
    milestones.filterNot { it.id == milestoneId },
)

internal fun LogisticsRouteWorkspaceSnapshot.withCustomsMilestone(milestoneId: String?): LogisticsRouteWorkspaceSnapshot = copy(
    milestones = milestones.map { milestone ->
        when {
            milestone.id == milestoneId && milestone.type != LogisticsMilestoneType.ORIGIN && milestone.type != LogisticsMilestoneType.DESTINATION ->
                milestone.copy(type = LogisticsMilestoneType.CUSTOMS, expectedStayDays = milestone.expectedStayDays ?: 1)
            milestone.type == LogisticsMilestoneType.CUSTOMS -> milestone.copy(type = LogisticsMilestoneType.TRANSIT, expectedStayDays = null)
            else -> milestone
        }
    },
)

internal fun LogisticsRouteWorkspaceSnapshot.withMilestonesAndReconciledLegs(
    draft: LogisticsPlanningDraft,
    values: List<LogisticsMilestone>,
): LogisticsRouteWorkspaceSnapshot {
    val normalized = values.mapIndexed { index, milestone -> milestone.copy(order = index) }
    val mode = if (routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED) LogisticsLegTransportMode.UNSPECIFIED else unifiedTransportMode
    val reconciled = reconcileLegs(draft.copy(transportDetails = null), normalized, legs).map { leg ->
        leg.asV230PlanningLeg(mode)
    }
    return copy(milestones = normalized, legs = reconciled)
}

internal fun LogisticsRouteWorkspaceSnapshot.toRouteTemplate(draft: LogisticsPlanningDraft): LogisticsRouteTemplate {
    val ordered = milestones.sortedBy { it.order }
    val origin = draft.sourceLocation.toLogisticsPlanningPlace()
    val destination = draft.destinationLocation.toLogisticsPlanningPlace()
    val legBySequence = legs.associateBy { it.sequence }
    val customsIndex = ordered.indexOfFirst { it.type == LogisticsMilestoneType.CUSTOMS }.takeIf { it >= 0 }
    return LogisticsRouteTemplate(
        id = "",
        organizationId = draft.organizationId,
        name = templateName.trim(),
        originCountryCode = origin.country.toPlanningCountryCode(),
        originCity = origin.city,
        destinationCountryCode = destination.country.toPlanningCountryCode(),
        destinationCity = destination.city,
        transportPlanKind = routeTransportPlanKind,
        unifiedTransportMode = unifiedTransportMode.takeIf { routeTransportPlanKind == LogisticsRouteTransportPlanKind.UNIFIED },
        customsStopOrder = customsIndex,
        expectedCustomsMinutes = ordered.getOrNull(customsIndex ?: -1)?.expectedStayDays?.times(MINUTES_PER_DAY),
        createdAt = 0L,
        updatedAt = 0L,
        stops = ordered.mapIndexed { index, milestone ->
            LogisticsRouteTemplateStop(
                id = "",
                templateId = "",
                order = index,
                countryCode = when (index) {
                    0 -> origin.country.toPlanningCountryCode()
                    ordered.lastIndex -> destination.country.toPlanningCountryCode()
                    else -> ""
                },
                city = when (index) {
                    0 -> origin.city
                    ordered.lastIndex -> destination.city
                    else -> milestone.location
                },
                placeName = milestone.location,
                expectedTransitMinutesToNext = legBySequence[index]?.expectedTransitDays?.times(MINUTES_PER_DAY),
            )
        },
    )
}

internal fun LogisticsRouteWorkspaceSnapshot.applyTemplate(
    draft: LogisticsPlanningDraft,
    template: LogisticsRouteTemplate,
): LogisticsRouteWorkspaceSnapshot {
    val shipmentId = draft.shipmentId
    val stops = template.stops.sortedBy { it.order }
    if (stops.size < 2) return this
    val values = template.toPlanningMilestones(shipmentId, stops)
    val mode = if (template.transportPlanKind == LogisticsRouteTransportPlanKind.MIXED) LogisticsLegTransportMode.UNSPECIFIED
    else template.unifiedTransportMode ?: LogisticsLegTransportMode.ROAD
    val routeDraft = draft.copy(
        sourceLocation = logisticsPlanningPlace(template.originCountryCode.toPlanningCountryName(), template.originCity),
        destinationLocation = logisticsPlanningPlace(template.destinationCountryCode.toPlanningCountryName(), template.destinationCity),
        transportDetails = null,
    )
    val newLegs = reconcileLegs(routeDraft, values, emptyList()).mapIndexed { index, leg ->
        leg.asV230PlanningLeg(mode).copy(
            expectedTransitDays = template.stops.getOrNull(index)?.expectedTransitMinutesToNext?.let { minutes ->
                (minutes / MINUTES_PER_DAY).coerceAtLeast(1)
            },
        )
    }
    return copy(
        routeTransportPlanKind = template.transportPlanKind,
        unifiedTransportMode = template.unifiedTransportMode ?: LogisticsLegTransportMode.ROAD,
        milestones = values,
        legs = newLegs,
        expandedSection = "ROUTE",
    )
}

private fun LogisticsRouteTemplate.toPlanningMilestones(
    shipmentId: String,
    stops: List<LogisticsRouteTemplateStop>,
): List<LogisticsMilestone> = stops.mapIndexed { index, stop ->
    val isOrigin = index == 0
    val isDestination = index == stops.lastIndex
    val countryName = when {
        isOrigin -> originCountryCode.toPlanningCountryName()
        isDestination -> destinationCountryCode.toPlanningCountryName()
        stop.countryCode.isNotBlank() -> stop.countryCode.toPlanningCountryName()
        else -> ""
    }
    val visiblePlace = when {
        isOrigin -> originCity
        isDestination -> destinationCity
        else -> stop.placeName.ifBlank { stop.city }
    }
    LogisticsMilestone(
        id = "ui-stop:${UUID.randomUUID()}",
        shipmentId = shipmentId,
        type = when {
            isOrigin -> LogisticsMilestoneType.ORIGIN
            isDestination -> LogisticsMilestoneType.DESTINATION
            customsStopOrder == index -> LogisticsMilestoneType.CUSTOMS
            else -> LogisticsMilestoneType.TRANSIT
        },
        order = index,
        location = visiblePlace,
        countryCode = countryName.takeIf(String::isNotBlank)?.let { LogisticsCountryNormalizer.key(it) }.orEmpty(),
        countryNameSnapshot = countryName,
        city = stop.city,
        placeName = visiblePlace,
        expectedStayDays = if (customsStopOrder == index) expectedCustomsMinutes?.div(MINUTES_PER_DAY) else null,
    )
}

internal fun String.toPlanningCountryCode(): String =
    logisticsCountryOptions().firstOrNull { it.name.equals(this, ignoreCase = true) }?.code ?: trim()

internal fun String.toPlanningCountryName(): String =
    logisticsCountryOptions().firstOrNull { it.code.equals(this, ignoreCase = true) }?.name ?: trim()

private const val MINUTES_PER_DAY = 24 * 60

internal fun LogisticsMilestone.withDefinitionLocation(value: String): LogisticsMilestone {
    val place = value.toLogisticsPlanningPlace()
    val countryName = LogisticsCountryNormalizer.displayName(place.country)
    return copy(
        location = place.city,
        placeName = place.city,
        countryCode = LogisticsCountryNormalizer.key(countryName),
        countryNameSnapshot = countryName,
        city = place.city,
    )
}
