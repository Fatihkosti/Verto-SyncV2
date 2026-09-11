package com.verto.app.feature.inventory.data.pendingaction

import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.feature.inventory.application.pendingaction.InventoryPendingActionSource
import com.verto.app.feature.inventory.application.pendingaction.InventoryPendingItemRecord
import com.verto.app.feature.inventory.application.pendingaction.InventoryPriceBatchRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInventoryPendingActionSource @Inject constructor(
    private val inventoryDao: InventoryDao,
) : InventoryPendingActionSource {
    override fun observeItems(organizationId: String, staleCutoffEpochMillis: Long): Flow<List<InventoryPendingItemRecord>> =
        inventoryDao.observePendingActionItems(organizationId, staleCutoffEpochMillis).map { rows ->
            rows.map { row ->
                InventoryPendingItemRecord(
                    itemId = row.itemId,
                    itemName = row.itemName,
                    quantity = row.quantity,
                    minQuantity = row.minQuantity,
                    isService = row.isService,
                    createdAtEpochMillis = row.createdAt,
                    updatedAtEpochMillis = row.updatedAt,
                    lastSaleAtEpochMillis = row.lastSaleAt,
                )
            }
        }

    override fun observePriceBatches(organizationId: String): Flow<List<InventoryPriceBatchRecord>> =
        inventoryDao.observePriceBatches(organizationId).map { rows ->
            rows.map { row ->
                InventoryPriceBatchRecord(
                    batchId = row.batchId,
                    itemCount = row.itemCount,
                    occurredAtEpochMillis = row.occurredAt,
                )
            }
        }
}
