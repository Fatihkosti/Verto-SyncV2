package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.invoice.domain.model.InvoicePurchaseStockCommand
import com.verto.app.feature.invoice.domain.model.InvoicePostingIdentity
import com.verto.app.feature.invoice.domain.model.InvoiceSaleStockMutation
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceLinePostingStockPort
import com.verto.app.money.Money
import javax.inject.Inject

internal data class InvoiceInventoryContext(
    val items: List<InvoiceStockItem>,
    val byName: Map<String, InvoiceStockItem>,
)

internal data class InvoiceInventoryWriteRequest(
    val command: SaveInvoiceCommand,
    val invoiceId: String,
    val allowNegativeStock: Boolean,
    val context: InvoiceInventoryContext,
    val validated: ValidatedInvoiceSave,
    val actorId: String,
    val actorName: String,
)

internal data class InvoiceInventoryRevaluation(
    val itemId: String,
    val itemName: String,
    val quantityBefore: Int,
    val oldBuyPriceMinor: Long,
    val newBuyPriceMinor: Long,
    val revaluationDifferenceMinor: Long,
)

internal data class InvoiceInventoryWriteResult(
    val newItemsCreated: Int = 0,
    val itemsUpdated: Int = 0,
    val revaluations: List<InvoiceInventoryRevaluation> = emptyList(),
)

