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
internal fun InventoryItemCard(
    item: InventoryItemView,
    categories: List<String>,
    canEdit: Boolean,
    showCost: Boolean,
    onEdit: () -> Unit,
    onShowMovements: () -> Unit
) {
    val isService = item.isService
    val isLow     = !isService && item.quantity <= item.minQuantity
    val isOutOf   = !isService && item.quantity == 0
    val cardBorder = when {
        isOutOf   -> MaterialTheme.colorScheme.error.copy(0.5f)
        isLow     -> WarningColor.copy(0.4f)
        isService -> InfoColor.copy(0.3f)
        else      -> BorderColor
    }

    VertoCard(
        modifier = Modifier.border(InventoryDimensions.dp1, cardBorder, RoundedCornerShape(InventoryDimensions.dp16)),
        onClick  = if (canEdit) onEdit else null
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            // ── معلومات الصنف ────────────────────────
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp6)
                ) {
                    Text(
                        item.name,
                        color      = TextPrimary,
                        fontSize   = InventoryTextScale.sp15,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (isService) {
                        Surface(shape = RoundedCornerShape(InventoryDimensions.dp6), color = InfoContainer) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_24db4b5a9540),
                                color    = InfoColor,
                                fontSize = InventoryTextScale.sp10,
                                modifier = Modifier.padding(horizontal = InventoryDimensions.dp6, vertical = InventoryDimensions.dp2)
                            )
                        }
                    }
                    if (item.isUnitItem) {
                        Surface(shape = RoundedCornerShape(InventoryDimensions.dp6), color = AccentDim) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_95eeaed8bd8b),
                                color    = AccentLight,
                                fontSize = InventoryTextScale.sp10,
                                modifier = Modifier.padding(horizontal = InventoryDimensions.dp6, vertical = InventoryDimensions.dp2)
                            )
                        }
                    }
                }

                // رقم القطعة
                if (item.partNumber.isNotBlank()) {
                    Spacer(Modifier.height(InventoryDimensions.dp2))
                    InfoChip(item.partNumber, InfoColor)
                }

                // ── صف الأسعار + الحركات ─────────────
                if (!isService) {
                    Spacer(Modifier.height(InventoryDimensions.dp8))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp6)
                    ) {
                        val pct = if (showCost && item.buyPrice > 0 && item.sellPrice > 0)
                            ((item.sellPrice - item.buyPrice) / item.buyPrice * 100).toInt()
                        else null

                        if (showCost) {
                            SquarePriceCard(
                                value    = if (item.buyPrice > 0) String.format(Locale.US, "%.0f", item.buyPrice) else "—",
                                label    = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c48e5f785436),
                                color    = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SquarePriceCard(
                            value    = if (item.sellPrice > 0) String.format(Locale.US, "%.0f", item.sellPrice) else "—",
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_bf3a3673e472),
                            color    = SuccessColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (showCost) {
                            SquarePriceCard(
                                value    = if (pct != null) String.format(Locale.US, "%d%%", pct) else "—",
                                label    = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_b9f1f675ce91),
                                color    = GoldPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SquarePriceCard(
                            value    = "📊",
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_eac09a5b854a),
                            color    = AccentLight,
                            modifier = Modifier.weight(1f),
                            onClick  = onShowMovements
                        )
                    }
                }
            }

            Spacer(Modifier.width(InventoryDimensions.dp12))

            // ── الكمية ───────────────────────────────
            Column(horizontalAlignment = Alignment.End) {
                if (isService) {
                    Surface(shape = RoundedCornerShape(InventoryDimensions.dp8), color = InfoContainer) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_3b774bd75789),
                            color      = InfoColor,
                            fontSize   = InventoryTextScale.sp13,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = InventoryDimensions.dp10, vertical = InventoryDimensions.dp4)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(InventoryDimensions.dp8),
                        color = when {
                            isOutOf -> MaterialTheme.colorScheme.errorContainer
                            isLow   -> WarningContainer
                            else    -> SuccessContainer
                        }
                    ) {
                        Text(
                            text  = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c79f71243f41, item.quantity),
                            color = when {
                                isOutOf -> MaterialTheme.colorScheme.error
                                isLow   -> WarningColor
                                else    -> SuccessColor
                            },
                            fontSize   = InventoryTextScale.sp14,
                            fontWeight = FontWeight.Black,
                            modifier   = Modifier.padding(horizontal = InventoryDimensions.dp10, vertical = InventoryDimensions.dp4)
                        )
                    }
                }
            }
        }

        // ── الموقع والملاحظة ─────────────────────────
        if (item.location.isNotBlank() || item.note.isNotBlank()) {
            Spacer(Modifier.height(InventoryDimensions.dp8))
            if (item.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_1b220ac954f1), fontSize = InventoryTextScale.sp11)
                    Text(item.location, color = TextMuted, fontSize = InventoryTextScale.sp11)
                }
            }
            if (item.note.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_edc47290ed60), fontSize = InventoryTextScale.sp11)
                    Text(item.note, color = TextMuted, fontSize = InventoryTextScale.sp11, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

    }
}

// ─────────────────────────────────────────────────────
// Reference layout — شاشة المخزون البيضاء
// ─────────────────────────────────────────────────────

