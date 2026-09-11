package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

data class CreateLogisticsShipmentCommand(
    val organizationId: String,
    val shipmentNumber: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val createdAt: Long,
    val requestId: String,
    val notes: String = "",
)

data class PrepareLogisticsShipmentCommand(
    val shipmentId: String,
    val assignee: LogisticsAssigneeSnapshot,
    val sources: List<LogisticsShipmentSource>,
    val lines: List<LogisticsShipmentLine>,
    val expectedDepartureAt: Long? = null,
    val expectedArrivalAt: Long? = null,
    val transportDetails: LogisticsTransportDetails? = null,
    val requestId: String,
)

data class StartLogisticsShipmentCommand(
    val shipmentId: String,
    val startedAt: Long,
    val requestId: String,
)

data class LogisticsMovementExecutionFacts(
    val carrierPartnerId: String,
    val representativeName: String? = null,
    val representativePhone: String? = null,
    val packageCount: Int,
    val weightKg: BigDecimal,
)

data class PrepareLogisticsMovementCommand(
    val shipmentId: String,
    val legId: String,
    val facts: LogisticsMovementExecutionFacts,
    val preparedAt: Long,
    val requestId: String,
)

data class StartLogisticsMovementCommand(
    val shipmentId: String,
    val movedAt: Long,
    val requestId: String,
)

data class AssignLogisticsShipmentEmployeeCommand(
    val shipmentId: String,
    val employeeId: String,
    val employeeNameSnapshot: String,
    val assignedAt: Long,
    val requestId: String,
)

data class CancelLogisticsShipmentCommand(
    val shipmentId: String,
    val cancelledAt: Long,
    val requestId: String,
    val reason: String = "",
)

data class RecordLogisticsMilestoneArrivalCommand(
    val shipmentId: String,
    val milestoneId: String,
    val arrivedAt: Long,
    val requestId: String,
    val note: String = "",
)

data class RecordLogisticsMilestoneDepartureCommand(
    val shipmentId: String,
    val milestoneId: String,
    val departedAt: Long,
    val requestId: String,
    val note: String = "",
)

data class LogisticsRouteTransportIntent(
    val kind: LogisticsRouteTransportPlanKind,
    val unifiedMode: LogisticsLegTransportMode? = null,
)

data class SaveShipmentRouteCommand(
    val shipmentId: String,
    val milestones: List<LogisticsMilestone>,
    val legs: List<LogisticsShipmentLeg>,
    val occurredAt: Long,
    val requestId: String,
    /** v237 explicit route intent; null keeps backward compatibility for older callers. */
    val transportIntent: LogisticsRouteTransportIntent? = null,
)

data class RecordCustodyHandoffCommand(
    val shipmentId: String,
    val sourceId: String? = null,
    val milestoneId: String? = null,
    val fromHolderType: LogisticsCustodyHolderType,
    val fromHolderId: String? = null,
    val fromHolderNameSnapshot: String,
    val toHolderType: LogisticsCustodyHolderType,
    val toHolderId: String? = null,
    val toHolderNameSnapshot: String,
    val transferredAt: Long,
    val receivedAt: Long,
    val requestId: String,
    val note: String = "",
    val handoverPackageCount: Int? = null,
    val receivedPackageCount: Int? = null,
    val handoverWeightKg: BigDecimal? = null,
    val receivedWeightKg: BigDecimal? = null,
    val discrepancyNote: String = "",
    val openedPackageCount: Int = 0,
    val damagedPackageCount: Int = 0,
)

data class LogisticsCustodyReceiptCounts(
    val handoverPackageCount: Int,
    val receivedPackageCount: Int,
    val openedPackageCount: Int = 0,
    val damagedPackageCount: Int = 0,
    val discrepancyNote: String = "",
)

data class StartLogisticsCustomsCommand(
    val shipmentId: String,
    val milestoneId: String,
    val brokerPartnerId: String,
    val receivedAt: Long,
    val requestId: String,
    val receipt: LogisticsCustodyReceiptCounts,
)

data class CompleteLogisticsCustomsCommand(
    val shipmentId: String,
    val milestoneId: String,
    val completedAt: Long,
    val requestId: String,
)

data class LogisticsRepackProof(
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
)

data class LogisticsCargoRepackChange(
    val previous: LogisticsCargoSnapshot,
    val updated: LogisticsCargoSnapshot,
    val reason: LogisticsPackageChangeReason,
    val note: String,
)

data class RecordCargoRepackCommand(
    val shipmentId: String,
    val milestoneId: String? = null,
    val change: LogisticsCargoRepackChange,
    val occurredAt: Long,
    val requestId: String,
    val proof: LogisticsRepackProof? = null,
)

data class UpdateShipmentEtaCommand(
    val shipmentId: String,
    val legId: String,
    val plannedArrivalAt: Long?,
    val occurredAt: Long,
    val requestId: String,
)

data class UpdateFutureShipmentLegCommand(
    val shipmentId: String,
    val leg: LogisticsShipmentLeg,
    val occurredAt: Long,
    val requestId: String,
    val reason: String = "",
    val changedByEmployeeId: String? = null,
    val changedByEmployeeName: String? = null,
)

data class UpdateMilestoneHandlingCommand(
    val shipmentId: String,
    val milestoneId: String,
    val targetStatus: LogisticsMilestoneHandlingStatus,
    val occurredAt: Long,
    val requestId: String,
)

data class ReopenShipmentPlanningCommand(
    val shipmentId: String,
    val reopenedAt: Long,
    val requestId: String,
)

data class RecordLogisticsFollowUpCommand(
    val shipmentId: String,
    val partnerId: String? = null,
    val phone: String? = null,
    val note: String,
    val nextFollowUpAt: Long? = null,
    val occurredAt: Long,
    val requestId: String,
)

data class CorrectLogisticsActualTimeCommand(
    val shipmentId: String,
    val milestoneId: String,
    val target: LogisticsActualTimeTarget,
    val correctedAt: Long,
    val reason: String,
    val occurredAt: Long,
    val requestId: String,
)

data class StartLogisticsReceivingCommand(
    val shipmentId: String,
    val startedAt: Long,
    val requestId: String,
)

data class RecordLogisticsReceivingBatchCommand(
    val shipmentId: String,
    val batchId: String,
    val receivedAt: Long,
    val receivedByEmployeeId: String,
    val receivedByEmployeeNameSnapshot: String,
    val lines: List<LogisticsReceivingLine>,
    val requestId: String,
)

data class RecordLogisticsCostCommand(
    val shipmentId: String,
    val type: LogisticsCostType,
    val amount: BigDecimal,
    val currency: String,
    val exchangeRateSnapshot: BigDecimal,
    val exchangeRateDate: Long? = null,
    val baseCurrencyAmount: BigDecimal,
    val status: LogisticsCostStatus,
    val servicePartnerId: String? = null,
    val legId: String? = null,
    val milestoneId: String? = null,
    val sourceId: String? = null,
    val reference: String? = null,
    val note: String = "",
    val requestId: String,
)

data class CloseLogisticsShipmentCommand(
    val shipmentId: String,
    val closedAt: Long,
    val requestId: String,
    val noAdditionalCosts: Boolean = false,
)