internal class InvoiceInventoryWriter @Inject constructor(
    private val stock: InvoiceStockPort,
) {

    suspend fun loadItems(): List<InvoiceStockItem> = stock.getAllItems()

    suspend fun loadContext(): InvoiceInventoryContext {
        val items = stock.getAllItems()
        return InvoiceInventoryContext(
            items = items,
            byName = items.associateBy { it.name.trim().lowercase() },
        )
    }

    /** Must run inside the same Room transaction that posts the invoice. */
    suspend fun snapshotSaleCosts(lines: List<InvoiceLine>, isSale: Boolean): List<InvoiceLine> {
        return lines.map { line ->
            val inventoryId = line.inventoryItemId.trim()
            val trackedInventory = inventoryId.isNotEmpty()
            val stockItem = if (trackedInventory) {
                requireNotNull(stock.getItem(inventoryId)) {
                    "صنف المخزون المرتبط غير موجود: ${line.itemName}"
                }
            } else {
                null
            }
            val descriptive = line.copy(
                itemSkuSnapshot = stockItem?.partNumber.orEmpty(),
                unitSnapshot = stockItem?.unitId.orEmpty(),
            )
            if (!isSale) {
                descriptive.copy(costSnapshotStatus = "NOT_APPLICABLE")
            } else {
                val currentCostMinor = stockItem?.buyPriceMinor ?: line.buyPriceMinor
                val snapshot = InvoiceSaleCostSnapshotCalculator.calculate(
                    quantity = line.quantity,
                    unitSellPriceMinor = line.sellPriceMinor,
                    unitCostAtSaleMinor = currentCostMinor,
                )
                descriptive.copy(
                    buyPrice = Money.ofMinor(currentCostMinor).toLegacyDouble(),
                    buyPriceMinor = currentCostMinor,
                    unitSellPrice = Money.ofMinor(snapshot.unitSellPriceMinor).toLegacyDouble(),
                    unitSellPriceMinor = snapshot.unitSellPriceMinor,
                    unitCostAtSale = Money.ofMinor(snapshot.unitCostAtSaleMinor).toLegacyDouble(),
                    unitCostAtSaleMinor = snapshot.unitCostAtSaleMinor,
                    lineRevenueSnapshot = Money.ofMinor(snapshot.lineRevenueSnapshotMinor).toLegacyDouble(),
                    lineRevenueSnapshotMinor = snapshot.lineRevenueSnapshotMinor,
                    lineCostSnapshot = Money.ofMinor(snapshot.lineCostSnapshotMinor).toLegacyDouble(),
                    lineCostSnapshotMinor = snapshot.lineCostSnapshotMinor,
                    grossProfitSnapshot = Money.ofMinor(snapshot.grossProfitSnapshotMinor).toLegacyDouble(),
                    grossProfitSnapshotMinor = snapshot.grossProfitSnapshotMinor,
                    costSnapshotStatus = if (trackedInventory) "KNOWN" else "UNTRACKED",
                )
            }
        }
    }

    suspend fun reverseForEdit(
        request: InvoiceInventoryWriteRequest,
        oldItems: List<InvoiceLine>,
        wasSale: Boolean,
    ) {
        if (skipInventory(request.command)) return
        stock.deleteMovements(request.invoiceId)
        for (oldItem in oldItems) {
            val explicitId = oldItem.inventoryItemId.trim()
            if (explicitId.isEmpty()) {
                require(request.context.byName[oldItem.itemName.trim().lowercase()] == null) {
                    "تعذر تعديل الفاتورة بأمان: البند ${oldItem.itemName} يطابق مخزوناً قديماً بدون inventoryItemId صريح"
                }
                continue
            }
            requireNotNull(stock.getItem(explicitId)) {
                "تعذر تعديل الفاتورة: صنف المخزون المرتبط غير موجود (${oldItem.itemName})"
            }
            if (wasSale) {
                stock.addStock(
                    explicitId,
                    oldItem.quantity,
                    request.invoiceId,
                    request.command.clientId,
                    oldItem.sellPrice,
                    sourceWriteId = request.command.writeId,
                ).getOrThrow()
            } else {
                stock.deductStock(
                    explicitId,
                    oldItem.quantity,
                    request.invoiceId,
                    request.command.clientId,
                    oldItem.buyPrice,
                    allowNegativeStock = request.allowNegativeStock,
                    sourceWriteId = request.command.writeId,
                ).getOrThrow()
            }
        }
    }

    suspend fun writeForEdit(
        request: InvoiceInventoryWriteRequest,
        postedLines: List<InvoiceLine> = emptyList(),
    ): InvoiceInventoryWriteResult {
        if (request.command.isSale) {
            writeSale(request, postedLines)
            return InvoiceInventoryWriteResult()
        }
        if (skipInventory(request.command)) return InvoiceInventoryWriteResult()
        return writePurchase(request, postedLines)
    }

    suspend fun requiresNegativeStockOverride(
        command: SaveInvoiceCommand,
        releasedEditItems: List<InvoiceLine> = emptyList(),
    ): Boolean {
        if (!command.isSale) return false
        val requestedByItem = command.items
            .mapNotNull { draft ->
                val id = draft.inventoryItemId.trim().ifBlank { return@mapNotNull null }
                val quantity = draft.quantity.trim().toIntOrNull() ?: return@mapNotNull null
                id to quantity
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, quantities) -> quantities.sum() }
        val releasedByItem = releasedEditItems
            .mapNotNull { line ->
                line.inventoryItemId.trim().takeIf { it.isNotEmpty() }?.let { it to line.quantity }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, quantities) -> quantities.sum() }
        return requestedByItem.any { (itemId, requested) ->
            val current = stock.getItem(itemId) ?: return@any false
            val availableAfterReverse = current.quantity + (releasedByItem[itemId] ?: 0)
            requested > availableAfterReverse
        }
    }

    suspend fun negativeReferencedItems(command: SaveInvoiceCommand): List<InvoiceStockItem> {
        if (!command.isSale) return emptyList()
        return command.items
            .mapNotNull { it.inventoryItemId.trim().ifBlank { null } }
            .distinct()
            .mapNotNull { stock.getItem(it) }
            .filter { it.quantity < 0 }
    }

    suspend fun writeForCreate(
        request: InvoiceInventoryWriteRequest,
        postedLines: List<InvoiceLine> = emptyList(),
    ): InvoiceInventoryWriteResult {
        if (request.command.isSale) {
            writeSale(request, postedLines)
            return InvoiceInventoryWriteResult()
        }
        if (skipInventory(request.command)) return InvoiceInventoryWriteResult()
        return writePurchase(request, postedLines)
    }

    private suspend fun writeSale(
        request: InvoiceInventoryWriteRequest,
        postedLines: List<InvoiceLine>,
    ) {
        require(postedLines.isEmpty() || postedLines.size == request.validated.items.size) {
            "عدد أسطر الترحيل لا يطابق بنود الفاتورة"
        }
        for ((index, validatedItem) in request.validated.items.withIndex()) {
            val item = validatedItem.draft
            val inventoryId = item.inventoryItemId.trim()
            if (inventoryId.isEmpty()) {
                require(request.context.byName[item.name.trim().lowercase()] == null) {
                    "الصنف ${item.name} موجود في المخزون؛ يجب اختياره صراحةً قبل البيع"
                }
                continue // Explicitly untracked sale line; no silent name matching.
            }
            val quantity = validatedItem.quantity.units
            val price = validatedItem.sellPrice.toLegacyDouble()
            val sourceLineId = postedLines.getOrNull(index)?.id ?: "line-${index + 1}"
            val postingGroupId = postingGroupId(request.invoiceId)
            val mutation = InvoiceSaleStockMutation(
                itemId = inventoryId,
                quantity = quantity,
                invoiceId = request.invoiceId,
                clientId = request.command.clientId,
                unitPrice = price,
                allowNegativeStock = request.allowNegativeStock,
            )
            val identity = InvoicePostingIdentity(
                writeId = lineCommandId(request.invoiceId, sourceLineId),
                sourceLineId = sourceLineId,
                postingGroupId = postingGroupId,
            )
            val lineAware = stock as? InvoiceLinePostingStockPort
            (lineAware?.deductPostingLine(mutation, identity) ?: stock.deductStock(
                itemId = mutation.itemId,
                quantity = mutation.quantity,
                invoiceId = mutation.invoiceId,
                clientId = mutation.clientId,
                unitPrice = mutation.unitPrice,
                allowNegativeStock = mutation.allowNegativeStock,
                sourceWriteId = identity.writeId,
            )).getOrThrow()
        }
    }

    private suspend fun writePurchase(
        request: InvoiceInventoryWriteRequest,
        postedLines: List<InvoiceLine>,
    ): InvoiceInventoryWriteResult {
        var newItemsCreated = 0
        var itemsUpdated = 0
        val revaluations = mutableListOf<InvoiceInventoryRevaluation>()
        require(postedLines.isEmpty() || postedLines.size == request.validated.items.size) {
            "عدد أسطر الترحيل لا يطابق بنود الفاتورة"
        }
        for ((index, validatedItem) in request.validated.items.withIndex()) {
            val (target, created) = resolvePurchaseTarget(validatedItem, request.context)
            if (created) newItemsCreated++ else itemsUpdated++
            val sourceLineId = postedLines.getOrNull(index)?.id ?: "line-${index + 1}"
            val event = postPurchaseLine(request, validatedItem, target, sourceLineId)
            if (event != null) {
                revaluations += InvoiceInventoryRevaluation(
                    itemId = target.id,
                    itemName = target.name,
                    quantityBefore = event.quantityBefore,
                    oldBuyPriceMinor = event.oldUnitCostMinor,
                    newBuyPriceMinor = event.newUnitCostMinor,
                    revaluationDifferenceMinor = event.revaluationDifferenceMinor,
                )
            }
        }
        return InvoiceInventoryWriteResult(
            newItemsCreated = newItemsCreated,
            itemsUpdated = itemsUpdated,
            revaluations = revaluations,
        )
    }

    private suspend fun resolvePurchaseTarget(
        validatedItem: ValidatedInvoiceItem,
        context: InvoiceInventoryContext,
    ): Pair<InvoiceStockItem, Boolean> {
        val item = validatedItem.draft
        val matched = findPurchaseItem(item.inventoryItemId, item.name, context)
        if (matched != null) return matched to false

        require(item.name.isNotBlank()) { "اسم صنف الشراء مطلوب" }
        val created = InvoiceStockItem(
            name = item.name.trim(),
            buyPrice = validatedItem.buyPrice.toLegacyDouble(),
            buyPriceMinor = validatedItem.buyPrice.amountMinor,
            sellPrice = validatedItem.sellPrice.toLegacyDouble(),
            sellPriceMinor = validatedItem.sellPrice.amountMinor,
            quantity = 0,
        )
        stock.saveItem(created)
        return created to true
    }

    private suspend fun postPurchaseLine(
        request: InvoiceInventoryWriteRequest,
        validatedItem: ValidatedInvoiceItem,
        target: InvoiceStockItem,
        sourceLineId: String,
    ) = stock.receivePurchaseAtLatestPrice(
        InvoicePurchaseStockCommand(
            itemId = target.id,
            quantity = validatedItem.quantity.units,
            invoiceId = request.invoiceId,
            supplierId = request.command.clientId,
            buyPriceMinor = validatedItem.buyPrice.amountMinor,
            sellPriceMinor = null,
            actorId = request.actorId,
            actorName = request.actorName,
            occurredAt = request.command.requestedAt,
            writeId = lineCommandId(request.invoiceId, sourceLineId),
            eventId = "invoice-revaluation:${request.invoiceId}:$sourceLineId:${target.id}:${validatedItem.buyPrice.amountMinor}",
            sourceLineId = sourceLineId,
            postingGroupId = postingGroupId(request.invoiceId),
        ),
    )

    private suspend fun findPurchaseItem(
        inventoryItemId: String,
        itemName: String,
        context: InvoiceInventoryContext,
    ): InvoiceStockItem? {
        val explicitId = inventoryItemId.trim()
        if (explicitId.isNotEmpty()) {
            return requireNotNull(stock.getItem(explicitId)) {
                "صنف المخزون المرتبط غير موجود: $itemName"
            }
        }
        val sameName = context.byName[itemName.trim().lowercase()]
        require(sameName == null) {
            "الصنف $itemName موجود مسبقاً؛ اختره من المخزون بدلاً من المطابقة بالاسم"
        }
        return null
    }

    private fun skipInventory(command: SaveInvoiceCommand): Boolean =
        !command.isSale && (
            command.purchaseScope == PurchaseScope.INTERNATIONAL || !command.purchaseOrderId.isNullOrBlank()
        )

    private fun postingGroupId(invoiceId: String): String = "invoice-post:$invoiceId"

    private fun lineCommandId(invoiceId: String, sourceLineId: String): String =
        "invoice-post:$invoiceId:$sourceLineId"
}
