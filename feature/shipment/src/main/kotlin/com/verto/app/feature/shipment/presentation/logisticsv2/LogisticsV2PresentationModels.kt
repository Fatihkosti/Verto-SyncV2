package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.model.*
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlanDocument
import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCountryNormalizer
import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryCatalogItem
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalStatus
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingBatch
import com.verto.app.feature.shipment.domain.model.LogisticsRepackProof
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsTransportDetails
import com.verto.app.feature.shipment.domain.model.LogisticsTransportMode
import java.math.BigDecimal

data class LogisticsV2Access(
    val canView: Boolean,
    val canManage: Boolean,
    val canConfirm: Boolean,
) {
    companion object {
        val None = LogisticsV2Access(false, false, false)
        val ViewOnly = LogisticsV2Access(true, false, false)
        val Full = LogisticsV2Access(true, true, true)
    }
}

enum class LogisticsCenterGroup(val label: String) {
    NEEDS_ACTION("تحتاج إجراء"),
    IN_TRANSIT("في الطريق"),
    RECEIVING("وصلت/تحت الاستلام"),
    COMPLETED("مكتملة"),
}

enum class LogisticsNextAction(val label: String) {
    PREPARE("إكمال التخطيط"),
    PREPARE_MOVEMENT("تجهيز الحركة"),
    START_TRANSPORT("بدء الرحلة"),
    START_MOVEMENT("بدأ التحرك"),
    RECORD_CUSTOMS_ARRIVAL("تسجيل وصول الجمارك"),
    RECORD_CUSTOMS_DEPARTURE("تسجيل مغادرة الجمارك"),
    RECORD_MILESTONE_ARRIVAL("تسجيل وصول المحطة"),
    RECORD_MILESTONE_DEPARTURE("تسجيل مغادرة المحطة"),
    RECORD_DESTINATION_ARRIVAL("تسجيل وصول الوجهة"),
    START_RECEIVING("بدء الاستلام"),
    RECEIVE_ANOTHER_BATCH("استلام دفعة"),
    SETTLE_COSTS("تسوية التكلفة"),
    CLOSE("إغلاق الشحنة"),
    NONE(""),
}

data class LogisticsCenterMetrics(
    val active: Int = 0,
    val delayed: Int = 0,
    val atCustoms: Int = 0,
    val waitingReceiving: Int = 0,
)

data class LogisticsShipmentCardUi(
    val id: String,
    val source: String = "V2",
    val shipmentNumber: String,
    val sourceLocation: String,
    val destinationLocation: String,
    val state: LogisticsShipmentState,
    val stateLabel: String,
    val assigneeName: String?,
    val expectedArrivalAt: Long?,
    val group: LogisticsCenterGroup,
    val nextAction: LogisticsNextAction,
    val delayed: Boolean,
    val atCustoms: Boolean,
    val phase: ShipmentCardPhase = ShipmentCardPhase.BEFORE_MOVEMENT, val suppliers: List<String> = emptyList(), val currentPackageCount: Int? = null, val currentWeightKg: BigDecimal? = null, val expectedDepartureAt: Long? = null, val primaryLocation: String? = null, val primaryCustodian: String? = null, val currentLegLabel: String? = null, val movementLabel: String? = null,
    val carrierName: String? = null, val expectedRemainingMillis: Long? = null, val lastUpdateAt: Long? = null, val actualArrivalAt: Long? = null, val acceptedQuantity: Int = 0, val missingQuantity: Int = 0, val totalLogisticsCost: BigDecimal = BigDecimal.ZERO, val finalInventoryValue: BigDecimal = BigDecimal.ZERO, val hasRecovery: Boolean = false,
)

sealed interface LogisticsCenterUiState {
    data object Loading : LogisticsCenterUiState
    data object PermissionDenied : LogisticsCenterUiState
    data class Error(val message: String) : LogisticsCenterUiState
    data class Content(val metrics: LogisticsCenterMetrics, val groups: Map<LogisticsCenterGroup, List<LogisticsShipmentCardUi>>,
        val analytics: LogisticsRouteAnalyticsReadModel = LogisticsRouteAnalyticsReadModel()) : LogisticsCenterUiState { val isEmpty: Boolean get() = groups.values.all { it.isEmpty() } }
}

