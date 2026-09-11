package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncAttachmentTransferEntity
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.local.entity.LogisticsAssignmentEntity
import com.verto.app.data.local.entity.LogisticsCustodyHandoffEntity
import com.verto.app.data.local.entity.LogisticsCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsCostEntity
import com.verto.app.data.local.entity.LogisticsCustomsPlanEntity
import com.verto.app.data.local.entity.LogisticsCustomsPlanDocumentEntity
import com.verto.app.data.local.entity.LogisticsPlanRevisionEntity
import com.verto.app.data.local.entity.LogisticsPlanRevisionChangeEntity
import com.verto.app.data.local.entity.LogisticsDocumentEntity
import com.verto.app.data.local.entity.LogisticsEventEntity
import com.verto.app.data.local.entity.LogisticsMilestoneEntity
import com.verto.app.data.local.entity.LogisticsPaymentEntity
import com.verto.app.data.local.entity.LogisticsRouteTemplateEntity
import com.verto.app.data.local.entity.LogisticsRouteTemplateStopEntity
import com.verto.app.data.local.entity.LogisticsReceivingLineEntity
import com.verto.app.data.local.entity.LogisticsShipmentEntity
import com.verto.app.data.local.entity.LogisticsShipmentLegEntity
import com.verto.app.data.local.entity.LogisticsShipmentPartnerLinkEntity
import com.verto.app.data.local.entity.LogisticsShipmentLineEntity
import com.verto.app.data.local.entity.LogisticsShipmentSourceEntity
import com.verto.app.data.local.entity.LogisticsTransportDetailsEntity
import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan
import com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlanDocument
import com.verto.app.feature.shipment.domain.model.LogisticsLocation
import com.verto.app.feature.shipment.domain.model.LogisticsPlanChangeScope
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevision
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionChange
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionKind
import com.verto.app.feature.shipment.domain.model.LogisticsV234Contract
import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPayment
import com.verto.app.feature.shipment.domain.model.LogisticsPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingBatch
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplateStop
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsRecovery
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryLine
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentPartnerLink
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlement
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAdjustment
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsTransportDetails
import com.verto.app.feature.shipment.domain.model.LogisticsTransportMode
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App-owned bridge for the inactive Logistics V2 vertical slice.
 * It intentionally has no navigation, sync, inventory write, or legacy shipment behavior.
 */
