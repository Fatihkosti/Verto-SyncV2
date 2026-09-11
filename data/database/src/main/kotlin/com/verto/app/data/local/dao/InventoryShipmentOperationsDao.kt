package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryShipmentOperationsDao : InventoryStockTargetDao, InventoryItemLifecycleDao, InventoryCostSourceDao, InventoryCostDao, InventoryMovementDao, InventorySyncDao {


@Query("SELECT * FROM inventory_movements WHERE id = :postingId LIMIT 1")
suspend fun getMovementByIdForShipmentReceipt(postingId: String): InventoryMovementEntity?

@Transaction
suspend fun applyShipmentLandedCostAtomic(
    organizationId: String,
    actorId: String,
    postingId: String,
    shipmentId: String,
    unitPriceMinor: Long,
): Result<Unit> {
    if (postingId.isBlank() || shipmentId.isBlank())
        return Result.failure(Exception("معرف حركة الاستلام والشحنة مطلوبان"))
    if (unitPriceMinor < 0L)
        return Result.failure(Exception("سعر الوحدة غير صالح"))

    val movement = getMovementByIdForShipmentReceipt(postingId)
        ?: return Result.failure(Exception("حركة استلام الشحنة غير موجودة"))
    if (movement.shipmentId != shipmentId ||
        movement.movementType != MovementType.IN ||
        movement.sourceType != "SHIPMENT_RECEIPT" ||
        movement.sourceId != shipmentId) {
        return Result.failure(Exception("الحركة ليست استلام Logistics V2 للشحنة المحددة"))
    }
    val item = getItemByIdSync(movement.itemId)
        ?: return Result.failure(Exception("صنف حركة الاستلام غير موجود"))
    val newCost = Money.ofMinor(unitPriceMinor)
    val oldPostingCostMinor = movement.unitPriceMinor.takeIf { it != 0L }
        ?: Money.fromLegacyDouble(movement.unitPrice).amountMinor
    if (oldPostingCostMinor == newCost.amountMinor && item.buyPriceMinor == newCost.amountMinor) {
        return Result.success(Unit)
    }

    val occurredAt = System.currentTimeMillis()
    val inventoryDelta = Math.subtractExact(newCost.amountMinor, item.buyPriceMinor)
    val revaluationDifference = Math.multiplyExact(item.quantity.toLong(), inventoryDelta)

    if (oldPostingCostMinor != newCost.amountMinor) {
        val updatedMovement = updateShipmentReceiptMovementUnitPrice(
            postingId = postingId,
            shipmentId = shipmentId,
            unitPrice = newCost.toLegacyDouble(),
            unitPriceMinor = newCost.amountMinor,
        )
        check(updatedMovement == 1) { "تعذر تحديث تكلفة حركة الاستلام" }
    }
    if (item.buyPriceMinor != newCost.amountMinor) {
        val updatedItem = updateLatestPurchasePriceAtomic(
            id = item.id,
            buyPrice = newCost.toLegacyDouble(),
            buyPriceMinor = newCost.amountMinor,
            now = occurredAt,
        )
        check(updatedItem == 1) { "تعذر تحديث آخر سعر شراء بعد Landed Cost" }
        insertCostRevaluationEvent(
            InventoryCostRevaluationEventEntity(
                id = "landed-revaluation:$postingId:${newCost.amountMinor}",
                itemId = item.id,
                quantityBefore = item.quantity,
                oldUnitCostMinor = item.buyPriceMinor,
                newUnitCostMinor = newCost.amountMinor,
                revaluationDifferenceMinor = revaluationDifference,
                sourceType = "LANDED_COST",
                sourceId = shipmentId,
                actorId = "SYSTEM",
                actorName = "Logistics",
                occurredAt = occurredAt,
                writeId = postingId,
            ),
        )
    }
    if (oldPostingCostMinor != newCost.amountMinor) {
        insertLandedCostAdjustmentEvent(
            LandedCostAdjustmentEventEntity(
                id = "landed-adjustment:$postingId:${newCost.amountMinor}",
                postingId = postingId,
                shipmentId = shipmentId,
                itemId = item.id,
                quantityAtAdjustment = item.quantity,
                previousPostingUnitCostMinor = oldPostingCostMinor,
                newPostingUnitCostMinor = newCost.amountMinor,
                previousInventoryUnitCostMinor = item.buyPriceMinor,
                newInventoryUnitCostMinor = newCost.amountMinor,
                revaluationDifferenceMinor = revaluationDifference,
                occurredAt = occurredAt,
                writeId = postingId,
            ),
        )
    }
    val directMinor = oldPostingCostMinor.coerceAtMost(newCost.amountMinor)
    val landedMinor = Math.subtractExact(newCost.amountMinor, directMinor)
    check(
        insertCostRevisionOnce(
            InventoryCostRevisionEntity(
                costRevisionId = "landed-cost:$postingId:${newCost.amountMinor}",
                organizationId = organizationId,
                itemId = item.id,
                sourceType = "LANDED_COST",
                sourceId = shipmentId,
                sourceLineId = movement.sourceLineId,
                revisionKind = InventoryCostRevisionKind.LANDED_COST_APPROVED,
                directPurchaseCostMinor = directMinor,
                landedCostPerBaseUnitMinor = landedMinor,
                approvedInventoryCostMinor = newCost.amountMinor,
                currencyCode = Money.TRANSACTION_CURRENCY,
                exchangeRateSnapshot = "1",
                allocationBasis = "LOGISTICS_ACCEPTED_QUANTITY",
                commandId = "landed:$postingId:${newCost.amountMinor}",
                idempotencyKey = "landed:$postingId:${newCost.amountMinor}",
                approvedAt = occurredAt,
                recordedAt = occurredAt,
                createdBy = actorId,
                deviceId = "local",
            )
        )
    ) { "اعتماد Landed Cost مكرر" }
    return Result.success(Unit)
}

@Query(
    "UPDATE inventory_movements SET unitPrice = :unitPrice, unit_price_minor = :unitPriceMinor " +
        "WHERE id = :postingId AND shipmentId = :shipmentId " +
        "AND movementType = 'IN' AND source_type = 'SHIPMENT_RECEIPT' AND source_id = :shipmentId",
)
suspend fun updateShipmentReceiptMovementUnitPrice(
    postingId: String,
    shipmentId: String,
    unitPrice: Double,
    unitPriceMinor: Long,
): Int

@Query(
    "SELECT * FROM inventory_movements WHERE shipmentId = :shipmentId " +
        "AND movementType = 'IN' AND source_type = 'SHIPMENT_RECEIPT' AND source_id = :shipmentId ORDER BY createdAt DESC, id DESC",
)
suspend fun getShipmentReceiptMovements(shipmentId: String): List<InventoryMovementEntity>

@Transaction
suspend fun reverseShipmentReceiptsAtomic(shipmentId: String, sourceWriteId: String = ""): Result<Unit> {
    if (shipmentId.isBlank()) return Result.failure(Exception("معرف الشحنة مطلوب"))
    val movements = getShipmentReceiptMovements(shipmentId)
    if (movements.isEmpty()) return Result.success(Unit)

    val grouped = movements.groupBy { it.itemId }
    for ((itemId, rows) in grouped) {
        val item = getItemByIdSync(itemId)
            ?: return Result.failure(Exception("تعذر عكس الشحنة: الصنف غير موجود"))
        val total = rows.sumOf { it.quantity }
        if (total <= 0 || item.quantity < total) {
            return Result.failure(Exception("تعذر عكس الشحنة: مخزون ${item.name} الحالي لا يكفي لعكس $total؛ يلزم قرار إداري"))
        }
    }

    movements.forEach { original ->
        if (getReversalForMovement(original.id) != null) return@forEach
        val item = getItemByIdSync(original.itemId) ?: error("صنف حركة الاستلام مفقود")
        val after = Math.subtractExact(item.quantity, original.quantity)
        updateQuantity(original.itemId, after)
        insertMovement(
            InventoryMovementEntity(
                id = "shipment-reversal:${original.id}",
                itemId = original.itemId,
                movementType = MovementType.OUT,
                movementKind = InventoryMovementKind.REVERSAL,
                quantity = original.quantity,
                signedBaseQuantity = -original.quantity.toLong(),
                quantityBefore = item.quantity,
                quantityAfter = after,
                unitPrice = original.unitPrice,
                unitPriceMinor = original.unitPriceMinor,
                note = "SHIPMENT_REVERSAL:${original.id}",
                sourceType = "SHIPMENT_REVERSAL",
                sourceId = shipmentId,
                sourceLineId = original.sourceLineId,
                postingGroupId = sourceWriteId,
                reversesMovementId = original.id,
                writeId = sourceWriteId,
                occurredAt = System.currentTimeMillis(),
            )
        )
    }
    return Result.success(Unit)
}
}


