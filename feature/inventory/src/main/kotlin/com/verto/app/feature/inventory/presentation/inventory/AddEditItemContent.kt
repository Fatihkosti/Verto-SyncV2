package com.verto.app.feature.inventory.presentation.inventory

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.ui.theme.*
import java.util.UUID

import com.verto.app.feature.inventory.application.model.CategoryItem
import com.verto.app.feature.inventory.application.model.InventoryUnitView

internal data class AddEditItemUiState(
    val itemId: String?,
    val items: List<InventoryItemView>,
    val units: List<InventoryUnitView>,
    val categoryMap: Map<String, List<String>>,
    val masterCategories: List<CategoryItem>,
    val duplicateNameError: String?,
    val canEditPrice: Boolean
)

internal typealias SaveUnitItemEvent = (InventoryItemView, String, Double, String, List<String>) -> Unit
internal typealias SaveInventoryItemEvent = (InventoryItemView, List<String>, () -> Unit) -> Unit

internal data class AddEditItemEvents(
    val onBack: () -> Unit,
    val onClearDuplicateNameError: () -> Unit,
    val onSaveAsUnitItem: SaveUnitItemEvent,
    val onSaveItemWithDetails: SaveInventoryItemEvent,
)

@Composable
internal fun AddEditItemContent(
    state: AddEditItemUiState,
    events: AddEditItemEvents,
) {
    val context = LocalContext.current
    val existingItem = remember(state.itemId, state.items) {
        if (state.itemId != null) state.items.find { it.id == state.itemId } else null
    }

    var name by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var sellPrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("0") }

    var partNum by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var minQty by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var showCategoryPickerDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var unitDialogName by remember { mutableStateOf("") }
    var unitDialogQty by remember { mutableStateOf("") }
    var unitDialogLinkedItemId by remember { mutableStateOf<String?>(null) }
    var hasUnit by remember { mutableStateOf(false) }
    var showExtras by remember { mutableStateOf(false) }

    LaunchedEffect(existingItem, state.categoryMap, state.units) {
        existingItem?.let { item ->
            name = item.name
            buyPrice = if (item.buyPrice > 0) item.buyPrice.toString() else ""
            sellPrice = if (item.sellPrice > 0) item.sellPrice.toString() else ""
            quantity = item.quantity.toString()
            partNum = item.partNumber
            barcode = item.barcode
            minQty = if (item.minQuantity != 5) item.minQuantity.toString() else ""
            location = item.location
            note = item.note
            selectedCategory = state.categoryMap[item.id]?.firstOrNull() ?: ""

            if (item.isUnitItem) {
                val unitName = item.unitId?.let { unitId -> state.units.find { it.id == unitId }?.name }.orEmpty()
                unitDialogName = unitName
                unitDialogQty = if (item.quantityPerUnit > 0) item.quantityPerUnit.toInt().toString() else ""
                unitDialogLinkedItemId = item.linkedUnitItemId
                hasUnit = true
                name = if (unitName.isNotBlank()) item.name.removeSuffix(" ($unitName)").trim() else item.name
            }

            if (item.partNumber.isNotBlank() || item.barcode.isNotBlank() || item.location.isNotBlank() || item.note.isNotBlank()) {
                showExtras = true
            }
        }
    }

    val isEdit = existingItem != null
    val isValid = name.isNotBlank()

    CompositionLocalProviderRtl {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .statusBarsPadding(),
        ) {
            AddItemHeader(
                title = if (isEdit) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_0e9f3da7b75a) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_6aedb06ee837),
                onBack = events.onBack,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = InventoryDimensions.dp18),
                verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp26),
            ) {
                TextPrimaryCard(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_8cec3e98a6a1),
                    icon = { Icon(Icons.Outlined.LocalOffer, null) },
                ) {
                    AddItemInput(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_747b8db5d687),
                        keyboardType = KeyboardType.Text,
                        isError = name.isBlank() && name.isNotEmpty(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp18),
                ) {
                    AddItemPriceCard(
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_6d3b8a57de85),
                        value = buyPrice,
                        onValueChange = { buyPrice = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_7db46f8a18e8),
                        icon = { Icon(Icons.Outlined.CloudDownload, null) },
                        enabled = state.canEditPrice,
                        modifier = Modifier.weight(1f),
                    )
                    AddItemPriceCard(
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_56390caa2af4),
                        value = sellPrice,
                        onValueChange = { sellPrice = it },
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9f860c086e4e),
                        icon = { Icon(Icons.Outlined.CloudUpload, null) },
                        modifier = Modifier.weight(1f),
                    )
                }

                TextPrimaryCard(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9d0ea9bc645d),
                    icon = { Icon(Icons.Outlined.Inventory2, null) },
                ) {
                    AddItemInput(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit) },
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9a79be611e02),
                        keyboardType = KeyboardType.Number,
                    )
                }

                AddItemOptionsRow(
                    expanded = showExtras,
                    onClick = { showExtras = !showExtras },
                )

                AnimatedVisibility(
                    visible = showExtras,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(InventoryDimensions.dp20))
                            .background(BgCard)
                            .border(InventoryDimensions.dp1, BorderColor, RoundedCornerShape(InventoryDimensions.dp20))
                            .padding(InventoryDimensions.dp16),
                        verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp12),
                    ) {
                        UnitButton(
                            hasUnit = hasUnit,
                            unitName = unitDialogName,
                            unitQty = unitDialogQty,
                            onEdit = { showUnitDialog = true },
                            onRemove = {
                                hasUnit = false
                                unitDialogName = ""
                                unitDialogQty = ""
                                unitDialogLinkedItemId = null
                            },
                        )
                        CategoryPickerButton(
                            selectedCategory = selectedCategory,
                            onClick = { showCategoryPickerDialog = true },
                        )
                        if (showCategoryPickerDialog) {
                            CategoryPickerDialog(
                                categories = state.masterCategories.map { it.name },
                                selected = selectedCategory,
                                onSelect = { selectedCategory = it; showCategoryPickerDialog = false },
                                onClear = { selectedCategory = ""; showCategoryPickerDialog = false },
                                onDismiss = { showCategoryPickerDialog = false },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp12)) {
                            AddItemInputCard(
                                value = partNum,
                                onValueChange = { partNum = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_fa1f4593291b),
                                modifier = Modifier.weight(1f),
                            )
                            AddItemInputCard(
                                value = minQty,
                                onValueChange = { minQty = it.filter(Char::isDigit) },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_96e68dbbae46),
                                placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_1f461120cfd1),
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        AddItemInputCard(
                            value = barcode,
                            onValueChange = { barcode = it.filter(Char::isDigit) },
                            label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_40e4a2cbdb2d),
                            placeholder = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_field_optional),
                            keyboardType = KeyboardType.Number,
                        )
                        AddItemInputCard(
                            value = location,
                            onValueChange = { location = it },
                            label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_10fea4bb0feb),
                            placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_e7e95c0baef2),
                        )
                        AddItemInputCard(
                            value = note,
                            onValueChange = { note = it },
                            label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_note),
                            singleLine = false,
                            modifier = Modifier.height(InventoryDimensions.dp90),
                        )
                    }
                }

                if (showUnitDialog) {
                    UnitDialog(
                        initialName = unitDialogName,
                        initialQty = unitDialogQty,
                        initialLinkedId = unitDialogLinkedItemId,
                        allItems = state.items.filter { !it.isUnitItem && !it.isService },
                        onDismiss = { showUnitDialog = false },
                        onSave = { unitName, qty, linkedId ->
                            unitDialogName = unitName
                            unitDialogQty = qty
                            unitDialogLinkedItemId = linkedId
                            hasUnit = true
                            showUnitDialog = false
                        },
                    )
                }

                if (state.duplicateNameError != null) {
                    AlertDialog(
                        onDismissRequest = { events.onClearDuplicateNameError() },
                        containerColor = BgCard,
                        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c7b987d93279), color = TextPrimary, fontWeight = FontWeight.Bold) },
                        text = { Text(state.duplicateNameError.orEmpty(), color = TextMuted, fontSize = InventoryTextScale.sp14) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { events.onClearDuplicateNameError() }) {
                                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = GradientStart, fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                }

                AddItemSaveButton(
                    text = if (isEdit) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_012125e0fd75_2) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_012125e0fd75),
                    enabled = isValid,
                    onClick = {
                        val computedMinQty = minQty.toIntOrNull() ?: 5
                        val item = InventoryItemView(
                            id = existingItem?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            partNumber = partNum.trim(),
                            barcode = barcode.trim(),
                            buyPrice = if (state.canEditPrice) buyPrice.toDoubleOrNull() ?: 0.0 else existingItem?.buyPrice ?: 0.0,
                            sellPrice = sellPrice.toDoubleOrNull() ?: 0.0,
                            quantity = quantity.toIntOrNull() ?: 0,
                            minQuantity = computedMinQty,
                            location = location.trim(),
                            note = note.trim(),
                            isService = existingItem?.isService ?: false,
                            createdAt = existingItem?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                        val categories = if (selectedCategory.isNotBlank()) listOf(selectedCategory) else emptyList()
                        if (hasUnit && unitDialogName.isNotBlank() && unitDialogQty.toDoubleOrNull() != null && unitDialogLinkedItemId != null) {
                            events.onSaveAsUnitItem(
                                item,
                                unitDialogName.trim(),
                                unitDialogQty.toDouble(),
                                checkNotNull(unitDialogLinkedItemId),
                                categories,
                            )
                            if (isEdit) {
                                Toast.makeText(context, "تم تعديل الصنف بنجاح", Toast.LENGTH_SHORT).show()
                                events.onBack()
                            } else {
                                Toast.makeText(context, "تمت إضافة ${name.trim()} بنجاح", Toast.LENGTH_SHORT).show()
                                resetAddItemFields(
                                    onName = { name = it },
                                    onBuyPrice = { buyPrice = it },
                                    onSellPrice = { sellPrice = it },
                                    onQuantity = { quantity = it },
                                    onPartNum = { partNum = it },
                                    onBarcode = { barcode = it },
                                    onMinQty = { minQty = it },
                                    onLocation = { location = it },
                                    onNote = { note = it },
                                    onCategory = { selectedCategory = it },
                                    onHasUnit = { hasUnit = it },
                                    onUnitName = { unitDialogName = it },
                                    onUnitQty = { unitDialogQty = it },
                                    onLinkedId = { unitDialogLinkedItemId = it },
                                    onExtras = { showExtras = it },
                                )
                            }
                        } else {
                            events.onSaveItemWithDetails(item, categories) {
                                if (isEdit) {
                                    Toast.makeText(context, "تم تعديل الصنف بنجاح", Toast.LENGTH_SHORT).show()
                                    events.onBack()
                                } else {
                                    Toast.makeText(context, "تمت إضافة ${name.trim()} بنجاح", Toast.LENGTH_SHORT).show()
                                    resetAddItemFields(
                                        onName = { name = it },
                                        onBuyPrice = { buyPrice = it },
                                        onSellPrice = { sellPrice = it },
                                        onQuantity = { quantity = it },
                                        onPartNum = { partNum = it },
                                        onBarcode = { barcode = it },
                                        onMinQty = { minQty = it },
                                        onLocation = { location = it },
                                        onNote = { note = it },
                                        onCategory = { selectedCategory = it },
                                        onHasUnit = { hasUnit = it },
                                        onUnitName = { unitDialogName = it },
                                        onUnitQty = { unitDialogQty = it },
                                        onLinkedId = { unitDialogLinkedItemId = it },
                                        onExtras = { showExtras = it },
                                    )
                                }
                            }
                        }
                    },
                )

                Spacer(Modifier.height(InventoryDimensions.dp28))
            }
        }
    }
}
