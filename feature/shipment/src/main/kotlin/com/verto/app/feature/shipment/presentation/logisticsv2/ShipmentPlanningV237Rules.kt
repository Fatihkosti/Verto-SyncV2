package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsDurationUnit
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedAttachment
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedCost
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import java.math.BigDecimal

internal const val V237_CURSOR_TRIP_TYPE = "V237_TRIP_TYPE"
internal const val V237_CURSOR_ROUTE_BUILDER = "V237_ROUTE_BUILDER"
internal const val V237_CURSOR_STATION_EDIT_PREFIX = "V237_STATION_EDIT:"
internal const val V237_CURSOR_STATION_CREATE_PREFIX = "V237_STATION_CREATE:"
internal const val V237_CURSOR_STATION_FLOW_PREFIX = "V237_STATION_FLOW:"

internal enum class LogisticsV237RouteScreen { TRIP_TYPE, ROUTE_BUILDER, STATION_DETAILS }

internal fun LogisticsRouteWorkspaceSnapshot.v237Screen(): LogisticsV237RouteScreen = when {
    expandedSection.startsWith(V237_CURSOR_STATION_EDIT_PREFIX) ||
        expandedSection.startsWith(V237_CURSOR_STATION_CREATE_PREFIX) ||
        expandedSection.startsWith(V237_CURSOR_STATION_FLOW_PREFIX) -> LogisticsV237RouteScreen.STATION_DETAILS
    expandedSection == V237_CURSOR_ROUTE_BUILDER -> LogisticsV237RouteScreen.ROUTE_BUILDER
    else -> LogisticsV237RouteScreen.TRIP_TYPE
}

internal fun LogisticsRouteWorkspaceSnapshot.v237StationId(): String? = when {
    expandedSection.startsWith(V237_CURSOR_STATION_EDIT_PREFIX) -> expandedSection.removePrefix(V237_CURSOR_STATION_EDIT_PREFIX)
    expandedSection.startsWith(V237_CURSOR_STATION_CREATE_PREFIX) -> expandedSection.removePrefix(V237_CURSOR_STATION_CREATE_PREFIX)
    expandedSection.startsWith(V237_CURSOR_STATION_FLOW_PREFIX) -> expandedSection.removePrefix(V237_CURSOR_STATION_FLOW_PREFIX)
    else -> null
}.orEmpty().takeIf(String::isNotBlank)

internal fun LogisticsRouteWorkspaceSnapshot.v237IsStationCreate(): Boolean =
    expandedSection.startsWith(V237_CURSOR_STATION_CREATE_PREFIX)

internal fun LogisticsRouteWorkspaceSnapshot.v237IsStationFlow(): Boolean =
    expandedSection.startsWith(V237_CURSOR_STATION_FLOW_PREFIX)

internal data class LogisticsV237TripValidation(
    val tripTypeError: String? = null,
    val unifiedModeError: String? = null,
) {
    val isValid: Boolean get() = tripTypeError == null && unifiedModeError == null
}

internal fun LogisticsRouteWorkspaceSnapshot.v237TripValidation(): LogisticsV237TripValidation = LogisticsV237TripValidation(
    tripTypeError = if (tripTypeSelected) null else "اختر نوع الرحلة",
    unifiedModeError = if (
        tripTypeSelected && routeTransportPlanKind == LogisticsRouteTransportPlanKind.UNIFIED && !unifiedTransportModeSelected
    ) "اختر نوع النقل" else null,
)

internal data class LogisticsV237StationValidation(
    val stationNameError: String? = null,
    val transportModeError: String? = null,
    val phoneError: String? = null,
    val packageCountError: String? = null,
    val weightError: String? = null,
    val amountError: String? = null,
    val currencyError: String? = null,
    val exchangeRateError: String? = null,
    val durationError: String? = null,
) {
    val isValid: Boolean
        get() = listOf(
            stationNameError,
            transportModeError,
            phoneError,
            packageCountError,
            weightError,
            amountError,
            currencyError,
            exchangeRateError,
            durationError,
        ).all { it == null }
}

internal fun v237DecimalInput(raw: String): String {
    val out = StringBuilder()
    var dotSeen = false
    raw.forEach { char ->
        when {
            char.isDigit() -> out.append(char)
            char == '.' && !dotSeen -> {
                if (out.isEmpty()) out.append('0')
                out.append(char)
                dotSeen = true
            }
        }
    }
    return out.toString()
}