class RoomLogisticsV2ShipmentStoreAdapter @Inject constructor(
    private val database: AppDatabase,
    private val unifiedOutboxWriter: UnifiedOutboxWriter,
) : LogisticsShipmentStorePort {
    private val core = LogisticsShipmentStoreCoreAdapter(database)
    private val journey = LogisticsJourneyStoreAdapter(database)
    private val customs = LogisticsCustomsStoreAdapter(database)
    private val receiving = LogisticsReceivingStoreAdapter(database)
    private val settlement = LogisticsV232SettlementStoreAdapter(database)
    private val cost = LogisticsCostStoreAdapter(database)
    private val partner = LogisticsPartnerStoreAdapter(database)
    private val document = LogisticsDocumentStoreAdapter(database)
    private val routeTemplate = LogisticsRouteTemplateStoreAdapter(database)
    private val planningContract = LogisticsV234PlanningStoreAdapter(database)

    override fun observeShipments(organizationId: String): Flow<List<LogisticsShipment>> = core.observeShipments(organizationId)
    override suspend fun getShipment(organizationId: String, shipmentId: String): LogisticsShipmentAggregate? = core.getShipment(organizationId, shipmentId)
    override suspend fun shipmentNumberExists(organizationId: String, shipmentNumber: String): Boolean = core.shipmentNumberExists(organizationId, shipmentNumber)
    override suspend fun isRequestProcessed(organizationId: String, requestId: String): Boolean = core.isRequestProcessed(organizationId, requestId)
    override suspend fun getPartner(organizationId: String, partnerId: String): LogisticsPartner? = partner.getPartner(organizationId, partnerId)
    override suspend fun listPartners(organizationId: String): List<LogisticsPartner> = partner.listPartners(organizationId)
    override suspend fun listRouteTemplates(organizationId: String): List<LogisticsRouteTemplate> = routeTemplate.list(organizationId)
    override suspend fun getRouteTemplate(organizationId: String, templateId: String): LogisticsRouteTemplate? = routeTemplate.get(organizationId, templateId)
    override suspend fun upsertRouteTemplate(template: LogisticsRouteTemplate) =
        captured(template.organizationId, "route-template:${template.id}", "UPSERT_ROUTE_TEMPLATE", template.toString()) {
            routeTemplate.upsert(template)
        }
    override suspend fun hasInventoryPosting(organizationId: String, shipmentId: String): Boolean = core.hasInventoryPosting(organizationId, shipmentId)
    override suspend fun getCustomsPlan(organizationId: String, shipmentId: String): LogisticsCustomsPlan? =
        planningContract.getCustomsPlan(organizationId, shipmentId)
    override suspend fun saveCustomsPlan(plan: LogisticsCustomsPlan, documents: List<LogisticsCustomsPlanDocument>) =
        captured(plan.organizationId, plan.shipmentId, "SAVE_CUSTOMS_PLAN", plan.toString()) {
            planningContract.saveCustomsPlan(plan, documents)
        }
    override suspend fun getPlanRevisions(organizationId: String, shipmentId: String): List<LogisticsPlanRevision> =
        planningContract.getPlanRevisions(organizationId, shipmentId)
    override suspend fun appendPlanRevision(updatedShipment: LogisticsShipment, revision: LogisticsPlanRevision) =
        captured(updatedShipment.organizationId, updatedShipment.id, "APPEND_PLAN_REVISION", revision.toString()) {
            planningContract.appendPlanRevision(updatedShipment, revision)
        }
    override suspend fun createShipment(shipment: LogisticsShipment, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "CREATE", event.requestId.ifBlank { event.id }) {
            core.createShipment(shipment, event)
        }

    override suspend fun saveShipmentHeader(shipment: LogisticsShipment, assignment: LogisticsAssignment?) =
        captured(shipment.organizationId, shipment.id, "SAVE_HEADER", shipment.toString() + assignment.toString()) {
            journey.saveShipmentHeader(shipment, assignment)
        }
    override suspend fun savePurchasePlan(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
    ) = captured(shipment.organizationId, shipment.id, "SAVE_PURCHASE_PLAN", shipment.toString() + sources.toString() + lines.toString()) {
        journey.savePurchasePlan(shipment, sources, lines)
    }

    override suspend fun savePlanning(shipment: LogisticsShipment, sources: List<LogisticsShipmentSource>, lines: List<LogisticsShipmentLine>, assignment: LogisticsAssignment, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "SAVE_PLANNING", event.requestId.ifBlank { event.id }) {
            journey.savePlanning(shipment, sources, lines, assignment, event)
        }
    override suspend fun saveShipmentState(shipment: LogisticsShipment, event: LogisticsEvent) =
        captured(
            shipment.organizationId, shipment.id, "SAVE_STATE", event.requestId.ifBlank { event.id },
            if (shipment.state == LogisticsShipmentState.CANCELLED) "CANCEL" else "COMMAND",
        ) { journey.saveShipmentState(shipment, event) }
    override suspend fun changeAssignment(shipment: LogisticsShipment, endedAssignmentId: String?, assignment: LogisticsAssignment, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "CHANGE_ASSIGNMENT", event.requestId.ifBlank { event.id }) {
            journey.changeAssignment(shipment, endedAssignmentId, assignment, event)
        }
    override suspend fun saveMilestone(shipment: LogisticsShipment, milestone: LogisticsMilestone, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "SAVE_MILESTONE", event.requestId.ifBlank { event.id }) { journey.saveMilestone(shipment, milestone, event) }
    override suspend fun replacePlannedRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) = captured(shipment.organizationId, shipment.id, "REPLACE_ROUTE", shipment.toString() + milestones.toString() + legs.toString()) {
        journey.replacePlannedRoute(shipment, milestones, legs)
    }
    override suspend fun saveRoute(shipment: LogisticsShipment, milestones: List<LogisticsMilestone>, legs: List<LogisticsShipmentLeg>, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "SAVE_ROUTE", event.requestId.ifBlank { event.id }) { journey.saveRoute(shipment, milestones, legs, event) }
    override suspend fun correctMilestone(shipment: LogisticsShipment, milestone: LogisticsMilestone, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "CORRECT_MILESTONE", event.requestId.ifBlank { event.id }) { journey.correctMilestone(shipment, milestone, event) }
    override suspend fun correctLeg(shipment: LogisticsShipment, leg: LogisticsShipmentLeg, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "CORRECT_LEG", event.requestId.ifBlank { event.id }) { journey.correctLeg(shipment, leg, event) }
    override suspend fun saveCustodyHandoff(handoff: LogisticsCustodyHandoff, event: LogisticsEvent) =
        captured(handoff.organizationId, handoff.shipmentId, "CUSTODY_HANDOFF", event.requestId.ifBlank { event.id }) { journey.saveCustodyHandoff(handoff, event) }
    override suspend fun saveCustomsTransition(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        handoff: LogisticsCustodyHandoff?,
        event: LogisticsEvent,
    ) = captured(shipment.organizationId, shipment.id, "CUSTOMS_TRANSITION", event.requestId.ifBlank { event.id }) {
        customs.saveCustomsTransition(shipment, milestone, handoff, event)
    }
    override suspend fun getLatestConfirmedCargoSnapshot(organizationId: String, shipmentId: String): LogisticsCargoSnapshot? =
        journey.getLatestConfirmedCargoSnapshot(organizationId, shipmentId)
    override suspend fun saveOperationalUpdate(shipment: LogisticsShipment, milestones: List<LogisticsMilestone>, legs: List<LogisticsShipmentLeg>, event: LogisticsEvent) =
        captured(shipment.organizationId, shipment.id, "OPERATIONAL_UPDATE", event.requestId.ifBlank { event.id }) { journey.saveOperationalUpdate(shipment, milestones, legs, event) }
    override suspend fun saveFuturePlanLegRevision(
        updatedShipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        leg: LogisticsShipmentLeg,
        event: LogisticsEvent,
        revision: LogisticsPlanRevision,
    ) = database.withTransaction {
        require(updatedShipment.currentPlanRevision == revision.revisionNumber) { "Revision/shipment version mismatch" }
        val preRevisionShipment = updatedShipment.copy(currentPlanRevision = revision.revisionNumber - 1)
        journey.saveOperationalUpdate(preRevisionShipment, milestones, listOf(leg), event)
        planningContract.appendPlanRevision(updatedShipment, revision)
        enqueueShipmentMutation(updatedShipment.organizationId, updatedShipment.id, "FUTURE_PLAN_REVISION", event.requestId.ifBlank { event.id })
    }
    override suspend fun appendEvent(event: LogisticsEvent) =
        captured(event.organizationId, event.shipmentId, "APPEND_EVENT", event.requestId.ifBlank { event.id }) { core.appendEvent(event) }
    override suspend fun upsertPartner(partner: LogisticsPartner) =
        captured(partner.organizationId, "partner:${partner.id}", "UPSERT_PARTNER", partner.toString()) {
            this.partner.upsertPartner(partner)
        }
    override suspend fun linkPartner(organizationId: String, link: LogisticsShipmentPartnerLink) =
        captured(organizationId, link.shipmentId, "LINK_PARTNER", link.toString()) { partner.linkPartner(organizationId, link) }
    override suspend fun saveDocument(document: LogisticsDocument, event: LogisticsEvent) =
        captured(document.organizationId, document.shipmentId, "SAVE_DOCUMENT", event.requestId.ifBlank { event.id }) {
            this.document.saveDocument(document, event)
            val transferId = stableMutationId(document.organizationId, document.shipmentId, "ATTACHMENT:${document.id}:${document.sha256}")
            unifiedOutboxWriter.enqueueAttachmentIntent(
                SyncAttachmentTransferEntity(
                    transferId = transferId,
                    organizationId = document.organizationId,
                    mutationId = null,
                    aggregateType = "SHIPMENT",
                    aggregateId = document.shipmentId,
                    localUri = document.privateUri,
                    objectKey = "organizations/${document.organizationId}/shipments/${document.shipmentId}/documents/${document.id}",
                    contentChecksum = document.sha256,
                    mimeType = document.mimeType,
                    byteSize = document.sizeBytes,
                    state = "PENDING",
                    createdAt = document.createdAt,
                )
            )
        }
    override suspend fun deleteDocument(organizationId: String, shipmentId: String, documentId: String): Boolean =
        captured(organizationId, shipmentId, "DELETE_DOCUMENT", documentId) { document.deleteDocument(organizationId, shipmentId, documentId) }
    override suspend fun saveCost(cost: LogisticsCost, event: LogisticsEvent) =
        captured(cost.organizationId, cost.shipmentId, "SAVE_COST", event.requestId.ifBlank { event.id }) { this.cost.saveCost(cost, event) }
    override suspend fun getCosts(organizationId: String, shipmentId: String): List<LogisticsCost> = cost.getCosts(organizationId, shipmentId)
    override suspend fun findCostByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsCost? =
        cost.findCostByRequest(organizationId, shipmentId, requestId)
    override suspend fun savePayment(payment: LogisticsPayment, event: LogisticsEvent) =
        captured(payment.organizationId, payment.shipmentId, "SAVE_PAYMENT", event.requestId.ifBlank { event.id }) { core.savePayment(payment, event) }
    override suspend fun getPayments(organizationId: String, shipmentId: String): List<LogisticsPayment> =
        core.getPayments(organizationId, shipmentId)
    override suspend fun findPaymentByRequest(organizationId: String, requestId: String): LogisticsPayment? =
        core.findPaymentByRequest(organizationId, requestId)
    override suspend fun saveReceivingBatch(batch: LogisticsReceivingBatch, updatedShipment: LogisticsShipment, event: LogisticsEvent) =
        captured(updatedShipment.organizationId, updatedShipment.id, "SAVE_RECEIVING_BATCH", event.requestId.ifBlank { event.id }) { receiving.saveReceivingBatch(batch, updatedShipment, event) }
    override suspend fun upsertShortage(shortage: LogisticsShortage): LogisticsShortage =
        captured(shortage.identity.organizationId, shortage.identity.shipmentId, "UPSERT_SHORTAGE", shortage.toString()) { receiving.upsertShortage(shortage) }
    override suspend fun getShortages(organizationId: String, shipmentId: String): List<LogisticsShortage> =
        receiving.getShortages(organizationId, shipmentId)
    override suspend fun findShortageByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsShortage? =
        receiving.findShortageByRequest(organizationId, shipmentId, requestId)
    override suspend fun saveShortageSettlement(
        settlement: LogisticsShortageSettlement,
        updatedShortage: LogisticsShortage,
        event: LogisticsEvent,
    ): LogisticsShortageSettlement = captured(settlement.organizationId, settlement.shipmentId, "SHORTAGE_SETTLEMENT", event.requestId.ifBlank { event.id }) {
        this.settlement.save(settlement, updatedShortage, event)
    }
    override suspend fun getShortageSettlements(organizationId: String, shipmentId: String): List<LogisticsShortageSettlement> =
        settlement.list(organizationId, shipmentId)
    override suspend fun findShortageSettlementByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsShortageSettlement? =
        settlement.findByRequest(organizationId, shipmentId, requestId)
    override suspend fun createRecovery(recovery: LogisticsRecovery): LogisticsRecovery =
        captured(recovery.organizationId, recovery.shipmentId, "CREATE_RECOVERY", recovery.toString()) { receiving.createRecovery(recovery) }
    override suspend fun getRecoveries(organizationId: String, shipmentId: String): List<LogisticsRecovery> =
        receiving.getRecoveries(organizationId, shipmentId)
    override suspend fun findRecoveryByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsRecovery? =
        receiving.findRecoveryByRequest(organizationId, shipmentId, requestId)
    override suspend fun createRecoveryLines(
        organizationId: String,
        shipmentId: String,
        lines: List<LogisticsRecoveryLine>,
    ): List<LogisticsRecoveryLine> = captured(organizationId, shipmentId, "CREATE_RECOVERY_LINES", lines.toString()) {
        receiving.createRecoveryLines(organizationId, shipmentId, lines)
    }
    override suspend fun getRecoveryLines(organizationId: String, recoveryId: String): List<LogisticsRecoveryLine> =
        receiving.getRecoveryLines(organizationId, recoveryId)
    override suspend fun saveRecoveryPosting(posting: LogisticsRecoveryPosting): LogisticsRecoveryPosting =
        captured(posting.organizationId, posting.shipmentId, "SAVE_RECOVERY_POSTING", posting.toString()) { receiving.saveRecoveryPosting(posting) }
    override suspend fun getRecoveryPosting(organizationId: String, recoveryLineId: String): LogisticsRecoveryPosting? =
        receiving.getRecoveryPosting(organizationId, recoveryLineId)
    override suspend fun saveCostAllocations(organizationId: String, shipmentId: String, allocations: List<LogisticsCostAllocation>, event: LogisticsEvent) =
        captured(organizationId, shipmentId, "SAVE_COST_ALLOCATIONS", event.requestId.ifBlank { event.id }) { cost.saveCostAllocations(organizationId, shipmentId, allocations, event) }
    override suspend fun saveLateCostAdjustment(
        adjustment: LogisticsLateCostAdjustment,
        allocations: List<LogisticsLateCostAllocation>,
        event: LogisticsEvent,
    ): LogisticsLateCostAdjustment = captured(adjustment.organizationId, adjustment.shipmentId, "LATE_COST_ADJUSTMENT", event.requestId.ifBlank { event.id }) {
        cost.saveLateCostAdjustment(adjustment, allocations, event)
    }
    override suspend fun getLateCostAdjustments(organizationId: String, shipmentId: String): List<LogisticsLateCostAdjustment> =
        cost.getLateCostAdjustments(organizationId, shipmentId)
    override suspend fun getLateCostAllocations(organizationId: String, shipmentId: String): List<LogisticsLateCostAllocation> =
        cost.getLateCostAllocations(organizationId, shipmentId)
    override suspend fun findLateCostAdjustmentByRequest(organizationId: String, shipmentId: String, requestId: String): LogisticsLateCostAdjustment? =
        cost.findLateCostAdjustmentByRequest(organizationId, shipmentId, requestId)

    private suspend fun <T> captured(
        organizationId: String,
        shipmentId: String,
        command: String,
        semanticKey: String,
        operationType: String = "COMMAND",
        block: suspend () -> T,
    ): T = database.withTransaction {
        val result = block()
        enqueueShipmentMutation(organizationId, shipmentId, command, semanticKey, operationType)
        result
    }

    private suspend fun enqueueShipmentMutation(
        organizationId: String,
        shipmentId: String,
        command: String,
        semanticKey: String,
        operationType: String = "COMMAND",
    ) {
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        unifiedOutboxWriter.enqueue(
            organizationId = organizationId,
            aggregateType = "SHIPMENT",
            aggregateId = shipmentId,
            operationType = operationType,
            mutationId = stableMutationId(organizationId, shipmentId, "$command|$semanticKey"),
            payload = mapOf("command" to command, "shipmentId" to shipmentId),
        )
    }

    private fun stableMutationId(organizationId: String, shipmentId: String, key: String): String =
        UUID.nameUUIDFromBytes("v307|SHIPMENT|$organizationId|$shipmentId|$key".toByteArray(StandardCharsets.UTF_8)).toString()
}


