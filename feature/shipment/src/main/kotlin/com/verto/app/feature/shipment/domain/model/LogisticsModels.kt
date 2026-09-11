package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

enum class LogisticsShipmentState {
    DRAFT,
    READY,
    WAITING_DEPARTURE,
    IN_TRANSIT,
    AT_STATION,
    CUSTOMS,
    RECEIVING,
    CLOSED,
    CANCELLED,
    /** Persisted v228 compatibility only; new v229 commands never write this state. */
    @Deprecated("v229 uses AT_STATION") ARRIVED,
    /** Persisted v228 compatibility only; new v229 commands never write this state. */
    @Deprecated("v229 keeps incomplete receiving in RECEIVING") PARTIAL,
    /** Persisted v228 compatibility only; new v229 commands never write this state. */
    @Deprecated("v229 closes after receiving/cost settlement") RECEIVED,
}

enum class LogisticsTransportMode { SEA, AIR, ROAD, MULTIMODAL }

enum class LogisticsMilestoneType {
    ORIGIN,
    TRANSIT,
    /** Pre-v234 persisted compatibility. New planning stores customs in [LogisticsCustomsPlan]. */
    @Deprecated("v234 customs is a separate event, not a station")
    CUSTOMS,
    DESTINATION,
}

enum class LogisticsMilestoneHandlingStatus {
    PENDING,
    ARRIVED,
    UNLOADED,
    LOADED,
    DEPARTED,
}

enum class LogisticsLegTransportMode { ROAD, SEA, AIR, /** Planning-only marker for MIXED routes; v231 resolves it before movement. */ UNSPECIFIED }

enum class LogisticsLegStatus {
    PLANNED,
    IN_TRANSIT,
    ARRIVED,
    SUPERSEDED,
    CANCELLED,
}

enum class LogisticsCustodyHolderType {
    SUPPLIER,
    LOGISTICS_PARTNER,
    WAREHOUSE,
    OTHER,
}

enum class LogisticsPartnerRole {
    CARRIER,
    FREIGHT_FORWARDER,
    CUSTOMS_BROKER,
    INSPECTOR,
    INSURER,
    OTHER,
}

enum class LogisticsDocumentType {
    COMMERCIAL_INVOICE,
    PACKING_LIST,
    BILL_OF_LADING,
    AIR_WAYBILL,
    CERTIFICATE_OF_ORIGIN,
    CUSTOMS_DOCUMENT,
    INSPECTION_REPORT,
    INSURANCE_DOCUMENT,
    OTHER,
}

enum class LogisticsCostType {
    FREIGHT,
    CUSTOMS_DUTY,
    CLEARANCE,
    STORAGE,
    INSURANCE,
    INSPECTION,
    LOCAL_TRANSPORT,
    OTHER,
}

enum class LogisticsCostStatus { ESTIMATED, ACTUAL }

enum class LogisticsOperationalAction {
    CALL,
    FOLLOW_UP,
    UPDATE_ETA,
    RECORD_ARRIVAL,
    CONFIRM_UNLOAD,
    START_CUSTOMS,
    COMPLETE_CUSTOMS,
    HANDOFF,
    CONFIRM_LOAD,
    RECORD_DEPARTURE,
    EDIT_FUTURE_PLAN,
    CANCEL_SHIPMENT,
    START_RECEIVING,
}

enum class LogisticsActualTimeTarget {
    MILESTONE_ARRIVAL,
    MILESTONE_DEPARTURE,
}

data class LogisticsCustodyPosition(
    val holderType: LogisticsCustodyHolderType,
    val holderId: String?,
    val holderName: String,
    val receivedAt: Long?,
)

data class LogisticsOperationalStatus(
    val currentLocation: String,
    val currentCustodianName: String,
    val currentCustodianPhone: String?,
    val nextMilestoneName: String?,
    val nextCarrierName: String?,
    val nextCarrierPhone: String?,
    val expectedArrivalAt: Long?,
    val delayMillis: Long,
    val activeLegId: String?,
    val availableActions: Set<LogisticsOperationalAction>,
)

