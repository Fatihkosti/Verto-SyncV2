package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import java.math.BigDecimal
import javax.inject.Inject

data class JourneyCorrectionAudit(val reason: String, val occurredAt: Long, val requestId: String)

data class CorrectedCustodyHolder(
    val type: LogisticsCustodyHolderType,
    val id: String? = null,
    val name: String,
)

data class CorrectedCargoSnapshot(val packageCount: Int, val weightKg: BigDecimal? = null)

sealed interface CorrectExecutedJourneyCommand {
    val shipmentId: String
    val audit: JourneyCorrectionAudit
    val reason: String get() = audit.reason
    val occurredAt: Long get() = audit.occurredAt
    val requestId: String get() = audit.requestId
}

data class CorrectExecutedLegCommand(
    override val shipmentId: String,
    val correctedLeg: LogisticsShipmentLeg,
    override val audit: JourneyCorrectionAudit,
) : CorrectExecutedJourneyCommand

data class CorrectExecutedCustodyCommand(
    override val shipmentId: String,
    val sourceId: String? = null,
    val correctedHolder: CorrectedCustodyHolder,
    val correctedAt: Long,
    override val audit: JourneyCorrectionAudit,
) : CorrectExecutedJourneyCommand

data class CorrectExecutedCargoCommand(
    override val shipmentId: String,
    val correctedCargo: CorrectedCargoSnapshot,
    val correctedAt: Long,
    override val audit: JourneyCorrectionAudit,
) : CorrectExecutedJourneyCommand

