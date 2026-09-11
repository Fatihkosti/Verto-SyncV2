package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "logistics_shipments",
    primaryKeys = ["organization_id", "id"],
    indices = [
        Index(value = ["organization_id", "shipment_number"], unique = true),
        Index(value = ["organization_id", "state"]),
    ],
)
data class LogisticsShipmentEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_number") val shipmentNumber: String,
    @ColumnInfo(name = "source_location") val sourceLocation: String,
    @ColumnInfo(name = "destination_location") val destinationLocation: String,
    val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "transport_mode") val transportMode: String? = null,
    @ColumnInfo(name = "assigned_employee_id") val assignedEmployeeId: String? = null,
    @ColumnInfo(name = "assigned_employee_name_snapshot") val assignedEmployeeNameSnapshot: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    @ColumnInfo(name = "expected_departure_at") val expectedDepartureAt: Long? = null,
    @ColumnInfo(name = "expected_arrival_at") val expectedArrivalAt: Long? = null,
    val notes: String = "",
    @ColumnInfo(name = "cancelled_at") val cancelledAt: Long? = null,
    @ColumnInfo(name = "cancel_reason", defaultValue = "''") val cancelReason: String = "",
    @ColumnInfo(name = "customs_milestone_id") val customsMilestoneId: String? = null,
    @ColumnInfo(name = "customs_calendar_policy_id", defaultValue = "'FRIDAY_OFF'") val customsCalendarPolicyId: String = "FRIDAY_OFF",
    @ColumnInfo(name = "event_timezone_id", defaultValue = "'UTC'") val eventTimezoneId: String = "UTC",
    @ColumnInfo(name = "origin_country_key") val originCountryKey: String? = null,
    @ColumnInfo(name = "origin_country_name_snapshot") val originCountryNameSnapshot: String? = null,
    @ColumnInfo(name = "origin_city") val originCity: String? = null,
    @ColumnInfo(name = "destination_country_key") val destinationCountryKey: String? = null,
    @ColumnInfo(name = "destination_country_name_snapshot") val destinationCountryNameSnapshot: String? = null,
    @ColumnInfo(name = "destination_city") val destinationCity: String? = null,
    @ColumnInfo(name = "route_transport_plan_kind") val routeTransportPlanKind: String? = null,
    @ColumnInfo(name = "unified_transport_mode") val unifiedTransportMode: String? = null,
    @ColumnInfo(name = "current_plan_revision", defaultValue = "0") val currentPlanRevision: Int = 0,
    @ColumnInfo(name = "plan_approved_at") val planApprovedAt: Long? = null,
)

@Entity(
    tableName = "logistics_shipment_sources",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_id", "invoice_id"], unique = true),
    ],
)
data class LogisticsShipmentSourceEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "supplier_id") val supplierId: String,
    @ColumnInfo(name = "supplier_name_snapshot") val supplierNameSnapshot: String,
    @ColumnInfo(name = "invoice_number_snapshot") val invoiceNumberSnapshot: String,
    @ColumnInfo(name = "original_currency") val originalCurrency: String? = null,
    @ColumnInfo(name = "exchange_rate_snapshot") val exchangeRateSnapshot: String? = null,
    @ColumnInfo(name = "planned_package_count") val plannedPackageCount: Int? = null,
    @ColumnInfo(name = "planned_weight_kg") val plannedWeightKg: String? = null,
    @ColumnInfo(name = "expected_ready_at") val expectedReadyAt: Long? = null,
)

@Entity(
    tableName = "logistics_shipment_lines",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_id", "source_invoice_item_id"], unique = true),
        Index(value = ["organization_id", "inventory_item_id"]),
    ],
)
data class LogisticsShipmentLineEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "source_invoice_id") val sourceInvoiceId: String,
    @ColumnInfo(name = "source_invoice_item_id") val sourceInvoiceItemId: String,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String,
    @ColumnInfo(name = "item_name_snapshot") val itemNameSnapshot: String,
    @ColumnInfo(name = "expected_quantity") val expectedQuantity: Int,
    @ColumnInfo(name = "base_purchase_unit_price") val basePurchaseUnitPrice: String,
    @ColumnInfo(name = "hs_code") val hsCode: String? = null,
)

