package com.verto.app.feature.inventory.application

import com.verto.app.feature.inventory.application.model.*
import com.verto.app.feature.inventory.application.port.*
import com.verto.app.feature.inventory.domain.port.InventoryOrganizationSettingsPort
import javax.inject.Inject

class InventoryApplicationService @Inject constructor(
    private val query: InventoryPresentationQuery,
    private val command: InventoryPresentationCommand,
) {
    fun getAllItems() = query.getAllItems()
    fun searchItems(queryText: String) = query.searchItems(queryText)
    fun getFilteredItemsPaged(queryText: String, stockFilter: String, category: String, sort: String) =
        query.getFilteredItemsPaged(queryText, stockFilter, category, sort)
    fun getLowStockItems() = query.getLowStockItems()
    fun getLowStockCount() = query.getLowStockCount()
    fun getLowStockWithSuppliers() = query.getLowStockWithSuppliers()
    fun getMovementsForItem(itemId: String) = query.getMovementsForItem(itemId)
    fun getAllMovements() = query.getAllMovements()
    suspend fun getItemByIdSync(id: String) = query.getItemByIdSync(id)
    suspend fun getAllItemsSync() = query.getAllItemsSync()
    suspend fun getSlowMovingItemsSync(sinceTimestamp: Long) = query.getSlowMovingItemsSync(sinceTimestamp)
    fun getAllUnits() = query.getAllUnits()
    fun getAllItemCategories() = query.getAllItemCategories()
    fun getAllDistinctCategories() = query.getAllDistinctCategories()
    fun getAllMasterCategories() = query.getAllMasterCategories()
    suspend fun isNameTaken(name: String, excludeId: String? = null) = query.isNameTaken(name, excludeId)

    suspend fun saveItem(item: InventoryItemViewData) = command.saveItem(item)
    suspend fun saveItemWithDetails(item: InventoryItemViewData, categories: List<String>, unit: InventoryUnitViewData?) =
        command.saveItemWithDetails(item, categories, unit)
    suspend fun syncUnitItemPrice(baseItem: InventoryItemViewData) = command.syncUnitItemPrice(baseItem)
    suspend fun saveAsUnitItem(item: InventoryItemViewData, unitName: String, unitQuantity: Double, linkedPieceItemId: String, categories: List<String>) =
        command.saveAsUnitItem(item, unitName, unitQuantity, linkedPieceItemId, categories)
    suspend fun updatePrices(item: InventoryItemViewData, buyPrice: Double, sellPrice: Double) = command.updatePrices(item, buyPrice, sellPrice)
    suspend fun updatePriceBatch(items: List<InventoryItemViewData>) = command.updatePriceBatch(items)
    suspend fun deleteItem(id: String) = command.deleteItem(id)
    suspend fun adjustStock(itemId: String, newQuantity: Int, note: String) = command.adjustStock(itemId, newQuantity, note)
    suspend fun saveUnit(unit: InventoryUnitViewData) = command.saveUnit(unit)
    suspend fun deleteUnit(id: String) = command.deleteUnit(id)
    suspend fun addMasterCategory(name: String) = command.addMasterCategory(name)
    suspend fun deleteMasterCategory(id: String) = command.deleteMasterCategory(id)
    suspend fun updateMasterCategory(id: String, newName: String) = command.updateMasterCategory(id, newName)
}

class PriceListApplicationService @Inject constructor(
    private val query: PriceListPresentationQuery,
    private val command: PriceListPresentationCommand,
    organizationSettingsPort: InventoryOrganizationSettingsPort,
) {
    val organizationSettings = organizationSettingsPort.settings
    fun observeTemplates(organizationId: String) = query.observeTemplates(organizationId)
    suspend fun saveTemplate(template: PriceListTemplateViewData) = command.saveTemplate(template)
    suspend fun deleteTemplate(templateId: String) = command.deleteTemplate(templateId)
}

class InventoryPreferencesService @Inject constructor(private val port: InventoryPreferencesPort) {
    val inventoryTemplate = port.inventoryTemplate
    val inventoryFont = port.inventoryFont
    val inventoryFontSize = port.inventoryFontSize
    val priceListFont = port.priceListFont
    val priceListFontSize = port.priceListFontSize
}

class InventoryPermissionService @Inject constructor(private val port: InventoryPermissionPort) {
    val permissions = port.permissions
    fun can(permission: InventoryPermission) = port.can(permission)
    suspend fun canNow(permission: InventoryPermission) = port.canNow(permission)
}
