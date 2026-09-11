package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import java.util.UUID
import javax.inject.Inject

internal class LogisticsJourneyWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val startMovement: StartLogisticsMovementUseCase,
    private val recordMilestoneArrival: RecordLogisticsMilestoneArrivalUseCase,
    private val recordMilestoneDeparture: RecordLogisticsMilestoneDepartureUseCase,
    private val updateHandling: UpdateMilestoneHandlingUseCase,
    private val custodyWriter: LogisticsCustodyWriter,
    private val customsCargoWriter: LogisticsCustomsCargoWriter
) {
    suspend fun journey(shipmentId: String, action: LogisticsNextAction, requestId: String) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        when (action) {
            LogisticsNextAction.START_MOVEMENT ->
                startMovement(organizationId, StartLogisticsMovementCommand(shipmentId, System.currentTimeMillis(), requestId))
            LogisticsNextAction.RECORD_CUSTOMS_ARRIVAL ->
                recordCustomsArrival(organizationId, shipmentId, aggregate, requestId)
            LogisticsNextAction.RECORD_CUSTOMS_DEPARTURE ->
                recordCustomsDeparture(organizationId, shipmentId, aggregate, requestId)
            LogisticsNextAction.RECORD_MILESTONE_ARRIVAL ->
                recordStationArrival(organizationId, shipmentId, aggregate, requestId)
            LogisticsNextAction.RECORD_MILESTONE_DEPARTURE ->
                recordStationDeparture(organizationId, shipmentId, aggregate, requestId)
            LogisticsNextAction.RECORD_DESTINATION_ARRIVAL ->
                recordDestinationArrival(organizationId, shipmentId, aggregate, requestId)
            else -> error("الإجراء لا يخص رحلة الشحنة")
        }
    }

    private suspend fun recordCustomsArrival(organizationId: String, shipmentId: String, aggregate: LogisticsShipmentAggregate, requestId: String) {
        val milestone = aggregate.milestones.sortedBy { it.order }.firstOrNull {
            it.type == LogisticsMilestoneType.CUSTOMS && it.arrivedAt == null
        } ?: error("لا توجد محطة جمارك تنتظر الوصول")
        recordMilestoneArrival(organizationId, RecordLogisticsMilestoneArrivalCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId))
    }

    private suspend fun recordCustomsDeparture(organizationId: String, shipmentId: String, aggregate: LogisticsShipmentAggregate, requestId: String) {
        val milestone = aggregate.milestones.sortedBy { it.order }.firstOrNull {
            it.type == LogisticsMilestoneType.CUSTOMS && it.arrivedAt != null && it.departedAt == null
        } ?: error("لا توجد محطة جمارك تنتظر المغادرة")
        recordMilestoneDeparture(organizationId, RecordLogisticsMilestoneDepartureCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId))
    }

    private suspend fun recordStationArrival(organizationId: String, shipmentId: String, aggregate: LogisticsShipmentAggregate, requestId: String) {
        val milestone = aggregate.milestones.sortedBy { it.order }.firstOrNull {
            it.type != LogisticsMilestoneType.DESTINATION && it.arrivedAt == null
        } ?: error("لا توجد محطة تنتظر الوصول")
        recordMilestoneArrival(organizationId, RecordLogisticsMilestoneArrivalCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId))
    }

    private suspend fun recordStationDeparture(organizationId: String, shipmentId: String, aggregate: LogisticsShipmentAggregate, requestId: String) {
        val milestone = aggregate.milestones.sortedBy { it.order }.firstOrNull {
            it.type != LogisticsMilestoneType.DESTINATION && it.arrivedAt != null && it.departedAt == null
        } ?: error("لا توجد محطة تنتظر المغادرة")
        recordMilestoneDeparture(organizationId, RecordLogisticsMilestoneDepartureCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId))
    }

    private suspend fun recordDestinationArrival(organizationId: String, shipmentId: String, aggregate: LogisticsShipmentAggregate, requestId: String) {
        val milestone = aggregate.milestones.singleOrNull { it.type == LogisticsMilestoneType.DESTINATION }
            ?: error("محطة الوجهة غير موجودة")
        recordMilestoneArrival(organizationId, RecordLogisticsMilestoneArrivalCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId))
    }

    suspend fun recordOperationalArrival(shipmentId: String, requestId: String) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val activeLeg = aggregate.legs.singleOrNull { it.status == LogisticsLegStatus.IN_TRANSIT }
            ?: error("لا توجد مرحلة نقل نشطة")
        val milestone = aggregate.milestones.singleOrNull { it.id == activeLeg.toMilestoneId }
            ?: error("محطة الوصول غير موجودة")
        recordMilestoneArrival(
            organizationId,
            RecordLogisticsMilestoneArrivalCommand(shipmentId, milestone.id, System.currentTimeMillis(), requestId),
        )
    }

    suspend fun updateMilestoneHandling(
        shipmentId: String,
        target: LogisticsMilestoneHandlingStatus,
        requestId: String,
    ) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val milestone = aggregate.milestones.sortedByDescending { it.order }.firstOrNull {
            it.arrivedAt != null && it.departedAt == null
        } ?: error("لا توجد محطة تشغيلية حالية")
        updateHandling(
            organizationId,
            UpdateMilestoneHandlingCommand(
                shipmentId = shipmentId,
                milestoneId = milestone.id,
                targetStatus = target,
                occurredAt = System.currentTimeMillis(),
                requestId = requestId,
            ),
        )
    }

    suspend fun recordOperationalDeparture(shipmentId: String, requestId: String) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val milestone = aggregate.milestones.sortedByDescending { it.order }.firstOrNull {
            it.handlingStatus == LogisticsMilestoneHandlingStatus.LOADED && it.departedAt == null
        } ?: error("لا توجد محطة جاهزة للمغادرة")
        startMovement(
            organizationId,
            StartLogisticsMovementCommand(shipmentId, System.currentTimeMillis(), requestId),
        )
    }

    suspend fun sourceHandoffDraft(shipmentId: String, sourceId: String) = custodyWriter.sourceHandoffDraft(shipmentId, sourceId)
    suspend fun handoffSourceToFirstCarrier(shipmentId: String, sourceId: String, draft: LogisticsCustodyHandoffDraft, requestId: String) =
        custodyWriter.handoffSourceToFirstCarrier(shipmentId, sourceId, draft, requestId)
    suspend fun operationalHandoffDraft(shipmentId: String) = custodyWriter.operationalHandoffDraft(shipmentId)
    suspend fun recordOperationalHandoff(shipmentId: String, draft: LogisticsCustodyHandoffDraft, requestId: String) =
        custodyWriter.recordOperationalHandoff(shipmentId, draft, requestId)
    suspend fun customsPickupDraft(shipmentId: String) = customsCargoWriter.customsPickupDraft(shipmentId)
    suspend fun startCustoms(shipmentId: String, draft: LogisticsCustomsPickupDraft, requestId: String) =
        customsCargoWriter.startCustoms(shipmentId, draft, requestId)
    suspend fun completeCustoms(shipmentId: String, requestId: String) = customsCargoWriter.completeCustoms(shipmentId, requestId)
    suspend fun cargoRepackDraft(shipmentId: String) = customsCargoWriter.cargoRepackDraft(shipmentId)
    suspend fun recordCargoRepack(shipmentId: String, draft: LogisticsCargoRepackDraft, requestId: String) =
        customsCargoWriter.recordCargoRepack(shipmentId, draft, requestId)

    private fun stableRequestId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private suspend fun organizationId(): String = readService.organizationId()
}
