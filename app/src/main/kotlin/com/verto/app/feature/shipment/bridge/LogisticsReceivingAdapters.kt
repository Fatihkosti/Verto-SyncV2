package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.LogisticsReceivingLineEntity
import com.verto.app.data.local.entity.LogisticsRecoveryEntity
import com.verto.app.data.local.entity.LogisticsRecoveryLineEntity
import com.verto.app.data.local.entity.LogisticsRecoveryPostingEntity
import com.verto.app.data.local.entity.LogisticsShortageEntity
import com.verto.app.data.local.entity.LogisticsReceivingBatchEntity
import com.verto.app.data.local.entity.LogisticsInventoryPostingEntity
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.inventory.domain.port.ReceiveShipmentStockPort
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingLine
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingBatch
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsRecovery
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryEconomics
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryLine
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsShortageIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageQuantity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageStatus
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryPostingPort
import com.verto.app.feature.shipment.domain.port.LogisticsReceivingTransactionPort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json

internal class LogisticsReceivingStoreAdapter(private val database: AppDatabase) {
    private val dao get() = database.logisticsDao()
    private val json = Json { encodeDefaults = true }

    suspend fun saveReceivingBatch(
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
    ) {
        database.withTransaction {
            persistReceivingBatchRows(batch, updatedShipment, event)
        }
    }

    /**
     * Writes only the Logistics-side rows. The caller must already own the Room transaction.
     * Session v223 uses this from the receiving transaction coordinator so stock + batch +
     * shipment state + posting audit + request event commit atomically.
     */
    internal suspend fun persistReceivingBatchRows(
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
    ) {
        requireSameTenant(batch.organizationId, updatedShipment.organizationId)
        requireSameTenant(batch.organizationId, event.organizationId)
        require(batch.shipmentId == updatedShipment.id && batch.shipmentId == event.shipmentId) {
            "Receiving batch/event belongs to another shipment"
        }
        require(batch.requestId == event.requestId) { "Receiving batch/event requestId mismatch" }
        require(batch.lines.all { it.shipmentId == batch.shipmentId }) { "Receiving line belongs to another shipment" }
        require(dao.getShipment(batch.organizationId, batch.shipmentId) != null) { "Shipment not found" }
        require(dao.updateShipment(updatedShipment.toEntityV2()) == 1) { "Shipment update failed" }
        dao.insertReceivingBatch(batch.toEntityV2())
        dao.insertReceivingLines(batch.lines.map { it.toEntityV2(batch.organizationId, batch.id) })
        dao.insertInventoryPostings(
            batch.lines.filter { it.acceptedQuantity > 0 }.map { line ->
                LogisticsInventoryPostingEntity(
                    organizationId = batch.organizationId,
                    postingId = "logistics-receipt:${batch.id}:${line.id}",
                    shipmentId = batch.shipmentId,
                    receivingBatchId = batch.id,
                    receivingLineId = line.id,
                    quantity = line.acceptedQuantity,
                )
            },
        )
        dao.insertEvent(event.toEntityV2(json))
    }


    suspend fun upsertShortage(shortage: LogisticsShortage): LogisticsShortage {
        LogisticsValidation.validateShortage(shortage)
        val identity = shortage.identity
        return database.withTransaction {
            require(dao.getShipment(identity.organizationId, identity.shipmentId) != null) { "Shipment not found" }
            require(dao.getLines(identity.organizationId, identity.shipmentId).any { it.id == identity.shipmentLineId }) {
                "Shortage line does not belong to shipment"
            }
            dao.findShortageByRequest(identity.organizationId, identity.shipmentId, shortage.requestId)?.let { existingRequest ->
                require(existingRequest.shipmentLineId == identity.shipmentLineId) {
                    "Shortage requestId belongs to another shipment line"
                }
                return@withTransaction existingRequest.toDomainV2()
            }
            val existing = dao.findShortageForLine(identity.organizationId, identity.shipmentId, identity.shipmentLineId)
            if (existing != null) {
                require(existing.originalMissingQuantity == shortage.quantity.originalMissingQuantity) {
                    "Shortage original quantity cannot be rewritten"
                }
                require(BigDecimal(existing.basePurchaseUnitPriceSnapshot) == shortage.quantity.basePurchaseUnitPriceSnapshot) {
                    "Shortage purchase price snapshot cannot be rewritten"
                }
            }
            val entity = shortage.toEntityV2().also { row ->
                if (existing != null) row.id = existing.id
            }
            if (existing == null) {
                dao.insertShortage(entity)
            } else {
                require(dao.updateShortage(entity) == 1) { "Shortage update failed" }
            }
            entity.toDomainV2()
        }
    }

