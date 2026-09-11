package com.verto.app.feature.inventory.data.search

import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.feature.inventory.application.search.InventoryHomeSearchRecord
import com.verto.app.feature.inventory.application.search.InventoryHomeSearchSource
import javax.inject.Inject

class RoomInventoryHomeSearchSource @Inject constructor(
    private val inventoryDao: InventoryDao,
) : InventoryHomeSearchSource {
    override suspend fun search(
        organizationId: String,
        textQuery: String,
        identifierQuery: String,
        limit: Int,
    ): List<InventoryHomeSearchRecord> = inventoryDao
        .searchItemsByPrefix(organizationId, textQuery, identifierQuery, limit)
        .map { item ->
            InventoryHomeSearchRecord(
                id = item.id,
                name = item.name,
                partNumber = item.partNumber,
                barcode = item.barcode,
                quantity = item.quantity,
                isService = item.isService,
                updatedAtEpochMillis = item.updatedAt,
            )
        }
}