enum class LogisticsEventType {
    SHIPMENT_CREATED,
    SHIPMENT_READY,
    SHIPMENT_STARTED,
    MOVEMENT_PREPARED,
    SHIPMENT_MOVEMENT_STARTED,
    CUSTOMS_STARTED,
    CUSTOMS_COMPLETED,
    PAYMENT_RECORDED,
    ASSIGNEE_CHANGED,
    MILESTONE_ARRIVED,
    MILESTONE_DEPARTED,
    COST_RECORDED,
    DOCUMENT_ADDED,
    DESTINATION_ARRIVED,
    RECEIVING_STARTED,
    RECEIVING_RECORDED,
    STOCK_POSTED,
    COST_SETTLED,
    SHIPMENT_CLOSED,
    SHIPMENT_CANCELLED,
    EXCEPTION_RECORDED,
    SHIPMENT_PLAN_UPDATED,
    LEG_UPDATED,
    ETA_UPDATED,
    HANDOFF_RECORDED,
    MILESTONE_HANDLING_UPDATED,
    FOLLOW_UP_RECORDED,
    CORRECTION_RECORDED,
}

data class LogisticsAssigneeSnapshot(
    val employeeId: String,
    val employeeName: String,
)

data class LogisticsTransportDetails(
    val incotermCode: String? = null,
    val containerNumber: String? = null,
    val billOrAirwayNumber: String? = null,
    val vesselOrFlightReference: String? = null,
    val weightKg: BigDecimal? = null,
    val volumeM3: BigDecimal? = null,
    val packageCount: Int? = null,
    val palletCount: Int? = null,
    val insuranceReference: String? = null,
)

data class LogisticsShipment(
    val id: String,
    val organizationId: String,
    val shipmentNumber: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val state: LogisticsShipmentState = LogisticsShipmentState.DRAFT,
    val createdAt: Long,
    val transportMode: LogisticsTransportMode? = null,
    val assignee: LogisticsAssigneeSnapshot? = null,
    val startedAt: Long? = null,
    val expectedDepartureAt: Long? = null,
    val expectedArrivalAt: Long? = null,
    val transportDetails: LogisticsTransportDetails? = null,
    val notes: String = "",
    val cancelledAt: Long? = null,
    val cancelReason: String = "",
    val customsMilestoneId: String? = null,
    val customsCalendarPolicyId: String = "FRIDAY_OFF",
    val eventTimezoneId: String = "UTC",
    /** v234 explicit definition fields. Legacy source/destination strings remain readable until resaved. */
    val originLocationDetails: LogisticsLocation? = null,
    val destinationLocationDetails: LogisticsLocation? = null,
    val routeTransportPlanKind: LogisticsRouteTransportPlanKind? = null,
    val unifiedTransportMode: LogisticsLegTransportMode? = null,
    val currentPlanRevision: Int = 0,
    val planApprovedAt: Long? = null,
)

data class LogisticsShipmentSource(
    val id: String,
    val shipmentId: String,
    val invoiceId: String,
    val supplierId: String,
    val supplierNameSnapshot: String,
    val invoiceNumberSnapshot: String,
    val originalCurrency: String? = null,
    val exchangeRateSnapshot: BigDecimal? = null,
    /** v234 planning-only invoice logistics metadata. */
    val plannedPackageCount: Int? = null,
    val plannedWeightKg: BigDecimal? = null,
    val expectedReadyAt: Long? = null,
)

data class LogisticsShipmentLine(
    val id: String,
    val shipmentId: String,
    val sourceInvoiceId: String,
    val sourceInvoiceItemId: String,
    val inventoryItemId: String,
    val itemNameSnapshot: String,
    val expectedQuantity: Int,
    val basePurchaseUnitPrice: BigDecimal,
    val hsCode: String? = null,
)

data class LogisticsMilestone(
    val id: String,
    val shipmentId: String,
    val type: LogisticsMilestoneType,
    val order: Int,
    val location: String,
    val plannedArrivalAt: Long? = null,
    val arrivedAt: Long? = null,
    val departedAt: Long? = null,
    val note: String = "",
    val plannedDepartureAt: Long? = null,
    val handlingStatus: LogisticsMilestoneHandlingStatus = LogisticsMilestoneHandlingStatus.PENDING,
    val unloadedAt: Long? = null,
    val loadedAt: Long? = null,
    val countryCode: String = "",
    val countryNameSnapshot: String = "",
    val city: String = "",
    val placeName: String = location,
    val planKind: LogisticsPlanKind = LogisticsPlanKind.PLANNED,
    val expectedStayDays: Int? = null,
    val customsBrokerPartnerId: String? = null,
    val customsBrokerNameSnapshot: String? = null,
    val customsBrokerPhoneSnapshot: String? = null,
    val customsStartedAt: Long? = null,
    val customsCompletedAt: Long? = null,
)

