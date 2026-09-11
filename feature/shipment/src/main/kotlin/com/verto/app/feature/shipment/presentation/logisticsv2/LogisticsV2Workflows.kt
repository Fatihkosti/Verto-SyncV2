package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.ResolveLogisticsInventoryIdentityUseCase
import com.verto.app.feature.shipment.domain.model.*
import javax.inject.Inject

/** UI-facing planning boundary; each dependency is itself a cohesive workflow component. */
internal class LogisticsPlanningWorkflow @Inject constructor(
    private val writer: LogisticsPlanningWriter,
    private val customs: LogisticsCustomsPlanningWriter,
    private val movement: LogisticsMovementPreparationWriter,
    private val inventoryIdentity: ResolveLogisticsInventoryIdentityUseCase,
    private val drafts: LogisticsDraftProgressStore,
) {
    suspend fun openDraft(shipmentId: String?, requestId: String) = writer.openDraft(shipmentId, requestId)
    suspend fun saveHeader(draft: LogisticsShipmentHeaderDraft) = writer.saveHeader(draft)
    suspend fun savePurchasePlan(draft: LogisticsPlanningDraft) = writer.savePurchasePlan(draft)
    suspend fun approvePlanning(shipmentId: String, requestId: String) = writer.approvePlanning(shipmentId, requestId)
    suspend fun savePlanning(shipmentId: String, draft: LogisticsPlanningDraft, requestId: String) = writer.savePlanning(shipmentId, draft, requestId)
    suspend fun saveRouteTemplate(template: LogisticsRouteTemplate) = writer.saveRouteTemplate(template)
    suspend fun saveRouteDraft(
        shipmentId: String,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
        transportIntent: LogisticsRouteTransportIntent,
        requestId: String,
    ) = writer.saveRouteDraft(shipmentId, milestones, legs, transportIntent, requestId)
    suspend fun start(shipmentId: String, requestId: String) = writer.start(shipmentId, requestId)
    suspend fun saveCustomsPlan(shipmentId: String, workspace: LogisticsRouteWorkspaceSnapshot) =
        customs.saveCustomsPlan(shipmentId, workspace)
    suspend fun deleteCustomsPlanDocument(shipmentId: String, document: LogisticsCustomsPlanDocument) =
        customs.deleteCustomsPlanDocument(shipmentId, document)
    suspend fun prepareMovement(shipmentId: String, draft: LogisticsMovementPreparationDraft, requestId: String) =
        movement.prepareMovement(shipmentId, draft, requestId)
    suspend fun bindExisting(invoiceItemId: String, inventoryItemId: String) = inventoryIdentity.bindExisting(invoiceItemId, inventoryItemId)
    suspend fun createNew(invoiceItemId: String) = inventoryIdentity.createNew(invoiceItemId)
    suspend fun catalog() = inventoryIdentity.catalog()
    suspend fun loadProgress() = drafts.loadProgress()
    suspend fun saveProgress(progress: LogisticsDraftProgress) = drafts.saveProgress(progress)
    suspend fun clearShipment(shipmentId: String) = drafts.clearShipment(shipmentId)
    suspend fun loadRouteWorkspace(shipmentId: String) = drafts.loadRouteWorkspace(shipmentId)
    suspend fun saveRouteWorkspace(snapshot: LogisticsRouteWorkspaceSnapshot) = drafts.saveRouteWorkspace(snapshot)
    suspend fun loadReceivingDraft(shipmentId: String) = drafts.loadReceivingDraft(shipmentId)
    suspend fun saveReceivingDraft(snapshot: LogisticsFinalReceivingDraftSnapshot) = drafts.saveReceivingDraft(snapshot)
}