sealed interface LogisticsDetailUiState {
    data object Idle : LogisticsDetailUiState
    data object Loading : LogisticsDetailUiState
    data object PermissionDenied : LogisticsDetailUiState
    data class Error(val message: String) : LogisticsDetailUiState
    data class Content(val detail: LogisticsShipmentDetailUi) : LogisticsDetailUiState
}

sealed interface LogisticsOperationUiState {
    data object Idle : LogisticsOperationUiState
    data class Working(val key: String) : LogisticsOperationUiState
    data class Success(val message: String) : LogisticsOperationUiState
    data class Error(val message: String) : LogisticsOperationUiState
    data class AwaitingHandoff(val draft: LogisticsCustodyHandoffDraft) : LogisticsOperationUiState
    data class AwaitingCustoms(val draft: LogisticsCustomsPickupDraft) : LogisticsOperationUiState
    data class AwaitingRepack(val draft: LogisticsCargoRepackDraft) : LogisticsOperationUiState
}
data class LogisticsCargoInput(val packageCount: String = "", val weightKg: String = "")
data class LogisticsPackageConditionDraft(
    val openedPackageCount: String = "0",
    val damagedPackageCount: String = "0",
)

data class LogisticsCustodyHandoffScope(
    val shipmentId: String,
    val sourceId: String? = null,
)

data class LogisticsCustodyHandoffDraft(
    val scope: LogisticsCustodyHandoffScope,
    val currentCargo: LogisticsCargoSnapshot,
    val handoverPackageCount: String = currentCargo.packageCount.toString(),
    val receivedPackageCount: String = currentCargo.packageCount.toString(),
    val receivedWeightKg: String = currentCargo.weightKg?.toPlainString().orEmpty(),
    val condition: LogisticsPackageConditionDraft = LogisticsPackageConditionDraft(),
    val discrepancyNote: String = ""
) {
    val shipmentId: String get() = scope.shipmentId
    val sourceId: String? get() = scope.sourceId
    val openedPackageCount: String get() = condition.openedPackageCount
    val damagedPackageCount: String get() = condition.damagedPackageCount
    val currentPackageCount: Int get() = currentCargo.packageCount
    val currentWeightKg: BigDecimal? get() = currentCargo.weightKg
    val handoverCount: Int? get() = handoverPackageCount.toIntOrNull()
    val receivedCount: Int? get() = receivedPackageCount.toIntOrNull()
    val receivedWeight: BigDecimal? get() = receivedWeightKg.trim().takeIf(String::isNotEmpty)?.toBigDecimalOrNull()
    val openedCount: Int? get() = openedPackageCount.toIntOrNull()
    val damagedCount: Int? get() = damagedPackageCount.toIntOrNull()
    val isDiscrepant: Boolean get() = handoverCount != null && receivedCount != null && handoverCount != receivedCount
    val isValid: Boolean get() {
        val received = receivedCount ?: return false
        val weight = receivedWeight
        val opened = openedCount ?: return false
        val damaged = damagedCount ?: return false
        return handoverCount == currentPackageCount && received >= 0 && opened in 0..received && damaged in 0..received &&
            (!isDiscrepant || discrepancyNote.isNotBlank()) && (weight == null || weight.signum() > 0)
    }
}
data class LogisticsCustomsPickupDraft(
    val handoff: LogisticsCustodyHandoffDraft,
    val milestoneId: String,
    val brokers: List<LogisticsPartner>,
    val selectedBrokerId: String = "",
) {
    val shipmentId: String get() = handoff.shipmentId
    val selectedBroker: LogisticsPartner? get() = brokers.firstOrNull { it.id == selectedBrokerId }
    val isValid: Boolean get() = handoff.isValid && selectedBroker?.role == LogisticsPartnerRole.CUSTOMS_BROKER
}