@Entity(
    tableName = "logistics_milestones",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_id", "milestone_order"], unique = true),
    ],
)
data class LogisticsMilestoneEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    val type: String,
    @ColumnInfo(name = "milestone_order") val milestoneOrder: Int,
    val location: String,
    @ColumnInfo(name = "planned_arrival_at") val plannedArrivalAt: Long? = null,
    @ColumnInfo(name = "arrived_at") val arrivedAt: Long? = null,
    @ColumnInfo(name = "departed_at") val departedAt: Long? = null,
    val note: String = "",
    @ColumnInfo(name = "planned_departure_at") val plannedDepartureAt: Long? = null,
    @ColumnInfo(name = "handling_status", defaultValue = "'PENDING'") val handlingStatus: String = "PENDING",
    @ColumnInfo(name = "unloaded_at") val unloadedAt: Long? = null,
    @ColumnInfo(name = "loaded_at") val loadedAt: Long? = null,
    @ColumnInfo(name = "country_code", defaultValue = "''") val countryCode: String = "",
    @ColumnInfo(name = "country_name_snapshot", defaultValue = "''") val countryNameSnapshot: String = "",
    @ColumnInfo(name = "city", defaultValue = "''") val city: String = "",
    @ColumnInfo(name = "place_name", defaultValue = "''") val placeName: String = "",
    @ColumnInfo(name = "plan_kind", defaultValue = "'PLANNED'") val planKind: String = "PLANNED",
    @ColumnInfo(name = "expected_stay_days") val expectedStayDays: Int? = null,
    @ColumnInfo(name = "customs_broker_partner_id") val customsBrokerPartnerId: String? = null,
    @ColumnInfo(name = "customs_broker_name_snapshot") val customsBrokerNameSnapshot: String? = null,
    @ColumnInfo(name = "customs_broker_phone_snapshot") val customsBrokerPhoneSnapshot: String? = null,
    @ColumnInfo(name = "customs_started_at") val customsStartedAt: Long? = null,
    @ColumnInfo(name = "customs_completed_at") val customsCompletedAt: Long? = null,
)

@Entity(
    tableName = "logistics_shipment_legs",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsMilestoneEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "from_milestone_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LogisticsMilestoneEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "to_milestone_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LogisticsPartnerEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "carrier_partner_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id", "sequence"], unique = true),
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "carrier_partner_id"]),
        Index(value = ["organization_id", "from_milestone_id"]),
        Index(value = ["organization_id", "to_milestone_id"]),
    ],
)
data class LogisticsShipmentLegEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    val sequence: Int,
    @ColumnInfo(name = "from_milestone_id") val fromMilestoneId: String,
    @ColumnInfo(name = "to_milestone_id") val toMilestoneId: String,
    val mode: String,
    @ColumnInfo(name = "carrier_partner_id") val carrierPartnerId: String? = null,
    val status: String,
    @ColumnInfo(name = "planned_departure_at") val plannedDepartureAt: Long? = null,
    @ColumnInfo(name = "planned_arrival_at") val plannedArrivalAt: Long? = null,
    @ColumnInfo(name = "actual_departure_at") val actualDepartureAt: Long? = null,
    @ColumnInfo(name = "actual_arrival_at") val actualArrivalAt: Long? = null,
    @ColumnInfo(name = "road_vehicle_number") val roadVehicleNumber: String? = null,
    @ColumnInfo(name = "road_driver_name") val roadDriverName: String? = null,
    @ColumnInfo(name = "road_driver_phone") val roadDriverPhone: String? = null,
    @ColumnInfo(name = "sea_container_number") val seaContainerNumber: String? = null,
    @ColumnInfo(name = "sea_bill_of_lading") val seaBillOfLading: String? = null,
    @ColumnInfo(name = "sea_vessel_reference") val seaVesselReference: String? = null,
    @ColumnInfo(name = "air_waybill_number") val airWaybillNumber: String? = null,
    @ColumnInfo(name = "air_flight_reference") val airFlightReference: String? = null,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    @ColumnInfo(name = "plan_kind", defaultValue = "'PLANNED'") val planKind: String = "PLANNED",
    @ColumnInfo(name = "expected_transit_days") val expectedTransitDays: Int? = null,
    @ColumnInfo(name = "representative_name_snapshot") val representativeNameSnapshot: String? = null,
    @ColumnInfo(name = "representative_phone_snapshot") val representativePhoneSnapshot: String? = null,
    @ColumnInfo(name = "package_count") val packageCount: Int? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: String? = null,
    @ColumnInfo(name = "superseded_at") val supersededAt: Long? = null,
    @ColumnInfo(name = "superseded_by_leg_id") val supersededByLegId: String? = null,
    @ColumnInfo(name = "expected_transit_minutes") val expectedTransitMinutes: Int? = null,
    @ColumnInfo(name = "planned_carrier_partner_id") val plannedCarrierPartnerId: String? = null,
    @ColumnInfo(name = "planned_carrier_name_snapshot") val plannedCarrierNameSnapshot: String? = null,
    @ColumnInfo(name = "planned_representative_name_snapshot") val plannedRepresentativeNameSnapshot: String? = null,
    @ColumnInfo(name = "planned_representative_phone_snapshot") val plannedRepresentativePhoneSnapshot: String? = null,
    @ColumnInfo(name = "planned_package_count") val plannedPackageCount: Int? = null,
    @ColumnInfo(name = "planned_weight_kg") val plannedWeightKg: String? = null,
    @ColumnInfo(name = "planned_cost_amount") val plannedCostAmount: String? = null,
    @ColumnInfo(name = "planned_cost_currency") val plannedCostCurrency: String? = null,
    @ColumnInfo(name = "planned_exchange_rate") val plannedExchangeRate: String? = null,
    @ColumnInfo(name = "planned_base_cost_amount") val plannedBaseCostAmount: String? = null,
    @ColumnInfo(name = "planned_proof_private_uri") val plannedProofPrivateUri: String? = null,
    @ColumnInfo(name = "planned_proof_display_name") val plannedProofDisplayName: String? = null,
    @ColumnInfo(name = "planned_proof_mime_type") val plannedProofMimeType: String? = null,
)