    suspend fun getShortages(organizationId: String, shipmentId: String): List<LogisticsShortage> =
        dao.getShortages(organizationId, shipmentId).map(LogisticsShortageEntity::toDomainV2)

    suspend fun findShortageByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsShortage? = dao.findShortageByRequest(organizationId, shipmentId, requestId)?.toDomainV2()

    suspend fun createRecovery(recovery: LogisticsRecovery): LogisticsRecovery {
        LogisticsValidation.validateRecovery(recovery)
        return database.withTransaction {
            require(dao.getShipment(recovery.organizationId, recovery.shipmentId) != null) { "Shipment not found" }
            dao.findRecoveryByRequest(recovery.organizationId, recovery.shipmentId, recovery.requestId)?.let {
                return@withTransaction it.toDomainV2()
            }
            dao.insertRecovery(recovery.toEntityV2())
            recovery
        }
    }

    suspend fun getRecoveries(organizationId: String, shipmentId: String): List<LogisticsRecovery> =
        dao.getRecoveries(organizationId, shipmentId).map(LogisticsRecoveryEntity::toDomainV2)

    suspend fun findRecoveryByRequest(
        organizationId: String,
        shipmentId: String,
        requestId: String,
    ): LogisticsRecovery? = dao.findRecoveryByRequest(organizationId, shipmentId, requestId)?.toDomainV2()

    suspend fun createRecoveryLines(
        organizationId: String,
        shipmentId: String,
        lines: List<LogisticsRecoveryLine>,
    ): List<LogisticsRecoveryLine> {
        lines.forEach(LogisticsValidation::validateRecoveryLine)
        if (lines.isEmpty()) return emptyList()
        return database.withTransaction {
            require(dao.getShipment(organizationId, shipmentId) != null) { "Shipment not found" }
            val recoveryId = lines.first().recoveryId
            require(lines.all { it.organizationId == organizationId && it.recoveryId == recoveryId }) {
                "Recovery lines must belong to one recovery and organization"
            }
            val existingLines = dao.getRecoveryLines(organizationId, recoveryId)
            if (existingLines.isNotEmpty()) {
                val persisted = existingLines.map(LogisticsRecoveryLineEntity::toDomainV2)
                val requested = lines.associate { it.shortageId to it.recoveredQuantity }
                val existing = persisted.associate { it.shortageId to it.recoveredQuantity }
                require(requested == existing) { "Recovery retry differs from persisted recovery lines" }
                return@withTransaction persisted
            }

            val recoveries = dao.getRecoveries(organizationId, shipmentId).associateBy { it.id }
            require(recoveryId in recoveries) { "Recovery line recovery does not belong to shipment" }
            val shortages = dao.getShortages(organizationId, shipmentId).associateBy { it.id }
            val shipmentLineIds = dao.getLines(organizationId, shipmentId).mapTo(mutableSetOf()) { it.id }
            val updates = lines.map { line ->
                val shortage = shortages[line.shortageId] ?: error("Recovery line shortage does not belong to shipment")
                require(shortage.shipmentLineId == line.shipmentLineId && line.shipmentLineId in shipmentLineIds) {
                    "Recovery line shipment line mismatch"
                }
                require(line.recoveredQuantity <= shortage.remainingMissingQuantity) {
                    "Recovered quantity cannot exceed remaining missing quantity"
                }
                shortage.remainingMissingQuantity -= line.recoveredQuantity
                shortage.status = when {
                    shortage.remainingMissingQuantity == 0 -> LogisticsShortageStatus.RECOVERED.name
                    shortage.remainingMissingQuantity < shortage.originalMissingQuantity -> LogisticsShortageStatus.PARTIALLY_RECOVERED.name
                    else -> LogisticsShortageStatus.OPEN.name
                }
                shortage
            }
            dao.insertRecoveryLines(lines.map { it.toEntityV2(organizationId) })
            updates.forEach { shortage -> require(dao.updateShortage(shortage) == 1) { "Shortage recovery update failed" } }
            lines
        }
    }

