package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryStockCoreOperationsDao : InventoryStockTargetDao, InventoryItemLifecycleDao, InventoryCostSourceDao, InventoryCostDao, InventoryMovementDao {
// ─────────────────────────────────────────────────────────────────────────
// ✅ الإصلاح الجوهري — الثغرة #3
//
// deductStockAtomic / addStockAtomic:
// تجمع قراءة الكمية + التحقق منها + تحديثها + تسجيل الحركة
// في transaction واحدة غير قابلة للتجزئة.
// ─────────────────────────────────────────────────────────────────────────

@Transaction
suspend fun openStockAtomic(
    itemId: String,
    quantity: Int,
    note: String = "رصيد افتتاحي",
    sourceWriteId: String,
    actorId: String,
): Result<Unit> {
    require(sourceWriteId.isNotBlank()) { "opening command id is required" }
    require(actorId.isNotBlank()) { "opening actor is required" }
    if (quantity <= 0) return Result.failure(IllegalArgumentException("الكمية الافتتاحية يجب أن تكون أكبر من صفر"))
    val before = getItemByIdSync(itemId) ?: return Result.failure(IllegalArgumentException("القطعة غير موجودة"))
    require(before.quantity == 0) { "OPENING_BALANCE is allowed only from zero stock" }
    val affected = addQuantityAtomic(itemId, quantity)
    check(affected == 1) { "تعذر تثبيت الرصيد الافتتاحي" }
    val after = getItemByIdSync(itemId) ?: error("اختفى الصنف بعد الرصيد الافتتاحي")
    insertMovement(
        InventoryMovementEntity(
            itemId = itemId,
            movementType = MovementType.IN,
            movementKind = com.verto.app.data.local.entity.InventoryMovementKind.OPENING_BALANCE,
            quantity = quantity,
            signedBaseQuantity = quantity.toLong(),
            quantityBefore = 0,
            quantityAfter = after.quantity,
            unitPrice = before.buyPrice,
            unitPriceMinor = before.buyPriceMinor,
            note = note,
            sourceType = "OPENING_BALANCE",
            sourceId = itemId,
            sourceVersion = 2,
            writeId = sourceWriteId,
            commandId = sourceWriteId,
            idempotencyKey = sourceWriteId,
            createdBy = actorId,
            occurredAt = System.currentTimeMillis(),
            recordedAt = System.currentTimeMillis(),
        )
    )
    return Result.success(Unit)
}

@Transaction
suspend fun deductStockAtomic(

    itemId: String,
    quantity: Int,
    invoiceId: String,
    clientId: String,
    unitPrice: Double,
    note: String = "",
    allowNegativeStock: Boolean = false,
    sourceWriteId: String = "",
    sourceLineId: String? = null,
    postingGroupId: String? = null,

): Result<Unit> {
        return deductStockAtomic(
            InventoryStockDeductionCommand(
                target = InventoryStockTarget(itemId, quantity),
                party = InventoryStockParty(invoiceId, clientId),
                unitPrice = unitPrice,
                metadata = InventoryStockWriteMetadata(note, allowNegativeStock, sourceWriteId, sourceLineId, postingGroupId),
            ),
        )
    }

    @Transaction
    suspend fun deductStockAtomic(command: InventoryStockDeductionCommand): Result<Unit> {
        val itemId = command.target.itemId
        val quantity = command.target.quantity
        val invoiceId = command.party.documentId
        val clientId = command.party.counterpartyId
        val unitPrice = command.unitPrice
        val note = command.metadata.note
        val allowNegativeStock = command.metadata.allowNegativeStock
        val sourceWriteId = command.metadata.sourceWriteId
        val sourceLineId = command.metadata.sourceLineId
        val postingGroupId = command.metadata.postingGroupId
        if (quantity <= 0) return Result.failure(IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر"))
            if (!unitPrice.isFinite() || unitPrice < 0.0) return Result.failure(IllegalArgumentException("سعر الوحدة غير صالح"))
        
            val target = resolveBaseStockTarget(itemId, quantity, unitPrice)
            val beforeSnapshot = target.item
            val affected = deductQuantityConditional(target.item.id, target.baseQuantity, allowNegativeStock)
            if (affected != 1) {
                val current = getItemByIdSync(target.item.id)
                    ?: return Result.failure(IllegalArgumentException("القطعة غير موجودة"))
                return Result.failure(IllegalStateException("الكمية الأساسية المطلوبة (${target.baseQuantity}) تتجاوز المتاح (${current.quantity})"))
            }
        
            // From this point onward failures MUST escape, so Room rolls the mutation back.
            val after = getItemByIdSync(target.item.id)
                ?: error("اختفى الصنف بعد خصم ذري ناجح")
            val beforeQuantity = Math.addExact(after.quantity, target.baseQuantity)
            check(beforeQuantity == beforeSnapshot.quantity) {
                "stock snapshot changed outside the owning Room transaction"
            }
            val sourceType = if (invoiceId.isBlank()) "" else "INVOICE"
            insertMovement(
                InventoryMovementEntity(
                    itemId = target.item.id,
                    invoiceId = invoiceId,
                    clientId = clientId,
                    movementType = MovementType.OUT,
                    quantity = target.baseQuantity,
                    quantityBefore = beforeQuantity,
                    quantityAfter = after.quantity,
                    unitPrice = target.baseUnitPrice.toLegacyDouble(),
                    unitPriceMinor = target.baseUnitPrice.amountMinor,
                    note = note,
                    sourceType = sourceType,
                    sourceId = invoiceId,
                    sourceLineId = sourceLineId,
                    postingGroupId = postingGroupId,
                    conversionFactorSnapshot = target.factor.toString(),
                    sourceVersion = 1,
                    writeId = sourceWriteId,
                )
            )
            return Result.success(Unit)
    }

@Transaction
suspend fun addStockAtomic(

    itemId: String,
    quantity: Int,
    invoiceId: String,
    supplierId: String,
    unitPrice: Double,
    note: String = "",
    sourceWriteId: String = "",
    sourceLineId: String? = null,
    postingGroupId: String? = null,

): Result<Unit> {
        return addStockAtomic(
            InventoryStockAdditionCommand(
                target = InventoryStockTarget(itemId, quantity),
                party = InventoryStockParty(invoiceId, supplierId),
                unitPrice = unitPrice,
                metadata = InventoryStockWriteMetadata(note, false, sourceWriteId, sourceLineId, postingGroupId),
            ),
        )
    }

    @Transaction
    suspend fun addStockAtomic(command: InventoryStockAdditionCommand): Result<Unit> {
        val itemId = command.target.itemId
        val quantity = command.target.quantity
        val invoiceId = command.party.documentId
        val supplierId = command.party.counterpartyId
        val unitPrice = command.unitPrice
        val note = command.metadata.note
        val sourceWriteId = command.metadata.sourceWriteId
        val sourceLineId = command.metadata.sourceLineId
        val postingGroupId = command.metadata.postingGroupId
        if (quantity <= 0) return Result.failure(IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر"))
            if (!unitPrice.isFinite() || unitPrice < 0.0) return Result.failure(IllegalArgumentException("سعر الوحدة غير صالح"))
            val target = resolveBaseStockTarget(itemId, quantity, unitPrice)
            val affected = addQuantityAtomic(target.item.id, target.baseQuantity)
            check(affected == 1) { "تعذر تحديث كمية الصنف" }
            // Any failure after the mutation escapes and therefore rolls back this @Transaction.
            val after = getItemByIdSync(target.item.id)
                ?: error("اختفى الصنف بعد إضافة ذرية ناجحة")
            val sourceType = if (invoiceId.isBlank()) "" else "INVOICE"
            insertMovement(
                InventoryMovementEntity(
                    itemId = target.item.id,
                    invoiceId = if (sourceType == "INVOICE") invoiceId else "",
                    clientId = supplierId,
                    movementType = MovementType.IN,
                    quantity = target.baseQuantity,
                    quantityBefore = after.quantity - target.baseQuantity,
                    quantityAfter = after.quantity,
                    unitPrice = target.baseUnitPrice.toLegacyDouble(),
                    unitPriceMinor = target.baseUnitPrice.amountMinor,
                    note = note,
                    sourceType = sourceType,
                    sourceId = invoiceId,
                    sourceLineId = sourceLineId,
                    postingGroupId = postingGroupId,
                    conversionFactorSnapshot = target.factor.toString(),
                    sourceVersion = 1,
                    writeId = sourceWriteId,
                )
            )
            return Result.success(Unit)
    }


}


