package com.verto.app.feature.inventory.presentation.inventory

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.verto.app.feature.inventory.application.InventoryApplicationService
import com.verto.app.feature.inventory.application.model.CategoryItem
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.feature.inventory.application.model.InventoryMovementItem
import com.verto.app.feature.inventory.application.model.InventoryUnitView
import com.verto.app.feature.inventory.application.model.LowStockSupplierItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class InventorySort(val label: String) {
    NAME("الاسم"),
    BUY_PRICE("سعر الشراء"),
    SELL_PRICE("سعر البيع"),
    CATEGORY("التصنيف"),
    QUANTITY("الكمية")
}

private data class InventoryPagedParams(
    val query: String,
    val stockFilter: InventoryStockFilter,
    val category: String,
    val sort: String
)

enum class InventoryStockFilter {
    ALL,
    LOW,
    OUT,
    SLOW,
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class InventoryReadState(
    private val repo: InventoryApplicationService,
    scope: CoroutineScope
) {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _stockFilter = MutableStateFlow(InventoryStockFilter.ALL)
    val stockFilter: StateFlow<InventoryStockFilter> = _stockFilter.asStateFlow()
    private val _sortBy = MutableStateFlow(InventorySort.NAME)
    val sortBy: StateFlow<InventorySort> = _sortBy.asStateFlow()
    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    val allUnits: StateFlow<List<InventoryUnitView>> = repo.getAllUnits()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allDistinctCategories: StateFlow<List<String>> = repo.getAllDistinctCategories()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allMasterCategories: StateFlow<List<CategoryItem>> = repo.getAllMasterCategories()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val itemCategoriesMap: StateFlow<Map<String, List<String>>> = repo.getAllItemCategories()
        .map { list -> list.groupBy({ it.itemId }, { it.category }) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val latestMovementByItem: StateFlow<Map<String, InventoryMovementItem>> = repo.getAllMovements()
        .map { movements ->
            movements
                .groupBy(InventoryMovementItem::itemId)
                .mapValues { (_, rows) -> checkNotNull(rows.maxByOrNull(InventoryMovementItem::createdAt)) }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val movementCountByItem: StateFlow<Map<String, Int>> = repo.getAllMovements()
        .map { movements -> movements.groupingBy(InventoryMovementItem::itemId).eachCount() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val bulkEditItems: StateFlow<List<InventoryItemView>> = repo.searchItems("")
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items: StateFlow<List<InventoryItemView>> = _searchQuery.debounce(300)
        .flatMapLatest(repo::searchItems)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filteredItems: StateFlow<List<InventoryItemView>> = combine(
        items, _stockFilter, _sortBy, itemCategoriesMap, _selectedCategoryFilter
    ) { list, filter, sort, categoriesByItem, categoryFilter ->
        var filtered = when (filter) {
            InventoryStockFilter.ALL -> list
            InventoryStockFilter.LOW -> list.filter { it.quantity > 0 && it.quantity <= it.minQuantity }
            InventoryStockFilter.OUT -> list.filter { it.quantity <= 0 }
            InventoryStockFilter.SLOW -> {
                val since = System.currentTimeMillis() - SLOW_MOVING_WINDOW_MS
                list.filter { it.updatedAt < since }
            }
        }
        if (categoryFilter != null) {
            filtered = filtered.filter { categoriesByItem[it.id]?.contains(categoryFilter) == true }
        }
        when (sort) {
            InventorySort.NAME -> filtered.sortedBy { it.name }
            InventorySort.BUY_PRICE -> filtered.sortedByDescending { it.buyPrice }
            InventorySort.SELL_PRICE -> filtered.sortedByDescending { it.sellPrice }
            InventorySort.CATEGORY -> filtered.sortedBy { categoriesByItem[it.id]?.firstOrNull() ?: "" }
            InventorySort.QUANTITY -> filtered.sortedBy { it.quantity }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filteredItemsPaged: Flow<PagingData<InventoryItemView>> = combine(
        _searchQuery.debounce(300), _stockFilter, _selectedCategoryFilter, _sortBy
    ) { query, filter, category, sort ->
        InventoryPagedParams(query, filter, category.orEmpty(), sort.name)
    }.flatMapLatest { params ->
        repo.getFilteredItemsPaged(params.query, params.stockFilter.name, params.category, params.sort)
    }.cachedIn(scope)
    val lowStockCount: StateFlow<Int> = repo.getLowStockCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)
    val lowStockItems: StateFlow<List<InventoryItemView>> = repo.getLowStockItems()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lowStockBySupplier: StateFlow<Map<String, List<LowStockSupplierItem>>> = repo.getLowStockWithSuppliers()
        .map { rows -> rows.groupBy { if (it.supplierName.isBlank()) "" else it.supplierName } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val totalInventoryValue: StateFlow<Double> = repo.getAllItems()
        .map { list -> list.filterNot(InventoryItemView::isService).sumOf { it.quantity * it.buyPrice } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)
    val totalItemsCount: StateFlow<Int> = repo.getAllItems().map { it.size }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    private val selectedItemId = MutableStateFlow<String?>(null)
    val selectedItemMovements: StateFlow<List<InventoryMovementItem>> = selectedItemId.filterNotNull()
        .flatMapLatest(repo::getMovementsForItem)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setStockFilter(filter: InventoryStockFilter) { _stockFilter.value = filter }
    fun setSortBy(sort: InventorySort) { _sortBy.value = sort }
    fun setSelectedCategoryFilter(category: String?) { _selectedCategoryFilter.value = category }

    fun selectItem(itemId: String) { selectedItemId.value = itemId }
    fun clearSelectedItem() { selectedItemId.value = null }

    private companion object {
        const val SLOW_MOVING_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