@Entity(
    tableName = "logistics_custody_handoffs",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentSourceEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "source_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LogisticsMilestoneEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "milestone_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id", "received_at"]),
        Index(value = ["organization_id", "source_id", "received_at"]),
        Index(value = ["organization_id", "shipment_id", "request_id"], unique = true),
    ],
)
data class LogisticsCustodyHandoffEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    @ColumnInfo(name = "milestone_id") val milestoneId: String? = null,
    @ColumnInfo(name = "from_holder_type") val fromHolderType: String,
    @ColumnInfo(name = "from_holder_id") val fromHolderId: String? = null,
    @ColumnInfo(name = "from_holder_name_snapshot") val fromHolderNameSnapshot: String,
    @ColumnInfo(name = "to_holder_type") val toHolderType: String,
    @ColumnInfo(name = "to_holder_id") val toHolderId: String? = null,
    @ColumnInfo(name = "to_holder_name_snapshot") val toHolderNameSnapshot: String,
    @ColumnInfo(name = "transferred_at") val transferredAt: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    @ColumnInfo(name = "handover_package_count") val handoverPackageCount: Int? = null,
    @ColumnInfo(name = "received_package_count") val receivedPackageCount: Int? = null,
    @ColumnInfo(name = "handover_weight_kg") val handoverWeightKg: String? = null,
    @ColumnInfo(name = "received_weight_kg") val receivedWeightKg: String? = null,
    @ColumnInfo(name = "package_change_reason") val packageChangeReason: String? = null,
    @ColumnInfo(name = "package_change_note") val packageChangeNote: String? = null,
    @ColumnInfo(name = "opened_package_count", defaultValue = "0") val openedPackageCount: Int = 0,
    @ColumnInfo(name = "damaged_package_count", defaultValue = "0") val damagedPackageCount: Int = 0,
)

@Entity(
    tableName = "logistics_assignments",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_id", "assigned_at"], unique = true),
        Index(value = ["organization_id", "employee_id"]),
    ],
)
data class LogisticsAssignmentEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "employee_id") val employeeId: String,
    @ColumnInfo(name = "employee_name_snapshot") val employeeNameSnapshot: String,
    @ColumnInfo(name = "assigned_at") val assignedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
)

