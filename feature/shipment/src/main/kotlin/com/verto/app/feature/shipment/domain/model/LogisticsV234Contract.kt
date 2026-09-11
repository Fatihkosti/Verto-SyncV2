package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

/** Canonical v234 planning state. Shipment execution state remains [LogisticsShipmentState]. */
enum class LogisticsPlanState { DRAFT, APPROVED }

enum class LogisticsPlanRevisionKind { INITIAL_APPROVAL, FUTURE_EDIT }

enum class LogisticsPlanChangeScope { SHIPMENT, SOURCE, STATION, LEG, CUSTOMS }

enum class LogisticsDurationUnit { HOURS, DAYS }

/**
 * Planned cost only. It must never post cash/expense or mutate landed cost until execution confirms it.
 */
data class LogisticsPlannedCost(
    val amount: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val baseCurrencyAmount: BigDecimal,
)

/** Draft/private attachment reference captured during planning. It is not proof of an executed payment. */
data class LogisticsPlannedAttachment(
    val privateUri: String,
    val displayName: String,
    val mimeType: String,
)

/**
 * v234 customs contract. Customs is an event positioned after a station, never a route station.
 * Legacy CUSTOMS milestones remain readable for pre-v234 rows only.
 */
data class LogisticsCustomsPlan(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val checkpointName: String,
    val afterStationId: String,
    val expectedDurationMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class LogisticsCustomsPlanDocument(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val customsPlanId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val privateUri: String,
    val sha256: String,
    val createdAt: Long,
)

data class LogisticsPlanRevisionChange(
    val id: String,
    val scope: LogisticsPlanChangeScope,
    val scopeId: String? = null,
    val fieldKey: String,
    val previousValue: String? = null,
    val newValue: String? = null,
)

data class LogisticsPlanRevision(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val revisionNumber: Int,
    val kind: LogisticsPlanRevisionKind,
    val reason: String,
    val changedByEmployeeId: String? = null,
    val changedByEmployeeNameSnapshot: String? = null,
    val recordedAt: Long,
    val requestId: String,
    val changes: List<LogisticsPlanRevisionChange> = emptyList(),
)

/** One domain vocabulary for v234+. Milestones are stations; legs are movements between adjacent stations. */
object LogisticsV234Contract {
    fun planState(shipment: LogisticsShipment): LogisticsPlanState =
        if (shipment.currentPlanRevision > 0 || shipment.planApprovedAt != null) {
            LogisticsPlanState.APPROVED
        } else {
            LogisticsPlanState.DRAFT
        }

    fun isStation(milestone: LogisticsMilestone): Boolean =
        milestone.type != LogisticsMilestoneType.CUSTOMS

    fun isFutureLeg(leg: LogisticsShipmentLeg): Boolean =
        leg.status == LogisticsLegStatus.PLANNED && leg.actualDepartureAt == null && leg.actualArrivalAt == null

    fun requireCustomsPlan(plan: LogisticsCustomsPlan, stations: List<LogisticsMilestone>) {
        require(plan.id.isNotBlank() && plan.organizationId.isNotBlank() && plan.shipmentId.isNotBlank()) {
            "Customs plan identity is incomplete"
        }
        require(plan.checkpointName.trim().isNotBlank()) { "Customs checkpoint is required" }
        require(plan.expectedDurationMinutes > 0) { "Customs expected duration must be positive" }
        require(plan.createdAt >= 0L && plan.updatedAt >= plan.createdAt) { "Customs plan timestamps are invalid" }
        val routeStations = stations.filter(::isStation)
        require(routeStations.any { it.id == plan.afterStationId && it.shipmentId == plan.shipmentId }) {
            "Customs event must be positioned after a station in the shipment route"
        }
        require(routeStations.none { it.type == LogisticsMilestoneType.DESTINATION && it.id == plan.afterStationId }) {
            "Customs event cannot be positioned after the final destination"
        }
    }

    fun requireRevisionShipmentTransition(
        current: LogisticsShipment,
        updated: LogisticsShipment,
        revision: LogisticsPlanRevision,
    ) {
        require(current.id == updated.id && current.organizationId == updated.organizationId) {
            "Plan revision cannot change shipment identity or organization"
        }
        require(updated.currentPlanRevision == revision.revisionNumber) {
            "Shipment currentPlanRevision must match appended revision"
        }
        require(updated.planApprovedAt != null) { "Approved plan must have planApprovedAt" }
        when (revision.kind) {
            LogisticsPlanRevisionKind.INITIAL_APPROVAL -> {
                require(current.state == LogisticsShipmentState.DRAFT) { "Only a draft plan can be approved" }
                require(updated.state == LogisticsShipmentState.READY) { "Initial approval must move shipment to READY" }
                require(current.currentPlanRevision == 0 && current.planApprovedAt == null) {
                    "Shipment plan is already approved"
                }
            }
            LogisticsPlanRevisionKind.FUTURE_EDIT -> {
                require(current.currentPlanRevision >= 1 && current.planApprovedAt != null) {
                    "Future edit requires an approved plan"
                }
                require(updated.state == current.state) { "Future plan edit cannot change execution state" }
                require(updated.planApprovedAt == current.planApprovedAt) { "Future plan edit cannot rewrite approval time" }
            }
        }
    }

    fun requireRevision(revision: LogisticsPlanRevision, currentRevision: Int) {
        require(revision.organizationId.isNotBlank() && revision.shipmentId.isNotBlank() && revision.requestId.isNotBlank()) {
            "Plan revision identity is incomplete"
        }
        require(revision.revisionNumber == currentRevision + 1) { "Plan revision number must be consecutive" }
        require(revision.recordedAt >= 0L) { "Plan revision timestamp is invalid" }
        when (revision.kind) {
            LogisticsPlanRevisionKind.INITIAL_APPROVAL -> {
                require(currentRevision == 0 && revision.revisionNumber == 1) { "Initial approval must create revision 1" }
            }
            LogisticsPlanRevisionKind.FUTURE_EDIT -> {
                require(currentRevision >= 1) { "Future edit requires an approved plan" }
                require(revision.reason.trim().isNotBlank()) { "Future plan edit requires a reason" }
                require(revision.changes.isNotEmpty()) { "Future plan edit requires at least one change" }
            }
        }
        require(revision.changes.map { it.id }.distinct().size == revision.changes.size) { "Duplicate plan revision change id" }
        revision.changes.forEach { change ->
            require(change.id.isNotBlank() && change.fieldKey.isNotBlank()) { "Plan revision change is incomplete" }
            require(change.previousValue != change.newValue) { "Plan revision change must alter a value" }
        }
    }
}