internal class LogisticsV234PlanningStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()

    suspend fun getCustomsPlan(organizationId: String, shipmentId: String): LogisticsCustomsPlan? =
        dao.getCustomsPlan(organizationId, shipmentId)?.toDomainV234()

    suspend fun saveCustomsPlan(
        plan: LogisticsCustomsPlan,
        documents: List<LogisticsCustomsPlanDocument>,
    ) {
        val aggregate = requireNotNull(dao.getShipment(plan.organizationId, plan.shipmentId)) { "Shipment not found" }
        require(aggregate.state == LogisticsShipmentState.DRAFT.name || aggregate.currentPlanRevision > 0) {
            "Customs planning requires a draft or an approved plan"
        }
        val stations = dao.getMilestones(plan.organizationId, plan.shipmentId).map { it.toDomainV2() }
        LogisticsV234Contract.requireCustomsPlan(plan, stations)
        require(documents.all {
            it.organizationId == plan.organizationId && it.shipmentId == plan.shipmentId && it.customsPlanId == plan.id
        }) { "Customs planning document belongs to another plan" }

        database.withTransaction {
            val current = dao.getCustomsPlan(plan.organizationId, plan.shipmentId)
            if (current == null) {
                dao.insertCustomsPlan(plan.toEntityV234())
            } else {
                require(current.id == plan.id) { "Customs plan identity cannot change silently" }
                require(dao.updateCustomsPlan(plan.toEntityV234()) == 1) { "Customs plan update failed" }
            }
            val currentDocs = dao.getCustomsPlanDocuments(plan.organizationId, plan.shipmentId)
            val incomingIds = documents.mapTo(mutableSetOf()) { it.id }
            currentDocs.filterNot { it.id in incomingIds }.forEach { document ->
                require(dao.deleteCustomsPlanDocument(plan.organizationId, plan.shipmentId, document.id) == 1) {
                    "Customs planning document changed before commit"
                }
            }
            val existingIds = currentDocs.mapTo(mutableSetOf()) { it.id }
            val inserts = documents.filterNot { it.id in existingIds }.map { it.toEntityV234() }
            if (inserts.isNotEmpty()) dao.insertCustomsPlanDocuments(inserts)
            require(documents.filter { it.id in existingIds }.all { incoming ->
                val persisted = currentDocs.single { it.id == incoming.id }
                persisted.toDomainV234() == incoming
            }) { "Existing customs planning documents are immutable; replace the document instead" }
        }
    }

    suspend fun getPlanRevisions(organizationId: String, shipmentId: String): List<LogisticsPlanRevision> =
        dao.getPlanRevisions(organizationId, shipmentId).map { revision ->
            revision.toDomainV234(
                dao.getPlanRevisionChanges(organizationId, revision.id).map { it.toDomainV234() },
            )
        }

    suspend fun appendPlanRevision(updatedShipment: LogisticsShipment, revision: LogisticsPlanRevision) {
        requireSameTenant(updatedShipment.organizationId, revision.organizationId)
        require(updatedShipment.id == revision.shipmentId) { "Plan revision belongs to another shipment" }
        database.withTransaction {
            val current = dao.getShipment(updatedShipment.organizationId, updatedShipment.id)
                ?: error("Shipment not found")
            LogisticsV234Contract.requireRevision(revision, current.currentPlanRevision)
            LogisticsV234Contract.requireRevisionShipmentTransition(current.toDomainV2(), updatedShipment, revision)
            require(current.state != LogisticsShipmentState.CLOSED.name && current.state != LogisticsShipmentState.CANCELLED.name) {
                "Closed/cancelled shipment plan cannot be revised"
            }
            dao.insertPlanRevision(revision.toEntityV234())
            val changes = revision.changes.map {
                it.toEntityV234(updatedShipment.organizationId, updatedShipment.id, revision.id)
            }
            if (changes.isNotEmpty()) dao.insertPlanRevisionChanges(changes)
            require(dao.updateShipment(updatedShipment.toEntityV2()) == 1) { "Shipment plan revision update failed" }
        }
    }
}

internal class LogisticsRouteTemplateStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()

    suspend fun list(organizationId: String): List<LogisticsRouteTemplate> =
        dao.getRouteTemplates(organizationId).map { entity ->
            entity.toDomainV2(dao.getRouteTemplateStops(organizationId, entity.id))
        }

    suspend fun get(organizationId: String, templateId: String): LogisticsRouteTemplate? {
        val entity = dao.getRouteTemplate(organizationId, templateId) ?: return null
        return entity.toDomainV2(dao.getRouteTemplateStops(organizationId, templateId))
    }

    suspend fun upsert(template: LogisticsRouteTemplate) {
        require(template.organizationId.isNotBlank()) { "organizationId is required" }
        require(template.id.isNotBlank() && template.name.isNotBlank()) { "Template id/name are required" }
        require(template.stops.size >= 2) { "Route template requires at least origin and destination" }
        require(template.stops.sortedBy { it.order }.map { it.order } == template.stops.indices.toList()) {
            "Route template stop order must be contiguous"
        }
        require(template.transportPlanKind != LogisticsRouteTransportPlanKind.UNIFIED || template.unifiedTransportMode != null) {
            "Unified route template requires a transport mode"
        }
        require(template.transportPlanKind != LogisticsRouteTransportPlanKind.MIXED || template.unifiedTransportMode == null) {
            "Mixed route template must not persist a unified transport mode"
        }
        database.withTransaction {
            val entity = template.toEntityV2()
            if (dao.getRouteTemplate(template.organizationId, template.id) == null) dao.insertRouteTemplate(entity)
            else require(dao.updateRouteTemplate(entity) == 1) { "Route template update failed" }
            dao.deleteRouteTemplateStops(template.organizationId, template.id)
            dao.insertRouteTemplateStops(template.stops.map { it.toEntityV2(template.organizationId) })
        }
    }
}