@Composable
internal fun InventoryReferenceItemCard(
    item: InventoryItemView,
    categories: List<String>,
    latestMovement: InventoryMovementItem?,
    movementCount: Int,
    showCost: Boolean,
    onEdit: (() -> Unit)?,
    onShowMovements: () -> Unit,
    onAdjustStock: () -> Unit,
    onDelete: () -> Unit,
) {
    val isOut = item.quantity <= 0
    val isLow = !isOut && item.quantity <= item.minQuantity
    val stateColor = when {
        isOut -> MaterialTheme.colorScheme.error
        isLow -> WarningColor
        else -> SuccessColor
    }
    val stateFill = when {
        isOut -> MaterialTheme.colorScheme.errorContainer
        isLow -> WarningContainer
        else -> SuccessContainer
    }
    val margin = if (item.buyPrice > 0) ((item.sellPrice - item.buyPrice) / item.buyPrice * 100).toInt() else 0
    val category = categories.firstOrNull() ?: "بدون تصنيف"
    var itemMenuExpanded by rememberSaveable(item.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp116)
            .shadow(InventoryDimensions.dp3, RoundedCornerShape(InventoryDimensions.dp10))
            .clip(RoundedCornerShape(InventoryDimensions.dp10))
            .clickable(enabled = onEdit != null, onClick = { onEdit?.invoke() }),
        shape = RoundedCornerShape(InventoryDimensions.dp10),
        color = BgCard,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = InventoryDimensions.dp11, vertical = InventoryDimensions.dp8)) {
            Row(
                Modifier.fillMaxWidth().height(InventoryDimensions.dp48),
                horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp6),
                verticalAlignment = Alignment.Top,
            ) {
                InventoryReferenceImageBox()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = InventoryDimensions.dp8)
                        .offset(y = InventoryDimensions.dpNegative3),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp7)) {
                        if (isOut) Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(InventoryDimensions.dp19))
                        Text(
                            item.name,
                            color = TextPrimary,
                            fontSize = InventoryTextScale.sp12,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(InventoryDimensions.dp1))
                    Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp7), verticalAlignment = Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_70ed8292b81e), color = TextMuted, fontSize = InventoryTextScale.sp9_5)
                        Text(category, color = WaitingColor, fontSize = InventoryTextScale.sp9_5)
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_ecf727ea048d), color = TextMuted, fontSize = InventoryTextScale.sp9_5)
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_5914f8e0f08d), color = TextMuted, fontSize = InventoryTextScale.sp9_5)
                        Text(
                            latestMovementReferenceLabel(latestMovement),
                            color = movementReferenceColor(latestMovement),
                            fontSize = InventoryTextScale.sp9_5,
                            fontWeight = FontWeight.Bold,
                        )
                        if (latestMovement != null) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_ecf727ea048d), color = TextMuted, fontSize = InventoryTextScale.sp8_5)
                            Text(
                                formatInventoryReferenceLastMovement(latestMovement.createdAt),
                                color = TextMuted,
                                fontSize = InventoryTextScale.sp9_5,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.width(InventoryDimensions.dp40),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(InventoryDimensions.dp48),
                        shape = RoundedCornerShape(InventoryDimensions.dp8),
                        color = stateFill,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(item.quantity.toString(), color = stateColor, fontSize = InventoryTextScale.sp17, fontWeight = FontWeight.Bold)
                            Text(
                                when { isOut -> androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_cd67d6d941f1); isLow -> androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_15b8dd4725b4); else -> androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_5dcd5144563e) },
                                color = stateColor,
                                fontSize = InventoryTextScale.sp8,
                            )
                        }
                    }
                }
                Box {
                    VertoIconButton(
                        onClick = { itemMenuExpanded = true },
                        modifier = Modifier.size(InventoryDimensions.dp48),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_b18ceda4c68f),
                            tint = TextMuted,
                            modifier = Modifier.size(InventoryDimensions.dp14),
                        )
                    }
                    DropdownMenu(
                        expanded = itemMenuExpanded,
                        onDismissRequest = { itemMenuExpanded = false },
                    ) {
                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f2e2bbc10b16)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { itemMenuExpanded = false; onEdit.invoke() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f26b72e41fe8)) },
                            leadingIcon = { Icon(Icons.Default.Inventory, null) },
                            onClick = { itemMenuExpanded = false; onAdjustStock() },
                        )
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_b57596f18b10)) },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = { itemMenuExpanded = false; onShowMovements() },
                        )
                        if (onEdit != null) {
                            Divider()
                            DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_68dc0483bdfe), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { itemMenuExpanded = false; onDelete() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(InventoryDimensions.dp4))
            InventoryReferenceMetrics(
                item = item,
                margin = margin,
                movementCount = movementCount,
                showCost = showCost,
                onShowMovements = onShowMovements,
            )
        }
    }
}

internal fun latestMovementReferenceLabel(movement: InventoryMovementItem?): String = when (movement?.movementType) {
    MovementType.IN -> "شراء"
    MovementType.OUT -> "بيع"
    MovementType.ADJUST -> "تعديل"
    MovementType.RETURN -> "مرتجع"
    null -> "لا توجد"
}

@Composable

internal fun movementReferenceColor(movement: InventoryMovementItem?): Color = when (movement?.movementType) {
    MovementType.IN, MovementType.RETURN -> SuccessColor
    MovementType.OUT -> SuccessColor
    MovementType.ADJUST -> WarningColor
    null -> TextMuted
}

internal fun formatInventoryReferenceLastMovement(timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR)
    val dayLabel = when {
        sameDay -> "اليوم"
        now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) - date.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> "أمس"
        else -> "${date.get(java.util.Calendar.DAY_OF_MONTH)}/${date.get(java.util.Calendar.MONTH) + 1}"
    }
    val hour = date.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
    val minute = date.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    val period = if (date.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "ص" else "م"
    return "$dayLabel $hour:$minute $period"
}

@Composable
internal fun InventoryReferenceImageBox() {
    Surface(
        modifier = Modifier.size(InventoryDimensions.dp40),
        shape = RoundedCornerShape(InventoryDimensions.dp9),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(InventoryDimensions.dp0_6, BorderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Build, null, tint = TextMuted, modifier = Modifier.size(InventoryDimensions.dp26))
        }
    }
}
