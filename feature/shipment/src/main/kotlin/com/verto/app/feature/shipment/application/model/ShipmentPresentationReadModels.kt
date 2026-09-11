package com.verto.app.feature.shipment.application.model

import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import java.math.BigDecimal

data class LogisticsEmployeeRef(
    val id: String,
    val name: String,
)

data class LogisticsActorRef(val id: String, val name: String)

enum class LogisticsUnifiedSource { V2 }

data class LogisticsUnifiedReadRecord(
    val source: LogisticsUnifiedSource,
    val id: String,
    val shipmentNumber: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val createdAt: Long,
    val expectedArrivalAt: Long?,
    val v2Shipment: LogisticsShipment? = null,
)

enum class LogisticsDelayClockKind { NONE, TRANSIT, DWELL }

enum class LogisticsDelayContactSource {
    CUSTODY_HOLDER,
    LEG_CARRIER,
    CUSTOMS_BROKER,
    FOLLOW_UP_EMPLOYEE,
}

data class LogisticsDelayContactReadModel(
    val displayName: String,
    val role: String,
    val phone: String?,
    val source: LogisticsDelayContactSource,
)

data class LogisticsDelayTimingReadModel(
    val startedAt: Long?,
    val expectedMillis: Long,
    val elapsedMillis: Long,
    val delayMillis: Long,
    val delayDays: Long,
    val veryLateThresholdAt: Long?,
)

data class LogisticsDelayReadModel(
    val level: com.verto.app.feature.shipment.domain.model.LogisticsDelayLevel,
    val clockKind: LogisticsDelayClockKind,
    val timing: LogisticsDelayTimingReadModel,
    val currentStage: String,
    val contact: LogisticsDelayContactReadModel?,
    val lastOperationalUpdateAt: Long?,
)


enum class ShipmentCardPhase { BEFORE_MOVEMENT, IN_MOVEMENT, CLOSED }

enum class ShipmentTimelineItemKind { MILESTONE, LEG }

enum class ShipmentTimelineState { COMPLETED, ACTIVE, PLANNED, SUPERSEDED, CANCELLED }

data class ShipmentTimelineTiming(
    val planKind: LogisticsPlanKind,
    val sequence: Int,
    val occurredAt: Long? = null,
    val plannedAt: Long? = null,
)

data class ShipmentTimelineReadItem(
    val id: String,
    val kind: ShipmentTimelineItemKind,
    val title: String,
    val detail: String,
    val state: ShipmentTimelineState,
    val timing: ShipmentTimelineTiming,
) {
    val planKind: LogisticsPlanKind get() = timing.planKind
    val sequence: Int get() = timing.sequence
    val occurredAt: Long? get() = timing.occurredAt
    val plannedAt: Long? get() = timing.plannedAt
}

data class ShipmentEventReadRecord(
    val id: String,
    val type: String,
    val occurredAt: Long,
    val employeeName: String? = null,
    val payloadJson: String = "{}",
)

data class ShipmentClosedRoutesReadModel(
    val planned: List<String>,
    val actual: List<String>,
)

data class ShipmentClosedTimingReadModel(
    val plannedStartAt: Long?,
    val plannedEndAt: Long?,
    val actualStartAt: Long?,
    val actualEndAt: Long?,
    val delayLocation: String? = null,
    val delayReason: String? = null,
    val delayMillis: Long = 0L,
)

data class ShipmentClosedEmployeeReadModel(
    val employeeId: String?,
    val employeeName: String?,
)

data class ShipmentClosedCostPaymentReadModel(
    val costId: String,
    val description: String,
    val costAmount: BigDecimal,
    val costCurrency: String,
    val baseAmount: BigDecimal,
    val paymentState: String,
    val paidAmount: BigDecimal,
    val paidAt: Long?,
    val proofDocumentName: String?,
)

data class ShipmentClosedShortageReadModel(
    val shortageId: String,
    val itemName: String,
    val originalMissingQuantity: Int,
    val remainingMissingQuantity: Int,
    val status: String,
    val settlement: String?,
)

data class ShipmentClosedAttachmentReadModel(
    val documentId: String,
    val displayName: String,
    val type: String,
    val employeeName: String?,
    val createdAt: Long,
)