@Entity(
    tableName = "logistics_events",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id", "occurred_at"]),
        Index(value = ["organization_id", "shipment_id", "request_id", "type"], unique = true),
    ],
)
data class LogisticsEventEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    val type: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "recorded_at", defaultValue = "0") val recordedAt: Long,
    @ColumnInfo(name = "employee_id") val employeeId: String? = null,
    @ColumnInfo(name = "employee_name_snapshot") val employeeNameSnapshot: String? = null,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String = "{}",
)

@Entity(
    tableName = "logistics_partners",
    primaryKeys = ["organization_id", "id"],
    indices = [
        Index(value = ["organization_id", "name", "role"]),
    ],
)
data class LogisticsPartnerEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    val name: String,
    val role: String,
    val phone: String? = null,
    @ColumnInfo(name = "representative_name") val representativeName: String? = null,
    @ColumnInfo(name = "representative_phone") val representativePhone: String? = null,
    val notes: String = "",
)

@Entity(
    tableName = "logistics_shipment_partner_links",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsPartnerEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "partner_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "partner_id"]),
        Index(value = ["organization_id", "shipment_id", "partner_id", "role"], unique = true),
    ],
)
data class LogisticsShipmentPartnerLinkEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "partner_id") val partnerId: String,
    val role: String,
)

@Entity(
    tableName = "logistics_transport_details",
    primaryKeys = ["organization_id", "shipment_id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["organization_id", "shipment_id"], unique = true)],
)
data class LogisticsTransportDetailsEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "incoterm_code") val incotermCode: String? = null,
    @ColumnInfo(name = "container_number") val containerNumber: String? = null,
    @ColumnInfo(name = "bill_or_airway_number") val billOrAirwayNumber: String? = null,
    @ColumnInfo(name = "vessel_or_flight_reference") val vesselOrFlightReference: String? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: String? = null,
    @ColumnInfo(name = "volume_m3") val volumeM3: String? = null,
    @ColumnInfo(name = "package_count") val packageCount: Int? = null,
    @ColumnInfo(name = "pallet_count") val palletCount: Int? = null,
    @ColumnInfo(name = "insurance_reference") val insuranceReference: String? = null,
)

@Entity(
    tableName = "logistics_documents",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsMilestoneEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "milestone_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "milestone_id"]),
        Index(value = ["organization_id", "shipment_id", "sha256"]),
        Index(value = ["organization_id", "handoff_id"]),
        Index(value = ["organization_id", "cost_id"]),
        Index(value = ["organization_id", "recovery_id"]),
    ],
)
data class LogisticsDocumentEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "milestone_id") val milestoneId: String? = null,
    val type: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "private_uri") val privateUri: String,
    val sha256: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    @ColumnInfo(name = "leg_id") val legId: String? = null,
    @ColumnInfo(name = "handoff_id") val handoffId: String? = null,
    @ColumnInfo(name = "cost_id") val costId: String? = null,
    @ColumnInfo(name = "recovery_id") val recoveryId: String? = null,
    @ColumnInfo(name = "employee_id") val employeeId: String? = null,
    @ColumnInfo(name = "employee_name_snapshot") val employeeNameSnapshot: String? = null,
)

@Entity(
    tableName = "logistics_costs",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsPartnerEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "service_partner_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "service_partner_id"]),
        Index(value = ["organization_id", "shipment_id", "request_id"], unique = true),
        Index(value = ["organization_id", "recovery_id"]),
    ],
)
data class LogisticsCostEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    val type: String,
    val amount: String,
    val currency: String,
    @ColumnInfo(name = "exchange_rate_snapshot") val exchangeRateSnapshot: String,
    @ColumnInfo(name = "exchange_rate_date") val exchangeRateDate: Long? = null,
    @ColumnInfo(name = "base_currency_amount") val baseCurrencyAmount: String,
    val status: String,
    @ColumnInfo(name = "service_partner_id") val servicePartnerId: String? = null,
    val reference: String? = null,
    val note: String = "",
    @ColumnInfo(name = "leg_id") val legId: String? = null,
    @ColumnInfo(name = "milestone_id") val milestoneId: String? = null,
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    @ColumnInfo(name = "description", defaultValue = "''") val description: String = "",
    @ColumnInfo(name = "payment_state", defaultValue = "'UNPAID'") val paymentState: String = "UNPAID",
    @ColumnInfo(name = "cash_reference") val cashReference: String? = null,
    @ColumnInfo(name = "cash_posted_base_amount") val cashPostedBaseAmount: String? = null,
    @ColumnInfo(name = "cash_posted_at") val cashPostedAt: Long? = null,
    @ColumnInfo(name = "reversal_of_cost_id") val reversalOfCostId: String? = null,
    @ColumnInfo(name = "recovery_id") val recoveryId: String? = null,
    @ColumnInfo(name = "request_id") val requestId: String? = null,
)

