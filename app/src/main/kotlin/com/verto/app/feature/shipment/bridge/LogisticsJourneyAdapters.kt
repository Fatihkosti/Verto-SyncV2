package com.verto.app.feature.shipment.bridge

import com.verto.app.core.error.BusinessRuleFailureException
import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.LogisticsShipmentLegEntity
import com.verto.app.feature.shipment.domain.model.LogisticsAssignment
import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import kotlinx.serialization.json.Json

internal class LogisticsJourneyStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }
    suspend fun saveShipmentHeader(
        shipment: LogisticsShipment,
        assignment: LogisticsAssignment?,
    ) {
        require(shipment.state == LogisticsShipmentState.DRAFT) { "Draft header persistence requires DRAFT shipment" }
        assignment?.let {
            require(it.shipmentId == shipment.id) { "Assignment belongs to another shipment" }
            require(shipment.assignee?.employeeId == it.employeeId) { "Shipment/assignment employee mismatch" }
            require(shipment.assignee?.employeeName == it.employeeNameSnapshot) { "Shipment/assignment name mismatch" }
        }
        database.withTransaction {
            requireDraftShipment(shipment)
            if (assignment == null) {
                require(dao.getAssignments(shipment.organizationId, shipment.id).none { it.endedAt == null }) {
                    "Clearing an active assignment requires explicit assignment change semantics"
                }
            } else {
                persistAssignment(shipment.organizationId, shipment.id, assignment)
            }
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
        }
    }
    suspend fun savePurchasePlan(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
    ) {
        validateDraftPurchasePlan(shipment, sources, lines)
        database.withTransaction {
            requireDraftShipment(shipment)
            persistPurchasePlan(shipment, sources, lines)
            persistTransportDetails(shipment)
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
        }
    }
    suspend fun savePlanning(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
        assignment: LogisticsAssignment,
        event: LogisticsEvent,
    ) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == event.shipmentId) { "Event shipment does not match shipment" }
        validateDraftPurchasePlan(shipment, sources, lines)
        require(assignment.shipmentId == shipment.id) { "Assignment belongs to another shipment" }
        database.withTransaction {
            requireDraftShipment(shipment)
            persistPurchasePlan(shipment, sources, lines)
            persistAssignment(shipment.organizationId, shipment.id, assignment)
            persistTransportDetails(shipment)
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    private fun validateDraftPurchasePlan(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
    ) {
        require(shipment.state == LogisticsShipmentState.DRAFT) { "Draft planning persistence requires DRAFT shipment" }
        require(sources.all { it.shipmentId == shipment.id }) { "Source belongs to another shipment" }
        require(lines.all { it.shipmentId == shipment.id }) { "Line belongs to another shipment" }
        require(sources.map { it.invoiceId }.distinct().size == sources.size) { "Duplicate purchase sources" }
        require(lines.map { it.sourceInvoiceId to it.sourceInvoiceItemId }.distinct().size == lines.size) {
            "Duplicate purchase invoice lines"
        }
        require(lines.all { line -> sources.any { it.invoiceId == line.sourceInvoiceId } }) {
            "Shipment line references an unselected purchase source"
        }
    }
    private suspend fun requireDraftShipment(shipment: LogisticsShipment) {
        val current = dao.getShipment(shipment.organizationId, shipment.id) ?: error("Shipment not found")
        require(current.state == LogisticsShipmentState.DRAFT.name) { "Shipment changed before draft commit" }
    }
    private suspend fun persistPurchasePlan(
        shipment: LogisticsShipment,
        sources: List<LogisticsShipmentSource>,
        lines: List<LogisticsShipmentLine>,
    ) {
        sources.forEach { source ->
            if (dao.countConflictingActiveShipmentsForInvoice(
                    shipment.organizationId, source.invoiceId, shipment.id,
                ) != 0
            ) {
                throw BusinessRuleFailureException(
                    code = "SHIPMENT_PURCHASE_INVOICE_CONFLICT",
                    target = source.invoiceId,
                )
            }
        }
        val existingSources = dao.getSources(shipment.organizationId, shipment.id)
        val existingLines = dao.getLines(shipment.organizationId, shipment.id)
        val existingSourcesByInvoice = existingSources.associateBy { it.invoiceId }
        val existingLinesByKey = existingLines.associateBy { it.sourceInvoiceId to it.sourceInvoiceItemId }
        val reconciledSources = sources.map { source ->
            source.copy(id = existingSourcesByInvoice[source.invoiceId]?.id ?: source.id)
        }
        val reconciledLines = lines.map { line ->
            line.copy(id = existingLinesByKey[line.sourceInvoiceId to line.sourceInvoiceItemId]?.id ?: line.id)
        }
        val incomingSourceInvoices = reconciledSources.mapTo(mutableSetOf()) { it.invoiceId }
        val incomingLineKeys = reconciledLines.mapTo(mutableSetOf()) { it.sourceInvoiceId to it.sourceInvoiceItemId }
        val removedSources = existingSources.filterNot { it.invoiceId in incomingSourceInvoices }
        val removedLines = existingLines.filterNot { (it.sourceInvoiceId to it.sourceInvoiceItemId) in incomingLineKeys }
        if (removedSources.isNotEmpty()) {
            val removedIds = removedSources.mapTo(mutableSetOf()) { it.id }
            require(dao.getCustodyHandoffs(shipment.organizationId, shipment.id).none { it.sourceId in removedIds }) {
                "Source with custody history cannot be removed from planning"
            }
            require(dao.getDocuments(shipment.organizationId, shipment.id).none { it.sourceId in removedIds }) {
                "Source with scoped document cannot be removed from planning"
            }
            require(dao.getCosts(shipment.organizationId, shipment.id).none { it.sourceId in removedIds }) {
                "Source with scoped cost cannot be removed from planning"
            }
        }
        if (removedLines.isNotEmpty()) {
            val removedIds = removedLines.mapTo(mutableSetOf()) { it.id }
            val receivingLineRefs = dao.getReceivingBatches(shipment.organizationId, shipment.id).any { batch ->
                dao.getReceivingLines(shipment.organizationId, batch.id).any { it.shipmentLineId in removedIds }
            }
            require(!receivingLineRefs) { "Shipment line with receiving history cannot be removed from planning" }
            require(dao.getCostAllocations(shipment.organizationId, shipment.id).none { it.shipmentLineId in removedIds }) {
                "Shipment line with landed-cost allocation cannot be removed from planning"
            }
        }
        removedLines.forEach { line ->
            require(dao.deleteLineForPlanning(shipment.organizationId, shipment.id, line.id) == 1) {
                "Removed shipment line changed before commit"
            }
        }
        removedSources.forEach { source ->
            require(dao.deleteSourceForPlanning(shipment.organizationId, shipment.id, source.id) == 1) {
                "Removed purchase source changed before commit"
            }
        }
        val existingSourceIds = existingSources.mapTo(mutableSetOf()) { it.id }
        val sourceUpdates = reconciledSources.filter { it.id in existingSourceIds }
            .map { it.toEntityV2(shipment.organizationId) }
        val sourceInserts = reconciledSources.filterNot { it.id in existingSourceIds }
            .map { it.toEntityV2(shipment.organizationId) }
        if (sourceUpdates.isNotEmpty()) {
            require(dao.updateSources(sourceUpdates) == sourceUpdates.size) { "Purchase source update failed" }
        }
        if (sourceInserts.isNotEmpty()) dao.insertSources(sourceInserts)
        val existingLineIds = existingLines.mapTo(mutableSetOf()) { it.id }
        val lineUpdates = reconciledLines.filter { it.id in existingLineIds }
            .map { it.toEntityV2(shipment.organizationId) }
        val lineInserts = reconciledLines.filterNot { it.id in existingLineIds }
            .map { it.toEntityV2(shipment.organizationId) }
        if (lineUpdates.isNotEmpty()) {
            require(dao.updateLines(lineUpdates) == lineUpdates.size) { "Shipment line update failed" }
        }
        if (lineInserts.isNotEmpty()) dao.insertLines(lineInserts)
    }
    private suspend fun persistAssignment(
        organizationId: String,
        shipmentId: String,
        assignment: LogisticsAssignment,
    ) {
        val activeAssignments = dao.getAssignments(organizationId, shipmentId).filter { it.endedAt == null }
        require(activeAssignments.size <= 1) { "Multiple active assignments found" }
        val active = activeAssignments.singleOrNull()
        val assignmentEntity = assignment.toEntityV2(organizationId)
        when {
            active == null -> dao.insertAssignment(assignmentEntity)
            active.id == assignment.id -> {
                require(dao.updateAssignment(assignmentEntity) == 1) { "Active assignment update failed" }
            }
            else -> {
                require(dao.endActiveAssignments(organizationId, shipmentId, assignment.assignedAt) == 1) {
                    "Active assignment could not be ended"
                }
                dao.insertAssignment(assignmentEntity)
            }
        }
    }
    private suspend fun persistTransportDetails(shipment: LogisticsShipment) {
        val currentTransport = dao.getTransportDetails(shipment.organizationId, shipment.id)
        val newTransport = shipment.transportDetails?.toEntityV2(shipment.organizationId, shipment.id)
        when {
            newTransport == null && currentTransport != null -> {
                require(dao.deleteTransportDetails(shipment.organizationId, shipment.id) == 1) {
                    "Transport details delete failed"
                }
            }
            newTransport != null && currentTransport == null -> dao.insertTransportDetails(newTransport)
            newTransport != null -> {
                require(dao.updateTransportDetails(newTransport) == 1) { "Transport details update failed" }
            }
        }
    }
    suspend fun saveShipmentState(shipment: LogisticsShipment, event: LogisticsEvent) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == event.shipmentId) { "Event shipment does not match shipment" }
        database.withTransaction {
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    suspend fun changeAssignment(
        shipment: LogisticsShipment,
        endedAssignmentId: String?,
        assignment: LogisticsAssignment,
        event: LogisticsEvent,
    ) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == assignment.shipmentId && shipment.id == event.shipmentId) {
            "Assignment/event belongs to another shipment"
        }
        database.withTransaction {
            val active = dao.getAssignments(shipment.organizationId, shipment.id).filter { it.endedAt == null }
            require(active.size <= 1) { "Multiple active assignments found" }
            if (endedAssignmentId == null) {
                require(active.isEmpty()) { "Active assignment exists but was not selected for ending" }
            } else {
                require(active.singleOrNull()?.id == endedAssignmentId) { "Active assignment changed before commit" }
                require(
                    dao.endActiveAssignments(shipment.organizationId, shipment.id, assignment.assignedAt) == 1,
                ) { "Active assignment could not be ended" }
            }
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertAssignment(assignment.toEntityV2(shipment.organizationId))
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    suspend fun saveMilestone(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        event: LogisticsEvent,
    ) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == milestone.shipmentId && shipment.id == event.shipmentId) {
            "Milestone/event belongs to another shipment"
        }
        database.withTransaction {
            val current = dao.getMilestones(shipment.organizationId, shipment.id).singleOrNull { it.id == milestone.id }
                ?: error("Milestone not found")
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            require(dao.updateMilestone(milestone.toEntityV2(shipment.organizationId)) == 1) {
                "Milestone update failed"
            }
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    suspend fun replacePlannedRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) {
        validateDraftRoute(shipment, milestones, legs)
        database.withTransaction {
            requireDraftShipment(shipment)
            persistPlannedRoute(shipment, milestones, legs)
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
        }
    }
    suspend fun saveRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
        event: LogisticsEvent,
    ) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == event.shipmentId) { "Event shipment does not match shipment" }
        validateDraftRoute(shipment, milestones, legs)
        database.withTransaction {
            requireDraftShipment(shipment)
            persistPlannedRoute(shipment, milestones, legs)
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    suspend fun correctMilestone(
        shipment: LogisticsShipment,
        milestone: LogisticsMilestone,
        event: LogisticsEvent,
    ) = saveMilestone(shipment, milestone, event)
    suspend fun correctLeg(
        shipment: LogisticsShipment,
        leg: LogisticsShipmentLeg,
        event: LogisticsEvent,
    ) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == leg.shipmentId && shipment.id == event.shipmentId) {
            "Leg/event belongs to another shipment"
        }
        require(leg.organizationId == shipment.organizationId) { "Leg belongs to another organization" }
        database.withTransaction {
            require(dao.getShipment(shipment.organizationId, shipment.id) != null) { "Shipment not found" }
            require(dao.getLegs(shipment.organizationId, shipment.id).any { it.id == leg.id }) { "Leg not found" }
            require(dao.updateLeg(leg.toEntityV2()) == 1) { "Leg update failed" }
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    private fun validateDraftRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) {
        require(shipment.state == LogisticsShipmentState.DRAFT) { "Route replacement requires DRAFT shipment" }
        require(milestones.all { it.shipmentId == shipment.id }) { "Milestone belongs to another shipment" }
        require(legs.all { it.organizationId == shipment.organizationId && it.shipmentId == shipment.id }) {
            "Leg belongs to another shipment"
        }
        require(milestones.map { it.id }.distinct().size == milestones.size) { "Duplicate milestone ids" }
        require(legs.map { it.id }.distinct().size == legs.size) { "Duplicate leg ids" }
    }
    private suspend fun persistPlannedRoute(
        shipment: LogisticsShipment,
        milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>,
    ) {
        val existingMilestones = dao.getMilestones(shipment.organizationId, shipment.id)
        val existingLegs = dao.getLegs(shipment.organizationId, shipment.id)
        require(existingMilestones.none {
            it.arrivedAt != null || it.unloadedAt != null || it.loadedAt != null || it.departedAt != null ||
                it.handlingStatus != "PENDING"
        }) { "Executed milestone route cannot be destructively replaced" }
        require(existingLegs.none {
            it.status != "PLANNED" || it.actualDepartureAt != null || it.actualArrivalAt != null
        }) { "Executed leg route cannot be destructively replaced" }
        val newMilestoneIds = milestones.mapTo(mutableSetOf()) { it.id }
        val newLegIds = legs.mapTo(mutableSetOf()) { it.id }
        val removedMilestoneIds = existingMilestones.map { it.id }.filterNot(newMilestoneIds::contains)
        val removedLegIds = existingLegs.map { it.id }.filterNot(newLegIds::contains)
        if (removedMilestoneIds.isNotEmpty()) {
            val usedByHandoff = dao.getCustodyHandoffs(shipment.organizationId, shipment.id)
                .any { it.milestoneId in removedMilestoneIds }
            val usedByDocument = dao.getDocuments(shipment.organizationId, shipment.id)
                .any { it.milestoneId in removedMilestoneIds }
            val usedByCost = dao.getCosts(shipment.organizationId, shipment.id)
                .any { it.milestoneId in removedMilestoneIds }
            require(!usedByHandoff && !usedByDocument && !usedByCost) {
                "Referenced milestone cannot be removed from route"
            }
        }
        if (removedLegIds.isNotEmpty()) {
            val usedByDocument = dao.getDocuments(shipment.organizationId, shipment.id)
                .any { it.legId in removedLegIds }
            val usedByCost = dao.getCosts(shipment.organizationId, shipment.id)
                .any { it.legId in removedLegIds }
            require(!usedByDocument && !usedByCost) { "Referenced leg cannot be removed from route" }
        }
        removedLegIds.forEach { id ->
            require(dao.deleteLegForRouteSave(shipment.organizationId, shipment.id, id) == 1) {
                "Removed route leg changed before commit"
            }
        }
        if (existingMilestones.any { it.id in newMilestoneIds }) {
            dao.offsetMilestoneOrdersForRouteSave(shipment.organizationId, shipment.id)
        }
        val existingMilestoneIds = existingMilestones.mapTo(mutableSetOf()) { it.id }
        milestones.sortedBy { it.order }.forEach { milestone ->
            val entity = milestone.toEntityV2(shipment.organizationId)
            if (milestone.id in existingMilestoneIds) {
                require(dao.updateMilestone(entity) == 1) { "Milestone update failed" }
            } else {
                dao.insertMilestone(entity)
            }
        }
        if (existingLegs.any { it.id in newLegIds }) {
            dao.offsetLegSequencesForRouteSave(shipment.organizationId, shipment.id)
        }
        val existingLegIds = existingLegs.mapTo(mutableSetOf()) { it.id }
        val newLegEntities = mutableListOf<LogisticsShipmentLegEntity>()
        legs.sortedBy { it.sequence }.forEach { leg ->
            val entity = leg.toEntityV2()
            if (leg.id in existingLegIds) {
                require(dao.updateLeg(entity) == 1) { "Leg update failed" }
            } else {
                newLegEntities += entity
            }
        }
        if (newLegEntities.isNotEmpty()) dao.insertLegs(newLegEntities)
        removedMilestoneIds.forEach { id ->
            require(dao.deleteMilestoneForRouteSave(shipment.organizationId, shipment.id, id) == 1) {
                "Removed route milestone changed before commit"
            }
        }
    }
    suspend fun saveCustodyHandoff(
        handoff: LogisticsCustodyHandoff,
        event: LogisticsEvent,
    ) {
        requireSameTenant(handoff.organizationId, event.organizationId)
        require(handoff.shipmentId == event.shipmentId) { "Handoff event belongs to another shipment" }
        database.withTransaction {
            require(dao.getShipment(handoff.organizationId, handoff.shipmentId) != null) { "Shipment not found" }
            handoff.sourceId?.let { sourceId ->
                require(dao.getSources(handoff.organizationId, handoff.shipmentId).any { it.id == sourceId }) {
                    "Handoff source does not belong to shipment"
                }
            }
            handoff.milestoneId?.let { milestoneId ->
                require(dao.getMilestones(handoff.organizationId, handoff.shipmentId).any { it.id == milestoneId }) {
                    "Handoff milestone does not belong to shipment"
                }
            }
            if (handoff.fromHolderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER) {
                val fromHolderId = handoff.fromHolderId
                require(fromHolderId != null && dao.getPartner(handoff.organizationId, fromHolderId) != null) {
                    "Handoff source logistics partner not found"
                }
            }
            if (handoff.toHolderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER) {
                val toHolderId = handoff.toHolderId
                require(toHolderId != null && dao.getPartner(handoff.organizationId, toHolderId) != null) {
                    "Handoff destination logistics partner not found"
                }
            }
            dao.insertCustodyHandoff(handoff.toEntityV2())
            dao.insertEvent(event.toEntityV2(json))
        }
    }
    suspend fun getLatestConfirmedCargoSnapshot(
        organizationId: String,
        shipmentId: String,
    ): LogisticsCargoSnapshot? = database.readLatestConfirmedCargoSnapshot(organizationId, shipmentId)
    suspend fun saveOperationalUpdate(
        shipment: LogisticsShipment, milestones: List<LogisticsMilestone>,
        legs: List<LogisticsShipmentLeg>, event: LogisticsEvent,
    ) {
        requireSameTenant(shipment.organizationId, event.organizationId)
        require(shipment.id == event.shipmentId) { "Operational event belongs to another shipment" }
        require(milestones.all { it.shipmentId == shipment.id }) { "Milestone belongs to another shipment" }
        require(legs.all { it.organizationId == shipment.organizationId && it.shipmentId == shipment.id }) {
            "Leg belongs to another shipment"
        }
        require(milestones.map { it.id }.distinct().size == milestones.size) { "Duplicate milestone update" }
        require(legs.map { it.id }.distinct().size == legs.size) { "Duplicate leg update" }
        database.withTransaction {
            require(dao.getShipment(shipment.organizationId, shipment.id) != null) { "Shipment not found" }
            val existingMilestones = dao.getMilestones(shipment.organizationId, shipment.id)
            val existingLegs = dao.getLegs(shipment.organizationId, shipment.id)
            val milestoneIds = existingMilestones.mapTo(mutableSetOf()) { it.id }
            val legIds = existingLegs.mapTo(mutableSetOf()) { it.id }
            val reordersMilestones = milestones.any { candidate ->
                val existing = existingMilestones.singleOrNull { it.id == candidate.id }
                existing == null || existing.milestoneOrder != candidate.order
            }
            val reordersLegs = legs.any { candidate ->
                val existing = existingLegs.singleOrNull { it.id == candidate.id }
                existing == null || existing.sequence != candidate.sequence
            }
            if (reordersMilestones) {
                require(milestones.mapTo(mutableSetOf()) { it.id }.containsAll(milestoneIds)) {
                    "Operational milestone reorder must provide the complete persisted milestone set"
                }
                dao.offsetMilestoneOrdersForRouteSave(shipment.organizationId, shipment.id)
            }
            milestones.sortedBy { it.order }.forEach { milestone ->
                val entity = milestone.toEntityV2(shipment.organizationId)
                if (milestone.id in milestoneIds) {
                    require(dao.updateMilestone(entity) == 1) { "Milestone update failed" }
                } else {
                    dao.insertMilestone(entity)
                }
            }
            if (reordersLegs) {
                require(legs.mapTo(mutableSetOf()) { it.id }.containsAll(legIds)) {
                    "Operational leg reorder must provide the complete persisted leg set"
                }
                dao.offsetLegSequencesForRouteSave(shipment.organizationId, shipment.id)
            }
            val newLegs = mutableListOf<LogisticsShipmentLegEntity>()
            legs.sortedBy { it.sequence }.forEach { leg ->
                val entity = leg.toEntityV2()
                if (leg.id in legIds) {
                    require(dao.updateLeg(entity) == 1) { "Leg update failed" }
                } else {
                    newLegs += entity
                }
            }
            if (newLegs.isNotEmpty()) dao.insertLegs(newLegs)
            require(dao.updateShipment(shipment.toEntityV2()) == 1) { "Shipment update failed" }
            dao.insertEvent(event.toEntityV2(json))
        }
    }
}