data class ShipmentClosedValuesReadModel(
    val accepted: BigDecimal,
    val missing: BigDecimal,
    val recovered: BigDecimal,
    val shipmentCost: BigDecimal,
    val recoveryCost: BigDecimal,
    val finalInventory: BigDecimal,
)

data class ShipmentClosedDetailReadModel(
    val suppliers: List<String>,
    val invoices: List<String>,
    val routes: ShipmentClosedRoutesReadModel,
    val timing: ShipmentClosedTimingReadModel,
    val employee: ShipmentClosedEmployeeReadModel,
    val values: ShipmentClosedValuesReadModel,
    val costsAndPayments: List<ShipmentClosedCostPaymentReadModel>,
    val shortages: List<ShipmentClosedShortageReadModel>,
    val attachments: List<ShipmentClosedAttachmentReadModel>,
    val events: List<ShipmentEventReadRecord>,
) {
    val plannedRoute: List<String> get() = routes.planned
    val actualRoute: List<String> get() = routes.actual
    val plannedStartAt: Long? get() = timing.plannedStartAt
    val plannedEndAt: Long? get() = timing.plannedEndAt
    val actualStartAt: Long? get() = timing.actualStartAt
    val actualEndAt: Long? get() = timing.actualEndAt
    val acceptedValue: BigDecimal get() = values.accepted
    val missingValue: BigDecimal get() = values.missing
    val recoveredValue: BigDecimal get() = values.recovered
    val shipmentCostTotal: BigDecimal get() = values.shipmentCost
    val recoveryCostTotal: BigDecimal get() = values.recoveryCost
    val finalInventoryValue: BigDecimal get() = values.finalInventory
}

data class ShipmentPresentationSnapshot(
    val timeline: List<ShipmentTimelineReadItem>,
    val closed: ShipmentClosedDetailReadModel? = null,
)

data class LogisticsDelayCountsReadModel(
    val normal: Int = 0,
    val late: Int = 0,
    val veryLate: Int = 0,
)

data class LogisticsCountryMovementReadModel(
    val count: Int,
    val transitDurationMillis: Long,
    val stationDwellDurationMillis: Long,
)

data class LogisticsCountrySummaryReadModel(
    val countryCode: String,
    val countryName: String,
    val movement: LogisticsCountryMovementReadModel,
    val scopedCost: BigDecimal,
    val delays: LogisticsDelayCountsReadModel,
) {
    val actualMovementCount: Int get() = movement.count
    val actualTransitDurationMillis: Long get() = movement.transitDurationMillis
    val actualStationDwellDurationMillis: Long get() = movement.stationDwellDurationMillis
    val lateCount: Int get() = delays.late
    val veryLateCount: Int get() = delays.veryLate
}

data class LogisticsCarrierMovementReadModel(
    val handledLegCount: Int,
    val averageActualTransitMillis: Long,
)

data class LogisticsCarrierSummaryReadModel(
    val carrierId: String,
    val carrierName: String?,
    val movement: LogisticsCarrierMovementReadModel,
    val delays: LogisticsDelayCountsReadModel,
    val scopedCost: BigDecimal,
) {
    val handledLegCount: Int get() = movement.handledLegCount
    val averageActualTransitMillis: Long get() = movement.averageActualTransitMillis
    val normalCount: Int get() = delays.normal
    val lateCount: Int get() = delays.late
    val veryLateCount: Int get() = delays.veryLate
}

data class LogisticsRouteStopsReadModel(
    val planned: Int,
    val actual: Int,
    val unplanned: Int,
)

data class LogisticsRouteSummaryReadModel(
    val shipmentId: String,
    val shipmentNumber: String,
    val stops: LogisticsRouteStopsReadModel,
    val totalElapsedMillis: Long,
    val totalCost: BigDecimal,
    val crossBorderCost: BigDecimal,
) {
    val plannedStopCount: Int get() = stops.planned
    val actualStopCount: Int get() = stops.actual
    val unplannedStopCount: Int get() = stops.unplanned
}

data class LogisticsRouteAnalyticsReadModel(
    val countries: List<LogisticsCountrySummaryReadModel> = emptyList(),
    val carriers: List<LogisticsCarrierSummaryReadModel> = emptyList(),
    val routes: List<LogisticsRouteSummaryReadModel> = emptyList(),
)