@Entity(
    tableName = "logistics_receiving_batches",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "request_id"], unique = true),
    ],
)
data class LogisticsReceivingBatchEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "received_by_employee_id") val receivedByEmployeeId: String,
    @ColumnInfo(name = "received_by_employee_name_snapshot") val receivedByEmployeeNameSnapshot: String,
)

@Entity(
    tableName = "logistics_receiving_lines",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsReceivingBatchEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "batch_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "batch_id"]),
        Index(value = ["organization_id", "shipment_line_id"]),
        Index(value = ["organization_id", "batch_id", "shipment_line_id"], unique = true),
    ],
)
data class LogisticsReceivingLineEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "shipment_line_id") val shipmentLineId: String,
    @ColumnInfo(name = "expected_quantity_snapshot") val expectedQuantitySnapshot: Int,
    @ColumnInfo(name = "received_quantity") val receivedQuantity: Int,
    @ColumnInfo(name = "accepted_quantity") val acceptedQuantity: Int,
    @ColumnInfo(name = "damaged_quantity") val damagedQuantity: Int,
    @ColumnInfo(name = "rejected_quantity") val rejectedQuantity: Int,
    @ColumnInfo(name = "quarantined_quantity") val quarantinedQuantity: Int,
)

@Entity(
    tableName = "logistics_inventory_postings",
    primaryKeys = ["organization_id", "posting_id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsReceivingBatchEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "receiving_batch_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsReceivingLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "receiving_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "receiving_batch_id"]),
        Index(value = ["organization_id", "receiving_line_id"], unique = true),
    ],
)
data class LogisticsInventoryPostingEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "posting_id") val postingId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "receiving_batch_id") val receivingBatchId: String,
    @ColumnInfo(name = "receiving_line_id") val receivingLineId: String,
    val quantity: Int,
)

@Entity(
    tableName = "logistics_cost_allocations",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_line_id"]),
        Index(value = ["organization_id", "shipment_id", "shipment_line_id"], unique = true),
    ],
)
data class LogisticsCostAllocationEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "shipment_line_id") val shipmentLineId: String,
    val amount: String,
)

@Entity(
    tableName = "logistics_shortages",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "status"]),
        Index(value = ["organization_id", "shipment_id", "shipment_line_id"], unique = true),
        Index(value = ["organization_id", "shipment_id", "request_id"], unique = true),
    ],
)
class LogisticsShortageEntity {
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    var id: String = ""
    @ColumnInfo(name = "shipment_id") var shipmentId: String = ""
    @ColumnInfo(name = "shipment_line_id") var shipmentLineId: String = ""
    @ColumnInfo(name = "original_missing_quantity") var originalMissingQuantity: Int = 0
    @ColumnInfo(name = "remaining_missing_quantity") var remainingMissingQuantity: Int = 0
    @ColumnInfo(name = "base_purchase_unit_price_snapshot") var basePurchaseUnitPriceSnapshot: String = "0"
    var status: String = "OPEN"
    @ColumnInfo(name = "detected_at") var detectedAt: Long = 0L
    var note: String = ""
    @ColumnInfo(name = "request_id") var requestId: String = ""
}

@Entity(
    tableName = "logistics_recoveries",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_id", "request_id"], unique = true),
    ],
)
class LogisticsRecoveryEntity {
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    var id: String = ""
    @ColumnInfo(name = "shipment_id") var shipmentId: String = ""
    @ColumnInfo(name = "recovered_at") var recoveredAt: Long = 0L
    @ColumnInfo(name = "employee_id") var employeeId: String = ""
    @ColumnInfo(name = "employee_name_snapshot") var employeeNameSnapshot: String = ""
    var note: String = ""
    @ColumnInfo(name = "request_id") var requestId: String = ""
}

