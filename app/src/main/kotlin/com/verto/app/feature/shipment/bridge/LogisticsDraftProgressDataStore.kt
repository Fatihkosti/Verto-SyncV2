package com.verto.app.feature.shipment.bridge

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedAttachment
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedCost
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsCostDraft
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsDraftProgress
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsDraftProgressStore
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsFinalReceivingDraftSnapshot
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsFinalReceivingLineSnapshot
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsPendingDocumentDraft
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsPlanningStep
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsRouteWorkspaceSnapshot
import com.verto.app.utils.dataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class LogisticsDraftProgressDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionReader: SessionReader,
) : LogisticsDraftProgressStore {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun loadProgress(): LogisticsDraftProgress? {
        val raw = context.dataStore.data.first()[progressKey()] ?: return null
        val dto = runCatching { json.decodeFromString<ProgressDto>(raw) }.getOrNull() ?: return null
        if (dto.version != LogisticsDraftProgress.CURRENT_VERSION) return null
        val step = runCatching { LogisticsPlanningStep.valueOf(dto.currentStep) }.getOrNull() ?: return null
        return LogisticsDraftProgress(dto.activeDraftShipmentId, step, dto.updatedAt, dto.version)
            .takeIf { it.activeDraftShipmentId.isNotBlank() }
    }

    override suspend fun saveProgress(progress: LogisticsDraftProgress) {
        require(progress.activeDraftShipmentId.isNotBlank()) { "activeDraftShipmentId is required" }
        val dto = ProgressDto.from(progress)
        val raw = json.encodeToString(dto)
        val key = progressKey()
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { stored ->
                runCatching { json.decodeFromString<ProgressDto>(stored) }.getOrNull()
            }
            if (current == null || current.version != dto.version ||
                current.activeDraftShipmentId != dto.activeDraftShipmentId || current.updatedAt <= dto.updatedAt
            ) {
                prefs[key] = raw
            }
        }
    }

    override suspend fun clearProgress(expectedShipmentId: String?) {
        val key = progressKey()
        context.dataStore.edit { prefs ->
            if (expectedShipmentId == null) {
                prefs.remove(key)
            } else {
                val current = prefs[key]?.let { raw -> runCatching { json.decodeFromString<ProgressDto>(raw) }.getOrNull() }
                if (current?.activeDraftShipmentId == expectedShipmentId) prefs.remove(key)
            }
        }
    }

    override suspend fun loadRouteWorkspace(shipmentId: String): LogisticsRouteWorkspaceSnapshot? {
        if (shipmentId.isBlank()) return null
        val raw = context.dataStore.data.first()[workspaceKey(shipmentId)] ?: return null
        val dto = runCatching { json.decodeFromString<WorkspaceDto>(raw) }.getOrNull() ?: return null
        if (dto.version != LogisticsRouteWorkspaceSnapshot.CURRENT_VERSION || dto.shipmentId != shipmentId) return null
        return runCatching { dto.toDomain() }.getOrNull()
    }

    override suspend fun saveRouteWorkspace(snapshot: LogisticsRouteWorkspaceSnapshot) {
        require(snapshot.shipmentId.isNotBlank()) { "shipmentId is required" }
        val dto = WorkspaceDto.from(snapshot)
        val raw = json.encodeToString(dto)
        val key = workspaceKey(snapshot.shipmentId)
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { stored ->
                runCatching { json.decodeFromString<WorkspaceDto>(stored) }.getOrNull()
            }
            if (current == null || current.version != dto.version || current.updatedAt <= dto.updatedAt) prefs[key] = raw
        }
    }

    override suspend fun clearRouteWorkspace(shipmentId: String) {
        if (shipmentId.isBlank()) return
        val key = workspaceKey(shipmentId)
        context.dataStore.edit { it.remove(key) }
    }

    override suspend fun loadReceivingDraft(shipmentId: String): LogisticsFinalReceivingDraftSnapshot? {
        if (shipmentId.isBlank()) return null
        val raw = context.dataStore.data.first()[receivingKey(shipmentId)] ?: return null
        val dto = runCatching { json.decodeFromString<ReceivingDraftDto>(raw) }.getOrNull() ?: return null
        if (dto.version != LogisticsFinalReceivingDraftSnapshot.CURRENT_VERSION || dto.shipmentId != shipmentId) return null
        return dto.toDomain()
    }

    override suspend fun saveReceivingDraft(snapshot: LogisticsFinalReceivingDraftSnapshot) {
        require(snapshot.shipmentId.isNotBlank()) { "shipmentId is required" }
        val dto = ReceivingDraftDto.from(snapshot)
        val raw = json.encodeToString(dto)
        val key = receivingKey(snapshot.shipmentId)
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { stored -> runCatching { json.decodeFromString<ReceivingDraftDto>(stored) }.getOrNull() }
            if (current == null || current.version != dto.version || current.updatedAt <= dto.updatedAt) prefs[key] = raw
        }
    }

    override suspend fun clearReceivingDraft(shipmentId: String) {
        if (shipmentId.isBlank()) return
        context.dataStore.edit { it.remove(receivingKey(shipmentId)) }
    }

    private suspend fun progressKey() = stringPreferencesKey("logistics_draft_progress_v1_${scopeHash()}")
    private suspend fun workspaceKey(shipmentId: String) = stringPreferencesKey(
        "logistics_route_workspace_v1_${scopeHash()}_${sha256(shipmentId)}",
    )
    private suspend fun receivingKey(shipmentId: String) = stringPreferencesKey(
        "logistics_final_receiving_v1_${scopeHash()}_${sha256(shipmentId)}",
    )

    private suspend fun scopeHash(): String {
        val session = sessionReader.snapshot()
        val org = session.organization.id.trim()
        val user = session.user.id.trim()
        require(org.isNotBlank() && user.isNotBlank()) { "Logistics draft persistence requires an active session" }
        return sha256("$org|$user")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24)

    @Serializable
    private data class ReceivingDraftDto(
        val shipmentId: String,
        val requestId: String,
        val receivedCompletely: Boolean?,
        val missingQuantities: Map<String, String>,
        val lines: List<ReceivingLineDto> = emptyList(),
        val updatedAt: Long,
        val version: Int,
    ) {
        fun toDomain() = LogisticsFinalReceivingDraftSnapshot(
            shipmentId = shipmentId,
            requestId = requestId,
            receivedCompletely = receivedCompletely,
            missingQuantities = missingQuantities,
            lines = lines.map(ReceivingLineDto::toDomain),
            updatedAt = updatedAt,
            version = version,
        )
        companion object {
            fun from(value: LogisticsFinalReceivingDraftSnapshot) = ReceivingDraftDto(
                shipmentId = value.shipmentId,
                requestId = value.requestId,
                receivedCompletely = value.receivedCompletely,
                missingQuantities = value.missingQuantities,
                lines = value.lines.map(ReceivingLineDto::from),
                updatedAt = value.updatedAt,
                version = value.version,
            )
        }
    }

    @Serializable
    private data class ReceivingLineDto(
        val shipmentLineId: String,
        val sourceInvoiceId: String,
        val sourceInvoiceNumber: String,
        val itemName: String,
        val expectedQuantity: Int,
        val alreadyReceivedQuantity: Int,
    ) {
        fun toDomain() = LogisticsFinalReceivingLineSnapshot(
            shipmentLineId, sourceInvoiceId, sourceInvoiceNumber, itemName, expectedQuantity, alreadyReceivedQuantity,
        )
        companion object {
            fun from(value: LogisticsFinalReceivingLineSnapshot) = ReceivingLineDto(
                value.shipmentLineId, value.sourceInvoiceId, value.sourceInvoiceNumber, value.itemName,
                value.expectedQuantity, value.alreadyReceivedQuantity,
            )
        }
    }

    @Serializable
    private data class ProgressDto(
        val activeDraftShipmentId: String,
        val currentStep: String,
        val updatedAt: Long,
        val version: Int,
    ) {
        companion object {
            fun from(value: LogisticsDraftProgress) = ProgressDto(
                value.activeDraftShipmentId, value.currentStep.name, value.updatedAt, value.version,
            )
        }
    }

    @Serializable
    private data class WorkspaceDto(
        val shipmentId: String,
        val newMilestoneLocation: String,
        val newMilestoneType: String,
        val incoterm: String,
        val volumeM3: String,
        val palletCount: String,
        val insuranceReference: String,
        val routeTransportPlanKind: String = LogisticsRouteTransportPlanKind.UNIFIED.name,
        val tripTypeSelected: Boolean = false,
        val unifiedTransportMode: String = LogisticsLegTransportMode.ROAD.name,
        val unifiedTransportModeSelected: Boolean = false,
        val duplicateConfirmedMilestoneIds: List<String> = emptyList(),
        val expandedSection: String = "V237_TRIP_TYPE",
        val templateName: String = "",
        val customsCheckpointName: String = "",
        val customsAfterStationId: String = "",
        val customsExpectedDurationMinutes: Int? = null,
        val returnToReviewAfterEdit: Boolean = false,
        val milestones: List<MilestoneDto>,
        val legs: List<LegDto>,
        val pendingCosts: List<CostDto>,
        val pendingDocuments: List<DocumentDto>,
        val updatedAt: Long,
        val version: Int,
    ) {
        fun toDomain() = LogisticsRouteWorkspaceSnapshot(
            shipmentId = shipmentId,
            newMilestoneLocation = newMilestoneLocation,
            newMilestoneType = LogisticsMilestoneType.valueOf(newMilestoneType),
            incoterm = incoterm,
            volumeM3 = volumeM3,
            palletCount = palletCount,
            insuranceReference = insuranceReference,
            routeTransportPlanKind = LogisticsRouteTransportPlanKind.valueOf(routeTransportPlanKind),
            tripTypeSelected = tripTypeSelected,
            unifiedTransportMode = LogisticsLegTransportMode.valueOf(unifiedTransportMode),
            unifiedTransportModeSelected = unifiedTransportModeSelected,
            duplicateConfirmedMilestoneIds = duplicateConfirmedMilestoneIds.toSet(),
            expandedSection = expandedSection,
            templateName = templateName,
            customsCheckpointName = customsCheckpointName,
            customsAfterStationId = customsAfterStationId,
            customsExpectedDurationMinutes = customsExpectedDurationMinutes,
            returnToReviewAfterEdit = returnToReviewAfterEdit,
            milestones = milestones.map(MilestoneDto::toDomain),
            legs = legs.map(LegDto::toDomain),
            pendingCosts = pendingCosts.map(CostDto::toDomain),
            pendingDocuments = pendingDocuments.map(DocumentDto::toDomain),
            updatedAt = updatedAt,
            version = version,
        )

        companion object {
            fun from(value: LogisticsRouteWorkspaceSnapshot) = WorkspaceDto(
                shipmentId = value.shipmentId,
                newMilestoneLocation = value.newMilestoneLocation,
                newMilestoneType = value.newMilestoneType.name,
                incoterm = value.incoterm,
                volumeM3 = value.volumeM3,
                palletCount = value.palletCount,
                insuranceReference = value.insuranceReference,
                routeTransportPlanKind = value.routeTransportPlanKind.name,
                tripTypeSelected = value.tripTypeSelected,
                unifiedTransportMode = value.unifiedTransportMode.name,
                unifiedTransportModeSelected = value.unifiedTransportModeSelected,
                duplicateConfirmedMilestoneIds = value.duplicateConfirmedMilestoneIds.toList(),
                expandedSection = value.expandedSection,
                templateName = value.templateName,
                customsCheckpointName = value.customsCheckpointName,
                customsAfterStationId = value.customsAfterStationId,
                customsExpectedDurationMinutes = value.customsExpectedDurationMinutes,
                returnToReviewAfterEdit = value.returnToReviewAfterEdit,
                milestones = value.milestones.map(MilestoneDto::from),
                legs = value.legs.map(LegDto::from),
                pendingCosts = value.pendingCosts.map(CostDto::from),
                pendingDocuments = value.pendingDocuments.map(DocumentDto::from),
                updatedAt = value.updatedAt,
                version = value.version,
            )
        }
    }

    @Serializable
    private data class MilestoneDto(
        val id: String, val shipmentId: String, val type: String, val order: Int, val location: String,
        val plannedArrivalAt: Long?, val arrivedAt: Long?, val departedAt: Long?, val note: String,
        val plannedDepartureAt: Long?, val handlingStatus: String, val unloadedAt: Long?, val loadedAt: Long?,
        val countryCode: String, val countryNameSnapshot: String, val city: String, val placeName: String,
        val planKind: String, val expectedStayDays: Int?, val customsBrokerPartnerId: String?,
        val customsBrokerNameSnapshot: String?, val customsBrokerPhoneSnapshot: String?,
    ) {
        fun toDomain() = LogisticsMilestone(
            id = id,
            shipmentId = shipmentId,
            type = LogisticsMilestoneType.valueOf(type),
            order = order,
            location = location,
            plannedArrivalAt = plannedArrivalAt,
            arrivedAt = arrivedAt,
            departedAt = departedAt,
            note = note,
            plannedDepartureAt = plannedDepartureAt,
            handlingStatus = LogisticsMilestoneHandlingStatus.valueOf(handlingStatus),
            unloadedAt = unloadedAt,
            loadedAt = loadedAt,
            countryCode = countryCode,
            countryNameSnapshot = countryNameSnapshot,
            city = city,
            placeName = placeName,
            planKind = LogisticsPlanKind.valueOf(planKind),
            expectedStayDays = expectedStayDays,
            customsBrokerPartnerId = customsBrokerPartnerId,
            customsBrokerNameSnapshot = customsBrokerNameSnapshot,
            customsBrokerPhoneSnapshot = customsBrokerPhoneSnapshot,
        )
        companion object { fun from(v: LogisticsMilestone) = MilestoneDto(
            v.id, v.shipmentId, v.type.name, v.order, v.location, v.plannedArrivalAt, v.arrivedAt, v.departedAt,
            v.note, v.plannedDepartureAt, v.handlingStatus.name, v.unloadedAt, v.loadedAt, v.countryCode,
            v.countryNameSnapshot, v.city, v.placeName, v.planKind.name, v.expectedStayDays, v.customsBrokerPartnerId,
            v.customsBrokerNameSnapshot, v.customsBrokerPhoneSnapshot,
        ) }
    }

    @Serializable
    private data class LegDto(
        val id: String, val organizationId: String, val shipmentId: String, val sequence: Int,
        val fromMilestoneId: String, val toMilestoneId: String, val mode: String, val carrierPartnerId: String,
        val status: String, val plannedDepartureAt: Long?, val plannedArrivalAt: Long?, val actualDepartureAt: Long?,
        val actualArrivalAt: Long?, val roadVehicleNumber: String?, val roadDriverName: String?, val roadDriverPhone: String?,
        val seaContainerNumber: String?, val seaBillOfLading: String?, val seaVesselReference: String?,
        val airWaybillNumber: String?, val airFlightReference: String?, val note: String, val planKind: String,
        val expectedTransitDays: Int?, val representativeNameSnapshot: String?, val representativePhoneSnapshot: String?,
        val packageCount: Int?, val weightKg: String?, val supersededAt: Long?, val supersededByLegId: String?,
        val expectedTransitMinutes: Int? = null,
        val plannedCarrierPartnerId: String? = null,
        val plannedCarrierNameSnapshot: String? = null,
        val plannedRepresentativeNameSnapshot: String? = null,
        val plannedRepresentativePhoneSnapshot: String? = null,
        val plannedPackageCount: Int? = null,
        val plannedWeightKg: String? = null,
        val plannedCostAmount: String? = null,
        val plannedCostCurrency: String? = null,
        val plannedExchangeRate: String? = null,
        val plannedBaseCostAmount: String? = null,
        val plannedProofPrivateUri: String? = null,
        val plannedProofDisplayName: String? = null,
        val plannedProofMimeType: String? = null,
    ) {
        fun toDomain() = LogisticsShipmentLeg(
            id = id,
            organizationId = organizationId,
            shipmentId = shipmentId,
            sequence = sequence,
            fromMilestoneId = fromMilestoneId,
            toMilestoneId = toMilestoneId,
            mode = LogisticsLegTransportMode.valueOf(mode),
            carrierPartnerId = carrierPartnerId,
            status = LogisticsLegStatus.valueOf(status),
            plannedDepartureAt = plannedDepartureAt,
            plannedArrivalAt = plannedArrivalAt,
            actualDepartureAt = actualDepartureAt,
            actualArrivalAt = actualArrivalAt,
            roadVehicleNumber = roadVehicleNumber,
            roadDriverName = roadDriverName,
            roadDriverPhone = roadDriverPhone,
            seaContainerNumber = seaContainerNumber,
            seaBillOfLading = seaBillOfLading,
            seaVesselReference = seaVesselReference,
            airWaybillNumber = airWaybillNumber,
            airFlightReference = airFlightReference,
            note = note,
            planKind = LogisticsPlanKind.valueOf(planKind),
            expectedTransitDays = expectedTransitDays,
            representativeNameSnapshot = representativeNameSnapshot,
            representativePhoneSnapshot = representativePhoneSnapshot,
            packageCount = packageCount,
            weightKg = weightKg?.toBigDecimalOrNull(),
            supersededAt = supersededAt,
            supersededByLegId = supersededByLegId,
            expectedTransitMinutes = expectedTransitMinutes,
            plannedCarrierPartnerId = plannedCarrierPartnerId,
            plannedCarrierNameSnapshot = plannedCarrierNameSnapshot,
            plannedRepresentativeNameSnapshot = plannedRepresentativeNameSnapshot,
            plannedRepresentativePhoneSnapshot = plannedRepresentativePhoneSnapshot,
            plannedPackageCount = plannedPackageCount,
            plannedWeightKg = plannedWeightKg?.toBigDecimalOrNull(),
            plannedCost = plannedCostAmount?.toBigDecimalOrNull()?.let { amount ->
                val currency = plannedCostCurrency ?: return@let null
                val rate = plannedExchangeRate?.toBigDecimalOrNull() ?: return@let null
                val base = plannedBaseCostAmount?.toBigDecimalOrNull() ?: return@let null
                LogisticsPlannedCost(amount, currency, rate, base)
            },
            plannedProof = plannedProofPrivateUri?.let { uri ->
                val name = plannedProofDisplayName ?: return@let null
                val mime = plannedProofMimeType ?: return@let null
                LogisticsPlannedAttachment(uri, name, mime)
            },
        )
        companion object { fun from(v: LogisticsShipmentLeg) = LegDto(
            v.id, v.organizationId, v.shipmentId, v.sequence, v.fromMilestoneId, v.toMilestoneId, v.mode.name,
            v.carrierPartnerId, v.status.name, v.plannedDepartureAt, v.plannedArrivalAt, v.actualDepartureAt, v.actualArrivalAt,
            v.roadVehicleNumber, v.roadDriverName, v.roadDriverPhone, v.seaContainerNumber, v.seaBillOfLading,
            v.seaVesselReference, v.airWaybillNumber, v.airFlightReference, v.note, v.planKind.name, v.expectedTransitDays,
            v.representativeNameSnapshot, v.representativePhoneSnapshot, v.packageCount, v.weightKg?.toPlainString(),
            v.supersededAt, v.supersededByLegId,
            expectedTransitMinutes = v.expectedTransitMinutes,
            plannedCarrierPartnerId = v.plannedCarrierPartnerId,
            plannedCarrierNameSnapshot = v.plannedCarrierNameSnapshot,
            plannedRepresentativeNameSnapshot = v.plannedRepresentativeNameSnapshot,
            plannedRepresentativePhoneSnapshot = v.plannedRepresentativePhoneSnapshot,
            plannedPackageCount = v.plannedPackageCount,
            plannedWeightKg = v.plannedWeightKg?.toPlainString(),
            plannedCostAmount = v.plannedCost?.amount?.toPlainString(),
            plannedCostCurrency = v.plannedCost?.currency,
            plannedExchangeRate = v.plannedCost?.exchangeRate?.toPlainString(),
            plannedBaseCostAmount = v.plannedCost?.baseCurrencyAmount?.toPlainString(),
            plannedProofPrivateUri = v.plannedProof?.privateUri,
            plannedProofDisplayName = v.plannedProof?.displayName,
            plannedProofMimeType = v.plannedProof?.mimeType,
        ) }
    }

    @Serializable
    private data class CostDto(
        val type: String, val amount: String, val currency: String, val exchangeRate: String, val status: String,
        val servicePartnerId: String?, val sourceInvoiceId: String?, val sourceId: String?, val milestoneId: String?,
        val legId: String?, val reference: String, val note: String,
    ) {
        fun toDomain() = LogisticsCostDraft(
            type = LogisticsCostType.valueOf(type),
            amount = amount,
            currency = currency,
            exchangeRate = exchangeRate,
            status = LogisticsCostStatus.valueOf(status),
            servicePartnerId = servicePartnerId,
            sourceInvoiceId = sourceInvoiceId,
            sourceId = sourceId,
            milestoneId = milestoneId,
            legId = legId,
            reference = reference,
            note = note,
        )
        companion object { fun from(v: LogisticsCostDraft) = CostDto(
            v.type.name, v.amount, v.currency, v.exchangeRate, v.status.name, v.servicePartnerId, v.sourceInvoiceId,
            v.sourceId, v.milestoneId, v.legId, v.reference, v.note,
        ) }
    }

    @Serializable
    private data class DocumentDto(
        val draftId: String = "",
        val sourceUri: String = "",
        val privateUri: String? = null,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long? = null,
        val sha256: String? = null,
        val type: String,
        val sourceInvoiceId: String? = null,
        val milestoneId: String? = null,
        val legId: String? = null,
        val isStaging: Boolean = false,
        val errorMessage: String? = null,
    ) {
        fun toDomain(): LogisticsPendingDocumentDraft {
            val recoveredId = draftId.ifBlank { "legacy-${sourceUri.hashCode().toUInt().toString(16)}" }
            val recoveredError = when {
                !privateUri.isNullOrBlank() -> errorMessage
                sourceUri.isNotBlank() && errorMessage.isNullOrBlank() -> "لم يكتمل تجهيز المستند. أعد المحاولة أو أزله."
                else -> errorMessage
            }
            return LogisticsPendingDocumentDraft(
                draftId = recoveredId,
                sourceUri = sourceUri,
                privateUri = privateUri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                type = LogisticsDocumentType.valueOf(type),
                sourceInvoiceId = sourceInvoiceId,
                milestoneId = milestoneId,
                legId = legId,
                isStaging = false,
                errorMessage = recoveredError,
            )
        }
        companion object {
            fun from(v: LogisticsPendingDocumentDraft) = DocumentDto(
                draftId = v.draftId,
                sourceUri = v.sourceUri,
                privateUri = v.privateUri,
                displayName = v.displayName,
                mimeType = v.mimeType,
                sizeBytes = v.sizeBytes,
                sha256 = v.sha256,
                type = v.type.name,
                sourceInvoiceId = v.sourceInvoiceId,
                milestoneId = v.milestoneId,
                legId = v.legId,
                isStaging = v.isStaging,
                errorMessage = v.errorMessage,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LogisticsDraftProgressModule {
    @Binds
    abstract fun bindLogisticsDraftProgressStore(implementation: LogisticsDraftProgressDataStore): LogisticsDraftProgressStore
}
