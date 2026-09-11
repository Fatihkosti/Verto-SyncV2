package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryShipmentReceiptOperationsDao : InventoryShipmentOperationsDao, InventoryItemLifecycleDao {
@Transaction
suspend fun receiveShipmentStockAtomic(

    organizationId: String,
    actorId: String,
    postingId: String,
    shipmentId: String,
    receivingBatchId: String,
    receivingLineId: String,
    itemId: String,
    quantity: Int,
    supplierId: String,
    unitPrice: Double,
    note: String = "",

): Result<Unit> {
        return receiveShipmentStockAtomic(
            InventoryShipmentReceiptCommand(
                identity = InventoryShipmentReceiptIdentity(
                    organizationId, actorId, postingId, shipmentId, receivingBatchId, receivingLineId,
                ),
                details = InventoryShipmentReceiptDetails(itemId, quantity, supplierId, unitPrice, note),
            ),
        )
    }

    @Transaction
    suspend fun receiveShipmentStockAtomic(command: InventoryShipmentReceiptCommand): Result<Unit> {
        val organizationId = command.identity.organizationId
        val actorId = command.identity.actorId
        val postingId = command.identity.postingId
        val shipmentId = command.identity.shipmentId
        val receivingBatchId = command.identity.receivingBatchId
        val receivingLineId = command.identity.receivingLineId
        val itemId = command.details.itemId
        val quantity = command.details.quantity
        val supplierId = command.details.supplierId
        val unitPrice = command.details.unitPrice
        val note = command.details.note
        if (postingId.isBlank() || shipmentId.isBlank() || receivingBatchId.isBlank() || receivingLineId.isBlank())
                return Result.failure(Exception("معرفات استلام الشحنة مطلوبة"))
            if (quantity <= 0)
                return Result.failure(Exception("الكمية يجب أن تكون أكبر من صفر"))
            if (!unitPrice.isFinite() || unitPrice < 0.0)
                return Result.failure(Exception("سعر الوحدة غير صالح"))
        
            val existing = getMovementByIdForShipmentReceipt(postingId)
            if (existing != null) {
                return if (existing.shipmentId == shipmentId && existing.itemId == itemId && existing.quantity == quantity) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("postingId مستخدم لحركة مختلفة"))
                }
            }
        
            val before = getItemByIdSync(itemId)
                ?: return Result.failure(Exception("القطعة غير موجودة"))
            val unitPriceMoney = Money.fromLegacyDouble(unitPrice)
            if (unitPriceMoney.amountMinor < 0L) return Result.failure(Exception("سعر الوحدة غير صالح"))
            val occurredAt = System.currentTimeMillis()
            val deltaMinor = Math.subtractExact(unitPriceMoney.amountMinor, before.buyPriceMinor)
            val differenceMinor = Math.multiplyExact(before.quantity.toLong(), deltaMinor)
            val affected = addQuantityAtLatestPurchasePriceAtomic(
                id = itemId,
                quantity = quantity,
                buyPrice = unitPriceMoney.toLegacyDouble(),
                buyPriceMinor = unitPriceMoney.amountMinor,
                updateSellPrice = false,
                sellPrice = before.sellPrice,
                sellPriceMinor = before.sellPriceMinor,
                now = occurredAt,
            )
            check(affected == 1) { "تعذر ترحيل الاستلام إلى المخزون" }
            val after = getItemByIdSync(itemId) ?: error("اختفى الصنف بعد استلام الشحنة")
            insertMovement(
                InventoryMovementEntity(
                    id = postingId,
                    itemId = itemId,
                    invoiceId = "",
                    clientId = supplierId,
                    movementType = MovementType.IN,
                    quantity = quantity,
                    quantityBefore = before.quantity,
                    quantityAfter = after.quantity,
                    unitPrice = unitPriceMoney.toLegacyDouble(),
                    unitPriceMinor = unitPriceMoney.amountMinor,
                    note = buildString {
                        append("LOGISTICS_V2_RECEIPT:")
                        append(receivingBatchId)
                        append(':')
                        append(receivingLineId)
                        if (note.isNotBlank()) { append(" — "); append(note) }
                    },
                    shipmentId = shipmentId,
                    sourceType = "SHIPMENT_RECEIPT",
                    sourceId = shipmentId,
                    sourceLineId = receivingLineId,
                    postingGroupId = receivingBatchId,
                    sourceVersion = 1,
                    writeId = postingId,
                    createdAt = occurredAt,
                )
            )
            check(
                insertCostRevisionOnce(
                    InventoryCostRevisionEntity(
                        costRevisionId = "shipment-provisional:$postingId",
                        organizationId = organizationId,
                        itemId = itemId,
                        sourceType = "SHIPMENT_RECEIPT",
                        sourceId = shipmentId,
                        sourceLineId = receivingLineId,
                        revisionKind = InventoryCostRevisionKind.LANDED_COST_PROVISIONAL,
                        directPurchaseCostMinor = unitPriceMoney.amountMinor,
                        landedCostPerBaseUnitMinor = 0L,
                        approvedInventoryCostMinor = unitPriceMoney.amountMinor,
                        currencyCode = Money.TRANSACTION_CURRENCY,
                        exchangeRateSnapshot = "1",
                        isProvisional = true,
                        commandId = postingId,
                        idempotencyKey = "shipment-provisional:$postingId",
                        approvedAt = occurredAt,
                        recordedAt = occurredAt,
                        createdBy = actorId,
                        deviceId = "local",
                    )
                )
            ) { "تكلفة الاستلام المؤقتة مكررة" }
            if (before.buyPriceMinor != unitPriceMoney.amountMinor) {
                insertCostRevaluationEvent(
                    InventoryCostRevaluationEventEntity(
                        id = "receipt-revaluation:$postingId",
                        itemId = itemId,
                        quantityBefore = before.quantity,
                        oldUnitCostMinor = before.buyPriceMinor,
                        newUnitCostMinor = unitPriceMoney.amountMinor,
                        revaluationDifferenceMinor = differenceMinor,
                        sourceType = "SHIPMENT_RECEIPT",
                        sourceId = shipmentId,
                        actorId = "SYSTEM",
                        actorName = "Logistics",
                        occurredAt = occurredAt,
                        writeId = postingId,
                    ),
                )
            }
            return Result.success(Unit)
    }
}