@Entity(
    tableName = "logistics_recovery_lines",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsRecoveryEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "recovery_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShortageEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shortage_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LogisticsShipmentLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "recovery_id"]),
        Index(value = ["organization_id", "shortage_id"]),
        Index(value = ["organization_id", "shipment_line_id"]),
        Index(value = ["organization_id", "recovery_id", "shortage_id"], unique = true),
    ],
)
class LogisticsRecoveryLineEntity {
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    var id: String = ""
    @ColumnInfo(name = "recovery_id") var recoveryId: String = ""
    @ColumnInfo(name = "shortage_id") var shortageId: String = ""
    @ColumnInfo(name = "shipment_line_id") var shipmentLineId: String = ""
    @ColumnInfo(name = "recovered_quantity") var recoveredQuantity: Int = 0
    @ColumnInfo(name = "base_purchase_unit_price_snapshot") var basePurchaseUnitPriceSnapshot: String = "0"
    @ColumnInfo(name = "allocated_recovery_cost") var allocatedRecoveryCost: String = "0"
}

@Entity(
    tableName = "logistics_recovery_postings",
    primaryKeys = ["organization_id", "posting_id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsRecoveryEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "recovery_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsRecoveryLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "recovery_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "recovery_id"]),
        Index(value = ["organization_id", "recovery_line_id"], unique = true),
        Index(value = ["organization_id", "shipment_line_id"]),
    ],
)
class LogisticsRecoveryPostingEntity {
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    @ColumnInfo(name = "posting_id") var postingId: String = ""
    @ColumnInfo(name = "recovery_id") var recoveryId: String = ""
    @ColumnInfo(name = "recovery_line_id") var recoveryLineId: String = ""
    @ColumnInfo(name = "shipment_id") var shipmentId: String = ""
    @ColumnInfo(name = "shipment_line_id") var shipmentLineId: String = ""
    var quantity: Int = 0
}



@Entity(tableName = "logistics_shipment_number_sequences")
data class LogisticsShipmentNumberSequenceEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "last_number") val lastNumber: Int,
)

@Entity(
    tableName = "logistics_payments",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsCostEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "cost_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "cost_id"]),
        Index(value = ["organization_id", "request_id"], unique = true),
        Index(value = ["organization_id", "cash_reference"], unique = true),
    ],
)
data class LogisticsPaymentEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "cost_id") val costId: String,
    val state: String,
    val amount: String,
    @ColumnInfo(defaultValue = "'SDG'")
    val currency: String = "SDG",
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    @ColumnInfo(name = "paid_at") val paidAt: Long? = null,
    val reference: String? = null,
    @ColumnInfo(name = "proof_document_id") val proofDocumentId: String? = null,
    @ColumnInfo(name = "cash_reference") val cashReference: String? = null,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "logistics_route_templates",
    primaryKeys = ["organization_id", "id"],
    indices = [Index(value = ["organization_id", "name"], unique = true)],
)
data class LogisticsRouteTemplateEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    val name: String,
    @ColumnInfo(name = "origin_country_code") val originCountryCode: String,
    @ColumnInfo(name = "origin_city") val originCity: String,
    @ColumnInfo(name = "destination_country_code") val destinationCountryCode: String,
    @ColumnInfo(name = "destination_city") val destinationCity: String,
    @ColumnInfo(name = "transport_plan_kind") val transportPlanKind: String,
    @ColumnInfo(name = "unified_transport_mode") val unifiedTransportMode: String? = null,
    @ColumnInfo(name = "customs_stop_order") val customsStopOrder: Int? = null,
    @ColumnInfo(name = "expected_customs_minutes") val expectedCustomsMinutes: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "logistics_route_template_stops",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsRouteTemplateEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "template_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "template_id"]),
        Index(value = ["organization_id", "template_id", "stop_order"], unique = true),
    ],
)
data class LogisticsRouteTemplateStopEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "stop_order") val stopOrder: Int,
    @ColumnInfo(name = "country_code") val countryCode: String,
    val city: String,
    @ColumnInfo(name = "place_name", defaultValue = "''") val placeName: String = "",
    @ColumnInfo(name = "expected_transit_minutes_to_next") val expectedTransitMinutesToNext: Int? = null,
)

