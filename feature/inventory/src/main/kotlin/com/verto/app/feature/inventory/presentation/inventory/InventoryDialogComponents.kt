package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.ui.components.VertoOutlinedTextField

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
import com.verto.app.ui.components.VertoBottomSheet


// ─────────────────────────────────────────────────────
// Dialog — إضافة صنف خدمي
// ─────────────────────────────────────────────────────

@Composable
internal fun ServiceItemDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_5ad608e6138f), fontSize = InventoryTextScale.sp20)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f439d8b5601e), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9691c608abac),
                    color = TextMuted, fontSize = InventoryTextScale.sp13
                )
                VertoOutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_ff514dc7dd5f), color = TextMuted) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(InventoryDimensions.dp12),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = AccentPrimary,
                        unfocusedBorderColor    = BorderColor,
                        focusedContainerColor   = BgDeep,
                        unfocusedContainerColor = BgDeep,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = AccentPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onSave(name) },
                enabled  = name.isNotBlank()
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add), color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────
// بطاقة الصنف
// ─────────────────────────────────────────────────────

@Composable
internal fun InventoryCategoryFilterDialog(
    categories: List<String>,
    selectedCategory: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val options = remember(categories) { listOf<String?>(null) + categories.distinct().map { it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c7655bda0748), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                items(options) { category ->
                    val selected = category == selectedCategory
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(InventoryDimensions.dp10)).clickable { onSelect(category) },
                        color = if (selected) WaitingContainer else Color.Transparent,
                        shape = RoundedCornerShape(InventoryDimensions.dp10),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = InventoryDimensions.dp12, vertical = InventoryDimensions.dp10),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(category ?: androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_ea6a63b9ba9a), color = if (selected) WaitingColor else TextPrimary)
                            if (selected) Icon(Icons.Default.Check, null, tint = WaitingColor)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    )
}

@Composable
internal fun StockAdjustmentPickerDialog(
    items: List<InventoryItemView>,
    onDismiss: () -> Unit,
    onItemSelected: (InventoryItemView) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f26b72e41fe8), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (items.isEmpty()) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_83ae0760c53f), color = TextMuted)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = InventoryDimensions.dp420),
                    verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4),
                ) {
                    items(items, key = { it.id }) { item ->
                        TextButton(
                            onClick = { onItemSelected(item) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = InventoryDimensions.dp8, vertical = InventoryDimensions.dp8),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c79f71243f41, item.quantity), color = SuccessColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    )
}

@Composable
internal fun AdjustStockDialog(
    item: InventoryItemView,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.toString()) }
    val parsed = quantity.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Column {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f26b72e41fe8), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InventoryTextScale.sp20)
                Text(item.name, color = TextMuted, fontSize = InventoryTextScale.sp13, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_e5b564928c56, item.quantity), color = TextMuted, fontSize = InventoryTextScale.sp13)
                VertoOutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit) },
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_10a56ee1bf16)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save), color = WaitingColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovementHistorySheet(
    item: InventoryItemView,
    movements: List<InventoryMovementItem>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    VertoBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = BgSurface,
        dragHandle       = {
            Box(
                Modifier
                    .padding(top = InventoryDimensions.dp12, bottom = InventoryDimensions.dp8)
                    .width(InventoryDimensions.dp40).height(InventoryDimensions.dp4)
                    .clip(RoundedCornerShape(InventoryDimensions.dp2)).background(BorderColor)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = InventoryDimensions.dp20).padding(bottom = InventoryDimensions.dp32)) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_a9ea9ec210c7, item.name), color = TextPrimary, fontSize = InventoryTextScale.sp16, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(InventoryDimensions.dp4))
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_e5b564928c56, item.quantity), color = TextSecondary, fontSize = InventoryTextScale.sp13)
            Spacer(Modifier.height(InventoryDimensions.dp16))

            if (movements.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(InventoryDimensions.dp120), contentAlignment = Alignment.Center) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_0bd33d38d2bd), color = TextMuted, fontSize = InventoryTextScale.sp13)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8),
                    modifier            = Modifier.heightIn(max = InventoryDimensions.dp420)
                ) {
                    items(movements, key = { it.id }) { mv -> MovementRow(mv) }
                }
            }
        }
    }
}