    suspend fun getRecoveryLines(
        organizationId: String,
        recoveryId: String,
    ): List<LogisticsRecoveryLine> =
        dao.getRecoveryLines(organizationId, recoveryId).map(LogisticsRecoveryLineEntity::toDomainV2)

    suspend fun saveRecoveryPosting(posting: LogisticsRecoveryPosting): LogisticsRecoveryPosting {
        LogisticsValidation.validateRecoveryPosting(posting)
        return database.withTransaction {
            dao.findRecoveryPostingForLine(posting.organizationId, posting.recoveryLineId)?.let {
                return@withTransaction it.toDomainV2()
            }
            require(dao.getRecoveries(posting.organizationId, posting.shipmentId).any { it.id == posting.recoveryId }) {
                "Recovery posting recovery does not belong to shipment"
            }
            val recoveryLine = dao.getRecoveryLines(posting.organizationId, posting.recoveryId)
                .singleOrNull { it.id == posting.recoveryLineId }
                ?: error("Recovery posting line not found")
            require(recoveryLine.shipmentLineId == posting.shipmentLineId) { "Recovery posting line mismatch" }
            dao.insertRecoveryPosting(posting.toEntityV2())
            posting
        }
    }

    suspend fun getRecoveryPosting(
        organizationId: String,
        recoveryLineId: String,
    ): LogisticsRecoveryPosting? =
        dao.findRecoveryPostingForLine(organizationId, recoveryLineId)?.toDomainV2()
}

internal enum class LogisticsReceivingFailurePoint {
    BEFORE_INVENTORY_POSTING,
    AFTER_FIRST_INVENTORY_POSTING,
    BEFORE_BATCH_INSERT,
}

internal fun interface LogisticsReceivingFailureInjector {
    suspend fun hit(point: LogisticsReceivingFailurePoint)
}

/**
 * v223 atomic receiving coordinator. Both the InventoryDao posting transaction and the
 * Logistics rows execute under this same outer AppDatabase transaction. Any exception
 * rolls back quantities, inventory movements, receiving rows, shipment state and event.
 */
class RoomLogisticsReceivingTransactionAdapter @Inject constructor(
    private val database: AppDatabase,
    private val inventory: ReceiveShipmentStockPort,
    private val outbox: UnifiedOutboxWriter,
) : LogisticsReceivingTransactionPort {
    private val receiving = LogisticsReceivingStoreAdapter(database)

    override suspend fun commitReceiving(
        postings: List<LogisticsInventoryPosting>,
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
    ) {
        commitReceiving(postings, batch, updatedShipment, event, LogisticsReceivingFailureInjector { _ -> })
    }

    internal suspend fun commitReceiving(
        postings: List<LogisticsInventoryPosting>,
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
        failureInjector: LogisticsReceivingFailureInjector,
    ) {
        validateAtomicCommit(postings, batch, updatedShipment, event)
        database.withTransaction {
            failureInjector.hit(LogisticsReceivingFailurePoint.BEFORE_INVENTORY_POSTING)
            postings.forEachIndexed { index, posting ->
                inventory.receive(
                    postingId = posting.postingId,
                    shipmentId = posting.shipmentId,
                    receivingBatchId = posting.receivingBatchId,
                    receivingLineId = posting.receivingLineId,
                    itemId = posting.itemId,
                    quantity = posting.quantity,
                    supplierId = posting.supplierId,
                    unitPrice = posting.unitPrice,
                    note = posting.note,
                ).getOrThrow()
                if (index == 0) {
                    failureInjector.hit(LogisticsReceivingFailurePoint.AFTER_FIRST_INVENTORY_POSTING)
                }
            }
            failureInjector.hit(LogisticsReceivingFailurePoint.BEFORE_BATCH_INSERT)
            receiving.persistReceivingBatchRows(batch, updatedShipment, event)
            val mutationId = UUID.nameUUIDFromBytes(
                "v307|SHIPMENT|${batch.organizationId}|${batch.shipmentId}|RECEIVING|${event.requestId}".toByteArray(Charsets.UTF_8)
            ).toString()
            outbox.enqueue(
                organizationId = batch.organizationId,
                aggregateType = "SHIPMENT",
                aggregateId = batch.shipmentId,
                operationType = "COMMAND",
                mutationId = mutationId,
                commandBatchId = event.requestId,
                commandOrder = postings.size,
                payload = mapOf(
                    "command" to "COMMIT_RECEIVING",
                    "batchId" to batch.id,
                    "requestId" to event.requestId,
                    "state" to updatedShipment.state.name,
                ),
            )
        }
    }

    private fun validateAtomicCommit(
        postings: List<LogisticsInventoryPosting>,
        batch: LogisticsReceivingBatch,
        updatedShipment: LogisticsShipment,
        event: LogisticsEvent,
    ) {
        requireSameTenant(batch.organizationId, updatedShipment.organizationId)
        requireSameTenant(batch.organizationId, event.organizationId)
        require(batch.shipmentId == updatedShipment.id && batch.shipmentId == event.shipmentId) {
            "Receiving transaction belongs to another shipment"
        }
        require(batch.requestId == event.requestId) { "Receiving transaction requestId mismatch" }
        require(postings.map { it.postingId }.toSet().size == postings.size) { "Duplicate receiving postingId" }
        require(postings.map { it.receivingLineId }.toSet().size == postings.size) { "Duplicate receiving posting line" }

        val accepted = batch.lines.filter { it.acceptedQuantity > 0 }.associateBy { it.id }
        require(postings.size == accepted.size) { "Accepted receiving lines/postings mismatch" }
        postings.forEach { posting ->
            require(posting.postingId == "logistics-receipt:${batch.id}:${posting.receivingLineId}") {
                "Receiving postingId mismatch"
            }
            require(posting.shipmentId == batch.shipmentId && posting.receivingBatchId == batch.id) {
                "Inventory posting belongs to another receiving batch"
            }
            val line = accepted[posting.receivingLineId] ?: error("Inventory posting has no accepted receiving line")
            require(posting.quantity == line.acceptedQuantity) { "Inventory posting quantity mismatch" }
            require(posting.itemId.isNotBlank()) { "itemId is required" }
            require(posting.supplierId.isNotBlank()) { "supplierId is required" }
            require(posting.quantity > 0) { "Only positive accepted quantity can be posted" }
            require(posting.unitPrice.signum() >= 0) { "unitPrice must be non-negative" }
        }
    }
}

