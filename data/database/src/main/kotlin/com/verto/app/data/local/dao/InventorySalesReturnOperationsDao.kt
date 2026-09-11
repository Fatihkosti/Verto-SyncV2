package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventorySalesReturnOperationsDao : InventoryCatalogReadDao, InventoryItemLifecycleDao, InventoryCostDao, InventoryMovementDao {
/** F252: sales return restores quantity at historical COGS without touching latest purchase price. */
@Transaction
suspend fun restoreSalesReturnAtomic(

    itemId: String,
    quantity: Int,
    returnId: String,
    returnLineId: String,
    clientId: String,
    historicalUnitCostMinor: Long,
    occurredAt: Long,
    writeId: String,

) {
        restoreSalesReturnAtomic(
            InventorySalesReturnCommand(
                identity = InventorySalesReturnIdentity(itemId, quantity, returnId, returnLineId, clientId),
                audit = InventorySalesReturnAudit(historicalUnitCostMinor, occurredAt, writeId),
            ),
        )
    }

    @Transaction
    suspend fun restoreSalesReturnAtomic(command: InventorySalesReturnCommand) {
        val itemId = command.identity.itemId
        val quantity = command.identity.quantity
        val returnId = command.identity.returnId
        val returnLineId = command.identity.returnLineId
        val clientId = command.identity.clientId
        val historicalUnitCostMinor = command.audit.historicalUnitCostMinor
        val occurredAt = command.audit.occurredAt
        val writeId = command.audit.writeId
        require(itemId.isNotBlank() && returnId.isNotBlank() && returnLineId.isNotBlank() && writeId.isNotBlank()) { "return stock identity is required" }
            require(quantity > 0) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
            require(historicalUnitCostMinor >= 0L) { "تكلفة البيع التاريخية غير صالحة" }
            val before = getItemByIdSync(itemId) ?: error("صنف المرتجع غير موجود")
            val affected = addQuantityAtomic(itemId, quantity, occurredAt)
            check(affected == 1) { "تعذر إعادة كمية مرتجع البيع" }
            val after = getItemByIdSync(itemId) ?: error("اختفى الصنف بعد مرتجع البيع")
            check(after.quantity == Math.addExact(before.quantity, quantity)) { "كمية مرتجع البيع لم تُرحّل ذرياً" }
            // Deliberately no buy-price mutation here: historical sale return never changes currentBuyPrice.
            check(after.buyPriceMinor == before.buyPriceMinor) { "مرتجع البيع غيّر آخر سعر شراء" }
            insertMovement(
                InventoryMovementEntity(
                    id = "invoice-return-sale:$returnLineId",
                    itemId = itemId,
                    invoiceId = "",
                    clientId = clientId,
                    movementType = MovementType.IN,
                    quantity = quantity,
                    quantityBefore = before.quantity,
                    quantityAfter = after.quantity,
                    unitPrice = Money.ofMinor(historicalUnitCostMinor).toLegacyDouble(),
                    unitPriceMinor = historicalUnitCostMinor,
                    note = "INVOICE_RETURN_SALE:$returnId",
                    sourceType = "INVOICE_RETURN",
                    sourceId = returnId,
                    sourceVersion = 1,
                    writeId = writeId,
                    createdAt = occurredAt,
                )
            )
    }
}

