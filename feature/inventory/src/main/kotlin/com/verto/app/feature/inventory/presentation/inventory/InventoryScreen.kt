package com.verto.app.feature.inventory.presentation.inventory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.InventoryItemView
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.verto.app.pdf.InventoryExportType
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onBulkPriceEdit: () -> Unit = {},
    onManageCategories: () -> Unit = {},
    onPriceList: () -> Unit = {},
    onLogistics: () -> Unit = {},
    vm: InventoryViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val items           = vm.filteredItemsPaged.collectAsLazyPagingItems()
    val allItems        by vm.items.collectAsStateWithLifecycle()
    val lowStockCount   by vm.lowStockCount.collectAsStateWithLifecycle()
    val stockFilter by vm.stockFilter.collectAsStateWithLifecycle()
    val searchQuery     by vm.searchQuery.collectAsStateWithLifecycle()
    val sortBy          by vm.sortBy.collectAsStateWithLifecycle()
    val totalValue      by vm.totalInventoryValue.collectAsStateWithLifecycle()
    val totalCount      by vm.totalItemsCount.collectAsStateWithLifecycle()
    val importResult        by vm.importResult.collectAsStateWithLifecycle()
    val permissionError     by vm.permissionError.collectAsStateWithLifecycle()
    val permissions         by vm.permissions.collectAsStateWithLifecycle()
    val lowStockBySupplier  by vm.lowStockBySupplier.collectAsStateWithLifecycle()
    val movements       by vm.selectedItemMovements.collectAsStateWithLifecycle()
    val allCategories   by vm.allDistinctCategories.collectAsStateWithLifecycle()
    val catMap          by vm.itemCategoriesMap.collectAsStateWithLifecycle()
    val latestMovements by vm.latestMovementByItem.collectAsStateWithLifecycle()
    val movementCounts  by vm.movementCountByItem.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategoryFilter.collectAsStateWithLifecycle()
    var showDeleteDialog        by remember { mutableStateOf<InventoryItemView?>(null) }
    var showMoreMenu            by remember { mutableStateOf(false) }
    var showMovementsSheet      by remember { mutableStateOf<InventoryItemView?>(null) }
    var showCategoryDialog      by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var showServiceDialog       by remember { mutableStateOf(false) }
    var showSlowMovingDialog    by remember { mutableStateOf(false) }
    var showLowStockDialog      by remember { mutableStateOf(false) }
    var showStockAdjustmentPicker by remember { mutableStateOf(false) }
    var showStockAdjustmentDialog by remember { mutableStateOf<InventoryItemView?>(null) }
    var selectedPdfExportType by rememberSaveable { mutableStateOf(InventoryExportType.BUY_PRICE) }
    var searchExpanded     by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val canEdit = permissions?.inventoryEdit == true
    val canPrice = permissions?.inventoryPrice == true
    val canImport = permissions?.inventoryImport == true
    val canExport = permissions?.inventoryExport == true
    val canViewShipments = permissions?.shipmentsView == true
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocusRequester.requestFocus()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.importCsv(context, it) } }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(importResult) {
        importResult?.let {
            snackbarHost.showSnackbar(it)
            vm.clearImportResult()
        }
    }
    LaunchedEffect(permissionError) {
        permissionError?.let {
            snackbarHost.showSnackbar(it)
            vm.clearPermissionError()
        }
    }

    showMovementsSheet?.let { selectedItem ->
        LaunchedEffect(selectedItem.id) { vm.selectItemForHistory(selectedItem.id) }
        MovementHistorySheet(
            item      = selectedItem,
            movements = movements,
            onDismiss = { showMovementsSheet = null; vm.clearSelectedItem() }
        )
    }

    if (showLowStockDialog) {
        LowStockSupplierDialog(
            groupedItems = lowStockBySupplier,
            onDismiss    = { showLowStockDialog = false }
        )
    }

    if (showServiceDialog) {
        ServiceItemDialog(
            onDismiss = { showServiceDialog = false },
            onSave    = { name ->
                vm.saveServiceItem(name)
                showServiceDialog = false
            }
        )
    }

    if (showStockAdjustmentPicker) {
        StockAdjustmentPickerDialog(
            items = allItems.filterNot { it.isService },
            onDismiss = { showStockAdjustmentPicker = false },
            onItemSelected = { item ->
                showStockAdjustmentPicker = false
                showStockAdjustmentDialog = item
            },
        )
    }

    showStockAdjustmentDialog?.let { item ->
        AdjustStockDialog(
            item = item,
            onDismiss = { showStockAdjustmentDialog = null },
            onSave = { newQuantity ->
                vm.adjustStock(item.id, newQuantity)
                showStockAdjustmentDialog = null
            },
        )
    }

    InventoryReferenceLayout(
        items            = items,
        allItems         = allItems,
        categoriesByItem = catMap,
        latestMovements  = latestMovements,
        movementCounts   = movementCounts,
        lowStockCount    = lowStockCount,
        stockFilter      = stockFilter,
        selectedCategory = selectedCategory,
        searchQuery      = searchQuery,
        canEdit          = canEdit,
        canPrice         = canPrice,
        canImport        = canImport,
        canExport        = canExport,
        canViewShipments = canViewShipments,
        onQueryChange    = { vm.setSearchQuery(it) },
        onFilterChange   = { vm.setStockFilter(it) },
        onCategoryFilter = { category -> vm.setSelectedCategoryFilter(category) },
        onOpenCategoryFilter = { showCategoryFilterDialog = true },
        onSortChange     = { vm.setSortBy(it) },
        onAddItem        = onAddItem,
        onEditItem       = onEditItem,
        onShowMovements  = { showMovementsSheet = it },
        onAdjustStock    = { showStockAdjustmentDialog = it },
        onDeleteItem     = { showDeleteDialog = it },
        onMoreClick      = { showMoreMenu = true },
        moreMenuExpanded  = showMoreMenu,
        onMoreMenuDismiss = { showMoreMenu = false },
        onPriceList       = onPriceList,
        onBulkPriceEdit   = onBulkPriceEdit,
        onManageCategories = onManageCategories,
        onAddService      = { showServiceDialog = true },
        onExportPdf       = { type ->
            selectedPdfExportType = type
            showCategoryDialog = true
        },
        onExportCsv       = {
            vm.exportCsv(context, allItems)
        },
        onImportCsv       = { importLauncher.launch("text/*") },
        onShowShortages   = { showLowStockDialog = true },
        onShowSlowMoving  = { showSlowMovingDialog = true },
        onAdjustStockAll  = { showStockAdjustmentPicker = true },
        onLogistics       = onLogistics,
        snackbarHost     = snackbarHost
    )

    if (showCategoryDialog) {
        val availableCategories: List<String> = remember(allCategories) {
            listOf("الكل") + allCategories
        }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            containerColor   = BgCard,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_686d835a4151), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = availableCategories) { category: String ->
                        TextButton(
                            onClick = {
                                showCategoryDialog = false
                                vm.exportPdf(context, allItems, selectedPdfExportType, category)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = InventoryDimensions.dp12)
                        ) {
                            Text(category, color = TextPrimary, fontSize = InventoryTextScale.sp16)
                        }
                        Divider(color = BorderColor.copy(alpha = 0.5f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showCategoryFilterDialog) {
        InventoryCategoryFilterDialog(
            categories = allCategories,
            selectedCategory = selectedCategory,
            onDismiss = { showCategoryFilterDialog = false },
            onSelect = { category ->
                vm.setSelectedCategoryFilter(category)
                showCategoryFilterDialog = false
            },
        )
    }

    if (showSlowMovingDialog) {
        AlertDialog(
            onDismissRequest = { showSlowMovingDialog = false },
            containerColor   = BgCard,
            title = {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_221887727fdc), fontSize = InventoryTextScale.sp20)
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_1d984e1ff78a), color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_5c4dae5d71bb), color = TextSecondary, fontSize = InventoryTextScale.sp13)
                    Spacer(Modifier.height(InventoryDimensions.dp4))
                    listOf(30, 60, 90, 180).forEach { days ->
                        TextButton(
                            onClick = {
                                showSlowMovingDialog = false
                                vm.exportSlowMovingPdf(context, days)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_e198a6c8f8ab, days), color = TextPrimary, fontSize = InventoryTextScale.sp15)
                        }
                        Divider(color = BorderColor.copy(alpha = 0.4f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSlowMovingDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    showDeleteDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor   = BgCard,
            title  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_68dc0483bdfe), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_e3177298b83d, item.name), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.deleteItem(item.id); showDeleteDialog = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }
}
