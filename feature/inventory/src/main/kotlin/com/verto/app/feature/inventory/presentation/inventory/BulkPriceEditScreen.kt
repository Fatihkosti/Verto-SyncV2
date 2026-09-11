package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoButton

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.InventoryItemView

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import com.verto.app.ui.components.VertoIconButton

// ─────────────────────────────────────────────────────
// شاشة تعديل الأسعار بالجملة
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkPriceEditScreen(
    onBack: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val allItems by vm.bulkEditItems.collectAsStateWithLifecycle()

    // ── وضع التعديل: نسبة أم مبلغ ───────────────────
    var isPct      by remember { mutableStateOf(true) }
    var isIncrease by remember { mutableStateOf(true) }
    var targetSell by remember { mutableStateOf(true) }   // true=بيع false=شراء
    var valueStr   by remember { mutableStateOf("") }

    // ── تحديد المنتجات ────────────────────────────────
    val selectedIds = remember { mutableStateListOf<String>() }
    val allSelected = selectedIds.size == allItems.size && allItems.isNotEmpty()

    // ── وضع التعديل اليدوي الفردي ─────────────────────
    var manualMode by remember { mutableStateOf(false) }
    var byCategory by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val itemCategoriesMap by vm.itemCategoriesMap.collectAsStateWithLifecycle()
    val allDistinctCategories by vm.allDistinctCategories.collectAsStateWithLifecycle()
    // سعر البيع المؤقت لكل منتج (للتعديل اليدوي)
    val tempSellPrices = remember(allItems) {
        mutableStateMapOf<String, String>().also { map ->
            allItems.forEach { map[it.id] = it.sellPrice.toString() }
        }
    }
    val tempBuyPrices = remember(allItems) {
        mutableStateMapOf<String, String>().also { map ->
            allItems.forEach { map[it.id] = it.buyPrice.toString() }
        }
    }

    var showConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDeep,
        topBar = { VertoTopBar(title = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_58eed3c5f3b1), onBack = onBack) },
        bottomBar = {
            Surface(color = BgCard, shadowElevation = InventoryDimensions.dp8) {
                VertoButton(
                    onClick  = { showConfirm = true },
                    enabled  = if (manualMode) true else (valueStr.isNotBlank() && selectedIds.isNotEmpty()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(InventoryDimensions.dp16),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape  = RoundedCornerShape(InventoryDimensions.dp12)
                ) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(InventoryDimensions.dp18))
                    Spacer(Modifier.width(InventoryDimensions.dp8))
                    Text(if (manualMode) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_d251feaa4e3e_2) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_d251feaa4e3e, selectedIds.size), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(InventoryDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp10)
        ) {

            // ── وضع التعديل ───────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
                ) {
                    ModeBtn("جملي", !manualMode, AccentPrimary, Modifier.weight(1f)) { manualMode = false }
                    ModeBtn("يدوي فردي", manualMode, InfoColor, Modifier.weight(1f)) { manualMode = true }
                }
            }

            if (!manualMode) {
                // ── وضع الجملة ────────────────────────
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(InventoryDimensions.dp14))
                            .background(BgCard)
                            .border(InventoryDimensions.dp1, BorderColor, RoundedCornerShape(InventoryDimensions.dp14))
                            .padding(InventoryDimensions.dp14),
                        verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp10)
                    ) {
                        // نوع التعديل
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_3c0a773ac355), color = TextMuted, fontSize = InventoryTextScale.sp12)
                        Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)) {
                            FilterChip(isPct, { isPct = true }, "نسبة %")
                            FilterChip(!isPct, { isPct = false }, "مبلغ ثابت")
                        }

                        // زيادة أم نقصان
                        Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)) {
                            FilterChip(isIncrease, { isIncrease = true }, "زيادة ▲")
                            FilterChip(!isIncrease, { isIncrease = false }, "نقصان ▼")
                        }

                        // سعر البيع أم الشراء
                        Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)) {
                            FilterChip(targetSell, { targetSell = true }, "سعر البيع")
                            FilterChip(!targetSell, { targetSell = false }, "سعر الشراء")
                        }

                        // القيمة
                        VertoOutlinedTextField(
                            value         = valueStr,
                            onValueChange = { valueStr = it },
                            label         = { Text(if (isPct) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_ba490730492f_2) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_ba490730492f)) },
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors        = fieldColors()
                        )
                    }
                }

                // ── تحديد الكل ────────────────────────
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_d491c01caf90), color = TextSecondary, fontSize = InventoryTextScale.sp13, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_dc42087e53de), color = AccentPrimary, fontSize = InventoryTextScale.sp12)
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = {
                                    if (allSelected) selectedIds.clear()
                                    else { selectedIds.clear(); selectedIds.addAll(allItems.map { it.id }) }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AccentPrimary)
                            )
                        }
                    }
                }

                // ── قائمة المنتجات للتحديد ─────────────
                itemsIndexed(allItems) { _, item ->
                    val selected = item.id in selectedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(InventoryDimensions.dp10))
                            .background(if (selected) AccentPrimary.copy(0.08f) else BgCard)
                            .border(InventoryDimensions.dp1, if (selected) AccentPrimary.copy(0.3f) else BorderColor, RoundedCornerShape(InventoryDimensions.dp10))
                            .clickable {
                                if (selected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                            }
                            .padding(InventoryDimensions.dp10),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                if (selected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = AccentPrimary)
                        )
                        Spacer(Modifier.width(InventoryDimensions.dp8))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = TextPrimary, fontSize = InventoryTextScale.sp13, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.partNumber.isNotBlank()) Text(item.partNumber, color = TextMuted, fontSize = InventoryTextScale.sp11)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_8ae0259f650f, WhatsAppUtils.formatAmount(item.sellPrice)), color = SuccessColor, fontSize = InventoryTextScale.sp11)
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_248529e37cb5, WhatsAppUtils.formatAmount(item.buyPrice)), color = TextMuted, fontSize = InventoryTextScale.sp11)
                        }
                    }
                }

            } else {
                // ── Toggle: حسب البند / حسب التصنيف ──
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
                    ) {
                        ModeBtn("حسب البند", !byCategory, InfoColor, Modifier.weight(1f)) {
                            byCategory = false; selectedCategory = null
                        }
                        ModeBtn("حسب التصنيف", byCategory, InfoColor, Modifier.weight(1f)) {
                            byCategory = true; selectedCategory = null
                        }
                    }
                }

                if (!byCategory) {
                    // ── حسب البند: الشكل الحالي ──────────
                    item {
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_39154a603a00),
                            color    = TextMuted,
                            fontSize = InventoryTextScale.sp12
                        )
                    }
                    itemsIndexed(allItems) { _, item ->
                        ManualItemRow(item, tempBuyPrices, tempSellPrices)
                    }

                } else if (selectedCategory == null) {
                    // ── حسب التصنيف: بطاقات التصنيفات ────
                    itemsIndexed(allDistinctCategories) { _, cat ->
                        val catItems = allItems.filter { itemCategoriesMap[it.id]?.contains(cat) == true }
                        val lastMs   = catItems.maxOfOrNull { it.updatedAt }
                        val dateStr  = lastMs?.let {
                            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ENGLISH)
                                .format(java.util.Date(it))
                        } ?: "--"
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(InventoryDimensions.dp12))
                                .background(BgCard)
                                .border(InventoryDimensions.dp1, BorderColor, RoundedCornerShape(InventoryDimensions.dp12))
                                .clickable { selectedCategory = cat }
                                .padding(InventoryDimensions.dp14)
                        ) {
                            Column {
                                Text(cat, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InventoryTextScale.sp14)
                                Spacer(Modifier.height(InventoryDimensions.dp4))
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_fdfd1fbb9a5b, dateStr), color = TextMuted, fontSize = InventoryTextScale.sp11)
                            }
                        }
                    }

                } else {
                    // ── بنود التصنيف المختار ──────────────
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VertoIconButton(onClick = { selectedCategory = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), tint = AccentPrimary)
                            }
                            Text(
                                checkNotNull(selectedCategory),
                                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InventoryTextScale.sp15
                            )
                        }
                    }
                    val catItems = allItems.filter { itemCategoriesMap[it.id]?.contains(selectedCategory) == true }
                    itemsIndexed(catItems) { _, item ->
                        ManualItemRow(item, tempBuyPrices, tempSellPrices)
                    }
                }
            }
        }
    }

    // ── ديالوج التأكيد ────────────────────────────────
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = BgCard,
            title = { Text(if (manualMode) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_fa69fb1e2b5a_2) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_fa69fb1e2b5a), color = TextPrimary) },
            text  = {
                Text(
                    if (manualMode) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_b2aae88c3149)
                    else {
                        val val_ = valueStr.toDoubleOrNull() ?: 0.0
                        androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_9aedd2a63971_5, if (isIncrease) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_9aedd2a63971_2) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_9aedd2a63971), if (isPct) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_9aedd2a63971_4, val_) else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_9aedd2a63971_3, val_), selectedIds.size)
                    },
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manualMode) {
                        allItems.forEach { item ->
                            val newSell = tempSellPrices[item.id]?.toDoubleOrNull() ?: item.sellPrice
                            val newBuy  = tempBuyPrices[item.id]?.toDoubleOrNull() ?: item.buyPrice
                            if (newSell != item.sellPrice || newBuy != item.buyPrice)
                                vm.updateSinglePrice(item, newSell, newBuy)
                        }
                    } else {
                        val value = valueStr.toDoubleOrNull() ?: return@TextButton
                        val items = allItems.filter { it.id in selectedIds }
                        vm.applyBulkPriceChange(items, isPct, value, isIncrease, targetSell)
                    }
                    showConfirm = false
                    onBack()
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
            }
        )
    }
}