internal fun v237NormalizePhone(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter(Char::isDigit)
    return if (trimmed.startsWith('+')) "+$digits" else digits
}

internal fun v237PhoneIsValid(raw: String): Boolean {
    val normalized = v237NormalizePhone(raw)
    val digits = normalized.filter(Char::isDigit)
    return normalized.isBlank() || digits.length in 7..15
}

internal fun LogisticsShipmentLeg.v237DurationUnit(): LogisticsDurationUnit =
    if (expectedTransitDays != null) LogisticsDurationUnit.DAYS else LogisticsDurationUnit.HOURS

internal fun LogisticsShipmentLeg.v237DurationValue(): Int? = when (v237DurationUnit()) {
    LogisticsDurationUnit.DAYS -> expectedTransitDays
    LogisticsDurationUnit.HOURS -> expectedTransitMinutes?.let { minutes ->
        if (minutes % MINUTES_PER_HOUR == 0) minutes / MINUTES_PER_HOUR else null
    }
}

internal fun LogisticsShipmentLeg.withV237Duration(value: Int?, unit: LogisticsDurationUnit): LogisticsShipmentLeg = when (unit) {
    LogisticsDurationUnit.DAYS -> copy(
        expectedTransitDays = value,
        expectedTransitMinutes = value?.times(MINUTES_PER_DAY),
    )
    LogisticsDurationUnit.HOURS -> copy(
        expectedTransitDays = null,
        expectedTransitMinutes = value?.times(MINUTES_PER_HOUR),
    )
}

