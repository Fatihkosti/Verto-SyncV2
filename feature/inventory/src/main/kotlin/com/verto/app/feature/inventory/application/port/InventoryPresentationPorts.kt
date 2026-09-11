package com.verto.app.feature.inventory.application.port

import androidx.paging.PagingData
import com.verto.app.feature.inventory.application.model.*
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import kotlinx.coroutines.flow.Flow

interface InventoryPresentationQuery {
    fun getAllItems(): Flow<List<InventoryItemViewData>>
    fun searchItems(query: String): Flow<List<InventoryItemViewData>>
    fun getFilteredItemsPaged(query: String, stockFilter: String, category: String, sort: String): Flow<PagingData<InventoryItemViewData>>
    fun getLowStockItems(): Flow<List<InventoryItemViewData>>
    fun getLowStockCount(): Flow<Int>
    fun getLowStockWithSuppliers(): Flow<List<LowStockSupplierViewData>>
    fun getMovementsForItem(itemId: String): Flow<List<InventoryMovementViewData>>
    fun getAllMovements(): Flow<List<InventoryMovementViewData>>
    suspend fun getItemByIdSync(id: String): InventoryItemViewData?
    suspend fun getAllItemsSync(): List<InventoryItemViewData>
    suspend fun getSlowMovingItemsSync(sinceTimestamp: Long): List<InventoryItemViewData>
    fun getAllUnits(): Flow<List<InventoryUnitViewData>>
    fun getAllItemCategories(): Flow<List<InventoryCategoryLinkViewData>>
    fun getAllDistinctCategories(): Flow<List<String>>
    fun getAllMasterCategories(): Flow<List<CategoryViewData>>
    suspend fun isNameTaken(name: String, excludeId: String? = null): Boolean
}

interface InventoryPresentationCommand {
    suspend fun saveItem(item: InventoryItemViewData)
    suspend fun saveItemWithDetails(item: InventoryItemViewData, categories: List<String>, unit: InventoryUnitViewData?): InventoryItemViewData
    suspend fun syncUnitItemPrice(baseItem: InventoryItemViewData)
    suspend fun saveAsUnitItem(item: InventoryItemViewData, unitName: String, unitQuantity: Double, linkedPieceItemId: String, categories: List<String>): InventoryItemViewData
    suspend fun updatePrices(item: InventoryItemViewData, buyPrice: Double, sellPrice: Double): InventoryItemViewData
    suspend fun updatePriceBatch(items: List<InventoryItemViewData>): List<InventoryItemViewData>
    suspend fun deleteItem(id: String)
    suspend fun adjustStock(itemId: String, newQuantity: Int, note: String): Result<Unit>
    suspend fun saveUnit(unit: InventoryUnitViewData)
    suspend fun deleteUnit(id: String)
    suspend fun addMasterCategory(name: String)
    suspend fun deleteMasterCategory(id: String)
    suspend fun updateMasterCategory(id: String, newName: String)
}

interface PriceListPresentationQuery {
    fun observeTemplates(organizationId: String): Flow<List<PriceListTemplateViewData>>
}

interface PriceListPresentationCommand {
    suspend fun saveTemplate(template: PriceListTemplateViewData): PriceListTemplateViewData
    suspend fun deleteTemplate(templateId: String)
}

interface InventoryPreferencesPort {
    val inventoryTemplate: Flow<InvoiceTemplate>
    val inventoryFont: Flow<InvoiceFont>
    val inventoryFontSize: Flow<Int>
    val priceListFont: Flow<InvoiceFont>
    val priceListFontSize: Flow<Int>
}

interface InventoryPermissionPort {
    val permissions: Flow<InventoryPermissionsViewData?>
    fun can(permission: InventoryPermission): Boolean
    suspend fun canNow(permission: InventoryPermission): Boolean
}