internal fun LogisticsRouteTemplateEntity.toDomainV2(stops: List<LogisticsRouteTemplateStopEntity>) = LogisticsRouteTemplate(
    id = id, organizationId = organizationId, name = name,
    originCountryCode = originCountryCode, originCity = originCity,
    destinationCountryCode = destinationCountryCode, destinationCity = destinationCity,
    transportPlanKind = LogisticsRouteTransportPlanKind.valueOf(transportPlanKind),
    unifiedTransportMode = unifiedTransportMode?.let(LogisticsLegTransportMode::valueOf),
    customsStopOrder = customsStopOrder, expectedCustomsMinutes = expectedCustomsMinutes,
    createdAt = createdAt, updatedAt = updatedAt,
    stops = stops.map { it.toDomainV2() },
)

internal fun LogisticsRouteTemplateStopEntity.toDomainV2() = LogisticsRouteTemplateStop(
    id = id, templateId = templateId, order = stopOrder, countryCode = countryCode, city = city,
    placeName = placeName, expectedTransitMinutesToNext = expectedTransitMinutesToNext,
)

internal fun LogisticsRouteTemplate.toEntityV2() = LogisticsRouteTemplateEntity(
    organizationId = organizationId, id = id, name = name.trim(), originCountryCode = originCountryCode.trim(),
    originCity = originCity.trim(), destinationCountryCode = destinationCountryCode.trim(), destinationCity = destinationCity.trim(),
    transportPlanKind = transportPlanKind.name, unifiedTransportMode = unifiedTransportMode?.name,
    customsStopOrder = customsStopOrder, expectedCustomsMinutes = expectedCustomsMinutes, createdAt = createdAt, updatedAt = updatedAt,
)

internal fun LogisticsRouteTemplateStop.toEntityV2(organizationId: String) = LogisticsRouteTemplateStopEntity(
    organizationId = organizationId, id = id, templateId = templateId, stopOrder = order, countryCode = countryCode.trim(),
    city = city.trim(), placeName = placeName.trim(), expectedTransitMinutesToNext = expectedTransitMinutesToNext,
)

internal class LogisticsShipmentStoreCoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }

    fun observeShipments(organizationId: String): Flow<List<LogisticsShipment>> =
        dao.observeShipments(organizationId).map { rows -> rows.map(LogisticsShipmentEntity::toDomainV2) }

    suspend fun getShipment(
        organizationId: String,
        shipmentId: String,
    ): LogisticsShipmentAggregate? {
        val shipment = dao.getShipment(organizationId, shipmentId) ?: return null
        val transportDetails = dao.getTransportDetails(organizationId, shipmentId)?.toDomainV2()
        val shortages = dao.getShortages(organizationId, shipmentId).map { it.toDomainV2() }
        val recoveries = dao.getRecoveries(organizationId, shipmentId).map { it.toDomainV2() }
        val recoveryLines = recoveries.flatMap { recovery ->
            dao.getRecoveryLines(organizationId, recovery.id).map { it.toDomainV2() }
        }
        val recoveryPostings = recoveryLines.mapNotNull { line ->
            dao.findRecoveryPostingForLine(organizationId, line.id)?.toDomainV2()
        }
        return LogisticsShipmentAggregate(
            shipment = shipment.toDomainV2().copy(transportDetails = transportDetails),
            sources = dao.getSources(organizationId, shipmentId).map(LogisticsShipmentSourceEntity::toDomainV2),
            lines = dao.getLines(organizationId, shipmentId).map(LogisticsShipmentLineEntity::toDomainV2),
            milestones = dao.getMilestones(organizationId, shipmentId).map(LogisticsMilestoneEntity::toDomainV2),
            legs = dao.getLegs(organizationId, shipmentId).map(LogisticsShipmentLegEntity::toDomainV2),
            custodyHandoffs = dao.getCustodyHandoffs(organizationId, shipmentId)
                .map(LogisticsCustodyHandoffEntity::toDomainV2),
            assignments = dao.getAssignments(organizationId, shipmentId).map(LogisticsAssignmentEntity::toDomainV2),
            partners = dao.getPartnerLinks(organizationId, shipmentId).map(LogisticsShipmentPartnerLinkEntity::toDomainV2),
            documents = dao.getDocuments(organizationId, shipmentId).map(LogisticsDocumentEntity::toDomainV2),
            costs = dao.getCosts(organizationId, shipmentId).map(LogisticsCostEntity::toDomainV2),
            payments = dao.getPayments(organizationId, shipmentId).map(LogisticsPaymentEntity::toDomainV2),
            receivingBatches = dao.getReceivingBatches(organizationId, shipmentId).map { batch ->
                batch.toDomainV2(dao.getReceivingLines(organizationId, batch.id).map(LogisticsReceivingLineEntity::toDomainV2))
            },
            costAllocations = dao.getCostAllocations(organizationId, shipmentId)
                .map(LogisticsCostAllocationEntity::toDomainV2),
            shortages = shortages,
            recoveries = recoveries,
            recoveryLines = recoveryLines,
            recoveryPostings = recoveryPostings,
            shortageSettlements = dao.getShortageSettlements(organizationId, shipmentId).map { it.toDomainV2() },
            lateCostAdjustments = dao.getLateCostAdjustments(organizationId, shipmentId).map { it.toDomainV2() },
            lateCostAllocations = dao.getLateCostAllocations(organizationId, shipmentId).map { it.toDomainV2() },
            customsPlan = dao.getCustomsPlan(organizationId, shipmentId)?.toDomainV234(),
            customsPlanDocuments = dao.getCustomsPlanDocuments(organizationId, shipmentId).map { it.toDomainV234() },
            planRevisions = dao.getPlanRevisions(organizationId, shipmentId).map { revision ->
                revision.toDomainV234(
                    dao.getPlanRevisionChanges(organizationId, revision.id).map { it.toDomainV234() },
                )
            },
        )
    }

    suspend fun shipmentNumberExists(organizationId: String, shipmentNumber: String): Boolean =
        withContext(Dispatchers.IO) {
            exists(
                "SELECT 1 FROM logistics_shipments WHERE organization_id = ? AND shipment_number = ? LIMIT 1",
                arrayOf(organizationId, shipmentNumber.trim()),
            )
        }

    suspend fun isRequestProcessed(organizationId: String, requestId: String): Boolean =
        withContext(Dispatchers.IO) {
            exists(
                "SELECT 1 FROM logistics_events WHERE organization_id = ? AND request_id = ? LIMIT 1",
                arrayOf(organizationId, requestId),
            )
        }

    suspend fun savePayment(payment: LogisticsPayment, event: LogisticsEvent) {
        requireSameTenant(payment.organizationId, event.organizationId)
        require(payment.shipmentId == event.shipmentId) { "Payment/event shipment mismatch" }
        database.withTransaction {
            require(dao.getShipment(payment.organizationId, payment.shipmentId) != null) { "Shipment not found" }
            require(dao.getCosts(payment.organizationId, payment.shipmentId).any { it.id == payment.costId }) { "Payment cost not found" }
            dao.insertPayment(payment.toEntityV2())
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    suspend fun getPayments(organizationId: String, shipmentId: String): List<LogisticsPayment> =
        dao.getPayments(organizationId, shipmentId).map(LogisticsPaymentEntity::toDomainV2)

    suspend fun findPaymentByRequest(organizationId: String, requestId: String): LogisticsPayment? =
        dao.findPaymentByRequest(organizationId, requestId)?.toDomainV2()

    suspend fun hasInventoryPosting(organizationId: String, shipmentId: String): Boolean =
        dao.countInventoryPostings(organizationId, shipmentId) > 0

    suspend fun createShipment(shipment: LogisticsShipment, event: LogisticsEvent) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == event.shipmentId) { "Event shipment does not match shipment" }
        database.withTransaction {
            dao.insertShipment(shipment.toEntityV2())
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    suspend fun appendEvent(event: LogisticsEvent) {
        require(event.organizationId.isNotBlank()) { "organizationId is required" }
        database.withTransaction {
            require(dao.getShipment(event.organizationId, event.shipmentId) != null) { "Shipment not found" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }

    private fun exists(sql: String, bindArgs: Array<out Any?>): Boolean {
        val cursor = database.openHelper.readableDatabase.query(sql, bindArgs)
        return cursor.use { it.moveToFirst() }
    }
}

internal fun requireSameTenant(first: String, second: String) {
    require(first == second) { "Cross-organization logistics write is forbidden" }
}

internal fun LogisticsShipmentEntity.toDomainV2(): LogisticsShipment = LogisticsShipment(
    id = id,
    organizationId = organizationId,
    shipmentNumber = shipmentNumber,
    sourceLocation = sourceLocation,
    destinationLocation = destinationLocation,
    state = LogisticsShipmentState.valueOf(state),
    createdAt = createdAt,
    transportMode = transportMode?.let(LogisticsTransportMode::valueOf),
    assignee = assignedEmployeeId?.let { employeeId ->
        assignedEmployeeNameSnapshot?.let { employeeName ->
            LogisticsAssigneeSnapshot(employeeId, employeeName)
        }
    },
    startedAt = startedAt,
    expectedDepartureAt = expectedDepartureAt,
    expectedArrivalAt = expectedArrivalAt,
    notes = notes,
    cancelledAt = cancelledAt,
    cancelReason = cancelReason,
    customsMilestoneId = customsMilestoneId,
    customsCalendarPolicyId = customsCalendarPolicyId,
    eventTimezoneId = eventTimezoneId,
    originLocationDetails = explicitLocationOrNull(originCountryKey, originCountryNameSnapshot, originCity),
    destinationLocationDetails = explicitLocationOrNull(destinationCountryKey, destinationCountryNameSnapshot, destinationCity),
    routeTransportPlanKind = routeTransportPlanKind?.let(LogisticsRouteTransportPlanKind::valueOf),
    unifiedTransportMode = unifiedTransportMode?.let(LogisticsLegTransportMode::valueOf),
    currentPlanRevision = currentPlanRevision,
    planApprovedAt = planApprovedAt,
)

internal fun LogisticsShipment.toEntityV2(): LogisticsShipmentEntity = LogisticsShipmentEntity(
    organizationId = organizationId,
    id = id,
    shipmentNumber = shipmentNumber,
    sourceLocation = sourceLocation,
    destinationLocation = destinationLocation,
    state = state.name,
    createdAt = createdAt,
    transportMode = transportMode?.name,
    assignedEmployeeId = assignee?.employeeId,
    assignedEmployeeNameSnapshot = assignee?.employeeName,
    startedAt = startedAt,
    expectedDepartureAt = expectedDepartureAt,
    expectedArrivalAt = expectedArrivalAt,
    notes = notes,
    cancelledAt = cancelledAt,
    cancelReason = cancelReason,
    customsMilestoneId = customsMilestoneId,
    customsCalendarPolicyId = customsCalendarPolicyId,
    eventTimezoneId = eventTimezoneId,
    originCountryKey = originLocationDetails?.countryCode,
    originCountryNameSnapshot = originLocationDetails?.countryNameSnapshot,
    originCity = originLocationDetails?.city,
    destinationCountryKey = destinationLocationDetails?.countryCode,
    destinationCountryNameSnapshot = destinationLocationDetails?.countryNameSnapshot,
    destinationCity = destinationLocationDetails?.city,
    routeTransportPlanKind = routeTransportPlanKind?.name,
    unifiedTransportMode = unifiedTransportMode?.name,
    currentPlanRevision = currentPlanRevision,
    planApprovedAt = planApprovedAt,
)

internal fun LogisticsShipmentSourceEntity.toDomainV2(): LogisticsShipmentSource = LogisticsShipmentSource(
    id = id,
    shipmentId = shipmentId,
    invoiceId = invoiceId,
    supplierId = supplierId,
    supplierNameSnapshot = supplierNameSnapshot,
    invoiceNumberSnapshot = invoiceNumberSnapshot,
    originalCurrency = originalCurrency,
    exchangeRateSnapshot = exchangeRateSnapshot?.let(::BigDecimal),
    plannedPackageCount = plannedPackageCount,
    plannedWeightKg = plannedWeightKg?.let(::BigDecimal),
    expectedReadyAt = expectedReadyAt,
)

internal fun LogisticsShipmentSource.toEntityV2(organizationId: String): LogisticsShipmentSourceEntity =
    LogisticsShipmentSourceEntity(
        organizationId = organizationId,
        id = id,
        shipmentId = shipmentId,
        invoiceId = invoiceId,
        supplierId = supplierId,
        supplierNameSnapshot = supplierNameSnapshot,
        invoiceNumberSnapshot = invoiceNumberSnapshot,
        originalCurrency = originalCurrency,
        exchangeRateSnapshot = exchangeRateSnapshot?.toPlainString(),
        plannedPackageCount = plannedPackageCount,
        plannedWeightKg = plannedWeightKg?.toPlainString(),
        expectedReadyAt = expectedReadyAt,
    )

internal fun LogisticsShipmentLineEntity.toDomainV2(): LogisticsShipmentLine = LogisticsShipmentLine(
    id = id,
    shipmentId = shipmentId,
    sourceInvoiceId = sourceInvoiceId,
    sourceInvoiceItemId = sourceInvoiceItemId,
    inventoryItemId = inventoryItemId,
    itemNameSnapshot = itemNameSnapshot,
    expectedQuantity = expectedQuantity,
    basePurchaseUnitPrice = BigDecimal(basePurchaseUnitPrice),
    hsCode = hsCode,
)

internal fun LogisticsShipmentLine.toEntityV2(organizationId: String): LogisticsShipmentLineEntity =
    LogisticsShipmentLineEntity(
        organizationId = organizationId,
        id = id,
        shipmentId = shipmentId,
        sourceInvoiceId = sourceInvoiceId,
        sourceInvoiceItemId = sourceInvoiceItemId,
        inventoryItemId = inventoryItemId,
        itemNameSnapshot = itemNameSnapshot,
        expectedQuantity = expectedQuantity,
        basePurchaseUnitPrice = basePurchaseUnitPrice.toPlainString(),
        hsCode = hsCode,
    )

internal fun LogisticsTransportDetailsEntity.toDomainV2(): LogisticsTransportDetails = LogisticsTransportDetails(
    incotermCode = incotermCode,
    containerNumber = containerNumber,
    billOrAirwayNumber = billOrAirwayNumber,
    vesselOrFlightReference = vesselOrFlightReference,
    weightKg = weightKg?.let(::BigDecimal),
    volumeM3 = volumeM3?.let(::BigDecimal),
    packageCount = packageCount,
    palletCount = palletCount,
    insuranceReference = insuranceReference,
)

internal fun LogisticsTransportDetails.toEntityV2(organizationId: String, shipmentId: String) =
    LogisticsTransportDetailsEntity(
        organizationId = organizationId,
        shipmentId = shipmentId,
        incotermCode = incotermCode,
        containerNumber = containerNumber,
        billOrAirwayNumber = billOrAirwayNumber,
        vesselOrFlightReference = vesselOrFlightReference,
        weightKg = weightKg?.toPlainString(),
        volumeM3 = volumeM3?.toPlainString(),
        packageCount = packageCount,
        palletCount = palletCount,
        insuranceReference = insuranceReference,
    )

internal fun LogisticsEvent.toEntityV2(json: Json): LogisticsEventEntity = LogisticsEventEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    type = type.name,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    employeeId = employeeId,
    employeeNameSnapshot = employeeNameSnapshot,
    requestId = requestId,
    payloadJson = json.encodeToString(payload),
)


internal fun LogisticsPaymentEntity.toDomainV2() = LogisticsPayment(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    costId = costId,
    state = LogisticsPaymentState.valueOf(state),
    amount = BigDecimal(amount),
    currency = currency,
    accountId = accountId,
    paidAt = paidAt,
    reference = reference,
    proofDocumentId = proofDocumentId,
    cashReference = cashReference,
    requestId = requestId,
    createdAt = createdAt,
)

internal fun LogisticsPayment.toEntityV2() = LogisticsPaymentEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    costId = costId,
    state = state.name,
    amount = amount.toPlainString(),
    currency = currency,
    accountId = accountId,
    paidAt = paidAt,
    reference = reference,
    proofDocumentId = proofDocumentId,
    cashReference = cashReference,
    requestId = requestId,
    createdAt = createdAt,
)


private fun explicitLocationOrNull(
    countryKey: String?,
    countryName: String?,
    city: String?,
): LogisticsLocation? {
    val normalizedCity = city?.trim().orEmpty()
    val normalizedCountryKey = countryKey?.trim().orEmpty()
    val normalizedCountryName = countryName?.trim().orEmpty()
    // v235 draft definition persists partial edits so Process Death never drops a country/city typed earlier.
    if (normalizedCity.isBlank() && normalizedCountryKey.isBlank() && normalizedCountryName.isBlank()) return null
    return LogisticsLocation(
        countryCode = normalizedCountryKey,
        countryNameSnapshot = normalizedCountryName,
        city = normalizedCity,
        placeName = normalizedCity,
    )
}

internal fun LogisticsCustomsPlanEntity.toDomainV234() = LogisticsCustomsPlan(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    checkpointName = checkpointName,
    afterStationId = afterStationId,
    expectedDurationMinutes = expectedDurationMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun LogisticsCustomsPlan.toEntityV234() = LogisticsCustomsPlanEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    checkpointName = checkpointName,
    afterStationId = afterStationId,
    expectedDurationMinutes = expectedDurationMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun LogisticsCustomsPlanDocumentEntity.toDomainV234() = LogisticsCustomsPlanDocument(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    customsPlanId = customsPlanId,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    privateUri = privateUri,
    sha256 = sha256,
    createdAt = createdAt,
)

internal fun LogisticsCustomsPlanDocument.toEntityV234() = LogisticsCustomsPlanDocumentEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    customsPlanId = customsPlanId,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    privateUri = privateUri,
    sha256 = sha256,
    createdAt = createdAt,
)

internal fun LogisticsPlanRevisionChangeEntity.toDomainV234() = LogisticsPlanRevisionChange(
    id = id,
    scope = LogisticsPlanChangeScope.valueOf(scope),
    scopeId = scopeId,
    fieldKey = fieldKey,
    previousValue = previousValue,
    newValue = newValue,
)

internal fun LogisticsPlanRevisionEntity.toDomainV234(changes: List<LogisticsPlanRevisionChange>) = LogisticsPlanRevision(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    revisionNumber = revisionNumber,
    kind = LogisticsPlanRevisionKind.valueOf(kind),
    reason = reason,
    changedByEmployeeId = changedByEmployeeId,
    changedByEmployeeNameSnapshot = changedByEmployeeNameSnapshot,
    recordedAt = recordedAt,
    requestId = requestId,
    changes = changes,
)

internal fun LogisticsPlanRevision.toEntityV234() = LogisticsPlanRevisionEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    revisionNumber = revisionNumber,
    kind = kind.name,
    reason = reason,
    changedByEmployeeId = changedByEmployeeId,
    changedByEmployeeNameSnapshot = changedByEmployeeNameSnapshot,
    recordedAt = recordedAt,
    requestId = requestId,
)

internal fun LogisticsPlanRevisionChange.toEntityV234(
    organizationId: String,
    shipmentId: String,
    revisionId: String,
) = LogisticsPlanRevisionChangeEntity(
    organizationId = organizationId,
    id = id,
    revisionId = revisionId,
    shipmentId = shipmentId,
    scope = scope.name,
    scopeId = scopeId,
    fieldKey = fieldKey,
    previousValue = previousValue,
    newValue = newValue,
)
