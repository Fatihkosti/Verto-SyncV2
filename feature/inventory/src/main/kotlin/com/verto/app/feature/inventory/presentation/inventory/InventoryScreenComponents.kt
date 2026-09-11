package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.feature.inventory.application.model.InventoryMovementItem
import com.verto.app.feature.inventory.application.model.LowStockSupplierItem
import com.verto.app.feature.inventory.application.model.MovementType

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoEmptyStateVariant
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import java.util.Locale
import com.verto.app.ui.components.VertoIconButton


// ─────────────────────────────────────────────────────
// Dialog — إضافة صنف خدمي
// ─────────────────────────────────────────────────────

@Composable
internal fun InventoryReferenceLayout(
    items: LazyPagingItems<InventoryItemView>,
    allItems: List<InventoryItemView>,
    categoriesByItem: Map<String, List<String>>,
    latestMovements: Map<String, InventoryMovementItem>,
    movementCounts: Map<String, Int>,
    lowStockCount: Int,
    stockFilter: InventoryStockFilter,
    selectedCategory: String?,
    searchQuery: String,
    canEdit: Boolean,
    canPrice: Boolean,
    canImport: Boolean,
    canExport: Boolean,
    canViewShipments: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterChange: (InventoryStockFilter) -> Unit,
    onCategoryFilter: (String?) -> Unit,
    onOpenCategoryFilter: () -> Unit,
    onSortChange: (InventorySort) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onShowMovements: (InventoryItemView) -> Unit,
    onAdjustStock: (InventoryItemView) -> Unit,
    onDeleteItem: (InventoryItemView) -> Unit,
    onMoreClick: () -> Unit,
    moreMenuExpanded: Boolean,
    onMoreMenuDismiss: () -> Unit,
    onPriceList: () -> Unit,
    onBulkPriceEdit: () -> Unit,
    onManageCategories: () -> Unit,
    onAddService: () -> Unit,
    onExportPdf: (com.verto.app.pdf.InventoryExportType) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onShowShortages: () -> Unit,
    onShowSlowMoving: () -> Unit,
    onAdjustStockAll: () -> Unit,
    onLogistics: () -> Unit,
    snackbarHost: SnackbarHostState,
) {
    val nonServiceItems = remember(allItems) { allItems.filterNot { it.isService } }
    val outOfStockCount = remember(nonServiceItems) { nonServiceItems.count { it.quantity <= 0 } }
    val lowStockVisibleCount = remember(nonServiceItems) {
        nonServiceItems.count { it.quantity > 0 && it.quantity <= it.minQuantity }
    }
    val newItemsCount = remember(nonServiceItems) {
        nonServiceItems.count {
            System.currentTimeMillis() - it.createdAt <= 30L * 24L * 60L * 60L * 1000L
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = BgDeep,
            snackbarHost = { SnackbarHost(snackbarHost) },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (canEdit) {
                    SmallFloatingActionButton(
                        onClick = onAddItem,
                        modifier = Modifier.size(InventoryDimensions.dp40),
                        shape = CircleShape,
                        containerColor = WaitingColor,
                        contentColor = TextOnAccent,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_6aedb06ee837), modifier = Modifier.size(InventoryDimensions.dp26))
                    }
                }
            },
        ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .padding(padding),
            contentPadding = PaddingValues(start = InventoryDimensions.dp16_5, end = InventoryDimensions.dp16_5, top = InventoryDimensions.dp1, bottom = InventoryDimensions.dp0),
            verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp0),
        ) {
            item {
                InventoryReferenceHeader(
                    onMoreClick = onMoreClick,
                    moreMenuExpanded = moreMenuExpanded,
                    onMoreMenuDismiss = onMoreMenuDismiss,
                    canEdit = canEdit,
                    canPrice = canPrice,
                    canImport = canImport,
                    canExport = canExport,
                    canViewShipments = canViewShipments,
                    onPriceList = onPriceList,
                    onBulkPriceEdit = onBulkPriceEdit,
                    onManageCategories = onManageCategories,
                    onAddService = onAddService,
                    onExportPdf = onExportPdf,
                    onExportCsv = onExportCsv,
                    onImportCsv = onImportCsv,
                    onShowShortages = onShowShortages,
                    onShowSlowMoving = onShowSlowMoving,
                    onAdjustStock = onAdjustStockAll,
                    onLogistics = onLogistics,
                    onCategoryFilter = onOpenCategoryFilter,
                    onSortChange = onSortChange,
                )
                Spacer(Modifier.height(InventoryDimensions.dp7))
            }
            item {
                InventoryReferenceSearch(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                )
                Spacer(Modifier.height(InventoryDimensions.dp13_5))
            }
            item {
                InventoryReferenceStats(
                    total = nonServiceItems.size,
                    low = lowStockVisibleCount.takeIf { it > 0 } ?: lowStockCount,
                    out = outOfStockCount,
                    fresh = newItemsCount,
                )
                Spacer(Modifier.height(InventoryDimensions.dp10))
            }
            item {
                InventoryReferenceFilters(
                    selectedFilter = stockFilter,
                    onFilterChange = onFilterChange,
                )
                Spacer(Modifier.height(InventoryDimensions.dp6))
            }
            if (items.itemCount == 0) {
                item {
                    Box(
                        Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        VertoEmptyState(
                            iconText = "📦",
                            variant = VertoEmptyStateVariant.Plain,
                            message = if (searchQuery.isNotBlank()) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_74e4918ecd4d)
                            else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_9995c255ec8e),
                        )
                    }
                }
            } else {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.id },
                ) { index ->
                    val item = items[index] ?: return@items
                    InventoryReferenceItemCard(
                        item = item,
                        categories = categoriesByItem[item.id].orEmpty(),
                        latestMovement = latestMovements[item.id],
                        movementCount = movementCounts[item.id] ?: 0,
                        showCost = canPrice,
                        onEdit = if (canEdit) ({ onEditItem(item.id) }) else null,
                        onShowMovements = { onShowMovements(item) },
                        onAdjustStock = { if (canEdit) onAdjustStock(item) },
                        onDelete = { if (canEdit) onDeleteItem(item) },
                    )
                    Spacer(Modifier.height(InventoryDimensions.dp7))
                }
                item { Spacer(Modifier.height(InventoryDimensions.dp44)) }
            }
        }
    }
    }
}

