package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryPurchasePostingOperationsDao : InventoryStockTargetDao, InventoryItemLifecycleDao, InventoryCostSourceDao, InventoryCostDao, InventoryMovementDao {
/**
 * F247 local purchase posting. The existing balance is re-priced to the latest purchase price;
 * the revaluation event measures only the balance that existed before this receipt.
 */
@Transaction
suspend fun receivePurchaseAtLatestPriceAtomic(

    organizationId: String,
    itemId: String,
    quantity: Int,
    invoiceId: String,
    supplierId: String,
    buyPriceMinor: Long,
    sellPriceMinor: Long?,
    actorId: String,
    actorName: String,
    occurredAt: Long,
    writeId: String,
    eventId: String,
    sourceType: String = "INVOICE",
    sourceId: String = invoiceId,
    sourceLineId: String? = null,
    postingGroupId: String? = null,

): InventoryCostRevaluationEventEntity? {
        return receivePurchaseAtLatestPriceAtomic(
            InventoryPurchasePostingCommand(
                organizationId = organizationId,
                itemId = itemId,
                quantity = quantity,
                source = InventoryPurchaseSource(
                    identity = InventoryPurchaseSourceIdentity(
                        invoiceId = invoiceId,
                        supplierId = supplierId,
                        sourceType = sourceType,
                        sourceId = sourceId,
                        sourceLineId = sourceLineId,
                        postingGroupId = postingGroupId,
                    ),
                    write = InventoryPurchaseSourceWrite(writeId, eventId),
                ),
                pricing = InventoryPurchasePricing(buyPriceMinor, sellPriceMinor),
                audit = InventoryWriteAudit(actorId, actorName, occurredAt, writeId),
            ),
        )
    }

    @Transaction
    suspend fun receivePurchaseAtLatestPriceAtomic(command: InventoryPurchasePostingCommand): InventoryCostRevaluationEventEntity? {
        val organizationId = command.organizationId
        val itemId = command.itemId
        val quantity = command.quantity
        val invoiceId = command.source.identity.invoiceId
        val supplierId = command.source.identity.supplierId
        val sourceType = command.source.identity.sourceType
        val sourceId = command.source.identity.sourceId
        val sourceLineId = command.source.identity.sourceLineId
        val postingGroupId = command.source.identity.postingGroupId
        val writeId = command.source.write.writeId
        val eventId = command.source.write.eventId
        val buyPriceMinor = command.pricing.buyPriceMinor
        val sellPriceMinor = command.pricing.sellPriceMinor
        val actorId = command.audit.actorId
        val actorName = command.audit.actorName
        val occurredAt = command.audit.occurredAt
        require(itemId.isNotBlank()) { "itemId is required" }
            require(quantity > 0) { "الكمية يجب أن تكون أكبر من صفر" }
            require(sourceType.isNotBlank()) { "sourceType is required" }
            require(sourceId.isNotBlank()) { "sourceId is required" }
            require(sourceType != "INVOICE" || invoiceId.isNotBlank()) { "invoiceId is required for INVOICE source" }
            require(writeId.isNotBlank()) { "writeId is required" }
            require(eventId.isNotBlank()) { "eventId is required" }
            require(buyPriceMinor > 0L) { "سعر الشراء يجب أن يكون أكبر من صفر" }
            require(sellPriceMinor == null || sellPriceMinor >= 0L) { "سعر البيع غير صالح" }
        
            val purchaseTarget = resolveBaseStockTarget(itemId, quantity, Money.ofMinor(buyPriceMinor).toLegacyDouble())
            val canonicalItemId = purchaseTarget.item.id
            val canonicalQuantity = purchaseTarget.baseQuantity
            val canonicalBuyPriceMinor = purchaseTarget.baseUnitPrice.amountMinor
            val canonicalSellPriceMinor = sellPriceMinor?.let {
                resolveBaseStockTarget(itemId, quantity, Money.ofMinor(it).toLegacyDouble()).baseUnitPrice.amountMinor
            }
            val before = purchaseTarget.item
            val deltaMinor = Math.subtractExact(canonicalBuyPriceMinor, before.buyPriceMinor)
            val differenceMinor = Math.multiplyExact(before.quantity.toLong(), deltaMinor)
            val buyPrice = Money.ofMinor(canonicalBuyPriceMinor).toLegacyDouble()
            val effectiveSellMinor = canonicalSellPriceMinor ?: before.sellPriceMinor
            val affected = addQuantityAtLatestPurchasePriceAtomic(
                id = canonicalItemId,
                quantity = canonicalQuantity,
                buyPrice = buyPrice,
                buyPriceMinor = canonicalBuyPriceMinor,
                updateSellPrice = canonicalSellPriceMinor != null,
                sellPrice = Money.ofMinor(effectiveSellMinor).toLegacyDouble(),
                sellPriceMinor = effectiveSellMinor,
                now = occurredAt,
            )
            check(affected == 1) { "تعذر إضافة كمية الشراء وتحديث آخر سعر" }
            val after = getItemByIdSync(canonicalItemId) ?: error("اختفى الصنف بعد ترحيل الشراء")
            check(after.quantity == Math.addExact(before.quantity, canonicalQuantity)) { "كمية الشراء لم تُرحّل بصورة ذرية" }
            check(after.buyPriceMinor == canonicalBuyPriceMinor) { "آخر سعر شراء لم يُحفظ" }
        
            insertMovement(
                InventoryMovementEntity(
                    itemId = canonicalItemId,
                    invoiceId = invoiceId,
                    clientId = supplierId,
                    movementType = MovementType.IN,
                    quantity = canonicalQuantity,
                    quantityBefore = before.quantity,
                    quantityAfter = after.quantity,
                    unitPrice = buyPrice,
                    unitPriceMinor = canonicalBuyPriceMinor,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    sourceLineId = sourceLineId,
                    postingGroupId = postingGroupId,
                    conversionFactorSnapshot = purchaseTarget.factor.toString(),
                    sourceVersion = 1,
                    writeId = writeId,
                    createdAt = occurredAt,
                ),
            )
        
            val revision = InventoryCostRevisionEntity(
                costRevisionId = "cost:$eventId",
                organizationId = organizationId,
                itemId = canonicalItemId,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceLineId = sourceLineId,
                revisionKind = InventoryCostRevisionKind.LOCAL_PURCHASE_APPROVED,
                directPurchaseCostMinor = canonicalBuyPriceMinor,
                landedCostPerBaseUnitMinor = 0L,
                approvedInventoryCostMinor = canonicalBuyPriceMinor,
                currencyCode = Money.TRANSACTION_CURRENCY,
                exchangeRateSnapshot = "1",
                commandId = writeId,
                idempotencyKey = "cost:$writeId",
                approvedAt = occurredAt,
                recordedAt = System.currentTimeMillis(),
                createdBy = actorId,
                deviceId = "local",
            )
            check(insertCostRevisionOnce(revision)) { "مراجعة تكلفة الشراء مكررة خارج مسار Idempotency" }
        
            if (before.buyPriceMinor == canonicalBuyPriceMinor) return null
            val event = InventoryCostRevaluationEventEntity(
                id = eventId,
                itemId = canonicalItemId,
                quantityBefore = before.quantity,
                oldUnitCostMinor = before.buyPriceMinor,
                newUnitCostMinor = canonicalBuyPriceMinor,
                revaluationDifferenceMinor = differenceMinor,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceVersion = 1,
                actorId = actorId,
                actorName = actorName,
                occurredAt = occurredAt,
                writeId = writeId,
            )
            val inserted = insertCostRevaluationEvent(event)
            if (inserted == -1L) {
                val existing = getCostRevaluationEventsForSource(sourceType, sourceId)
                    .firstOrNull { it.id == eventId }
                require(existing == event) { "تعارض هوية حدث إعادة تقييم المخزون" }
            }
            return event
    }
}
