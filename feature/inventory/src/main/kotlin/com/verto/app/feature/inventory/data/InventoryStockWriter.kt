package com.verto.app.feature.inventory.data

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.data.local.entity.InventoryStockOutboxEntity
import com.verto.app.data.local.entity.InventoryCostOutboxEntity
import com.verto.app.data.local.entity.InventoryWriteGuardEntity
import com.verto.app.feature.inventory.domain.model.InventoryCostRevaluationRecord
import com.verto.app.feature.inventory.domain.model.InventoryPurchaseReceiptCommand
import com.verto.app.data.sync.SpecializedMutationCaptureV2
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncSourceOwner
import com.verto.app.data.sync.push.UnifiedStrongerSourceFactory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v259 single local stock-write gateway.
 *
 * The outer Room transaction owns: command claim -> stock snapshot mutation -> movement(s) ->
 * canonical movement identity -> transactional outbox. Any exception rolls all of it back.
 */
@Singleton
class InventoryStockWriter @Inject constructor(
    private val database: AppDatabase,
    private val inventoryDao: InventoryDao,
    private val sessionReader: SessionReader,
    private val authorization: InventoryStockWriteAuthorization,
    private val specializedCapture: SpecializedMutationCaptureV2,
    private val writeGate: InventoryWriteGate = InventoryWriteGate(),
) {
    suspend fun open(
        itemId: String,
        quantity: Int,
        reason: String = "رصيد افتتاحي",
        commandId: String = UUID.randomUUID().toString(),
    ): Result<Unit> = writeUnit(commandId, "OPEN") { actorId ->
        inventoryDao.openStockAtomic(
            itemId = itemId,
            quantity = quantity,
            note = reason,
            sourceWriteId = commandId,
            actorId = actorId,
        )
    }

    suspend fun issue(
        request: InventoryIssueRequest,
        meta: InventoryWriteMeta = InventoryWriteMeta(),
    ): Result<Unit> = writeUnit(meta.commandId, "ISSUE") {
        inventoryDao.deductStockAtomic(
            itemId = request.itemId,
            quantity = request.quantity,
            invoiceId = request.invoiceId,
            clientId = request.clientId,
            unitPrice = request.unitPrice,
            note = meta.note,
            allowNegativeStock = request.allowNegativeStock,
            sourceWriteId = meta.commandId,
            sourceLineId = meta.sourceLineId,
            postingGroupId = meta.postingGroupId,
        )
    }

    suspend fun receive(
        request: InventoryReceiveRequest,
        meta: InventoryWriteMeta = InventoryWriteMeta(),
    ): Result<Unit> = writeUnit(meta.commandId, "RECEIVE") {
        inventoryDao.addStockAtomic(
            itemId = request.itemId,
            quantity = request.quantity,
            invoiceId = request.invoiceId,
            supplierId = request.supplierId,
            unitPrice = request.unitPrice,
            note = meta.note,
            sourceWriteId = meta.commandId,
            sourceLineId = meta.sourceLineId,
            postingGroupId = meta.postingGroupId,
        )
    }

    suspend fun adjust(
        itemId: String,
        newQuantity: Int,
        reason: String,
        commandId: String = UUID.randomUUID().toString(),
    ): Result<Unit> {
        if (reason.isBlank()) return Result.failure(IllegalArgumentException("سبب تعديل المخزون مطلوب"))
        if (!authorization.canAdjust()) {
            return Result.failure(SecurityException("لا توجد صلاحية لتعديل المخزون"))
        }
        return writeUnit(commandId, "ADJUST") { actorId ->
            inventoryDao.adjustStockAtomic(
                itemId = itemId,
                newQuantity = newQuantity,
                note = reason,
                sourceWriteId = commandId,
                actorId = actorId,
            )
        }
    }

    suspend fun reverseInvoice(
        invoiceId: String,
        commandId: String,
    ): Result<Unit> {
        val session = sessionReader.snapshot()
        return writeUnit(commandId, "REVERSE", allowNoMovement = true) {
        inventoryDao.reverseInvoiceMovementsAtomic(session.organization.id, session.user.id, invoiceId, commandId)
        Result.success(Unit)
        }
    }

    suspend fun receivePurchaseAtLatestPrice(
        command: InventoryPurchaseReceiptCommand,
    ): InventoryCostRevaluationRecord? {
        val commandId = command.writeId.ifBlank { UUID.randomUUID().toString() }
        val trustedSession = sessionReader.snapshot()
        return writeValue(commandId, "RECEIVE_PURCHASE", null) { _ ->
            inventoryDao.receivePurchaseAtLatestPriceAtomic(
                organizationId = trustedSession.organization.id,
                itemId = command.itemId,
                quantity = command.quantity,
                invoiceId = command.invoiceId,
                supplierId = command.supplierId,
                buyPriceMinor = command.buyPriceMinor,
                sellPriceMinor = command.sellPriceMinor,
                actorId = trustedSession.user.id,
                actorName = trustedSession.user.name,
                occurredAt = command.occurredAt,
                writeId = commandId,
                eventId = command.eventId,
                sourceType = command.sourceType,
                sourceId = command.sourceId,
                sourceLineId = command.sourceLineId,
                postingGroupId = command.postingGroupId,
            )?.let { event ->
                InventoryCostRevaluationRecord(
                    itemId = event.itemId,
                    quantityBefore = event.quantityBefore,
                    oldUnitCostMinor = event.oldUnitCostMinor,
                    newUnitCostMinor = event.newUnitCostMinor,
                    revaluationDifferenceMinor = event.revaluationDifferenceMinor,
                )
            }
        }
    }

    suspend fun receiveShipment(
        request: ShipmentStockReceiptRequest,
        details: InventoryReceiptDetails,
    ): Result<Unit> {
        val session = sessionReader.snapshot()
        return writeUnit(request.postingId, "RECEIVE_SHIPMENT") {
        inventoryDao.receiveShipmentStockAtomic(
            organizationId = session.organization.id,
            actorId = session.user.id,
            postingId = request.postingId,
            shipmentId = request.shipmentId,
            receivingBatchId = request.receivingBatchId,
            receivingLineId = request.receivingLineId,
            itemId = request.itemId,
            quantity = request.quantity,
            supplierId = details.partyId,
            unitPrice = details.unitPrice,
            note = details.note,
        )
        }
    }

    suspend fun applyShipmentLandedCost(
        postingId: String,
        shipmentId: String,
        unitPriceMinor: Long,
    ): Result<Unit> {
        val session = sessionReader.snapshot()
        val commandId = "landed:$postingId:$unitPriceMinor"
        return writeUnit(commandId, "APPLY_LANDED_COST", allowNoMovement = true) {
            inventoryDao.applyShipmentLandedCostAtomic(
                organizationId = session.organization.id,
                actorId = session.user.id,
                postingId = postingId,
                shipmentId = shipmentId,
                unitPriceMinor = unitPriceMinor,
            )
        }
    }

    suspend fun reverseShipment(
        shipmentId: String,
        commandId: String = "shipment-reverse:$shipmentId",
    ): Result<Unit> = writeUnit(commandId, "REVERSE_SHIPMENT", allowNoMovement = true) {
        inventoryDao.reverseShipmentReceiptsAtomic(shipmentId, commandId)
    }

    suspend fun returnStock(
        request: SalesReturnStockRequest,
        timing: InventoryWriteTiming,
    ) = writeUnit(timing.commandId, "RETURN_STOCK") {
        inventoryDao.restoreSalesReturnAtomic(
            request.itemId,
            request.quantity,
            request.returnId,
            request.returnLineId,
            request.clientId,
            request.historicalUnitCostMinor,
            timing.occurredAt,
            timing.commandId,
        )
        Result.success(Unit)
    }.getOrThrow()

    suspend fun reverse(invoiceId: String, commandId: String): Result<Unit> =
        reverseInvoice(invoiceId, commandId)

    @Suppress("UNUSED_PARAMETER")
    suspend fun deductPurchaseReturn(
        target: PurchaseReturnStockTarget,
        source: PurchaseReturnSource,
        policy: PurchaseReturnPolicy,
        timing: InventoryWriteTiming,
        actor: InventoryWriteActor,
    ): Unit {
        val trustedSession = sessionReader.snapshot()
        writeUnit(timing.commandId, "RETURN_STOCK") {
        inventoryDao.deductPurchaseReturnAtomic(
            target.itemId,
            target.quantity,
            source.returnId,
            source.returnLineId,
            target.supplierId,
            source.originalInvoiceId,
            source.originalInvoiceItemId,
            policy.internationalPurchase,
            target.originalUnitCostMinor,
            policy.sourceStillValidForItem,
            timing.occurredAt,
            timing.commandId,
            trustedSession.user.id,
            trustedSession.user.name,
        )
        Result.success(Unit)
        }.getOrThrow()
    }

    private suspend fun writeUnit(
        commandId: String,
        operation: String,
        allowNoMovement: Boolean = false,
        block: suspend (actorId: String) -> Result<Unit>,
    ): Result<Unit> = runCatching {
        writeValue(commandId, operation, Unit, allowNoMovement) { actorId ->
            block(actorId).getOrThrow()
            Unit
        }
        Unit
    }

    private suspend fun <T> writeValue(
        commandId: String,
        operation: String,
        duplicateValue: T,
        allowNoMovement: Boolean = false,
        block: suspend (actorId: String) -> T,
    ): T {
        require(commandId.isNotBlank()) { "inventory commandId is required" }
        check(writeGate.isEnabled()) { "كتابة المخزون متوقفة مؤقتاً للصيانة" }
        val session = sessionReader.snapshot()
        val organizationId = session.organization.id
        val actorId = session.user.id
        require(organizationId.isNotBlank()) { "inventory organization identity is required" }
        require(actorId.isNotBlank()) { "inventory actor identity is required" }
        val now = System.currentTimeMillis()
        val guard = InventoryWriteGuardEntity().apply {
            id = "$organizationId:$commandId"
            this.organizationId = organizationId
            this.commandId = commandId
            idempotencyKey = commandId
            this.operation = operation
            this.actorId = actorId
            actorName = session.user.name
            createdAt = now
        }

        return database.withTransaction {
            if (inventoryDao.claimInventoryWrite(guard) == -1L) return@withTransaction duplicateValue

            val value = block(actorId)
            inventoryDao.canonicalizeMovementsForWrite(
                organizationId = organizationId,
                commandId = commandId,
                actorId = actorId,
                recordedAt = now,
            )
            val movements = inventoryDao.getMovementsByWriteId(commandId)
            check(allowNoMovement || movements.isNotEmpty()) { "inventory write committed without movement" }
            movements.forEach { movement ->
                val signed = movement.signedBaseQuantity
                    ?: (movement.quantityAfter.toLong() - movement.quantityBefore.toLong())
                val outbox = InventoryStockOutboxEntity().apply {
                    id = "$organizationId:${movement.id}"
                    this.organizationId = organizationId
                    this.commandId = commandId
                    idempotencyKey = "$commandId:${movement.id}"
                    movementId = movement.id
                    itemId = movement.itemId
                    this.operation = operation
                    signedBaseQuantity = signed
                    createdAt = now
                }
                inventoryDao.insertInventoryStockOutbox(outbox)
                val canonical = requireNotNull(inventoryDao.getMovementById(movement.id))
                specializedCapture.capture(
                    SyncSourceOwner.INVENTORY_STOCK, outbox.id, outbox.id,
                    UnifiedStrongerSourceFactory.inventoryMovement(outbox, canonical),
                    setOf(ProtectedSyncKey("INVENTORY_MOVEMENT", movement.id), ProtectedSyncKey("INVENTORY_ITEM", movement.itemId)),
                )
            }
            inventoryDao.getCostRevisionsByCommandId(commandId).forEach { revision ->
                val costOutbox = InventoryCostOutboxEntity(
                    id = "$organizationId:${revision.costRevisionId}",
                    organizationId = organizationId,
                    commandId = commandId,
                    costRevisionId = revision.costRevisionId,
                    createdAt = now,
                )
                check(inventoryDao.insertInventoryCostOutbox(costOutbox) != -1L) {
                    "inventory cost outbox identity collision"
                }
                specializedCapture.capture(
                    SyncSourceOwner.INVENTORY_COST, costOutbox.id, costOutbox.id,
                    UnifiedStrongerSourceFactory.inventoryCost(costOutbox, revision),
                    setOf(ProtectedSyncKey("INVENTORY_COST_REVISION", revision.costRevisionId), ProtectedSyncKey("INVENTORY_ITEM", revision.itemId)),
                )
            }
            value
        }
    }
}