internal fun v237PlannedCost(
    amount: String,
    currency: String,
    exchangeRate: String,
): LogisticsPlannedCost? {
    if (amount.isBlank()) return null
    val parsedAmount = amount.toBigDecimalOrNull() ?: return null
    if (parsedAmount.signum() < 0) return null
    val normalizedCurrency = runCatching { LogisticsCurrencyPolicy.normalizeIso4217(currency) }.getOrNull() ?: return null
    val rate = if (normalizedCurrency == LogisticsCurrencyPolicy.BASE_CURRENCY) {
        BigDecimal.ONE
    } else {
        exchangeRate.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
    return LogisticsPlannedCost(
        amount = parsedAmount,
        currency = normalizedCurrency,
        exchangeRate = rate,
        baseCurrencyAmount = if (rate.signum() > 0) parsedAmount.multiply(rate) else BigDecimal.ZERO,
    )
}

internal fun LogisticsShipmentLeg.v237Validation(
    target: LogisticsMilestone,
    routeKind: LogisticsRouteTransportPlanKind,
): LogisticsV237StationValidation {
    val cost = plannedCost
    val rawPhone = plannedRepresentativePhoneSnapshot.orEmpty()
    return LogisticsV237StationValidation(
        stationNameError = if (target.location.trim().isNotBlank()) null else "أدخل اسم المحطة",
        transportModeError = if (
            routeKind == LogisticsRouteTransportPlanKind.MIXED && mode == LogisticsLegTransportMode.UNSPECIFIED
        ) "اختر نوع النقل" else null,
        phoneError = if (v237PhoneIsValid(rawPhone)) null else "أدخل رقم هاتف صحيح",
        packageCountError = if (plannedPackageCount?.let { it > 0 } == true) null else "أدخل عدد الكراتين",
        weightError = if (plannedWeightKg?.signum() == 1) null else "أدخل الوزن",
        amountError = if (cost == null || cost.amount.signum() >= 0) null else "المبلغ لا يمكن أن يكون سالبًا",
        currencyError = if (cost == null || runCatching { LogisticsCurrencyPolicy.normalizeIso4217(cost.currency) }.isSuccess) null else "اختر العملة",
        exchangeRateError = if (
            cost == null || cost.currency == LogisticsCurrencyPolicy.BASE_CURRENCY || cost.exchangeRate.signum() == 1
        ) null else "أدخل سعر صرف أكبر من صفر",
        durationError = if ((expectedTransitMinutes ?: expectedTransitDays?.times(MINUTES_PER_DAY) ?: 0) > 0) null else "أدخل الزمن المتوقع",
    )
}

internal fun LogisticsRouteWorkspaceSnapshot.withV237TripKind(kind: LogisticsRouteTransportPlanKind): LogisticsRouteWorkspaceSnapshot {
    val wasMixed = tripTypeSelected && routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED
    val mode = if (kind == LogisticsRouteTransportPlanKind.UNIFIED && unifiedTransportModeSelected && !wasMixed) {
        unifiedTransportMode
    } else if (kind == LogisticsRouteTransportPlanKind.MIXED) {
        LogisticsLegTransportMode.UNSPECIFIED
    } else {
        LogisticsLegTransportMode.UNSPECIFIED
    }
    return copy(
        routeTransportPlanKind = kind,
        tripTypeSelected = true,
        unifiedTransportModeSelected = if (kind == LogisticsRouteTransportPlanKind.UNIFIED) unifiedTransportModeSelected && !wasMixed else unifiedTransportModeSelected,
        legs = legs.map { leg ->
            if (kind == LogisticsRouteTransportPlanKind.MIXED) {
                leg.copy(mode = LogisticsLegTransportMode.UNSPECIFIED)
            } else if (mode != LogisticsLegTransportMode.UNSPECIFIED) {
                leg.copy(mode = mode)
            } else {
                leg.copy(mode = LogisticsLegTransportMode.UNSPECIFIED)
            }
        },
    )
}

internal fun LogisticsRouteWorkspaceSnapshot.withV237UnifiedMode(mode: LogisticsLegTransportMode): LogisticsRouteWorkspaceSnapshot {
    require(mode in V237_CONCRETE_TRANSPORT_MODES) { "Only ROAD, SEA and AIR are planning transport modes" }
    return copy(
        routeTransportPlanKind = LogisticsRouteTransportPlanKind.UNIFIED,
        tripTypeSelected = true,
        unifiedTransportMode = mode,
        unifiedTransportModeSelected = true,
        legs = legs.map { it.copy(mode = mode) },
    )
}

internal fun LogisticsShipmentLeg.withV237CargoDefaults(draft: LogisticsPlanningDraft): LogisticsShipmentLeg = copy(
    plannedPackageCount = plannedPackageCount ?: draft.v236PlannedPackageTotal.takeIf { it > 0 },
    plannedWeightKg = plannedWeightKg ?: draft.v236PlannedWeightTotal.takeIf { it.signum() == 1 },
)

internal fun LogisticsRouteWorkspaceSnapshot.withV237CargoDefaults(draft: LogisticsPlanningDraft): LogisticsRouteWorkspaceSnapshot {
    var previousPackages = draft.v236PlannedPackageTotal.takeIf { it > 0 }
    var previousWeight = draft.v236PlannedWeightTotal.takeIf { it.signum() == 1 }
    val populated = legs.sortedBy { it.sequence }.map { leg ->
        val mode = when {
            routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED -> leg.mode
            unifiedTransportModeSelected -> unifiedTransportMode
            else -> leg.mode
        }
        val updated = leg.copy(
            mode = mode,
            plannedPackageCount = leg.plannedPackageCount ?: previousPackages,
            plannedWeightKg = leg.plannedWeightKg ?: previousWeight,
        )
        previousPackages = updated.plannedPackageCount ?: previousPackages
        previousWeight = updated.plannedWeightKg ?: previousWeight
        updated
    }
    return copy(legs = populated)
}

internal fun LogisticsRouteWorkspaceSnapshot.withV237StationName(
    milestoneId: String,
    name: String,
): LogisticsRouteWorkspaceSnapshot = copy(
    duplicateConfirmedMilestoneIds = duplicateConfirmedMilestoneIds - milestoneId,
    milestones = milestones.map { milestone ->
        if (milestone.id == milestoneId && milestone.type != LogisticsMilestoneType.ORIGIN && milestone.type != LogisticsMilestoneType.DESTINATION) {
            milestone.copy(location = name, city = name, placeName = name)
        } else milestone
    },
)

internal fun LogisticsRouteWorkspaceSnapshot.withV237Leg(
    legId: String,
    transform: (LogisticsShipmentLeg) -> LogisticsShipmentLeg,
): LogisticsRouteWorkspaceSnapshot = copy(
    legs = legs.map { leg -> if (leg.id == legId) transform(leg) else leg },
)

internal fun LogisticsRouteWorkspaceSnapshot.v237LegForTarget(milestoneId: String): LogisticsShipmentLeg? =
    legs.firstOrNull { it.toMilestoneId == milestoneId }

internal fun LogisticsRouteWorkspaceSnapshot.v237TargetForLeg(leg: LogisticsShipmentLeg): LogisticsMilestone? =
    milestones.firstOrNull { it.id == leg.toMilestoneId }

internal fun LogisticsRouteWorkspaceSnapshot.v237ReadyProofForLeg(legId: String): LogisticsPendingDocumentDraft? =
    pendingDocuments.lastOrNull { it.legId == legId && it.isReady }

internal fun LogisticsRouteWorkspaceSnapshot.v237LegsWithReadyProofs(): List<LogisticsShipmentLeg> = legs.map { leg ->
    val pending = v237ReadyProofForLeg(leg.id)
    if (pending == null) leg else leg.copy(
        plannedProof = LogisticsPlannedAttachment(
            privateUri = requireNotNull(pending.privateUri),
            displayName = pending.displayName,
            mimeType = pending.mimeType,
        ),
    )
}

internal fun LogisticsRouteWorkspaceSnapshot.v237FirstIncompleteLeg(): LogisticsShipmentLeg? =
    legs.sortedBy { it.sequence }.firstOrNull { leg ->
        val target = v237TargetForLeg(leg) ?: return@firstOrNull true
        !leg.v237Validation(target, routeTransportPlanKind).isValid
    }

internal fun LogisticsRouteWorkspaceSnapshot.v237NextIncompleteLeg(afterSequence: Int): LogisticsShipmentLeg? =
    legs.sortedBy { it.sequence }.firstOrNull { leg ->
        if (leg.sequence <= afterSequence) return@firstOrNull false
        val target = v237TargetForLeg(leg) ?: return@firstOrNull true
        !leg.v237Validation(target, routeTransportPlanKind).isValid
    }

internal fun LogisticsRouteWorkspaceSnapshot.v237PreviousLeg(beforeSequence: Int): LogisticsShipmentLeg? =
    legs.filter { it.sequence < beforeSequence }.maxByOrNull { it.sequence }

internal fun LogisticsRouteWorkspaceSnapshot.v237UnconfirmedImmediateDuplicateId(): String? {
    val ordered = milestones.sortedBy { it.order }
    return ordered.zipWithNext().firstNotNullOfOrNull { (left, right) ->
        right.id.takeIf {
            left.location.trim().isNotBlank() &&
                left.location.trim().equals(right.location.trim(), ignoreCase = true) &&
                right.id !in duplicateConfirmedMilestoneIds
        }
    }
}

internal fun LogisticsRouteWorkspaceSnapshot.withV237DuplicateConfirmed(milestoneId: String): LogisticsRouteWorkspaceSnapshot =
    copy(duplicateConfirmedMilestoneIds = duplicateConfirmedMilestoneIds + milestoneId)

internal fun LogisticsRouteWorkspaceSnapshot.withV237ReorderedIntermediate(
    draft: LogisticsPlanningDraft,
    milestoneId: String,
    delta: Int,
): LogisticsRouteWorkspaceSnapshot {
    if (delta == 0) return this
    val ordered = milestones.sortedBy { it.order }.toMutableList()
    val index = ordered.indexOfFirst { it.id == milestoneId }
    if (index <= 0 || index >= ordered.lastIndex) return this
    val targetIndex = (index + delta).coerceIn(1, ordered.lastIndex - 1)
    if (targetIndex == index) return this
    val moved = ordered.removeAt(index)
    ordered.add(targetIndex, moved)
    val previousLegIds = legs.map { it.id }.toSet()
    val reconciled = withMilestonesAndReconciledLegs(draft, ordered).withV237CargoDefaults(draft)
    val liveLegIds = reconciled.legs.map { it.id }.toSet()
    return reconciled.copy(
        duplicateConfirmedMilestoneIds = emptySet(),
        pendingDocuments = pendingDocuments.filter { it.legId == null || it.legId !in previousLegIds || it.legId in liveLegIds },
    )
}

internal fun LogisticsRouteWorkspaceSnapshot.withV237RemovedIntermediate(
    draft: LogisticsPlanningDraft,
    milestoneId: String,
): LogisticsRouteWorkspaceSnapshot {
    val previousLegIds = legs.map { it.id }.toSet()
    val reconciled = withoutIntermediateMilestone(draft, milestoneId).withV237CargoDefaults(draft)
    val liveLegIds = reconciled.legs.map { it.id }.toSet()
    return reconciled.copy(
        duplicateConfirmedMilestoneIds = duplicateConfirmedMilestoneIds - milestoneId,
        pendingDocuments = pendingDocuments.filter { it.milestoneId != milestoneId && (it.legId == null || it.legId !in previousLegIds || it.legId in liveLegIds) },
    )
}

internal fun LogisticsPlanningDraft.isV237RouteReady(workspace: LogisticsRouteWorkspaceSnapshot): Boolean {
    if (!workspace.v237TripValidation().isValid) return false
    val orderedMilestones = workspace.milestones.sortedBy { it.order }
    val orderedLegs = workspace.v237LegsWithReadyProofs().sortedBy { it.sequence }
    if (orderedMilestones.size < 2 || orderedLegs.size != orderedMilestones.size - 1) return false
    if (orderedMilestones.first().type != LogisticsMilestoneType.ORIGIN || orderedMilestones.last().type != LogisticsMilestoneType.DESTINATION) return false
    if (orderedMilestones.any { it.type == LogisticsMilestoneType.CUSTOMS }) return false
    if (orderedMilestones.map { it.order } != orderedMilestones.indices.toList()) return false
    if (workspace.v237UnconfirmedImmediateDuplicateId() != null) return false
    if (orderedMilestones.first().location != sourceLocation.toLogisticsPlanningPlace().city) return false
    if (orderedMilestones.last().location != destinationLocation.toLogisticsPlanningPlace().city) return false
    if (orderedLegs.map { it.sequence } != orderedLegs.indices.toList()) return false
    if (orderedLegs.any { leg ->
            orderedMilestones.getOrNull(leg.sequence)?.id != leg.fromMilestoneId ||
                orderedMilestones.getOrNull(leg.sequence + 1)?.id != leg.toMilestoneId
        }
    ) return false
    if (workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.UNIFIED) {
        if (!workspace.unifiedTransportModeSelected || workspace.unifiedTransportMode !in V237_CONCRETE_TRANSPORT_MODES) return false
        if (orderedLegs.any { it.mode != workspace.unifiedTransportMode }) return false
    } else if (orderedLegs.any { it.mode !in V237_CONCRETE_TRANSPORT_MODES }) return false
    return orderedLegs.all { leg ->
        val target = orderedMilestones.getOrNull(leg.sequence + 1) ?: return@all false
        leg.v237Validation(target, workspace.routeTransportPlanKind).isValid &&
            leg.carrierPartnerId.isBlank() && leg.packageCount == null && leg.weightKg == null &&
            leg.actualDepartureAt == null && leg.actualArrivalAt == null &&
            leg.representativeNameSnapshot == null && leg.representativePhoneSnapshot == null
    }
}

internal fun LogisticsPlanningDraft.isV237PersistableRoute(): Boolean {
    val orderedMilestones = milestones.sortedBy { it.order }
    val orderedLegs = legs.sortedBy { it.sequence }
    if (orderedMilestones.size < 2 || orderedLegs.size != orderedMilestones.size - 1) return false
    if (orderedMilestones.first().type != LogisticsMilestoneType.ORIGIN || orderedMilestones.last().type != LogisticsMilestoneType.DESTINATION) return false
    if (orderedMilestones.any { it.type == LogisticsMilestoneType.CUSTOMS }) return false
    if (orderedMilestones.map { it.order } != orderedMilestones.indices.toList()) return false
    if (orderedLegs.map { it.sequence } != orderedLegs.indices.toList()) return false
    if (orderedLegs.any { it.mode !in V237_CONCRETE_TRANSPORT_MODES }) return false
    return orderedLegs.all { leg ->
        val from = orderedMilestones.getOrNull(leg.sequence) ?: return@all false
        val target = orderedMilestones.getOrNull(leg.sequence + 1) ?: return@all false
        leg.fromMilestoneId == from.id && leg.toMilestoneId == target.id &&
            leg.v237Validation(target, if (orderedLegs.map { it.mode }.toSet().size > 1) LogisticsRouteTransportPlanKind.MIXED else LogisticsRouteTransportPlanKind.UNIFIED).isValid &&
            leg.carrierPartnerId.isBlank() && leg.packageCount == null && leg.weightKg == null &&
            leg.actualDepartureAt == null && leg.actualArrivalAt == null
    }
}

internal val V237_CONCRETE_TRANSPORT_MODES: Set<LogisticsLegTransportMode> = setOf(
    LogisticsLegTransportMode.ROAD,
    LogisticsLegTransportMode.SEA,
    LogisticsLegTransportMode.AIR,
)

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