data class LogisticsCargoRepackDraft(
    val shipmentId: String,
    val milestoneId: String? = null,
    val previousCargo: LogisticsCargoSnapshot,
    val newCargo: LogisticsCargoInput = LogisticsCargoInput(weightKg = previousCargo.weightKg?.toPlainString().orEmpty()),
    val reason: LogisticsPackageChangeReason = LogisticsPackageChangeReason.REPACKAGED,
    val note: String = "",
    val proof: LogisticsRepackProof? = null
) {
    val previousPackageCount: Int get() = previousCargo.packageCount
    val previousWeightKg: BigDecimal? get() = previousCargo.weightKg
    val newPackageCount: String get() = newCargo.packageCount
    val newWeightKg: String get() = newCargo.weightKg
    val newCount: Int? get() = newCargo.packageCount.toIntOrNull()
    val newWeight: BigDecimal? get() = newCargo.weightKg.trim().takeIf(String::isNotEmpty)?.toBigDecimalOrNull()
    val isValid: Boolean get() {
        val count = newCount ?: return false
        val weight = newWeight
        return count >= 0 && count != previousPackageCount && reason != LogisticsPackageChangeReason.CORRECTION &&
            note.isNotBlank() && (weight == null || weight.signum() > 0)
    }
}
data class LogisticsShipmentDetailUi(
    val aggregate: LogisticsShipmentAggregate,
    val nextAction: LogisticsNextAction,
    val currentMilestone: LogisticsMilestone?,
    val operationalStatus: LogisticsOperationalStatus? = null,
    val operationalDelay: com.verto.app.feature.shipment.application.model.LogisticsDelayReadModel? = null,
    val sourceCustody: List<LogisticsSourceCustodyUi> = emptyList(),
    val firstCarrierId: String? = null,
    val firstCarrierName: String? = null,
    val startReady: Boolean = false,
    val availableCarriers: List<LogisticsPartner> = emptyList(),
    val receivedQuantity: Int,
    val expectedQuantity: Int,
    val acceptedQuantity: Int,
    val actualCostTotal: BigDecimal,
    val costSettled: Boolean,
    val timeline: List<ShipmentTimelineReadItem> = emptyList(), val closedSummary: ShipmentClosedDetailReadModel? = null,
)

data class LogisticsSourceCustodyUi(
    val sourceId: String,
    val invoiceNumber: String,
    val supplierName: String,
    val holderType: LogisticsCustodyHolderType,
    val holderId: String?,
    val holderName: String,
    val phone: String? = null,
    val atFirstCarrier: Boolean = false,
)


internal const val LOGISTICS_PLANNING_PLACE_SEPARATOR = " • "

internal data class LogisticsPlanningPlace(val country: String, val city: String) {
    val isValid: Boolean get() = country.isNotBlank() && city.isNotBlank()
}

internal fun logisticsPlanningPlace(country: String, city: String): String =
    listOf(country.trim(), city.trim()).joinToString(LOGISTICS_PLANNING_PLACE_SEPARATOR)

internal fun String.toLogisticsPlanningPlace(): LogisticsPlanningPlace {
    val parts = split(LOGISTICS_PLANNING_PLACE_SEPARATOR, limit = 2)
    return if (parts.size == 2) LogisticsPlanningPlace(parts[0].trim(), parts[1].trim())
    else LogisticsPlanningPlace("", trim())
}

data class CreateLogisticsShipmentDraft(
    val sourceLocation: String = "",
    val destinationLocation: String = "",
    val notes: String = "",
) {
    // Compatibility only for the existing operation key; never user-entered or persisted from v172 onward.
    val shipmentNumber: String get() = "AUTO"

    val isValid: Boolean
        get() = sourceLocation.isNotBlank() && destinationLocation.isNotBlank()
}

data class LogisticsDefinitionLocationDraft(
    val countryName: String = "",
    val city: String = "",
)