/** UI-facing execution boundary for movement, custody, corrections and documents. */
internal class LogisticsExecutionWorkflow @Inject constructor(
    private val journey: LogisticsJourneyWriter,
    private val correction: LogisticsCorrectionWriter,
    private val documents: LogisticsPartnerDocumentWriter,
) {
    suspend fun sourceHandoffDraft(shipmentId: String, sourceId: String) = journey.sourceHandoffDraft(shipmentId, sourceId)
    suspend fun operationalHandoffDraft(shipmentId: String) = journey.operationalHandoffDraft(shipmentId)
    suspend fun handoffSourceToFirstCarrier(shipmentId: String, sourceId: String, draft: LogisticsCustodyHandoffDraft, requestId: String) =
        journey.handoffSourceToFirstCarrier(shipmentId, sourceId, draft, requestId)
    suspend fun recordOperationalHandoff(shipmentId: String, draft: LogisticsCustodyHandoffDraft, requestId: String) =
        journey.recordOperationalHandoff(shipmentId, draft, requestId)
    suspend fun customsPickupDraft(shipmentId: String) = journey.customsPickupDraft(shipmentId)
    suspend fun startCustoms(shipmentId: String, draft: LogisticsCustomsPickupDraft, requestId: String) =
        journey.startCustoms(shipmentId, draft, requestId)
    suspend fun completeCustoms(shipmentId: String, requestId: String) = journey.completeCustoms(shipmentId, requestId)
    suspend fun cargoRepackDraft(shipmentId: String) = journey.cargoRepackDraft(shipmentId)
    suspend fun recordCargoRepack(shipmentId: String, draft: LogisticsCargoRepackDraft, requestId: String) =
        journey.recordCargoRepack(shipmentId, draft, requestId)
    suspend fun recordOperationalArrival(shipmentId: String, requestId: String) = journey.recordOperationalArrival(shipmentId, requestId)
    suspend fun recordOperationalDeparture(shipmentId: String, requestId: String) = journey.recordOperationalDeparture(shipmentId, requestId)
    suspend fun updateMilestoneHandling(shipmentId: String, status: LogisticsMilestoneHandlingStatus, requestId: String) =
        journey.updateMilestoneHandling(shipmentId, status, requestId)
    suspend fun journey(shipmentId: String, action: LogisticsNextAction, requestId: String) = journey.journey(shipmentId, action, requestId)
    suspend fun updateEta(shipmentId: String, legId: String, plannedArrivalAt: Long?, requestId: String) =
        correction.updateEta(shipmentId, legId, plannedArrivalAt, requestId)
    suspend fun updateFutureLeg(shipmentId: String, leg: LogisticsShipmentLeg, reason: String, requestId: String) =
        correction.updateFutureLeg(shipmentId, leg, reason, requestId)
    suspend fun insertUnplannedStation(shipmentId: String, draft: LogisticsUnplannedStationDraft, requestId: String) =
        correction.insertUnplannedStation(shipmentId, draft, requestId)
    suspend fun correctActualTime(shipmentId: String, draft: LogisticsActualTimeCorrectionDraft, requestId: String) =
        correction.correctActualTime(shipmentId, draft, requestId)
    suspend fun followUp(shipmentId: String, note: String, requestId: String) = correction.followUp(shipmentId, note, requestId)
    suspend fun cancel(shipmentId: String, reason: String, requestId: String) = correction.cancel(shipmentId, reason, requestId)
    suspend fun saveDocument(request: LogisticsDocumentWriteRequest) = documents.saveDocument(request)
    suspend fun stageDocument(shipmentId: String, draftId: String, sourceUri: String, displayName: String, mimeType: String) =
        documents.stageDocument(shipmentId, draftId, sourceUri, displayName, mimeType)
    suspend fun removeStagedDocument(shipmentId: String, privateUri: String) = documents.removeStagedDocument(shipmentId, privateUri)
    suspend fun openStagedDocument(shipmentId: String, privateUri: String, mimeType: String) =
        documents.openStagedDocument(shipmentId, privateUri, mimeType)
    suspend fun deleteDocument(shipmentId: String, documentId: String) = documents.deleteDocument(shipmentId, documentId)
    suspend fun openDocument(shipmentId: String, documentId: String) = documents.openDocument(shipmentId, documentId)
    suspend fun saveAndLinkPartner(shipmentId: String, draft: LogisticsPartnerDraft) = documents.saveAndLinkPartner(shipmentId, draft)
}

