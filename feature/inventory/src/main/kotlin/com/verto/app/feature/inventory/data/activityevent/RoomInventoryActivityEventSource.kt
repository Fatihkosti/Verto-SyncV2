package com.verto.app.feature.inventory.data.activityevent

import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.data.local.entity.MovementType
import com.verto.app.feature.inventory.application.activityevent.InventoryActivityEventSource
import com.verto.app.feature.inventory.application.activityevent.InventoryActivityMovement
import com.verto.app.feature.inventory.application.activityevent.InventoryActivityRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInventoryActivityEventSource @Inject constructor(
    private val inventoryDao: InventoryDao,
) : InventoryActivityEventSource {
    override fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<InventoryActivityRecord>> =
        inventoryDao.observeActivityMovements(organizationId, sinceEpochMillis, limit).map { rows ->
            rows.map { row ->
                InventoryActivityRecord(
                    movementId = row.movement.id,
                    itemId = row.movement.itemId,
                    itemName = row.itemName,
                    movement = when (row.movement.movementType) {
                        MovementType.IN -> InventoryActivityMovement.IN
                        MovementType.OUT -> InventoryActivityMovement.OUT
                        MovementType.ADJUST -> InventoryActivityMovement.ADJUST
                        MovementType.RETURN -> InventoryActivityMovement.RETURN
                    },
                    quantity = row.movement.quantity,
                    quantityBefore = row.movement.quantityBefore,
                    quantityAfter = row.movement.quantityAfter,
                    note = row.movement.note,
                    occurredAtEpochMillis = row.movement.occurredAt ?: row.movement.createdAt,
                )
            }
        }
}