@Composable
private fun InventoryReferenceHeader(
    onMoreClick: () -> Unit,
    moreMenuExpanded: Boolean,
    onMoreMenuDismiss: () -> Unit,
    canEdit: Boolean,
    canPrice: Boolean,
    canImport: Boolean,
    canExport: Boolean,
    canViewShipments: Boolean,
    onPriceList: () -> Unit,
    onBulkPriceEdit: () -> Unit,
    onManageCategories: () -> Unit,
    onAddService: () -> Unit,
    onExportPdf: (com.verto.app.pdf.InventoryExportType) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onShowShortages: () -> Unit,
    onShowSlowMoving: () -> Unit,
    onAdjustStock: () -> Unit,
    onLogistics: () -> Unit,
    onCategoryFilter: () -> Unit,
    onSortChange: (InventorySort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp43),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_d697a2b1a7ee),
            color = TextPrimary,
            fontSize = InventoryTextScale.sp22,
            fontWeight = FontWeight.Black,
        )
        Box {
            Surface(
                modifier = Modifier
                    .size(InventoryDimensions.dp32)
                    .offset(y = InventoryDimensions.dpNegative3)
                    .shadow(InventoryDimensions.dp4, RoundedCornerShape(InventoryDimensions.dp10)),
                shape = RoundedCornerShape(InventoryDimensions.dp10),
                color = BgCard,
            ) {
                VertoIconButton(onClick = onMoreClick, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_6a4ef561481c),
                        tint = TextPrimary,
                        modifier = Modifier.size(InventoryDimensions.dp21),
                    )
                }
            }
            InventoryMoreMenu(
                expanded = moreMenuExpanded,
                onDismiss = onMoreMenuDismiss,
                canEdit = canEdit,
                canPrice = canPrice,
                canImport = canImport,
                canExport = canExport,
                canViewShipments = canViewShipments,
                onPriceList = { onMoreMenuDismiss(); onPriceList() },
                onBulkPriceEdit = { onMoreMenuDismiss(); onBulkPriceEdit() },
                onManageCategories = { onMoreMenuDismiss(); onManageCategories() },
                onAddService = { onMoreMenuDismiss(); onAddService() },
                onExportPdf = { type -> onMoreMenuDismiss(); onExportPdf(type) },
                onExportCsv = { onMoreMenuDismiss(); onExportCsv() },
                onImportCsv = { onMoreMenuDismiss(); onImportCsv() },
                onShowShortages = { onMoreMenuDismiss(); onShowShortages() },
                onShowSlowMoving = { onMoreMenuDismiss(); onShowSlowMoving() },
                onAdjustStock = { onMoreMenuDismiss(); onAdjustStock() },
                onLogistics = { onMoreMenuDismiss(); onLogistics() },
                onCategoryFilter = { onMoreMenuDismiss(); onCategoryFilter() },
                onSortChange = { sort -> onMoreMenuDismiss(); onSortChange(sort) },
            )
        }
    }
}

