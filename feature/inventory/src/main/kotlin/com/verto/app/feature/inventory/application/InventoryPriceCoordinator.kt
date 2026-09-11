package com.verto.app.feature.inventory.application

import com.verto.app.feature.inventory.domain.model.InventoryItem
import com.verto.app.feature.inventory.domain.model.InventoryPriceBatchChangeRecord
import com.verto.app.feature.inventory.domain.model.InventoryPriceChange
import com.verto.app.feature.inventory.domain.port.InventoryPriceBatchEventPort
import com.verto.app.feature.inventory.domain.port.InventoryStorePort
import javax.inject.Inject

class InventoryPriceCoordinator @Inject constructor(
    private val store: InventoryStorePort,
    private val batchEvents: InventoryPriceBatchEventPort,
) {
    /** Kept for focused pure tests that do not exercise batch persistence. */
    constructor(store: InventoryStorePort) : this(store, NoOpInventoryPriceBatchEventPort)

    suspend fun update(change: InventoryPriceChange): InventoryItem {
        val updated = change.item.copy(
            buyPrice = change.buyPrice.coerceAtLeast(0.0),
            sellPrice = change.sellPrice.coerceAtLeast(0.0),
            updatedAt = change.now,
            isDirty = true
        )
        store.saveItem(updated)
        syncLinkedPrice(updated, change.now)
        return updated
    }

    suspend fun updateBatch(
        batchId: String,
        changes: List<InventoryPriceChange>,
    ): List<InventoryItem> {
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        val effectiveChanges = changes
            .distinctBy { it.item.id }
            .filter { change ->
                change.item.buyPrice != change.buyPrice.coerceAtLeast(0.0) ||
                    change.item.sellPrice != change.sellPrice.coerceAtLeast(0.0)
            }
        if (effectiveChanges.isEmpty()) return emptyList()

        val updatedItems = effectiveChanges.map { update(it) }
        if (updatedItems.size > 1) {
            batchEvents.recordBatch(
                batchId = batchId,
                changes = effectiveChanges.zip(updatedItems) { change, updated ->
                    InventoryPriceBatchChangeRecord(
                        itemId = updated.id,
                        quantity = updated.quantity,
                        oldBuyPrice = change.item.buyPrice,
                        oldSellPrice = change.item.sellPrice,
                        newBuyPrice = updated.buyPrice,
                        newSellPrice = updated.sellPrice,
                        occurredAtEpochMillis = change.now,
                    )
                },
            )
        }
        return updatedItems
    }

    suspend fun syncLinkedPrice(item: InventoryItem, now: Long) {
        val linkedId = item.linkedUnitItemId ?: return
        val unitId = item.unitId ?: return
        val unit = store.getUnit(unitId) ?: return
        val linked = store.getItem(linkedId) ?: return
        store.saveItem(
            linked.copy(
                buyPrice = item.buyPrice * unit.quantityPerUnit,
                sellPrice = item.sellPrice * unit.quantityPerUnit,
                updatedAt = now,
                isDirty = true
            )
        )
    }
}

private object NoOpInventoryPriceBatchEventPort : InventoryPriceBatchEventPort {
    override suspend fun recordBatch(
        batchId: String,
        changes: List<InventoryPriceBatchChangeRecord>,
    ) = Unit
}
