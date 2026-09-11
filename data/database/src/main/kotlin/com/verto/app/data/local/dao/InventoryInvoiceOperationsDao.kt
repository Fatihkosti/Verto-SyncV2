package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryInvoiceOperationsDao : InventoryCatalogReadDao, InventoryItemLifecycleDao, InventoryCostSourceDao, InventoryCostDao, InventoryMovementDao, InventorySyncDao {
@Transaction
suspend fun adjustStockAtomic(
    itemId: String,
    newQuantity: Int,
    note: String = "تعديل يدوي",
    sourceWriteId: String = "",
    actorId: String = "",
): Result<Unit> {


    val item = getItemByIdSync(itemId)
        ?: return Result.failure(Exception("القطعة غير موجودة"))

    updateQuantity(itemId, newQuantity)
    insertMovement(
        InventoryMovementEntity(
            itemId         = itemId,
            movementType   = MovementType.ADJUST,
            quantity       = kotlin.math.abs(newQuantity - item.quantity),
            quantityBefore = item.quantity,
            quantityAfter  = newQuantity,
            note           = note,
            sourceType     = "MANUAL_ADJUSTMENT",
            sourceId       = itemId,
            writeId        = sourceWriteId,
            createdBy      = actorId.ifBlank { null },
        )
    )
    return Result.success(Unit)
}

// ─────────────────────────────────────────────────────────────────────────
// ✅ الإصلاح — عكس حركات الفاتورة عند حذفها
//
// قبل الإصلاح: كل قطعة تُعكس في transaction منفصلة → خطر التعارض
// بعد الإصلاح: كل قطع الفاتورة تُعكس في transaction واحدة
// ─────────────────────────────────────────────────────────────────────────

@Transaction
suspend fun reverseInvoiceMovementsAtomic(
    organizationId: String,
    actorId: String,
    invoiceId: String,
    sourceWriteId: String = "",
) {
    require(invoiceId.isNotBlank()) { "invoiceId is required" }
    val reversalWriteId = sourceWriteId.ifBlank { "void:$invoiceId" }
    val movements = getMovementsByInvoice(invoiceId).filter {
        it.movementType in setOf(MovementType.OUT, MovementType.IN) &&
            it.sourceType != "INVOICE_VOID" &&
            it.reversesMovementId == null
    }
    for (mv in movements) {
        if (getReversalForMovement(mv.id) != null) continue
        val item = getItemByIdSync(mv.itemId) ?: error("تعذر عكس الفاتورة: صنف مخزون مفقود")
        when (mv.movementType) {
            MovementType.OUT -> {
                val restored = Math.addExact(item.quantity, mv.quantity)
                updateQuantity(mv.itemId, restored)
                insertMovement(
                    InventoryMovementEntity(
                        id = "invoice-reversal:${mv.id}",
                        itemId = mv.itemId,
                        invoiceId = invoiceId,
                        clientId = mv.clientId,
                        movementType = MovementType.IN,
                        movementKind = InventoryMovementKind.REVERSAL,
                        quantity = mv.quantity,
                        signedBaseQuantity = mv.quantity.toLong(),
                        quantityBefore = item.quantity,
                        quantityAfter = restored,
                        unitPrice = mv.unitPrice,
                        unitPriceMinor = mv.unitPriceMinor,
                        note = "INVOICE_VOID_REVERSAL:${mv.id}",
                        sourceType = "INVOICE_VOID",
                        sourceId = invoiceId,
                        sourceVersion = 2,
                        sourceLineId = mv.sourceLineId,
                        postingGroupId = reversalWriteId,
                        reversesMovementId = mv.id,
                        writeId = reversalWriteId,
                        occurredAt = System.currentTimeMillis(),
                    )
                )
            }
            MovementType.IN -> {
                require(item.quantity >= mv.quantity) {
                    "تعذر إلغاء الفاتورة: الكمية الحالية للصنف ${item.name} لا تكفي لعكس الاستلام"
                }
                val restored = item.quantity - mv.quantity
                updateQuantity(mv.itemId, restored)
                insertMovement(
                    InventoryMovementEntity(
                        id = "invoice-reversal:${mv.id}",
                        itemId = mv.itemId,
                        invoiceId = invoiceId,
                        clientId = mv.clientId,
                        movementType = MovementType.OUT,
                        movementKind = InventoryMovementKind.REVERSAL,
                        quantity = mv.quantity,
                        signedBaseQuantity = -mv.quantity.toLong(),
                        quantityBefore = item.quantity,
                        quantityAfter = restored,
                        unitPrice = mv.unitPrice,
                        unitPriceMinor = mv.unitPriceMinor,
                        note = "INVOICE_VOID_REVERSAL:${mv.id}",
                        sourceType = "INVOICE_VOID",
                        sourceId = invoiceId,
                        sourceVersion = 2,
                        sourceLineId = mv.sourceLineId,
                        postingGroupId = reversalWriteId,
                        reversesMovementId = mv.id,
                        writeId = reversalWriteId,
                        occurredAt = System.currentTimeMillis(),
                    )
                )
            }
            else -> Unit
        }
    }

    // Reverse the latest-purchase-price effect only when this invoice is still the latest
    // revaluation source. A later valid purchase must never be overwritten by voiding history.
    val originalRevaluations = getCostRevaluationEventsForSource("INVOICE", invoiceId)
    for (original in originalRevaluations) {
        val latest = getLatestCostRevaluationEventForItem(original.itemId) ?: continue
        if (latest.id != original.id) continue
        val item = getItemByIdSync(original.itemId) ?: error("تعذر عكس تكلفة الفاتورة: الصنف مفقود")
        if (item.buyPriceMinor != original.newUnitCostMinor) continue

        val affected = updateLatestPurchasePriceAtomic(
            id = item.id,
            buyPrice = Money.ofMinor(original.oldUnitCostMinor).toLegacyDouble(),
            buyPriceMinor = original.oldUnitCostMinor,
        )
        check(affected == 1) { "تعذر إعادة آخر سعر شراء السابق عند إلغاء الفاتورة" }

        val deltaMinor = Math.subtractExact(original.oldUnitCostMinor, original.newUnitCostMinor)
        val inverse = InventoryCostRevaluationEventEntity(
            id = "invoice-void-revaluation:${original.id}:$reversalWriteId",
            itemId = item.id,
            quantityBefore = item.quantity,
            oldUnitCostMinor = original.newUnitCostMinor,
            newUnitCostMinor = original.oldUnitCostMinor,
            revaluationDifferenceMinor = Math.multiplyExact(item.quantity.toLong(), deltaMinor),
            sourceType = "INVOICE_VOID",
            sourceId = invoiceId,
            sourceVersion = 2,
            actorId = "SYSTEM",
            actorName = "Invoice lifecycle",
            occurredAt = System.currentTimeMillis(),
            writeId = reversalWriteId,
        )
        val inserted = insertCostRevaluationEvent(inverse)
        check(inserted != -1L) { "حدث عكس إعادة تقييم التكلفة موجود مسبقًا" }
    }

    getCostRevisionsForSource(organizationId, "INVOICE", invoiceId).forEach { original ->
        if (original.revisionKind == InventoryCostRevisionKind.REVERSAL || getCostReversal(organizationId, original.costRevisionId) != null) return@forEach
        val currentCost = getItemByIdSync(original.itemId)?.buyPriceMinor ?: original.directPurchaseCostMinor
        check(
            insertCostRevisionOnce(
                InventoryCostRevisionEntity(
                    costRevisionId = "cost-reversal:${original.costRevisionId}",
                    organizationId = organizationId,
                    itemId = original.itemId,
                    sourceType = "INVOICE_VOID",
                    sourceId = invoiceId,
                    sourceLineId = original.sourceLineId,
                    revisionKind = InventoryCostRevisionKind.REVERSAL,
                    directPurchaseCostMinor = currentCost,
                    landedCostPerBaseUnitMinor = 0L,
                    approvedInventoryCostMinor = currentCost,
                    currencyCode = original.currencyCode,
                    exchangeRateSnapshot = original.exchangeRateSnapshot,
                    reversesCostRevisionId = original.costRevisionId,
                    commandId = reversalWriteId,
                    idempotencyKey = "cost-reversal:${original.costRevisionId}",
                    approvedAt = System.currentTimeMillis(),
                    recordedAt = System.currentTimeMillis(),
                    createdBy = actorId,
                    deviceId = "local",
                )
            )
        ) { "مراجعة عكس التكلفة مكررة" }
    }
}

}