data class LogisticsShipmentLeg(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val sequence: Int,
    val fromMilestoneId: String,
    val toMilestoneId: String,
    val mode: LogisticsLegTransportMode,
    val carrierPartnerId: String,
    val status: LogisticsLegStatus = LogisticsLegStatus.PLANNED,
    val plannedDepartureAt: Long? = null,
    val plannedArrivalAt: Long? = null,
    val actualDepartureAt: Long? = null,
    val actualArrivalAt: Long? = null,
    val roadVehicleNumber: String? = null,
    val roadDriverName: String? = null,
    val roadDriverPhone: String? = null,
    val seaContainerNumber: String? = null,
    val seaBillOfLading: String? = null,
    val seaVesselReference: String? = null,
    val airWaybillNumber: String? = null,
    val airFlightReference: String? = null,
    val note: String = "",
    val planKind: LogisticsPlanKind = LogisticsPlanKind.PLANNED,
    val expectedTransitDays: Int? = null,
    val representativeNameSnapshot: String? = null,
    val representativePhoneSnapshot: String? = null,
    val packageCount: Int? = null,
    val weightKg: BigDecimal? = null,
    val supersededAt: Long? = null,
    val supersededByLegId: String? = null,
    /** v234 canonical duration. Legacy expectedTransitDays is retained for compatibility. */
    val expectedTransitMinutes: Int? = expectedTransitDays?.times(24 * 60),
    /** Planning defaults are intentionally separate from actual execution fields above. */
    val plannedCarrierPartnerId: String? = null,
    val plannedCarrierNameSnapshot: String? = null,
    val plannedRepresentativeNameSnapshot: String? = null,
    val plannedRepresentativePhoneSnapshot: String? = null,
    val plannedPackageCount: Int? = null,
    val plannedWeightKg: BigDecimal? = null,
    val plannedCost: LogisticsPlannedCost? = null,
    val plannedProof: LogisticsPlannedAttachment? = null,
)

data class LogisticsCustodyHandoff(
    val id: String,
    val organizationId: String,
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
    val packageChangeReason: LogisticsPackageChangeReason? = null,
    val packageChangeNote: String? = null,
    val openedPackageCount: Int = 0,
    val damagedPackageCount: Int = 0,
)

data class LogisticsAssignment(
    val id: String,
    val shipmentId: String,
    val employeeId: String,
    val employeeNameSnapshot: String,
    val assignedAt: Long,
    val endedAt: Long? = null,
)

data class LogisticsPartner(
    val id: String,
    val organizationId: String,
    val name: String,
    val role: LogisticsPartnerRole,
    val phone: String? = null,
    val representativeName: String? = null,
    val representativePhone: String? = null,
    val notes: String = "",
)

data class LogisticsShipmentPartnerLink(
    val id: String,
    val shipmentId: String,
    val partnerId: String,
    val role: LogisticsPartnerRole,
)

data class LogisticsDocument(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val milestoneId: String? = null,
    val type: LogisticsDocumentType,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val privateUri: String,
    val sha256: String,
    val createdAt: Long,
    val sourceId: String? = null,
    val legId: String? = null,
    val handoffId: String? = null,
    val costId: String? = null,
    val recoveryId: String? = null,
    val employeeId: String? = null,
    val employeeNameSnapshot: String? = null,
)

data class LogisticsCost(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val type: LogisticsCostType,
    val amount: BigDecimal,
    val currency: String,
    val exchangeRateSnapshot: BigDecimal,
    val exchangeRateDate: Long? = null,
    val baseCurrencyAmount: BigDecimal,
    val status: LogisticsCostStatus,
    val servicePartnerId: String? = null,
    val reference: String? = null,
    val note: String = "",
    val legId: String? = null,
    val milestoneId: String? = null,
    val sourceId: String? = null,
    val description: String = "",
    val paymentState: LogisticsCostPaymentState = LogisticsCostPaymentState.UNPAID,
    val cashReference: String? = null,
    val cashPostedBaseAmount: BigDecimal? = null,
    val cashPostedAt: Long? = null,
    val reversalOfCostId: String? = null,
    val recoveryId: String? = null,
    val requestId: String? = null,
)

