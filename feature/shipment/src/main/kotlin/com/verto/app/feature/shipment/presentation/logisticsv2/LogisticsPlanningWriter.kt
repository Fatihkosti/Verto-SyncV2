package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import javax.inject.Inject

internal class LogisticsPlanningWriter @Inject constructor(
    private val draftWriter: LogisticsDraftPlanningWriter,
    private val routeWriter: LogisticsRoutePlanningWriter,
    private val planningOrchestrator: LogisticsPlanningOrchestrator,
) {
    suspend fun openDraft(draftShipmentId: String?, requestId: String): OpenLogisticsShipmentDraftResult =
        draftWriter.openDraft(draftShipmentId, requestId)

    suspend fun saveHeader(draft: LogisticsShipmentHeaderDraft): LogisticsShipment = draftWriter.saveHeader(draft)

    suspend fun savePurchasePlan(draft: LogisticsPlanningDraft): LogisticsShipment = draftWriter.savePurchasePlan(draft)

    suspend fun approvePlanning(shipmentId: String, requestId: String): LogisticsShipment =
        routeWriter.approvePlanning(shipmentId, requestId)

    suspend fun saveRouteTemplate(template: LogisticsRouteTemplate): LogisticsRouteTemplate =
        routeWriter.saveRouteTemplate(template)

    suspend fun create(draft: CreateLogisticsShipmentDraft, requestId: String): LogisticsShipment =
        draftWriter.create(draft, requestId)

    suspend fun prepare(shipmentId: String, draft: LogisticsPlanningDraft, requestId: String): LogisticsShipment =
        routeWriter.prepare(shipmentId, draft, requestId)

    suspend fun saveRouteDraft(
        shipmentId: String,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
        transportIntent: LogisticsRouteTransportIntent,
        requestId: String,
    ): LogisticsShipment = routeWriter.saveRouteDraft(shipmentId, milestones, legs, transportIntent, requestId)

    suspend fun savePlanning(shipmentId: String, draft: LogisticsPlanningDraft, requestId: String): LogisticsShipment =
        planningOrchestrator(toWorkflowCommand(shipmentId, draft, requestId))

    suspend fun start(shipmentId: String, requestId: String): LogisticsShipment = routeWriter.start(shipmentId, requestId)

    private fun toWorkflowCommand(
        shipmentId: String,
        draft: LogisticsPlanningDraft,
        requestId: String,
    ): LogisticsPlanningWorkflowCommand {
        require(draft.pendingDocuments.all { it.isReady }) {
            "أكمل تجهيز المستندات أو أزل المستند الذي تعذر تجهيزه"
        }
        return LogisticsPlanningWorkflowCommand(
            shipmentId = shipmentId,
            core = LogisticsPlanningWorkflowCore(
                LogisticsAssigneeSnapshot(draft.assigneeId, draft.assigneeName), draft.sources, draft.lines,
            ),
            schedule = LogisticsPlanningWorkflowSchedule(
                draft.expectedDepartureAt, draft.expectedArrivalAt, draft.transportDetails,
            ),
            route = LogisticsPlanningWorkflowRoute(draft.milestones, draft.legs),
            artifacts = LogisticsPlanningWorkflowArtifacts(
                costs = draft.pendingCosts.map(::toWorkflowCost),
                documents = draft.pendingDocuments.map(::toWorkflowDocument),
            ),
            submission = LogisticsPlanningWorkflowSubmission(
                markReady = draft.submitAction == LogisticsPlanningSubmitAction.MARK_READY,
                requestId = requestId,
            ),
        )
    }

    private fun toWorkflowCost(cost: LogisticsCostDraft): LogisticsPlanningWorkflowCost =
        LogisticsPlanningWorkflowCost(
            money = LogisticsPlanningWorkflowCostMoney(
                cost.type,
                requireNotNull(cost.amountDecimal),
                cost.currency,
                requireNotNull(cost.exchangeRateDecimal),
                cost.status,
            ),
            scope = LogisticsPlanningWorkflowCostScope(
                servicePartnerId = cost.servicePartnerId,
                sourceInvoiceId = cost.sourceInvoiceId,
                sourceId = cost.sourceId,
                milestoneId = cost.milestoneId,
                legId = cost.legId,
            ),
            reference = cost.reference.ifBlank { null },
            note = cost.note,
        )

    private fun toWorkflowDocument(document: LogisticsPendingDocumentDraft): LogisticsPlanningWorkflowDocument =
        LogisticsPlanningWorkflowDocument(
            source = LogisticsPlanningWorkflowDocumentSource(
                requireNotNull(document.privateUri), document.displayName, document.mimeType, document.type,
            ),
            scope = LogisticsPlanningWorkflowDocumentScope(
                document.sourceInvoiceId, document.milestoneId, document.legId,
            ),
        )
}
