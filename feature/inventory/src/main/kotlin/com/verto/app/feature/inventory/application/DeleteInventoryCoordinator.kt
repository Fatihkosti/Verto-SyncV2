package com.verto.app.feature.inventory.application

import com.verto.app.feature.inventory.domain.port.InventoryDeletionQueuePort
import com.verto.app.feature.inventory.domain.port.InventoryStorePort
import javax.inject.Inject

class DeleteInventoryCoordinator @Inject constructor(
    private val store: InventoryStorePort,
    private val deletionQueue: InventoryDeletionQueuePort
) {
    suspend fun deleteItem(itemId: String) {
        store.deleteItem(itemId)
        // v261: archival is synchronized as metadata; inventory history is never remotely deleted.
    }
}