@Composable
private fun InventoryMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canEdit: Boolean,
    canPrice: Boolean,
    canImport: Boolean,
    canExport: Boolean,
    canViewShipments: Boolean,
    onPriceList: () -> Unit,
    onBulkPriceEdit: () -> Unit,
    onManageCategories: () -> Unit,
    onAddService: () -> Unit,
    onExportPdf: (com.verto.app.pdf.InventoryExportType) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onShowShortages: () -> Unit,
    onShowSlowMoving: () -> Unit,
    onAdjustStock: () -> Unit,
    onLogistics: () -> Unit,
    onCategoryFilter: () -> Unit,
    onSortChange: (InventorySort) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(InventoryDimensions.dp232),
    ) {
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_cce5050d9e16)) },
            leadingIcon = { Icon(Icons.Default.ListAlt, null) },
            onClick = onPriceList,
        )
        if (canPrice) {
            DropdownMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_58eed3c5f3b1)) },
                leadingIcon = { Icon(Icons.Default.PriceChange, null) },
                onClick = onBulkPriceEdit,
            )
        }
        if (canEdit) {
            DropdownMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f26b72e41fe8)) },
                leadingIcon = { Icon(Icons.Default.Inventory, null) },
                onClick = onAdjustStock,
            )
            DropdownMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9f22ffde0566)) },
                leadingIcon = { Icon(Icons.Default.Category, null) },
                onClick = onManageCategories,
            )
            DropdownMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f439d8b5601e)) },
                leadingIcon = { Icon(Icons.Default.Handyman, null) },
                onClick = onAddService,
            )
        }
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c7655bda0748)) },
            leadingIcon = { Icon(Icons.Default.FilterAlt, null) },
            onClick = onCategoryFilter,
        )
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_21aa4eac4eee)) },
            leadingIcon = { Icon(Icons.Default.WarningAmber, null) },
            onClick = onShowShortages,
        )
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_6ee2fa67a8d5)) },
            leadingIcon = { Icon(Icons.Default.Schedule, null) },
            onClick = onShowSlowMoving,
        )
        if (canViewShipments) {
            DropdownMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_3ba38ead5b1d)) },
                leadingIcon = { Icon(Icons.Default.LocalShipping, null) },
                onClick = onLogistics,
            )
        }
        Divider()
        InventorySort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_2748940eb641, sort.label)) },
                leadingIcon = { Icon(Icons.Default.Sort, null) },
                onClick = { onSortChange(sort) },
            )
        }
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_770c11e048d1)) },
            leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
            enabled = canExport,
            onClick = { onExportPdf(com.verto.app.pdf.InventoryExportType.BUY_PRICE) },
        )
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_3987a12855d6)) },
            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
            enabled = canExport,
            onClick = onExportCsv,
        )
        DropdownMenuItem(
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_e551e1bff940)) },
            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
            enabled = canImport,
            onClick = onImportCsv,
        )
    }
}
