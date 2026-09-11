package com.verto.app.feature.inventory.application.pendingaction

import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionDetailField
import com.verto.feature.dashboard.api.PendingActionDetailRow
import com.verto.feature.dashboard.api.PendingActionDetails
import com.verto.feature.dashboard.api.PendingActionEventKey
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionSection

object InventoryPendingActionKeys {
    const val PROVIDER_ID = "inventory.pending"
    const val EVENT_OUT_OF_STOCK = "out_of_stock"
    const val EVENT_LOW_STOCK = "low_stock"
    const val ACTION_OPEN_STOCK_GROUP = "open_stock_group"
}

data class InventoryPendingItemRecord(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int,
    val isService: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastSaleAtEpochMillis: Long?,
)

data class InventoryPriceBatchRecord(
    val batchId: String,
    val itemCount: Int,
    val occurredAtEpochMillis: Long,
)

/** Session 358: stock health is represented by one card per condition, never one card per item. */
internal fun buildInventoryStockStatusEvents(
    context: HomePermissionContext,
    items: List<InventoryPendingItemRecord>,
): List<PendingAction> {
    val stockItems = items.asSequence().filterNot(InventoryPendingItemRecord::isService).toList()
    val outOfStock = stockItems
        .filter { it.quantity <= 0 }
        .sortedBy { it.itemName }
    val lowStock = stockItems
        .filter { it.quantity > 0 && it.quantity <= it.minQuantity }
        .sortedWith(compareBy({ it.quantity.toDouble() / it.minQuantity.coerceAtLeast(1) }, { it.itemName }))

    return buildList(2) {
        if (outOfStock.isNotEmpty()) {
            add(
                stockGroupEvent(
                    context = context,
                    eventType = InventoryPendingActionKeys.EVENT_OUT_OF_STOCK,
                    title = "أصناف نافدة تمامًا",
                    summary = "${outOfStock.size} صنف نافد يحتاج إعادة توريد",
                    items = outOfStock,
                    priority = PendingActionPriority.CRITICAL,
                    statusLabel = "نافد",
                ),
            )
        }
        if (lowStock.isNotEmpty()) {
            add(
                stockGroupEvent(
                    context = context,
                    eventType = InventoryPendingActionKeys.EVENT_LOW_STOCK,
                    title = "أصناف أوشكت على النفاد",
                    summary = "${lowStock.size} صنف وصل إلى حد إعادة الطلب",
                    items = lowStock,
                    priority = PendingActionPriority.HIGH,
                    statusLabel = "أوشك على النفاد",
                ),
            )
        }
    }
}

private fun stockGroupEvent(
    context: HomePermissionContext,
    eventType: String,
    title: String,
    summary: String,
    items: List<InventoryPendingItemRecord>,
    priority: PendingActionPriority,
    statusLabel: String,
): PendingAction {
    val destination = HomeDestination(id = HomeDestinationIds.INVENTORY_ITEM_DETAILS)
    return PendingAction(
        eventKey = PendingActionEventKey.create(
            providerId = InventoryPendingActionKeys.PROVIDER_ID,
            eventType = eventType,
            sourceId = context.organizationId,
        ),
        title = title,
        summary = summary,
        occurredAtEpochMillis = items.maxOf { it.updatedAtEpochMillis.coerceAtLeast(0L) },
        priority = priority,
        destination = destination,
        actions = listOf(
            HomeAction(
                id = InventoryPendingActionKeys.ACTION_OPEN_STOCK_GROUP,
                label = "عرض الأصناف",
                destination = destination,
                requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
            ),
        ),
        details = PendingActionDetails(
            title = title,
            rows = items.map { item ->
                PendingActionDetailRow(
                    title = item.itemName,
                    fields = listOf(
                        PendingActionDetailField("الكمية الحالية", item.quantity.toString()),
                        PendingActionDetailField("حد إعادة الطلب", item.minQuantity.toString()),
                        PendingActionDetailField("الحالة", statusLabel),
                    ),
                )
            },
            copyText = items.toStockGroupCopyText(title),
        ),
        section = PendingActionSection.INVENTORY,
        requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
    )
}

private fun List<InventoryPendingItemRecord>.toStockGroupCopyText(title: String): String =
    buildString {
        appendLine(title)
        this@toStockGroupCopyText.forEachIndexed { index, item ->
            append(index + 1)
            append(". ")
            append(item.itemName)
            append(" — الحالي: ")
            append(item.quantity)
            append(" — الحد: ")
            append(item.minQuantity)
            if (index != this@toStockGroupCopyText.lastIndex) appendLine()
        }
    }
