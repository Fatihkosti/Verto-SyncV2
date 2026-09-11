package com.verto.app.feature.inventory.data

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.MovementType
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.inventory.domain.port.InventoryLandedCostAdjustment
import com.verto.app.feature.inventory.domain.port.InventoryLandedCostAdjustmentPort
import com.verto.app.money.Money
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInventoryLandedCostAdjustmentAdapter @Inject constructor(
    private val database: AppDatabase,
    private val dao: InventoryDao,
    private val outbox: UnifiedOutboxWriter,
) : InventoryLandedCostAdjustmentPort {
    override suspend fun apply(command: InventoryLandedCostAdjustment): Boolean =
        database.withTransaction {
            val newBuyPriceMinor = Money.fromLegacyDouble(command.newBuyPrice).amountMinor
            require(newBuyPriceMinor > 0L) { "new buy price minor must be positive" }
            val item = dao.getItemByIdSync(command.itemId) ?: return@withTransaction false
            val legacyPrice = Money.ofMinor(newBuyPriceMinor).toLegacyDouble()
            dao.updateBuyPrice(item.id, legacyPrice)
            val persisted = dao.getItemByIdSync(item.id) ?: error("inventory item disappeared during landed-cost update")
            check(persisted.buyPriceMinor == newBuyPriceMinor) {
                "FAIL_EXACT_ALLOCATION_UNREPRESENTABLE: inventory compatibility write changed minor-unit value"
            }
            dao.insertMovement(
                InventoryMovementEntity(
                    id = command.movementId,
                    itemId = item.id,
                    movementType = MovementType.ADJUST,
                    quantity = 0,
                    quantityBefore = item.quantity,
                    quantityAfter = item.quantity,
                    unitPrice = legacyPrice,
                    unitPriceMinor = newBuyPriceMinor,
                    note = command.movementNote,
                    sourceType = "EXPENSE_LANDED_COST",
                    sourceId = command.sourceExpenseId,
                    writeId = command.movementMutationId,
                )
            )
            outbox.enqueue(
                organizationId = command.organizationId,
                aggregateType = "INVENTORY_ITEM",
                aggregateId = item.id,
                operationType = "UPSERT",
                payload = mapOf(
                    "buyPrice" to legacyPrice,
                    "buyPriceMinor" to newBuyPriceMinor,
                    "sourceExpenseId" to command.sourceExpenseId,
                ),
                mutationId = command.itemMutationId,
                commandBatchId = command.commandBatchId,
                commandOrder = command.commandOrder,
            )
            outbox.enqueue(
                organizationId = command.organizationId,
                aggregateType = "INVENTORY_MOVEMENT",
                aggregateId = command.movementId,
                operationType = "COMMAND",
                payload = mapOf(
                    "itemId" to item.id,
                    "movementType" to MovementType.ADJUST.name,
                    "quantity" to "0",
                    "unitPrice" to legacyPrice,
                    "unitPriceMinor" to newBuyPriceMinor,
                    "sourceExpenseId" to command.sourceExpenseId,
                ),
                mutationId = command.movementMutationId,
                commandBatchId = command.commandBatchId,
                commandOrder = command.commandOrder + 1,
                dependsOnMutationId = command.itemMutationId,
            )
            true
        }
}
