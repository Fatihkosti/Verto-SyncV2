package com.verto.app.feature.inventory.application

import com.verto.app.feature.inventory.domain.model.InventoryUnit
import com.verto.app.feature.inventory.domain.model.SaveInventoryUnitItemCommand
import com.verto.app.feature.inventory.domain.port.InventoryIdentityPort
import com.verto.app.feature.inventory.domain.port.InventoryStorePort
import kotlin.math.ceil
import javax.inject.Inject

class SaveInventoryUnitItemCoordinator @Inject constructor(
    private val store: InventoryStorePort,
    private val identities: InventoryIdentityPort
) {
    suspend fun save(command: SaveInventoryUnitItemCommand) = with(command) {
        val existing = store.getItem(item.id)
        val unit = InventoryUnit(
            id = existing?.unitId ?: identities.newId(),
            name = unitName,
            quantityPerUnit = unitQuantity
        )
        store.saveUnit(unit)

        val finalItem = item.copy(
            name = "${item.name.trim()} ($unitName)",
            isUnitItem = true,
            linkedUnitItemId = linkedPieceItemId,
            unitId = unit.id,
            quantityPerUnit = unitQuantity,
            updatedAt = now,
            isDirty = true
        )
        store.saveItem(finalItem)

        if (item.buyPrice > 0 && unitQuantity > 0) {
            store.getItem(linkedPieceItemId)?.let { piece ->
                val rounded = ceil((item.buyPrice / unitQuantity) / 10.0) * 10.0
                store.saveItem(piece.copy(buyPrice = rounded, updatedAt = now, isDirty = true))
            }
        }
        store.replaceCategories(finalItem.id, categories.normalized())
        finalItem
    }
}