/** Append-only correction workflow for executed journey facts other than actual milestone time. */
class CorrectExecutedJourneyUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(organizationId: String, command: CorrectExecutedJourneyCommand) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        require(command.reason.trim().isNotBlank()) { "Correction reason is required" }
        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return
        when (command) {
            is CorrectExecutedLegCommand -> correctLeg(organizationId, aggregate, command)
            is CorrectExecutedCustodyCommand -> correctCustody(organizationId, aggregate, command)
            is CorrectExecutedCargoCommand -> correctCargo(organizationId, aggregate, command)
        }
    }

    private suspend fun correctLeg(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
        command: CorrectExecutedLegCommand,
    ) {
        val current = aggregate.legs.singleOrNull { it.id == command.correctedLeg.id } ?: error("Executed leg not found")
        require(current.status == LogisticsLegStatus.ARRIVED || current.status == LogisticsLegStatus.SUPERSEDED) {
            "Only executed/superseded route history can use correction workflow"
        }
        require(command.correctedLeg.organizationId == organizationId && command.correctedLeg.shipmentId == command.shipmentId &&
            command.correctedLeg.id == current.id && command.correctedLeg.sequence == current.sequence &&
            command.correctedLeg.fromMilestoneId == current.fromMilestoneId && command.correctedLeg.toMilestoneId == current.toMilestoneId &&
            command.correctedLeg.status == current.status && command.correctedLeg.actualDepartureAt == current.actualDepartureAt &&
            command.correctedLeg.actualArrivalAt == current.actualArrivalAt && command.correctedLeg.supersededAt == current.supersededAt &&
            command.correctedLeg.supersededByLegId == current.supersededByLegId
        ) { "Completed-route correction cannot rewrite topology, lifecycle, or actual timestamps" }
        LogisticsValidation.validateLeg(command.correctedLeg)
        val payload = mapOf(
            "scope" to "EXECUTED_LEG", "legId" to current.id,
            "oldCarrierPartnerId" to current.carrierPartnerId, "newCarrierPartnerId" to command.correctedLeg.carrierPartnerId,
            "oldMode" to current.mode.name, "newMode" to command.correctedLeg.mode.name, "reason" to command.reason.trim(),
        )
        store.correctLeg(aggregate.shipment, command.correctedLeg, correctionEvent(organizationId, command, aggregate.shipment.assignee, payload))
    }

    private suspend fun correctCustody(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
        command: CorrectExecutedCustodyCommand,
    ) {
        require(command.correctedAt >= 0L) { "correctedAt must be non-negative" }
        val holder = command.correctedHolder
        val holderName = holder.name.trim()
        require(holderName.isNotBlank()) { "Corrected holder name is required" }
        if (holder.type == LogisticsCustodyHolderType.LOGISTICS_PARTNER) {
            val holderId = requireNotNull(holder.id) { "Corrected logistics partner id is required" }
            require(store.getPartner(organizationId, holderId) != null) { "Corrected logistics partner not found" }
        }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate)
        val affected = if (command.sourceId == null) {
            require(positions.isNotEmpty()) { "Shipment has no custody sources" }
            require(positions.values.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }.size == 1) {
                "Shipment-wide custody correction requires one current holder"
            }
            aggregate.sources
        } else listOf(aggregate.sources.singleOrNull { it.id == command.sourceId } ?: error("Custody correction source not found"))
        val current = positions.getValue(affected.first().id)
        val handoff = LogisticsCustodyHandoff(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId, sourceId = command.sourceId,
            fromHolderType = current.holderType, fromHolderId = current.holderId, fromHolderNameSnapshot = current.holderName,
            toHolderType = holder.type, toHolderId = holder.id, toHolderNameSnapshot = holderName,
            transferredAt = command.correctedAt, receivedAt = command.correctedAt, requestId = command.requestId, note = command.reason.trim(),
        )
        LogisticsValidation.validateCustodyHandoff(handoff)
        val payload = mapOf(
            "scope" to "CUSTODY", "sourceId" to (command.sourceId ?: "ALL"), "oldHolderType" to current.holderType.name,
            "oldHolderId" to current.holderId.orEmpty(), "newHolderType" to holder.type.name,
            "newHolderId" to holder.id.orEmpty(), "reason" to command.reason.trim(),
        )
        store.saveCustodyHandoff(handoff, correctionEvent(organizationId, command, aggregate.shipment.assignee, payload))
    }

    private suspend fun correctCargo(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
        command: CorrectExecutedCargoCommand,
    ) {
        require(command.correctedAt >= 0L) { "correctedAt must be non-negative" }
        val corrected = command.correctedCargo
        require(corrected.packageCount >= 0) { "correctedPackageCount must be non-negative" }
        corrected.weightKg?.let { require(it.signum() > 0) { "correctedWeightKg must be positive" } }
        val currentCargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate) ?: error("Current cargo snapshot is unavailable")
        require(currentCargo.packageCount != corrected.packageCount || currentCargo.weightKg != corrected.weightKg) {
            "Cargo correction must change package count or weight"
        }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
        require(positions.isNotEmpty()) { "Shipment has no custody sources" }
        val distinct = positions.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }
        require(distinct.size == 1) { "Cargo correction requires one current holder for the full shipment" }
        val holder = distinct.single()
        val reason = command.reason.trim()
        val handoff = LogisticsCustodyHandoff(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
            fromHolderType = holder.holderType, fromHolderId = holder.holderId, fromHolderNameSnapshot = holder.holderName,
            toHolderType = holder.holderType, toHolderId = holder.holderId, toHolderNameSnapshot = holder.holderName,
            transferredAt = command.correctedAt, receivedAt = command.correctedAt, requestId = command.requestId, note = reason,
            handoverPackageCount = currentCargo.packageCount, receivedPackageCount = corrected.packageCount,
            handoverWeightKg = currentCargo.weightKg, receivedWeightKg = corrected.weightKg,
            packageChangeReason = LogisticsPackageChangeReason.CORRECTION, packageChangeNote = reason,
        )
        LogisticsValidation.validateConfirmedCustodyHandoff(handoff)
        val payload = mapOf(
            "scope" to "CARGO", "oldPackageCount" to currentCargo.packageCount.toString(),
            "newPackageCount" to corrected.packageCount.toString(), "oldWeightKg" to currentCargo.weightKg?.toPlainString().orEmpty(),
            "newWeightKg" to corrected.weightKg?.toPlainString().orEmpty(), "reason" to reason,
        )
        store.saveCustodyHandoff(handoff, correctionEvent(organizationId, command, aggregate.shipment.assignee, payload))
    }

    private fun correctionEvent(
        organizationId: String,
        command: CorrectExecutedJourneyCommand,
        assignee: LogisticsAssigneeSnapshot?,
        payload: Map<String, String>,
    ) = LogisticsEvent(
        id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
        type = LogisticsEventType.CORRECTION_RECORDED, occurredAt = command.occurredAt,
        employeeId = assignee?.employeeId, employeeNameSnapshot = assignee?.employeeName,
        requestId = command.requestId, payload = payload,
    )
}