class InventoryLogisticsV2PostingAdapter @Inject constructor(
    private val inventory: ReceiveShipmentStockPort,
) : LogisticsInventoryPostingPort {
    override suspend fun postAcceptedStock(posting: LogisticsInventoryPosting) {
        inventory.receive(
            postingId = posting.postingId,
            shipmentId = posting.shipmentId,
            receivingBatchId = posting.receivingBatchId,
            receivingLineId = posting.receivingLineId,
            itemId = posting.itemId,
            quantity = posting.quantity,
            supplierId = posting.supplierId,
            unitPrice = posting.unitPrice,
            note = posting.note,
        ).getOrThrow()
    }
}

internal fun LogisticsReceivingBatch.toEntityV2() = LogisticsReceivingBatchEntity(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    requestId = requestId,
    receivedAt = receivedAt,
    receivedByEmployeeId = receivedByEmployeeId,
    receivedByEmployeeNameSnapshot = receivedByEmployeeNameSnapshot,
)

internal fun LogisticsReceivingBatchEntity.toDomainV2(lines: List<LogisticsReceivingLine>) = LogisticsReceivingBatch(
    id = id,
    organizationId = organizationId,
    shipmentId = shipmentId,
    requestId = requestId,
    receivedAt = receivedAt,
    receivedByEmployeeId = receivedByEmployeeId,
    receivedByEmployeeNameSnapshot = receivedByEmployeeNameSnapshot,
    lines = lines,
)

internal fun LogisticsReceivingLine.toEntityV2(organizationId: String, batchId: String) = LogisticsReceivingLineEntity(
    organizationId = organizationId,
    id = id,
    batchId = batchId,
    shipmentId = shipmentId,
    shipmentLineId = shipmentLineId,
    expectedQuantitySnapshot = expectedQuantitySnapshot,
    receivedQuantity = receivedQuantity,
    acceptedQuantity = acceptedQuantity,
    damagedQuantity = damagedQuantity,
    rejectedQuantity = rejectedQuantity,
    quarantinedQuantity = quarantinedQuantity,
)