data class LogisticsShipmentHeaderDraft(
    val shipmentId: String,
    val shipmentNumber: String,
    val origin: LogisticsDefinitionLocationDraft = LogisticsDefinitionLocationDraft(),
    val destination: LogisticsDefinitionLocationDraft = LogisticsDefinitionLocationDraft(),
    val employeeId: String = "",
    val employeeName: String = "",
) {
    val originCountryName: String get() = origin.countryName
    val originCity: String get() = origin.city
    val destinationCountryName: String get() = destination.countryName
    val destinationCity: String get() = destination.city
    val departureStation: String get() = origin.city
    val finalArrivalStation: String get() = destination.city

    internal val validation: LogisticsShipmentDefinitionValidation
        get() = validateLogisticsShipmentDefinition(this)

    val canContinue: Boolean
        get() = validation.isValid

    fun asCreateDraft(): CreateLogisticsShipmentDraft = CreateLogisticsShipmentDraft(
        sourceLocation = origin.city,
        destinationLocation = destination.city,
    )
}

internal enum class LogisticsDefinitionField {
    SHIPMENT_NUMBER, ORIGIN_COUNTRY, ORIGIN_CITY, DESTINATION_COUNTRY, DESTINATION_CITY, DESTINATION_MATCH, EMPLOYEE,
}

internal class LogisticsShipmentDefinitionValidation internal constructor(
    private val errors: Map<LogisticsDefinitionField, String>,
) {
    val shipmentNumberError: String? get() = errors[LogisticsDefinitionField.SHIPMENT_NUMBER]
    val originCountryError: String? get() = errors[LogisticsDefinitionField.ORIGIN_COUNTRY]
    val originCityError: String? get() = errors[LogisticsDefinitionField.ORIGIN_CITY]
    val destinationCountryError: String? get() = errors[LogisticsDefinitionField.DESTINATION_COUNTRY]
    val destinationCityError: String? get() = errors[LogisticsDefinitionField.DESTINATION_CITY]
    val destinationMatchError: String? get() = errors[LogisticsDefinitionField.DESTINATION_MATCH]
    val employeeError: String? get() = errors[LogisticsDefinitionField.EMPLOYEE]
    val isValid: Boolean get() = errors.isEmpty()
}

internal fun validateLogisticsShipmentDefinition(draft: LogisticsShipmentHeaderDraft): LogisticsShipmentDefinitionValidation {
    val originCountry = LogisticsCountryNormalizer.displayName(draft.originCountryName)
    val originCity = draft.originCity.trim()
    val destinationCountry = LogisticsCountryNormalizer.displayName(draft.destinationCountryName)
    val destinationCity = draft.destinationCity.trim()
    val samePoint = originCountry.isNotBlank() && originCity.isNotBlank() && destinationCountry.isNotBlank() &&
        destinationCity.isNotBlank() && LogisticsCountryNormalizer.key(originCountry) == LogisticsCountryNormalizer.key(destinationCountry) &&
        originCity.equals(destinationCity, ignoreCase = true)
    val errors = buildMap {
        if (draft.shipmentNumber.isBlank()) put(LogisticsDefinitionField.SHIPMENT_NUMBER, "تعذر إنشاء رقم الشحنة، حاول مجددًا")
        if (originCountry.isBlank()) put(LogisticsDefinitionField.ORIGIN_COUNTRY, "اكتب دولة الانطلاق")
        if (originCity.isBlank()) put(LogisticsDefinitionField.ORIGIN_CITY, "اكتب مدينة الانطلاق")
        if (destinationCountry.isBlank()) put(LogisticsDefinitionField.DESTINATION_COUNTRY, "اكتب دولة الوصول")
        if (destinationCity.isBlank()) put(LogisticsDefinitionField.DESTINATION_CITY, "اكتب مدينة الوصول")
        if (samePoint) put(LogisticsDefinitionField.DESTINATION_MATCH, "يجب أن تختلف نقطة الوصول عن نقطة الانطلاق")
        if (draft.employeeId.isBlank()) put(LogisticsDefinitionField.EMPLOYEE, "اختر مسؤول المتابعة")
    }
    return LogisticsShipmentDefinitionValidation(errors)
}