data class LogisticsReceivingBatch(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val requestId: String,
    val receivedAt: Long,
    val receivedByEmployeeId: String,
    val receivedByEmployeeNameSnapshot: String,
    val lines: List<LogisticsReceivingLine>,
)

data class LogisticsReceivingLine(
    val id: String,
    val shipmentId: String,
    val shipmentLineId: String,
    val expectedQuantitySnapshot: Int,
    val receivedQuantity: Int,
    val acceptedQuantity: Int,
    val damagedQuantity: Int,
    val rejectedQuantity: Int,
    val quarantinedQuantity: Int,
)

data class LogisticsInventoryPosting(
    val postingId: String,
    val shipmentId: String,
    val receivingBatchId: String,
    val receivingLineId: String,
    val itemId: String,
    val quantity: Int,
    val supplierId: String,
    val unitPrice: BigDecimal,
    val note: String = "",
)

data class LogisticsCostAllocation(
    val id: String,
    val shipmentId: String,
    val shipmentLineId: String,
    val amount: BigDecimal,
)

data class LogisticsEvent(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val type: LogisticsEventType,
    val occurredAt: Long,
    val recordedAt: Long = occurredAt,
    val employeeId: String? = null,
    val employeeNameSnapshot: String? = null,
    val requestId: String,
    val payload: Map<String, String> = emptyMap(),
)

data class LogisticsShipmentAggregate(
    val shipment: LogisticsShipment,
    val sources: List<LogisticsShipmentSource> = emptyList(),
    val lines: List<LogisticsShipmentLine> = emptyList(),
    val milestones: List<LogisticsMilestone> = emptyList(),
    val legs: List<LogisticsShipmentLeg> = emptyList(),
    val custodyHandoffs: List<LogisticsCustodyHandoff> = emptyList(),
    val assignments: List<LogisticsAssignment> = emptyList(),
    val partners: List<LogisticsShipmentPartnerLink> = emptyList(),
    val documents: List<LogisticsDocument> = emptyList(),
    val costs: List<LogisticsCost> = emptyList(),
    val payments: List<LogisticsPayment> = emptyList(),
    val receivingBatches: List<LogisticsReceivingBatch> = emptyList(),
    val costAllocations: List<LogisticsCostAllocation> = emptyList(),
    val shortages: List<LogisticsShortage> = emptyList(),
    val recoveries: List<LogisticsRecovery> = emptyList(),
    val recoveryLines: List<LogisticsRecoveryLine> = emptyList(),
    val recoveryPostings: List<LogisticsRecoveryPosting> = emptyList(),
    val shortageSettlements: List<LogisticsShortageSettlement> = emptyList(),
    val lateCostAdjustments: List<LogisticsLateCostAdjustment> = emptyList(),
    val lateCostAllocations: List<LogisticsLateCostAllocation> = emptyList(),
    val customsPlan: LogisticsCustomsPlan? = null,
    val customsPlanDocuments: List<LogisticsCustomsPlanDocument> = emptyList(),
    val planRevisions: List<LogisticsPlanRevision> = emptyList(),
)

data class LogisticsPurchaseInvoiceSnapshot(
    val invoiceId: String,
    val supplierId: String,
    val supplierName: String,
    val invoiceNumber: String,
    val currency: String? = null,
    val exchangeRate: BigDecimal? = null,
    val lines: List<LogisticsPurchaseInvoiceLineSnapshot>,
    /** Purchase-invoice metadata used only by the v236 chooser; shipment snapshots remain source/line based. */
    val invoiceDate: Long = 0L,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
)

data class LogisticsPurchaseInvoiceLineSnapshot(
    val invoiceItemId: String,
    val inventoryItemId: String,
    val itemName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val remainingShippableQuantity: Int = quantity,
)

/** Stable inventory identity exposed to logistics without leaking persistence entities. */
data class LogisticsInventoryCatalogItem(
    val id: String,
    val name: String,
    val partNumber: String = "",
)
