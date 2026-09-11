package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryPurchaseReturnOperationsDao : InventoryStockTargetDao, InventoryItemLifecycleDao, InventoryCostSourceDao, InventoryCostDao, InventoryMovementDao {
/**
 * F252 purchase return. Stock is deducted first. If the fully-returned purchase/shipment still
 * owns latest purchase price, price falls back to the newest remaining valid purchase/receipt.
 * A newer source wins and is never silently overwritten.
 */
@Transaction
suspend fun deductPurchaseReturnAtomic(

    itemId: String,
    quantity: Int,
    returnId: String,
    returnLineId: String,
    supplierId: String,
    originalInvoiceId: String,
    originalInvoiceItemId: String,
    internationalPurchase: Boolean,
    originalUnitCostMinor: Long,
    sourceStillValidForItem: Boolean,
    occurredAt: Long,
    writeId: String,
    actorId: String,
    actorName: String,

) {
        deductPurchaseReturnAtomic(
            InventoryPurchaseReturnCommand(
                target = InventoryPurchaseReturnTarget(itemId, quantity, supplierId, originalUnitCostMinor),
                source = InventoryPurchaseReturnSource(returnId, returnLineId, originalInvoiceId, originalInvoiceItemId),
                policy = InventoryPurchaseReturnPolicy(internationalPurchase, sourceStillValidForItem),
                audit = InventoryWriteAudit(actorId, actorName, occurredAt, writeId),
            ),
        )
    }

    @Transaction
    suspend fun deductPurchaseReturnAtomic(command: InventoryPurchaseReturnCommand) {
        val itemId = command.target.itemId
        val quantity = command.target.quantity
        val supplierId = command.target.supplierId
        val originalUnitCostMinor = command.target.originalUnitCostMinor
        val returnId = command.source.returnId
        val returnLineId = command.source.returnLineId
        val originalInvoiceId = command.source.originalInvoiceId
        val originalInvoiceItemId = command.source.originalInvoiceItemId
        val internationalPurchase = command.policy.internationalPurchase
        val sourceStillValidForItem = command.policy.sourceStillValidForItem
        val occurredAt = command.audit.occurredAt
        val writeId = command.audit.writeId
        val actorId = command.audit.actorId
        val actorName = command.audit.actorName
        require(
                itemId.isNotBlank() && returnId.isNotBlank() && returnLineId.isNotBlank() &&
                    originalInvoiceId.isNotBlank() && originalInvoiceItemId.isNotBlank() && writeId.isNotBlank()
            ) { "purchase return stock identity is required" }
            require(quantity > 0) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
            require(originalUnitCostMinor >= 0L) { "تكلفة الشراء الأصلية غير صالحة" }
            if (internationalPurchase) {
                val postedQuantity = getPostedShipmentQuantityForInvoiceItem(
                    invoiceId = originalInvoiceId,
                    invoiceItemId = originalInvoiceItemId,
                    itemId = itemId,
                )
                val returnedQuantity = getReturnedQuantityForInvoiceItem(originalInvoiceItemId)
                require(postedQuantity > 0 && returnedQuantity <= postedQuantity) {
                    "مرتجع الشراء الدولي يتجاوز الكمية المستلمة والمرحلة للمخزون"
                }
            } else if (isPurchaseInvoiceLinkedToOrder(originalInvoiceId)) {
                val postedQuantity = getAllocatedGoodsReceiptQuantityForInvoiceItem(
                    invoiceId = originalInvoiceId,
                    invoiceItemId = originalInvoiceItemId,
                    itemId = itemId,
                )
                val returnedQuantity = getReturnedQuantityForInvoiceItem(originalInvoiceItemId)
                require(postedQuantity > 0 && returnedQuantity <= postedQuantity) {
                    "مرتجع الشراء يتجاوز كمية GRN المقبولة والمخصصة للفاتورة"
                }
            }
            val before = getItemByIdSync(itemId) ?: error("صنف مرتجع الشراء غير موجود")
            val affected = deductQuantityConditional(itemId, quantity, false, occurredAt)
            require(affected == 1) { "المخزون الحالي لا يكفي لتنفيذ مرتجع الشراء" }
            val after = getItemByIdSync(itemId) ?: error("اختفى الصنف بعد مرتجع الشراء")
            check(after.quantity == Math.subtractExact(before.quantity, quantity)) { "كمية مرتجع الشراء لم تُرحّل ذرياً" }
            insertMovement(
                InventoryMovementEntity(
                    id = "invoice-return-purchase:$returnLineId",
                    itemId = itemId,
                    invoiceId = "",
                    clientId = supplierId,
                    movementType = MovementType.OUT,
                    quantity = quantity,
                    quantityBefore = before.quantity,
                    quantityAfter = after.quantity,
                    unitPrice = Money.ofMinor(originalUnitCostMinor).toLegacyDouble(),
                    unitPriceMinor = originalUnitCostMinor,
                    note = "INVOICE_RETURN_PURCHASE:$returnId",
                    sourceType = "INVOICE_RETURN",
                    sourceId = returnId,
                    sourceVersion = 1,
                    writeId = writeId,
                    createdAt = occurredAt,
                )
            )
            if (sourceStillValidForItem) return
        
            val history = getCostRevaluationEventsForItemDescending(itemId)
            val latest = history.firstOrNull() ?: return
        
            suspend fun belongsToReturnedSource(event: InventoryCostRevaluationEventEntity): Boolean {
                if (event.sourceType == "INVOICE" && event.sourceId == originalInvoiceId) return true
                if (!internationalPurchase && event.sourceType == "GOODS_RECEIPT") {
                    return isGoodsReceiptSourceForInvoiceItem(
                        receiptId = event.sourceId,
                        invoiceId = originalInvoiceId,
                        invoiceItemId = originalInvoiceItemId,
                        itemId = itemId,
                    )
                }
                if (!internationalPurchase || event.sourceType !in setOf("SHIPMENT_RECEIPT", "LANDED_COST")) return false
                return isShipmentSourceForInvoiceItem(
                    shipmentId = event.sourceId,
                    invoiceId = originalInvoiceId,
                    invoiceItemId = originalInvoiceItemId,
                    itemId = itemId,
                )
            }
        
            suspend fun isValidAlternative(event: InventoryCostRevaluationEventEntity): Boolean = when (event.sourceType) {
                "INVOICE" -> !belongsToReturnedSource(event) && isPurchaseInvoiceCostSourceValid(event.sourceId, itemId)
                "GOODS_RECEIPT" -> !belongsToReturnedSource(event) && isGoodsReceiptCostSourceValid(event.sourceId, itemId)
                "SHIPMENT_RECEIPT", "LANDED_COST" ->
                    !belongsToReturnedSource(event) && isShipmentCostSourceValid(event.sourceId)
                else -> false
            }
        
            // Critical guard: a newer independent purchase/receipt keeps ownership of currentBuyPrice.
            if (!belongsToReturnedSource(latest)) return
            if (after.buyPriceMinor != latest.newUnitCostMinor) return
        
            var previousValid: InventoryCostRevaluationEventEntity? = null
            for (event in history.drop(1)) {
                if (isValidAlternative(event)) {
                    previousValid = event
                    break
                }
            }
            val returnedSourceHistory = mutableListOf<InventoryCostRevaluationEventEntity>()
            for (event in history) {
                if (belongsToReturnedSource(event)) returnedSourceHistory += event
            }
            val sourceChainFallback = returnedSourceHistory
                .minWithOrNull(compareBy<InventoryCostRevaluationEventEntity> { it.occurredAt }.thenBy { it.id })
                ?.oldUnitCostMinor
                ?: latest.oldUnitCostMinor
            val targetMinor = previousValid?.newUnitCostMinor ?: sourceChainFallback
            if (targetMinor == after.buyPriceMinor) return
        
            val updated = updateLatestPurchasePriceAtomic(
                id = itemId,
                buyPrice = Money.ofMinor(targetMinor).toLegacyDouble(),
                buyPriceMinor = targetMinor,
                now = occurredAt,
            )
            check(updated == 1) { "تعذر إعادة تحديد آخر سعر شراء بعد المرتجع" }
            val deltaMinor = Math.subtractExact(targetMinor, after.buyPriceMinor)
            val revaluation = InventoryCostRevaluationEventEntity(
                id = "invoice-return-revaluation:$returnId:$itemId",
                itemId = itemId,
                quantityBefore = after.quantity,
                oldUnitCostMinor = after.buyPriceMinor,
                newUnitCostMinor = targetMinor,
                revaluationDifferenceMinor = Math.multiplyExact(after.quantity.toLong(), deltaMinor),
                sourceType = "INVOICE_RETURN",
                sourceId = returnId,
                sourceVersion = 1,
                actorId = actorId,
                actorName = actorName,
                occurredAt = occurredAt,
                writeId = writeId,
            )
            check(insertCostRevaluationEvent(revaluation) != -1L) { "حدث إعادة تقييم مرتجع الشراء مكرر" }
    }

// ─────────────────────────────────────────────────────────────────────────
// ✅ الإصلاح — الثغرة #2 (adjustStock):
//
// adjustStockAtomic: تجمع قراءة الكمية + تحديثها + تسجيل حركة ADJUST
// في transaction واحدة غير قابلة للتجزئة.
//
// قبل الإصلاح: كانت InventoryRepository تنفّذ updateQuantity ثم insertMovement
//               بشكل منفصل → إذا انهار التطبيق بينهما، الكمية تتغير بدون أثر
// بعد الإصلاح: @Transaction يضمن all-or-nothing للقراءة + التحديث + السجل
// ─────────────────────────────────────────────────────────────────────────
}


