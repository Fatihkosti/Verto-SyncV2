package com.verto.app.feature.inventory.application

import com.verto.app.feature.inventory.domain.model.InventoryItem
import com.verto.app.feature.inventory.domain.model.SaveInventoryItemCommand
import com.verto.app.feature.inventory.domain.model.SaveInventoryItemResult
import com.verto.app.feature.inventory.domain.port.InventoryIdentityPort
import com.verto.app.feature.inventory.domain.port.InventoryStorePort
import javax.inject.Inject

class SaveInventoryItemCoordinator @Inject constructor(
    private val store: InventoryStorePort,
    private val identities: InventoryIdentityPort
) {
    suspend fun save(command: SaveInventoryItemCommand): SaveInventoryItemResult {
        val item = command.item
        val existing = store.getItem(item.id)
        val duplicate = store.getAllItems().any { candidate ->
            candidate.name.trim().equals(item.name.trim(), ignoreCase = true) &&
                candidate.id != existing?.id
        }
        if (duplicate) return SaveInventoryItemResult.DuplicateName(item.name)

        var finalItem = item
        if (command.unit != null) {
            val linkedId = item.linkedUnitItemId ?: identities.newId()
            val linked = InventoryItem(
                id = linkedId,
                name = "${command.unit.name} ${item.name}",
                partNumber = item.partNumber,
                buyPrice = item.buyPrice * command.unit.quantityPerUnit,
                sellPrice = item.sellPrice * command.unit.quantityPerUnit,
                quantity = 0,
                minQuantity = 1,
                location = item.location,
                note = "وحدة مرتبطة بـ: ${item.name}",
                isUnitItem = true,
                createdAt = item.createdAt,
                updatedAt = command.now
            )
            store.saveItem(linked)
            finalItem = item.copy(
                unitId = command.unit.id,
                linkedUnitItemId = linkedId,
                updatedAt = command.now
            )
        } else if (item.linkedUnitItemId != null) {
            store.deleteItem(item.linkedUnitItemId)
            finalItem = item.copy(
                unitId = null,
                linkedUnitItemId = null,
                updatedAt = command.now
            )
        }

        store.saveItem(finalItem.copy(isDirty = true))
        store.replaceCategories(finalItem.id, command.categories.normalized())
        syncLinkedPrice(finalItem, command.now)
        return SaveInventoryItemResult.Success(finalItem)
    }

    private suspend fun syncLinkedPrice(item: InventoryItem, now: Long) {
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

internal fun List<String>.normalized(): List<String> =
    map(String::trim).filter(String::isNotBlank).distinct()
