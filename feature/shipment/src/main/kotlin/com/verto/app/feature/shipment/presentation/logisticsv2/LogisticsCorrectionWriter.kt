package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import javax.inject.Inject

internal class LogisticsCorrectionWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val updateEtaUseCase: UpdateShipmentEtaUseCase,
    private val updateFutureLegUseCase: UpdateFutureShipmentLegUseCase,
    private val insertUnplannedMilestone: InsertUnplannedLogisticsMilestoneUseCase,
    private val correctActualTimeUseCase: CorrectLogisticsActualTimeUseCase,
    private val recordFollowUp: RecordLogisticsFollowUpUseCase,
    private val cancelShipment: CancelLogisticsShipmentUseCase
) {
    suspend fun updateEta(shipmentId: String, legId: String, plannedArrivalAt: Long?, requestId: String) {
        updateEtaUseCase(
            organizationId(),
            UpdateShipmentEtaCommand(
                shipmentId = shipmentId,
                legId = legId,
                plannedArrivalAt = plannedArrivalAt,
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
            ),
        )
    }

    suspend fun updateFutureLeg(shipmentId: String, leg: LogisticsShipmentLeg, reason: String, requestId: String) {
        val actor = readService.actor()
        updateFutureLegUseCase(
            organizationId(),
            UpdateFutureShipmentLegCommand(
                shipmentId = shipmentId,
                leg = leg,
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
                reason = reason.trim(),
                changedByEmployeeId = actor.id,
                changedByEmployeeName = actor.name,
            ),
        )
    }

    suspend fun insertUnplannedStation(
        shipmentId: String,
        draft: LogisticsUnplannedStationDraft,
        requestId: String,
    ) {
        require(draft.isValid) { "بيانات المحطة غير المخططة غير صحيحة" }
        insertUnplannedMilestone(
            organizationId(),
            InsertUnplannedLogisticsMilestoneCommand(
                shipmentId = shipmentId,
                location = LogisticsLocation(
                    countryCode = draft.location.countryCode,
                    countryNameSnapshot = draft.location.countryName.trim(),
                    city = draft.location.city.trim(),
                    placeName = draft.location.placeName.trim(),
                ),
                stationType = draft.stationType,
                reason = draft.reason.trim(),
                continuation = UnplannedContinuation(draft.continuationCarrierPartnerId, draft.continuationMode),
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
            ),
        )
    }

    suspend fun correctActualTime(
        shipmentId: String,
        draft: LogisticsActualTimeCorrectionDraft,
        requestId: String,
    ) {
        require(draft.isValid) { "بيانات تصحيح الوقت غير صحيحة" }
        correctActualTimeUseCase(
            organizationId(),
            CorrectLogisticsActualTimeCommand(
                shipmentId = shipmentId,
                milestoneId = draft.milestoneId,
                target = draft.target,
                correctedAt = draft.correctedAt,
                reason = draft.reason.trim(),
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
            ),
        )
    }

    suspend fun followUp(shipmentId: String, note: String, requestId: String) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
        val one = positions.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }.singleOrNull()
        val holderId = one?.holderId
        val partner = if (one?.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER && holderId != null) {
            readService.partner(organizationId, holderId)
        } else null
        recordFollowUp(
            organizationId,
            RecordLogisticsFollowUpCommand(
                shipmentId = shipmentId,
                partnerId = partner?.id,
                phone = partner?.phone,
                note = note,
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
            ),
        )
    }

    suspend fun cancel(shipmentId: String, reason: String, requestId: String) {
        cancelShipment(
            organizationId(),
            CancelLogisticsShipmentCommand(
                shipmentId = shipmentId,
                cancelledAt = System.currentTimeMillis(),
                requestId = requestId,
                reason = reason,
            ),
        )
    }

    private suspend fun organizationId(): String = readService.organizationId()
}