@Entity(
    tableName = "logistics_shortage_settlements",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShortageEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shortage_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shortage_id"]),
        Index(value = ["organization_id", "shipment_id", "request_id"], unique = true),
    ],
)
class LogisticsShortageSettlementEntity {
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    var id: String = ""
    @ColumnInfo(name = "shipment_id") var shipmentId: String = ""
    @ColumnInfo(name = "shortage_id") var shortageId: String = ""
    var type: String = ""
    var quantity: Int = 0
    @ColumnInfo(name = "compensation_amount") var compensationAmount: String? = null
    var currency: String? = null
    @ColumnInfo(name = "exchange_rate_snapshot") var exchangeRateSnapshot: String? = null
    @ColumnInfo(name = "base_currency_amount") var baseCurrencyAmount: String? = null
    @ColumnInfo(name = "occurred_at") var occurredAt: Long = 0L
    @ColumnInfo(name = "employee_id") var employeeId: String? = null
    @ColumnInfo(name = "employee_name_snapshot") var employeeNameSnapshot: String? = null
    var note: String = ""
    @ColumnInfo(name = "request_id") var requestId: String = ""
}

@Entity(
    tableName = "logistics_late_cost_adjustments",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsCostEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "cost_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "cost_id"], unique = true),
        Index(value = ["organization_id", "shipment_id", "request_id"], unique = true),
    ],
)
class LogisticsLateCostAdjustmentEntity {
    @ColumnInfo(name = "organization_id") var organizationId: String = ""
    var id: String = ""
    @ColumnInfo(name = "shipment_id") var shipmentId: String = ""
    @ColumnInfo(name = "cost_id") var costId: String = ""
    @ColumnInfo(name = "recorded_at") var recordedAt: Long = 0L
    @ColumnInfo(name = "employee_id") var employeeId: String? = null
    @ColumnInfo(name = "employee_name_snapshot") var employeeNameSnapshot: String? = null
    @ColumnInfo(name = "request_id") var requestId: String = ""
}

@Entity(
    tableName = "logistics_late_cost_allocations",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsLateCostAdjustmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "adjustment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentLineEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_line_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "adjustment_id"]),
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "shipment_line_id"]),
        Index(value = ["organization_id", "adjustment_id", "shipment_line_id"], unique = true),
    ],
)
data class LogisticsLateCostAllocationEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "adjustment_id") val adjustmentId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "shipment_line_id") val shipmentLineId: String,
    val amount: String,
)


@Entity(
    tableName = "logistics_customs_plans",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsMilestoneEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "after_station_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"], unique = true),
        Index(value = ["organization_id", "after_station_id"]),
    ],
)
data class LogisticsCustomsPlanEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "checkpoint_name") val checkpointName: String,
    @ColumnInfo(name = "after_station_id") val afterStationId: String,
    @ColumnInfo(name = "expected_duration_minutes") val expectedDurationMinutes: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "logistics_customs_plan_documents",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsCustomsPlanEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "customs_plan_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id"]),
        Index(value = ["organization_id", "customs_plan_id"]),
    ],
)
data class LogisticsCustomsPlanDocumentEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "customs_plan_id") val customsPlanId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "private_uri") val privateUri: String,
    val sha256: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "logistics_plan_revisions",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "shipment_id", "revision_number"], unique = true),
        Index(value = ["organization_id", "request_id"], unique = true),
    ],
)
data class LogisticsPlanRevisionEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    @ColumnInfo(name = "revision_number") val revisionNumber: Int,
    val kind: String,
    val reason: String,
    @ColumnInfo(name = "changed_by_employee_id") val changedByEmployeeId: String? = null,
    @ColumnInfo(name = "changed_by_employee_name_snapshot") val changedByEmployeeNameSnapshot: String? = null,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "request_id") val requestId: String,
)

@Entity(
    tableName = "logistics_plan_revision_changes",
    primaryKeys = ["organization_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = LogisticsPlanRevisionEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "revision_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LogisticsShipmentEntity::class,
            parentColumns = ["organization_id", "id"],
            childColumns = ["organization_id", "shipment_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["organization_id", "revision_id"]),
        Index(value = ["organization_id", "shipment_id"]),
    ],
)
data class LogisticsPlanRevisionChangeEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val id: String,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "shipment_id") val shipmentId: String,
    val scope: String,
    @ColumnInfo(name = "scope_id") val scopeId: String? = null,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "previous_value") val previousValue: String? = null,
    @ColumnInfo(name = "new_value") val newValue: String? = null,
)
