package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.ApproveLogisticsPlanUseCase
import com.verto.app.feature.shipment.application.ApproveLogisticsPlanCommand
import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.MarkLogisticsShipmentReadyUseCase
import com.verto.app.feature.shipment.application.PrepareLogisticsShipmentUseCase
import com.verto.app.feature.shipment.application.SaveDynamicRoutePlanUseCase
import com.verto.app.feature.shipment.application.SaveLogisticsRouteTemplateUseCase
import com.verto.app.feature.shipment.application.StartLogisticsShipmentUseCase
import com.verto.app.feature.shipment.domain.model.*
import java.util.UUID
import javax.inject.Inject

internal class LogisticsRoutePlanningWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val approvePlan: ApproveLogisticsPlanUseCase,
    private val prepareShipment: PrepareLogisticsShipmentUseCase,
    private val saveRoute: SaveDynamicRoutePlanUseCase,
    private val saveRouteTemplate: SaveLogisticsRouteTemplateUseCase,
    private val startShipment: StartLogisticsShipmentUseCase
) {
    suspend fun approvePlanning(shipmentId: String, requestId: String): LogisticsShipment {
        val actor = readService.actor()
        return approvePlan(
            organizationId(),
            ApproveLogisticsPlanCommand(
                shipmentId = shipmentId,
                requestId = stableRequestId("$requestId:plan-approval"),
                employeeId = actor.id,
                employeeName = actor.name,
            ),
        )
    }

    suspend fun saveRouteTemplate(template: LogisticsRouteTemplate): LogisticsRouteTemplate =
        saveRouteTemplate(organizationId(), template)

    suspend fun prepare(shipmentId: String, draft: LogisticsPlanningDraft, requestId: String): LogisticsShipment =
        prepareShipment(
            organizationId(),
            PrepareLogisticsShipmentCommand(
                shipmentId = shipmentId,
                assignee = LogisticsAssigneeSnapshot(draft.assigneeId, draft.assigneeName),
                sources = draft.sources,
                lines = draft.lines,
                expectedDepartureAt = draft.expectedDepartureAt,
                expectedArrivalAt = draft.expectedArrivalAt,
                transportDetails = draft.transportDetails,
                requestId = requestId,
            ),
        )

    suspend fun saveRouteDraft(
        shipmentId: String,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
        transportIntent: LogisticsRouteTransportIntent,
        requestId: String,
    ): LogisticsShipment {
        val organizationId = organizationId()
        saveRoute(
            organizationId,
            SaveShipmentRouteCommand(
                shipmentId = shipmentId,
                milestones = milestones.sortedBy { it.order },
                legs = legs.sortedBy { it.sequence }.map { it.asV230PlanningLeg(it.mode) },
                occurredAt = System.currentTimeMillis(),
                requestId = stableRequestId("$requestId:route-draft"),
                transportIntent = transportIntent,
            ),
        )
        return readService.aggregate(organizationId, shipmentId)?.shipment ?: error("لم يتم العثور على الشحنة")
    }

    suspend fun start(shipmentId: String, requestId: String): LogisticsShipment =
        startShipment(
            organizationId(),
            StartLogisticsShipmentCommand(shipmentId, System.currentTimeMillis(), requestId),
        )

    private fun stableRequestId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private suspend fun organizationId(): String = readService.organizationId()
}