sealed interface LogisticsDraftUiState {
    data object Idle : LogisticsDraftUiState
    data object Loading : LogisticsDraftUiState
    data object PermissionDenied : LogisticsDraftUiState
    data class Error(val message: String) : LogisticsDraftUiState
    data class ResumeAvailable(
        val shipmentId: String,
        val shipmentNumber: String,
        val currentStep: LogisticsPlanningStep,
    ) : LogisticsDraftUiState
    data class Content(
        val draft: LogisticsShipmentHeaderDraft,
        val employees: List<LogisticsEmployeeOption> = emptyList(),
        val countrySuggestions: List<String> = emptyList(),
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : LogisticsDraftUiState
}

data class LogisticsEmployeeOption(
    val id: String,
    val name: String,
)

data class LogisticsPurchaseInvoiceOptionUi(
    val source: LogisticsShipmentSource,
    val lines: List<LogisticsShipmentLine>,
    val invoiceDate: Long = 0L,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
) {
    val id: String get() = source.invoiceId
    val itemCount: Int get() = lines.size
    val label: String get() = "#${source.invoiceNumberSnapshot}"
    /** Hidden line identities are validated before selection so v236 never exposes item rows. */
    val selectable: Boolean get() = lines.isNotEmpty() && lines.all { it.inventoryItemId.isNotBlank() }
    val readinessLabel: String get() = if (selectable) "جاهزة" else "تحتاج ربط بالمخزون"
}

data class LogisticsPurchaseSupplierOptionUi(
    val supplierId: String,
    val supplierName: String,
    val invoices: List<LogisticsPurchaseInvoiceOptionUi>,
)

data class LogisticsPurchaseSaveUi(val saving: Boolean, val error: String?)

data class LogisticsPurchasePlanActions(
    val onDraftChange: (LogisticsPlanningDraft) -> Unit,
    val onBindInventoryItem: (invoiceItemId: String, inventoryItemId: String) -> Unit,
    val onCreateInventoryItem: (invoiceItemId: String) -> Unit,
)


internal fun List<LogisticsPurchaseInvoiceOptionUi>.groupedBySupplier(): List<LogisticsPurchaseSupplierOptionUi> =
    groupBy { it.source.supplierId }.map { (supplierId, invoices) ->
        LogisticsPurchaseSupplierOptionUi(
            supplierId = supplierId,
            supplierName = invoices.first().source.supplierNameSnapshot,
            invoices = invoices.sortedBy { it.source.invoiceNumberSnapshot },
        )
    }.sortedBy { it.supplierName }

enum class LogisticsPlanningSubmitAction { SAVE_DRAFT, MARK_READY }

data class PlanningDocumentTarget(
    val sourceInvoiceId: String? = null,
    val milestoneId: String? = null,
    val legId: String? = null,
)

data class LogisticsPendingDocumentDraft(
    val draftId: String,
    /** Original SAF URI is retained only while staging failed so Retry can re-read it. */
    val sourceUri: String = "",
    /** App-private shipment-owned URI. Never render this value as user-facing text. */
    val privateUri: String? = null,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val type: LogisticsDocumentType = LogisticsDocumentType.OTHER,
    val sourceInvoiceId: String? = null,
    val milestoneId: String? = null,
    val legId: String? = null,
    val isStaging: Boolean = false,
    val errorMessage: String? = null,
) {
    val isReady: Boolean
        get() = !isStaging && errorMessage == null && !privateUri.isNullOrBlank() && (sizeBytes ?: 0L) > 0L
}

data class LogisticsPlanningDraft(
    val organizationId: String = "",
    val shipmentId: String = "",
    val sourceLocation: String = "",
    val destinationLocation: String = "",
    val transportMode: LogisticsTransportMode? = null,
    val assigneeId: String = "",
    val assigneeName: String = "",
    val expectedDepartureAt: Long? = null,
    val expectedArrivalAt: Long? = null,
    val transportDetails: LogisticsTransportDetails? = null,
    val sources: List<LogisticsShipmentSource> = emptyList(),
    val lines: List<LogisticsShipmentLine> = emptyList(),
    val milestones: List<LogisticsMilestone> = emptyList(),
    val legs: List<LogisticsShipmentLeg> = emptyList(),
    val carriers: List<LogisticsPartner> = emptyList(),
    val costs: List<LogisticsCost> = emptyList(),
    val documents: List<LogisticsDocument> = emptyList(),
    val pendingCosts: List<LogisticsCostDraft> = emptyList(),
    val pendingDocuments: List<LogisticsPendingDocumentDraft> = emptyList(),
    val submitAction: LogisticsPlanningSubmitAction = LogisticsPlanningSubmitAction.SAVE_DRAFT,
) {
    fun isPurchaseStepReady(options: List<LogisticsPurchaseInvoiceOptionUi>): Boolean {
        if (sources.isEmpty() || lines.isEmpty()) return false
        if (lines.any { it.inventoryItemId.isBlank() }) return false
        if (sources.any { source ->
                source.plannedPackageCount?.let { it > 0 } != true ||
                    source.plannedWeightKg?.signum() != 1 ||
                    source.expectedReadyAt?.let { it > 0L } != true
            }
        ) return false
        if (sources.map { it.invoiceId }.distinct().size != sources.size) return false
        if (lines.map { it.sourceInvoiceId to it.sourceInvoiceItemId }.distinct().size != lines.size) return false
        val optionByInvoice = options.associateBy { it.id }
        return sources.all { source ->
            val option = optionByInvoice[source.invoiceId] ?: return@all false
            if (option.source.supplierId != source.supplierId) return@all false
            val selected = lines.filter { it.sourceInvoiceId == source.invoiceId }.associateBy { it.sourceInvoiceItemId }
            selected.keys == option.lines.map { it.sourceInvoiceItemId }.toSet() &&
                option.lines.all { available ->
                    val line = selected[available.sourceInvoiceItemId] ?: return@all false
                    line.inventoryItemId == available.inventoryItemId &&
                        line.expectedQuantity == available.expectedQuantity
                }
        }
    }

    val isValid: Boolean
        get() {
            val orderedMilestones = milestones.sortedBy { it.order }
            val orderedLegs = legs.sortedBy { it.sequence }
            val milestoneFieldsValid = orderedMilestones.all { milestone ->
                val structuredStarted = milestone.countryCode.isNotBlank() || milestone.countryNameSnapshot.isNotBlank() || milestone.city.isNotBlank()
                val structuredValid = !structuredStarted ||
                    (milestone.countryNameSnapshot.isNotBlank() && milestone.city.isNotBlank() &&
                        (milestone.countryCode.isBlank() || LogisticsCountryNormalizer.isInternalKey(milestone.countryCode)))
                val customsScopeValid = milestone.type == com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.CUSTOMS ||
                    (milestone.customsBrokerPartnerId == null && milestone.customsBrokerNameSnapshot.isNullOrBlank() &&
                        milestone.customsBrokerPhoneSnapshot.isNullOrBlank())
                milestone.location.isNotBlank() && milestone.placeName.isNotBlank() && structuredValid && customsScopeValid &&
                    (milestone.expectedStayDays == null || milestone.expectedStayDays >= 0)
            }
            val routeValid = orderedMilestones.size >= 2 &&
                orderedMilestones.firstOrNull()?.type?.name == "ORIGIN" &&
                orderedMilestones.lastOrNull()?.type?.name == "DESTINATION" &&
                orderedMilestones.firstOrNull()?.location?.trim() == sourceLocation.toLogisticsPlanningPlace().city.trim() &&
                orderedMilestones.lastOrNull()?.location?.trim() == destinationLocation.toLogisticsPlanningPlace().city.trim() &&
                orderedMilestones.drop(1).dropLast(1).none { it.type.name == "ORIGIN" || it.type.name == "DESTINATION" } &&
                milestoneFieldsValid &&
                orderedMilestones.map { it.order } == orderedMilestones.indices.toList() &&
                orderedLegs.size == orderedMilestones.size - 1 &&
                orderedLegs.map { it.sequence } == orderedLegs.indices.toList() &&
                orderedLegs.all { leg ->
                    leg.carrierPartnerId.isBlank() && leg.packageCount == null && leg.weightKg == null &&
                        ((leg.expectedTransitMinutes ?: leg.expectedTransitDays?.times(24 * 60) ?: 0) > 0) &&
                        orderedMilestones.getOrNull(leg.sequence)?.id == leg.fromMilestoneId &&
                        orderedMilestones.getOrNull(leg.sequence + 1)?.id == leg.toMilestoneId
                }
            return sourceLocation.isNotBlank() && destinationLocation.isNotBlank() &&
                assigneeId.isNotBlank() && assigneeName.isNotBlank() && sources.isNotEmpty() && lines.isNotEmpty() && lines.all { it.expectedQuantity > 0 } && routeValid
        }
}

sealed interface LogisticsPlanningUiState {
    data object Idle : LogisticsPlanningUiState
    data object Loading : LogisticsPlanningUiState
    data object PermissionDenied : LogisticsPlanningUiState
    data class Error(val message: String) : LogisticsPlanningUiState
    data class Content(
        val shipmentId: String,
        val shipmentNumber: String,
        val initialDraft: LogisticsPlanningDraft,
        val employees: List<LogisticsEmployeeOption>,
        val purchaseInvoices: List<LogisticsPurchaseInvoiceOptionUi>,
        val inventoryCatalog: List<LogisticsInventoryCatalogItem> = emptyList(),
        val carriers: List<LogisticsPartner> = emptyList(),
        val customsPlan: LogisticsCustomsPlan? = null,
        val customsPlanDocuments: List<LogisticsCustomsPlanDocument> = emptyList(),
        val customsCheckpointSuggestions: List<String> = emptyList(),
        val purchaseSaving: Boolean = false,
        val purchaseSaveError: String? = null,
        val purchaseInvoiceLoading: Boolean = false,
        val purchaseInvoiceLoadError: String? = null,
    ) : LogisticsPlanningUiState
}

data class LogisticsReceivingLineDraft(
    val shipmentLineId: String,
    val sourceInvoiceId: String = "",
    val sourceInvoiceNumber: String = "",
    val itemName: String,
    val expectedQuantity: Int,
    val alreadyReceivedQuantity: Int = 0,
    val receivedQuantity: Int = 0,
    val acceptedQuantity: Int = 0,
    val damagedQuantity: Int = 0,
    val rejectedQuantity: Int = 0,
    val quarantinedQuantity: Int = 0,
) {
    val partitionTotal: Int
        get() = acceptedQuantity + damagedQuantity + rejectedQuantity + quarantinedQuantity

    val remainingBeforeBatch: Int
        get() = (expectedQuantity - alreadyReceivedQuantity).coerceAtLeast(0)

    val missingAfterBatch: Int
        get() = (expectedQuantity - alreadyReceivedQuantity - receivedQuantity).coerceAtLeast(0)

    val isValid: Boolean
        get() = expectedQuantity >= 0 && alreadyReceivedQuantity >= 0 && receivedQuantity >= 0 &&
            acceptedQuantity >= 0 && damagedQuantity >= 0 && rejectedQuantity >= 0 && quarantinedQuantity >= 0 &&
            partitionTotal == receivedQuantity && receivedQuantity <= remainingBeforeBatch
}

data class LogisticsReceivingDraft(
    val receivedCompletely: Boolean,
    val lines: List<LogisticsReceivingLineDraft>,
) {
    val isValid: Boolean
        get() {
            if (lines.isEmpty() || lines.any { !it.isValid }) return false
            val missing = lines.filter { it.missingAfterBatch > 0 }
            return if (receivedCompletely) {
                missing.isEmpty() && lines.any { it.receivedQuantity > 0 }
            } else {
                missing.isNotEmpty() && lines.any { it.receivedQuantity > 0 }
            }
        }
}

sealed interface LogisticsReceivingUiState {
    data object Idle : LogisticsReceivingUiState
    data object Loading : LogisticsReceivingUiState
    data object PermissionDenied : LogisticsReceivingUiState
    data class Error(val message: String) : LogisticsReceivingUiState
    data class Content(
        val shipmentId: String,
        val shipmentNumber: String,
        val lines: List<LogisticsReceivingLineDraft>,
        val savedDraft: LogisticsFinalReceivingDraftSnapshot? = null,
    ) : LogisticsReceivingUiState
}

data class LogisticsCostSettlementUi(
    val costs: List<LogisticsCost>,
    val allocations: List<LogisticsCostAllocation>,
    val actualTotal: BigDecimal,
    val settled: Boolean,
    val sources: List<LogisticsShipmentSource> = emptyList(),
    val milestones: List<LogisticsMilestone> = emptyList(),
    val legs: List<LogisticsShipmentLeg> = emptyList(),
)

data class LogisticsCostDraft(
    val type: LogisticsCostType = LogisticsCostType.FREIGHT,
    val amount: String = "",
    val currency: String = "SDG",
    val exchangeRate: String = "1",
    val status: LogisticsCostStatus = LogisticsCostStatus.ACTUAL,
    val servicePartnerId: String? = null,
    val sourceInvoiceId: String? = null,
    val sourceId: String? = null,
    val milestoneId: String? = null,
    val legId: String? = null,
    val reference: String = "",
    val note: String = "",
) {
    val amountDecimal: BigDecimal? get() = amount.toBigDecimalOrNull()
    val exchangeRateDecimal: BigDecimal? get() = exchangeRate.toBigDecimalOrNull()
    val isValid: Boolean
        get() = amountDecimal?.signum() == 1 && exchangeRateDecimal?.signum() == 1 && currency.isNotBlank()
}

sealed interface LogisticsCostsUiState {
    data object Idle : LogisticsCostsUiState
    data object Loading : LogisticsCostsUiState
    data object PermissionDenied : LogisticsCostsUiState
    data class Error(val message: String) : LogisticsCostsUiState
    data class Content(
        val shipmentId: String,
        val shipmentNumber: String,
        val settlement: LogisticsCostSettlementUi,
    ) : LogisticsCostsUiState
}

data class LogisticsPartnerDirectoryUi(
    val partners: List<LogisticsPartner> = emptyList(),
    val canManage: Boolean = false,
)

data class LogisticsPartnerDraft(
    val name: String = "",
    val role: LogisticsPartnerRole = LogisticsPartnerRole.CARRIER,
    val phone: String = "",
    val representativeName: String = "",
    val representativePhone: String = "",
    val notes: String = "",
) {
    val isValid: Boolean get() = name.isNotBlank() && role in setOf(
        LogisticsPartnerRole.CARRIER, LogisticsPartnerRole.FREIGHT_FORWARDER, LogisticsPartnerRole.CUSTOMS_BROKER,
    )
}

internal fun List<LogisticsPartner>.matchingShippingContacts(query: String): List<LogisticsPartner> {
    val needle = query.trim()
    val eligible = filter { it.role in setOf(LogisticsPartnerRole.CARRIER, LogisticsPartnerRole.FREIGHT_FORWARDER, LogisticsPartnerRole.CUSTOMS_BROKER) }
    if (needle.isBlank()) return eligible.sortedBy { it.name }
    return eligible.filter { partner ->
        listOfNotNull(partner.name, partner.phone, partner.representativeName, partner.representativePhone)
            .any { it.contains(needle, ignoreCase = true) }
    }.sortedBy { it.name }
}
sealed interface LogisticsPartnersUiState {
    data object Idle : LogisticsPartnersUiState
    data object Loading : LogisticsPartnersUiState
    data object PermissionDenied : LogisticsPartnersUiState
    data class Error(val message: String) : LogisticsPartnersUiState
    data class Content(
        val shipmentId: String,
        val state: LogisticsPartnerDirectoryUi,
    ) : LogisticsPartnersUiState
}
data class LogisticsReceivingSummary(
    val batches: List<LogisticsReceivingBatch>,
    val expectedQuantity: Int,
    val receivedQuantity: Int,
    val acceptedQuantity: Int,
)