@Composable
private fun ManualItemRow(
    item: InventoryItemView,
    tempBuyPrices: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    tempSellPrices: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InventoryDimensions.dp12))
            .background(BgCard)
            .border(InventoryDimensions.dp1, BorderColor, RoundedCornerShape(InventoryDimensions.dp12))
            .padding(InventoryDimensions.dp10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
    ) {
        Column(Modifier.weight(1.2f)) {
            Text(item.name, color = TextPrimary, fontSize = InventoryTextScale.sp12, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.partNumber.isNotBlank()) Text(item.partNumber, color = TextMuted, fontSize = InventoryTextScale.sp10)
        }
        VertoOutlinedTextField(
            value         = tempBuyPrices[item.id] ?: "",
            onValueChange = { tempBuyPrices[item.id] = it },
            label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c48e5f785436), fontSize = InventoryTextScale.sp10) },
            modifier      = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors        = fieldColors(),
            singleLine    = true
        )
        VertoOutlinedTextField(
            value         = tempSellPrices[item.id] ?: "",
            onValueChange = { tempSellPrices[item.id] = it },
            label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_bf3a3673e472), fontSize = InventoryTextScale.sp10) },
            modifier      = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors        = fieldColors(),
            singleLine    = true
        )
    }
}

@Composable
private fun ModeBtn(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(InventoryDimensions.dp10))
            .background(if (selected) color.copy(0.15f) else BgCard)
            .border(InventoryDimensions.dp1, if (selected) color else BorderColor, RoundedCornerShape(InventoryDimensions.dp10))
            .clickable { onClick() }
            .padding(vertical = InventoryDimensions.dp10),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) color else TextMuted, fontSize = InventoryTextScale.sp13, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(InventoryDimensions.dp8))
            .background(if (selected) AccentPrimary.copy(0.15f) else BgDeep)
            .border(InventoryDimensions.dp1, if (selected) AccentPrimary else BorderColor, RoundedCornerShape(InventoryDimensions.dp8))
            .clickable { onClick() }
            .padding(horizontal = InventoryDimensions.dp12, vertical = InventoryDimensions.dp6)
    ) {
        Text(label, color = if (selected) AccentPrimary else TextMuted, fontSize = InventoryTextScale.sp12, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = AccentPrimary,
    unfocusedBorderColor    = BorderColor,
    focusedContainerColor   = BgDeep,
    unfocusedContainerColor = BgDeep,
    focusedTextColor        = TextPrimary,
    unfocusedTextColor      = TextPrimary,
    focusedLabelColor       = AccentPrimary,
    unfocusedLabelColor     = TextMuted
)