internal fun LogisticsReceivingLineEntity.toDomainV2() = LogisticsReceivingLine(
    id = id,
    shipmentId = shipmentId,
    shipmentLineId = shipmentLineId,
    expectedQuantitySnapshot = expectedQuantitySnapshot,
    receivedQuantity = receivedQuantity,
    acceptedQuantity = acceptedQuantity,
    damagedQuantity = damagedQuantity,
    rejectedQuantity = rejectedQuantity,
    quarantinedQuantity = quarantinedQuantity,
)


internal fun LogisticsShortage.toEntityV2() = LogisticsShortageEntity().also { row ->
    row.organizationId = identity.organizationId
    row.id = identity.id
    row.shipmentId = identity.shipmentId
    row.shipmentLineId = identity.shipmentLineId
    row.originalMissingQuantity = quantity.originalMissingQuantity
    row.remainingMissingQuantity = quantity.remainingMissingQuantity
    row.basePurchaseUnitPriceSnapshot = quantity.basePurchaseUnitPriceSnapshot.toPlainString()
    row.status = status.name
    row.detectedAt = detectedAt
    row.note = note
    row.requestId = requestId
}

internal fun LogisticsShortageEntity.toDomainV2() = LogisticsShortage(
    identity = LogisticsShortageIdentity(
        organizationId = organizationId,
        id = id,
        shipmentId = shipmentId,
        shipmentLineId = shipmentLineId,
    ),
    quantity = LogisticsShortageQuantity(
        originalMissingQuantity = originalMissingQuantity,
        remainingMissingQuantity = remainingMissingQuantity,
        basePurchaseUnitPriceSnapshot = BigDecimal(basePurchaseUnitPriceSnapshot),
    ),
    status = LogisticsShortageStatus.valueOf(status),
    detectedAt = detectedAt,
    note = note,
    requestId = requestId,
)

internal fun LogisticsRecovery.toEntityV2() = LogisticsRecoveryEntity().also { row ->
    row.organizationId = organizationId
    row.id = id
    row.shipmentId = shipmentId
    row.recoveredAt = recoveredAt
    row.employeeId = employee.employeeId
    row.employeeNameSnapshot = employee.employeeName
    row.note = note
    row.requestId = requestId
}

internal fun LogisticsRecoveryEntity.toDomainV2() = LogisticsRecovery(
    organizationId = organizationId,
    id = id,
    shipmentId = shipmentId,
    recoveredAt = recoveredAt,
    employee = LogisticsAssigneeSnapshot(employeeId = employeeId, employeeName = employeeNameSnapshot),
    note = note,
    requestId = requestId,
)

internal fun LogisticsRecoveryLine.toEntityV2(organizationId: String) = LogisticsRecoveryLineEntity().also { row ->
    row.organizationId = organizationId
    row.id = id
    row.recoveryId = recoveryId
    row.shortageId = shortageId
    row.shipmentLineId = shipmentLineId
    row.recoveredQuantity = recoveredQuantity
    row.basePurchaseUnitPriceSnapshot = economics.basePurchaseUnitPriceSnapshot.toPlainString()
    row.allocatedRecoveryCost = economics.allocatedRecoveryCost.toPlainString()
}

internal fun LogisticsRecoveryLineEntity.toDomainV2() = LogisticsRecoveryLine(
    organizationId = organizationId,
    id = id,
    recoveryId = recoveryId,
    shortageId = shortageId,
    shipmentLineId = shipmentLineId,
    recoveredQuantity = recoveredQuantity,
    economics = LogisticsRecoveryEconomics(
        basePurchaseUnitPriceSnapshot = BigDecimal(basePurchaseUnitPriceSnapshot),
        allocatedRecoveryCost = BigDecimal(allocatedRecoveryCost),
    ),
)

internal fun LogisticsRecoveryPosting.toEntityV2() = LogisticsRecoveryPostingEntity().also { row ->
    row.organizationId = organizationId
    row.postingId = postingId
    row.recoveryId = recoveryId
    row.recoveryLineId = recoveryLineId
    row.shipmentId = shipmentId
    row.shipmentLineId = shipmentLineId
    row.quantity = quantity
}

internal fun LogisticsRecoveryPostingEntity.toDomainV2() = LogisticsRecoveryPosting(
    organizationId = organizationId,
    postingId = postingId,
    recoveryId = recoveryId,
    recoveryLineId = recoveryLineId,
    shipmentId = shipmentId,
    shipmentLineId = shipmentLineId,
    quantity = quantity,
)
