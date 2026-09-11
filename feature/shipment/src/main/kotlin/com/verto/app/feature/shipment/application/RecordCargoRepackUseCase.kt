package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyPosition
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsRepackProof
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordCargoRepackCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.LogisticsDocumentStoragePort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.port.LogisticsStoredDocument
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Records a physical package-count change without pretending that custody changed. */
class RecordCargoRepackUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val storage: LogisticsDocumentStoragePort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: RecordCargoRepackCommand,
    ): LogisticsCustodyHandoff {
        validateCommand(organizationId, command)
        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) {
            return aggregate.custodyHandoffs.singleOrNull { it.requestId == command.requestId }
                ?: error("Request was already processed by another logistics operation")
        }
        validateOperationalContext(aggregate, command)
        val cargo = currentCargo(aggregate, command)
        val custodian = currentCustodian(aggregate)
        val repack = newRepack(organizationId, command, cargo, custodian)
        LogisticsValidation.validateConfirmedCustodyHandoff(repack)
        store.saveCustodyHandoff(repack, repackEvent(aggregate, command, repack, custodian))
        command.proof?.let { saveProof(aggregate, repack, it, command.occurredAt) }
        return repack
    }

    private fun validateCommand(organizationId: String, command: RecordCargoRepackCommand) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        LogisticsValidation.validateCargoRepack(
            command.change.previous, command.change.updated, command.change.reason, command.change.note,
        )
    }

    private fun validateOperationalContext(aggregate: LogisticsShipmentAggregate, command: RecordCargoRepackCommand) {
        require(aggregate.shipment.state in setOf(
            LogisticsShipmentState.READY, LogisticsShipmentState.WAITING_DEPARTURE, LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.AT_STATION, LogisticsShipmentState.CUSTOMS, LogisticsShipmentState.ARRIVED, LogisticsShipmentState.PARTIAL,
        )) { "Cargo repack is outside the allowed operational states" }
        command.milestoneId?.let { milestoneId ->
            require(aggregate.milestones.any { it.id == milestoneId }) { "Repack milestone does not belong to shipment" }
        }
    }

    private fun currentCargo(aggregate: LogisticsShipmentAggregate, command: RecordCargoRepackCommand): LogisticsCargoSnapshot {
        val cargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate)
            ?: error("Confirmed cargo snapshot is required before repack")
        require(command.change.previous.packageCount == cargo.packageCount) {
            "Previous package count is stale; refresh from the latest confirmed cargo snapshot"
        }
        command.change.previous.weightKg?.let { previous ->
            cargo.weightKg?.let { confirmed ->
                require(previous.compareTo(confirmed) == 0) {
                    "Previous weight is stale; refresh from the latest confirmed cargo snapshot"
                }
            }
        }
        return cargo
    }

    private fun currentCustodian(aggregate: LogisticsShipmentAggregate): LogisticsCustodyPosition {
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values.toList()
        require(positions.isNotEmpty()) { "Shipment has no purchase sources" }
        val distinct = positions.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }
        require(distinct.size == 1) { "Shipment-wide repack requires all sources to be under one current custodian" }
        return distinct.single()
    }

    private fun newRepack(
        organizationId: String,
        command: RecordCargoRepackCommand,
        cargo: LogisticsCargoSnapshot,
        custodian: LogisticsCustodyPosition,
    ): LogisticsCustodyHandoff = LogisticsCustodyHandoff(
        id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
        milestoneId = command.milestoneId, fromHolderType = custodian.holderType, fromHolderId = custodian.holderId,
        fromHolderNameSnapshot = custodian.holderName, toHolderType = custodian.holderType, toHolderId = custodian.holderId,
        toHolderNameSnapshot = custodian.holderName, transferredAt = command.occurredAt, receivedAt = command.occurredAt,
        requestId = command.requestId, note = command.change.note.trim(), handoverPackageCount = cargo.packageCount,
        receivedPackageCount = command.change.updated.packageCount, handoverWeightKg = command.change.previous.weightKg ?: cargo.weightKg,
        receivedWeightKg = command.change.updated.weightKg ?: cargo.weightKg, packageChangeReason = command.change.reason,
        packageChangeNote = command.change.note.trim(),
    )

    private fun repackEvent(
        aggregate: LogisticsShipmentAggregate,
        command: RecordCargoRepackCommand,
        repack: LogisticsCustodyHandoff,
        custodian: LogisticsCustodyPosition,
    ) = LogisticsEvent(
        id = identities.newId(), organizationId = repack.organizationId, shipmentId = repack.shipmentId,
        type = LogisticsEventType.HANDOFF_RECORDED, occurredAt = command.occurredAt,
        employeeId = aggregate.shipment.assignee?.employeeId, employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
        requestId = command.requestId,
        payload = buildMap {
            put("operation", "CARGO_REPACK"); put("custodianType", custodian.holderType.name)
            custodian.holderId?.let { put("custodianId", it) }; put("custodianName", custodian.holderName)
            put("previousPackageCount", repack.handoverPackageCount.toString())
            put("newPackageCount", repack.receivedPackageCount.toString()); put("reason", command.change.reason.name)
            put("note", command.change.note.trim()); command.milestoneId?.let { put("milestoneId", it) }
            repack.handoverWeightKg?.let { put("previousWeightKg", it.toPlainString()) }
            repack.receivedWeightKg?.let { put("newWeightKg", it.toPlainString()) }
        },
    )

    private suspend fun saveProof(
        aggregate: LogisticsShipmentAggregate,
        repack: LogisticsCustodyHandoff,
        proof: LogisticsRepackProof,
        occurredAt: Long,
    ) {
        require(proof.sourceUri.isNotBlank()) { "Repack proof sourceUri is required" }
        require(proof.displayName.isNotBlank()) { "Repack proof displayName is required" }
        require(proof.mimeType.startsWith("image/")) { "Repack proof must be an image" }
        val documentId = identities.newId()
        val stored = storage.importPrivate(
            repack.organizationId, repack.shipmentId, documentId, proof.sourceUri, proof.displayName, proof.mimeType,
        )
        require(stored.sizeBytes > 0L && storage.exists(stored.privateUri)) { "Private repack proof copy was not created" }
        val document = repackDocument(repack, documentId, stored, occurredAt)
        LogisticsValidation.validateDocument(document)
        try {
            store.saveDocument(document, proofEvent(aggregate, repack, document, occurredAt))
        } catch (cancellation: CancellationException) {
            runCatching { storage.deletePrivate(stored.privateUri) }; throw cancellation
        } catch (error: IllegalStateException) {
            runCatching { storage.deletePrivate(stored.privateUri) }; throw error
        }
    }

    private fun repackDocument(
        repack: LogisticsCustodyHandoff,
        documentId: String,
        stored: LogisticsStoredDocument,
        occurredAt: Long,
    ) = LogisticsDocument(
        id = documentId, organizationId = repack.organizationId, shipmentId = repack.shipmentId, handoffId = repack.id,
        type = LogisticsDocumentType.OTHER, displayName = stored.displayName, mimeType = stored.mimeType,
        sizeBytes = stored.sizeBytes, privateUri = stored.privateUri, sha256 = stored.sha256, createdAt = occurredAt,
    )

    private fun proofEvent(
        aggregate: LogisticsShipmentAggregate,
        repack: LogisticsCustodyHandoff,
        document: LogisticsDocument,
        occurredAt: Long,
    ) = LogisticsEvent(
        id = identities.newId(), organizationId = repack.organizationId, shipmentId = repack.shipmentId,
        type = LogisticsEventType.DOCUMENT_ADDED, occurredAt = occurredAt,
        employeeId = aggregate.shipment.assignee?.employeeId, employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
        requestId = "${repack.requestId}:proof",
        payload = mapOf("documentId" to document.id, "documentType" to document.type.name, "handoffId" to repack.id, "proofKind" to "CARGO_REPACK"),
    )
}