/** UI-facing receiving/settlement boundary. */
internal class LogisticsReceivingSettlementWorkflow @Inject constructor(
    private val receiving: LogisticsReceivingWriter,
    private val settlement: LogisticsSettlementWriter,
) {
    suspend fun ensureReceivingStarted(shipmentId: String, requestId: String) = receiving.ensureReceivingStarted(shipmentId, requestId)
    suspend fun finalizeReceiving(shipmentId: String, draft: LogisticsReceivingDraft, requestId: String) =
        receiving.finalizeReceiving(shipmentId, draft, requestId)
    suspend fun recoverMissingGoods(shipmentId: String, draft: LogisticsRecoveryDraft, requestId: String) =
        receiving.recoverMissingGoods(shipmentId, draft, requestId)
    suspend fun settleShortage(shipmentId: String, draft: LogisticsShortageSettlementDraft, requestId: String) =
        receiving.settleShortage(shipmentId, draft, requestId)
    suspend fun addLateCost(shipmentId: String, draft: LogisticsCostDraft, requestId: String) = receiving.addLateCost(shipmentId, draft, requestId)
    suspend fun permanentlyDeleteShipment(shipmentId: String, shipmentNumber: String?, reason: String, requestId: String) =
        receiving.permanentlyDeleteShipment(shipmentId, shipmentNumber, reason, requestId)
    suspend fun addCost(shipmentId: String, draft: LogisticsCostDraft, requestId: String) = settlement.addCost(shipmentId, draft, requestId)
    suspend fun confirmCostPayment(shipmentId: String, costId: String, requestId: String) = settlement.confirmCostPayment(shipmentId, costId, requestId)
    suspend fun adjustPaidCost(shipmentId: String, costId: String, draft: LogisticsPaidCostEditDraft, requestId: String) =
        settlement.adjustPaidCost(shipmentId, costId, draft, requestId)
    suspend fun saveCostProof(shipmentId: String, costId: String, draft: LogisticsCostProofDraft, requestId: String) =
        settlement.saveCostProof(shipmentId, costId, draft, requestId)
    suspend fun settle(shipmentId: String, requestId: String) = settlement.settle(shipmentId, requestId)
    suspend fun close(shipmentId: String, requestId: String) = settlement.close(shipmentId, requestId)
}

/** Read-only bridge used by the presentation state coordinator. */
internal class LogisticsReadWorkflow @Inject constructor(
    private val presentation: LogisticsPresentationReadService,
    private val operational: LogisticsV2OperationalReads,
) {
    fun observeOrganizationId() = presentation.observeOrganizationId()
    fun observeUnified(organizationId: String) = presentation.observeUnified(organizationId)
    suspend fun aggregate(organizationId: String, shipmentId: String) = presentation.aggregate(organizationId, shipmentId)
    suspend fun aggregate(shipmentId: String) = presentation.aggregate(shipmentId)
    suspend fun activeEmployees() = presentation.activeEmployees()
    suspend fun countrySuggestions() = presentation.countrySuggestions()
    suspend fun organizationId() = presentation.organizationId()
    suspend fun partners(organizationId: String) = presentation.partners(organizationId)
    suspend fun routeTemplates(organizationId: String) = presentation.routeTemplates(organizationId)
    suspend fun detail(shipmentId: String) = operational.detail(shipmentId)
    suspend fun planning(shipmentId: String) = operational.planning(shipmentId)
    suspend fun receiving(shipmentId: String) = operational.receiving(shipmentId)
    suspend fun costs(shipmentId: String) = operational.costs(shipmentId)
    suspend fun operationalOrganizationId() = operational.organizationId()
    suspend fun operationalAggregate(shipmentId: String) = operational.aggregate(shipmentId)
}