@Composable
internal fun MovementRow(mv: InventoryMovementItem) {
    val (icon, color, label) = when (mv.movementType) {
        MovementType.IN     -> Triple("⬆️", SuccessColor,  "وارد")
        MovementType.OUT    -> Triple("⬇️", MaterialTheme.colorScheme.error,    "صادر")
        MovementType.ADJUST -> Triple("🔧", AccentLight,  "تعديل")
        MovementType.RETURN -> Triple("↩️", WarningColor, "مرتجع")
    }

    Surface(shape = RoundedCornerShape(InventoryDimensions.dp12), color = BgCard, border = BorderStroke(InventoryDimensions.dp1, color.copy(0.25f))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = InventoryDimensions.dp14, vertical = InventoryDimensions.dp10),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp10), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(InventoryDimensions.dp36).clip(RoundedCornerShape(InventoryDimensions.dp10)).background(color.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = InventoryTextScale.sp16) }
                Column {
                    Text(label, color = color, fontSize = InventoryTextScale.sp13, fontWeight = FontWeight.SemiBold)
                    Text(DateUtils.formatDateTime(mv.createdAt), color = TextMuted, fontSize = InventoryTextScale.sp11)
                    if (mv.note.isNotBlank()) {
                        Text(mv.note, color = TextSecondary, fontSize = InventoryTextScale.sp11, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val sign = if (mv.movementType == MovementType.OUT) "-" else "+"
                val signColor = if (mv.movementType == MovementType.OUT) MaterialTheme.colorScheme.error else SuccessColor
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_0726b053ba4c, sign, mv.quantity), color = signColor, fontSize = InventoryTextScale.sp15, fontWeight = FontWeight.Black)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_1198266d5f80, mv.quantityBefore, mv.quantityAfter), color = TextMuted, fontSize = InventoryTextScale.sp10)
                if (mv.unitPrice > 0) Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_b9c97effb520_2, androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_b9c97effb520).format(mv.unitPrice)), color = GoldPrimary, fontSize = InventoryTextScale.sp11)
            }
        }
    }
}

// ── مساعدات ──────────────────────────────────────────

@Composable
internal fun LowStockSupplierDialog(
    groupedItems: Map<String, List<LowStockSupplierItem>>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context          = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_d0065b7ffda9), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = InventoryTextScale.sp16)
                VertoIconButton(onClick = {
                    val sb = StringBuilder()
                    groupedItems.entries.forEachIndexed { idx, (supplier, rows) ->
                        if (idx > 0) sb.append('\n')
                        if (supplier.isBlank()) sb.appendLine(context.getString(com.verto.feature.inventory.R.string.inventory_v298_2183806367be))
                        else sb.appendLine(context.getString(com.verto.feature.inventory.R.string.inventory_v298_16d819776545, supplier))
                        rows.forEach { row -> sb.appendLine(context.getString(com.verto.feature.inventory.R.string.inventory_v298_ed08dee7d56d, row.itemName)) }
                    }
                    clipboardManager.setText(AnnotatedString(sb.toString().trimEnd()))
                    Toast.makeText(context, context.getString(com.verto.feature.inventory.R.string.inventory_v298_1d8942bec592), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, null, tint = AccentPrimary, modifier = Modifier.size(InventoryDimensions.dp20))
                }
            }
        },
        text = {
            if (groupedItems.isEmpty()) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_38d84dcdf542), color = TextMuted, fontSize = InventoryTextScale.sp14)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp12)) {
                    groupedItems.entries.forEach { (supplier, rows) ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                                Text(
                                    text       = if (supplier.isBlank()) androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_35c213600b8d) else supplier,
                                    color      = AccentPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = InventoryTextScale.sp14
                                )
                                rows.forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = InventoryDimensions.dp8),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(row.itemName, color = TextPrimary, fontSize = InventoryTextScale.sp13, modifier = Modifier.weight(1f))
                                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c79f71243f41, row.quantity), color = MaterialTheme.colorScheme.error, fontSize = InventoryTextScale.sp13, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close), color = AccentPrimary)
            }
        }
    )
}
