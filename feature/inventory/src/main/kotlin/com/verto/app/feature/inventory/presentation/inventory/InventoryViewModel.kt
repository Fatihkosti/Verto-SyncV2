package com.verto.app.feature.inventory.presentation.inventory

import dagger.hilt.android.qualifiers.ApplicationContext

import com.verto.app.feature.inventory.application.model.*
import com.verto.app.feature.inventory.application.InventoryApplicationService
import com.verto.app.feature.inventory.application.InventoryPreferencesService
import com.verto.app.feature.inventory.application.InventoryPermissionService

import com.verto.app.core.export.domain.DocumentSharePort
import com.verto.app.core.audit.domain.AuditTable


import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.verto.app.core.session.domain.SessionReader
import androidx.lifecycle.viewModelScope
import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repo: InventoryApplicationService,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val prefs: InventoryPreferencesService,
    private val permissionProvider: InventoryPermissionService,
    private val documentSharePort: DocumentSharePort,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val inventoryTemplate: StateFlow<InvoiceTemplate> = prefs.inventoryTemplate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvoiceTemplate.CLASSIC)
    val inventoryFont: StateFlow<InvoiceFont> = prefs.inventoryFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvoiceFont.CAIRO)
    val inventoryFontSize: StateFlow<Int> = prefs.inventoryFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14)
    val permissions: StateFlow<InventoryPermissionsViewData?> = permissionProvider.permissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _duplicateNameError = MutableStateFlow<String?>(null)
    val duplicateNameError: StateFlow<String?> = _duplicateNameError.asStateFlow()

    private val _permissionError = MutableStateFlow<String?>(null)
    val permissionError: StateFlow<String?> = _permissionError.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    private val permissionGate = InventoryPermissionGate(
        auditLogger = auditLogger,
        sessionReader = sessionReader,
        permissionProvider = permissionProvider,
        resolveString = appContext::getString,
        onDenied = { _permissionError.value = it }
    )
    private val documentService = InventoryDocumentService(
        repo = repo,
        auditLogger = auditLogger,
        documentSharePort = documentSharePort
    )

    private suspend fun canEdit(action: String, details: String = ""): Boolean =
        permissionGate.canEdit(action, details)

    private suspend fun canPrice(action: String, details: String = ""): Boolean =
        permissionGate.canPrice(action, details)

    private suspend fun canImport(details: String = ""): Boolean =
        permissionGate.canImport(details)

    private suspend fun canExport(action: String, details: String = ""): Boolean =
        permissionGate.canExport(action, details)

    fun clearDuplicateNameError() { _duplicateNameError.value = null }
    fun clearPermissionError() { _permissionError.value = null }
    fun clearImportResult() { _importResult.value = null }

    private val readState = InventoryReadState(repo, viewModelScope)
    val searchQuery = readState.searchQuery
    val stockFilter = readState.stockFilter
    val sortBy = readState.sortBy
    val selectedCategoryFilter = readState.selectedCategoryFilter
    val allUnits = readState.allUnits
    val allDistinctCategories = readState.allDistinctCategories
    val allMasterCategories = readState.allMasterCategories
    val itemCategoriesMap = readState.itemCategoriesMap
    val bulkEditItems = readState.bulkEditItems
    val items = readState.items
    val filteredItems = readState.filteredItems
    val filteredItemsPaged = readState.filteredItemsPaged
    val lowStockCount = readState.lowStockCount
    val lowStockItems = readState.lowStockItems
    val lowStockBySupplier = readState.lowStockBySupplier
    val totalInventoryValue = readState.totalInventoryValue
    val totalItemsCount = readState.totalItemsCount
    val selectedItemMovements = readState.selectedItemMovements
    val latestMovementByItem = readState.latestMovementByItem
    val movementCountByItem = readState.movementCountByItem
    fun setSearchQuery(query: String) = readState.setSearchQuery(query)
    fun setStockFilter(filter: InventoryStockFilter) = readState.setStockFilter(filter)
    fun setSortBy(sort: InventorySort) = readState.setSortBy(sort)
    fun setSelectedCategoryFilter(category: String?) = readState.setSelectedCategoryFilter(category)
    fun selectItemForHistory(itemId: String) = readState.selectItem(itemId)
    fun clearSelectedItem() = readState.clearSelectedItem()

    // ── CRUD الأساسي ──────────────────────────────────

    /** حفظ صنف مع تصنيفاته ووحدته (يُستدعى من AddEditItemScreen) */
    fun saveItemWithDetails(
        item: InventoryItemView,
        categories: List<String>,
        unit: InventoryUnitView?,
        onSuccess: () -> Unit = {}
    ) = viewModelScope.launch {
        if (!canEdit("save_item", "itemId=${item.id}")) return@launch
        // تحقق من تكرار الاسم — عند الإضافة: excludeId=null، عند التعديل: excludeId=item.id
        val isEdit = repo.getItemByIdSync(item.id) != null
        val excludeId = if (isEdit) item.id else null
        if (repo.isNameTaken(item.name, excludeId)) {
            _duplicateNameError.value = "الصنف \"${item.name}\" موجود مسبقاً في المخزون"
            return@launch
        }
        val saved = repo.saveItemWithDetails(item, categories, unit)
        // مزامنة سعر صنف الوحدة لو موجود
        repo.syncUnitItemPrice(saved)
        onSuccess()
    }

    /** حفظ صنف جديد كوحدة (كرتونة/دستة) مرتبطة بصنف قطعة موجود */
    fun saveAsUnitItem(
        item: InventoryItemView,
        unitName: String,
        unitQuantity: Double,
        linkedPieceItemId: String,
        categories: List<String>
    ) = viewModelScope.launch {
        if (!canEdit("save_unit_item", "itemId=${item.id}")) return@launch
        repo.saveAsUnitItem(item, unitName, unitQuantity, linkedPieceItemId, categories)
    }

    /** حفظ صنف خدمي (اسم فقط) */
    fun saveServiceItem(name: String) = viewModelScope.launch {
        if (!canEdit("save_service_item", "name=$name")) return@launch
        val item = InventoryItemView(
            name      = name.trim(),
            isService = true,
            buyPrice  = 0.0,
            sellPrice = 0.0,
            quantity  = 0,
            minQuantity = 0
        )
        repo.saveItem(item)
    }

    fun saveItem(item: InventoryItemView) = viewModelScope.launch {
        if (!canEdit("save_item_simple", "itemId=${item.id}")) return@launch
        repo.saveItem(item)
        repo.syncUnitItemPrice(item)
    }

    fun deleteItem(id: String) = viewModelScope.launch {
        if (!canEdit("delete_item", "itemId=$id")) return@launch
        repo.deleteItem(id)
    }

    fun adjustStock(itemId: String, newQty: Int, note: String = "تعديل يدوي") =
        viewModelScope.launch {
            if (!canEdit("adjust_stock", "itemId=$itemId newQty=$newQty")) return@launch
            val item = repo.getItemByIdSync(itemId) ?: return@launch
            val result = repo.adjustStock(itemId, newQty, note)
            if (result.isSuccess) {
                auditLogger.logUpdate(
                    table    = AuditTable.INVENTORY,
                    recordId = itemId,
                    summary  = "تعديل كمية: ${item.name} — ${item.quantity} → $newQty",
                    oldValue = "{\"quantity\":${item.quantity}}",
                    newValue = "{\"quantity\":$newQty,\"note\":\"$note\"}"
                )
            }
        }

    // ── وحدات ─────────────────────────────────────────

    fun saveUnit(unit: InventoryUnitView) = viewModelScope.launch {
        if (!canEdit("save_unit", "unitId=${unit.id}")) return@launch
        repo.saveUnit(unit)
    }

    fun deleteUnit(id: String) = viewModelScope.launch {
        if (!canEdit("delete_unit", "unitId=$id")) return@launch
        repo.deleteUnit(id)
    }

    // ── Bulk Price Edit ───────────────────────────────

    fun applyBulkPriceChange(
        items: List<InventoryItemView>,
        isPct: Boolean,
        value: Double,
        isIncrease: Boolean,
        targetSell: Boolean
    ) = viewModelScope.launch {
        if (!canPrice("bulk_update", "count=${items.size}")) return@launch
        val updated = items.map { item ->
            val basePrice = if (targetSell) item.sellPrice else item.buyPrice
            val delta = if (isPct) basePrice * (value / 100) else value
            val newPrice = (if (isIncrease) basePrice + delta else basePrice - delta).coerceAtLeast(0.0)
            if (targetSell) item.copy(sellPrice = newPrice)
            else item.copy(buyPrice = newPrice)
        }.filter { updatedItem ->
            val original = items.first { it.id == updatedItem.id }
            updatedItem.buyPrice != original.buyPrice || updatedItem.sellPrice != original.sellPrice
        }
        repo.updatePriceBatch(updated)
    }

    fun updateSinglePrice(item: InventoryItemView, newSellPrice: Double, newBuyPrice: Double) =
        viewModelScope.launch {
            if (!canPrice("single_update", "itemId=${item.id}")) return@launch
            val updated = item.copy(
                sellPrice = newSellPrice,
                buyPrice  = newBuyPrice,
                updatedAt = System.currentTimeMillis()
            )
            repo.updatePrices(updated, newBuyPrice, newSellPrice)
        }

    // ── Import / Export ────────────────────────────────
    fun exportCsv(context: Context, items: List<InventoryItemView>): Result<Uri> {
        if (!permissionGate.canExportNow()) {
            val message = appContext.getString(com.verto.feature.inventory.R.string.inventory_v298_permission_export_denied)
            _permissionError.value = message
            viewModelScope.launch { permissionGate.recordDeniedExport("csv", "count=${items.size}", message) }
            return Result.failure(PermissionDeniedException(message))
        }
        return documentService.exportCsv(context, items)
    }

    fun importCsv(context: Context, uri: Uri) = viewModelScope.launch {
        if (!canImport("uri=$uri")) return@launch
        _importResult.value = documentService.importCsv(context, uri)
    }

    // ── إدارة التصنيفات المستقلة ──────────────────────

    fun addMasterCategory(name: String) = viewModelScope.launch {
        if (!canEdit("add_category", "name=$name")) return@launch
        repo.addMasterCategory(name)
    }

    fun deleteMasterCategory(id: String) = viewModelScope.launch {
        if (!canEdit("delete_category", "categoryId=$id")) return@launch
        repo.deleteMasterCategory(id)
    }

    fun updateMasterCategory(id: String, newName: String) = viewModelScope.launch {
        if (!canEdit("update_category", "categoryId=$id")) return@launch
        repo.updateMasterCategory(id, newName)
    }

    // ── PDF exports ────────────────────────────────────
    fun exportSlowMovingPdf(context: Context, days: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!canExport("slow_moving_pdf", "days=$days")) return@launch
            documentService.exportSlowMovingPdf(
                context, days, inventoryTemplate.value, inventoryFont.value, inventoryFontSize.value
            )?.let { _importResult.value = it }
        }
    }

    fun exportPdf(
        context: Context,
        items: List<InventoryItemView>,
        exportType: com.verto.app.pdf.InventoryExportType,
        categoryFilter: String = "الكل"
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!canExport("pdf", "count=${items.size} type=$exportType category=$categoryFilter")) return@launch
            documentService.exportPdf(
                context = context,
                items = items,
                exportType = exportType,
                categoryFilter = categoryFilter,
                categoriesByItem = itemCategoriesMap.value,
                template = inventoryTemplate.value,
                font = inventoryFont.value,
                fontSize = inventoryFontSize.value
            )?.let { _importResult.value = it }
        }
    }

}
