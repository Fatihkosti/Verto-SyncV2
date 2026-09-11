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


// ─────────────────────────────────────────────────────
// Dialog — إضافة صنف خدمي
// ─────────────────────────────────────────────────────

@Composable
internal fun InventoryReferenceSearch(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp30)
            .shadow(InventoryDimensions.dp3, RoundedCornerShape(InventoryDimensions.dp10)),
        shape = RoundedCornerShape(InventoryDimensions.dp10),
        color = BgCard,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = InventoryDimensions.dp10, end = InventoryDimensions.dp13),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_search),
                tint = TextMuted,
                modifier = Modifier.size(InventoryDimensions.dp17),
            )
            Spacer(Modifier.width(InventoryDimensions.dp4))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = TextPrimary,
                    fontSize = InventoryTextScale.sp11,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                ),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c43acabd84f6),
                                color = TextMuted,
                                fontSize = InventoryTextScale.sp11,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
internal fun InventoryReferenceFilters(
    selectedFilter: InventoryStockFilter,
    onFilterChange: (InventoryStockFilter) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp34)
            .shadow(InventoryDimensions.dp3, RoundedCornerShape(InventoryDimensions.dp11)),
        shape = RoundedCornerShape(InventoryDimensions.dp11),
        color = BgCard,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = InventoryDimensions.dp15),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InventoryReferenceFilterChip(
                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_all),
                selected = selectedFilter == InventoryStockFilter.ALL,
                selectedColor = WaitingColor,
                onClick = { onFilterChange(InventoryStockFilter.ALL) },
                modifier = Modifier.width(InventoryDimensions.dp63),
            )
            InventoryReferenceFilterChip(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_15b8dd4725b4),
                dotColor = WarningColor,
                selected = selectedFilter == InventoryStockFilter.LOW,
                selectedColor = WaitingColor,
                onClick = { onFilterChange(InventoryStockFilter.LOW) },
                modifier = Modifier.width(InventoryDimensions.dp77),
            )
            InventoryReferenceFilterChip(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_cd67d6d941f1),
                dotColor = MaterialTheme.colorScheme.error,
                selected = selectedFilter == InventoryStockFilter.OUT,
                selectedColor = WaitingColor,
                onClick = { onFilterChange(InventoryStockFilter.OUT) },
                modifier = Modifier.width(InventoryDimensions.dp74),
            )
            InventoryReferenceFilterChip(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_50836ecf83b8),
                icon = Icons.Default.Schedule,
                selected = selectedFilter == InventoryStockFilter.SLOW,
                selectedColor = WaitingColor,
                onClick = { onFilterChange(InventoryStockFilter.SLOW) },
                modifier = Modifier.width(InventoryDimensions.dp72),
            )
        }
    }
}

@Composable
internal fun InventoryReferenceFilterChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        modifier = modifier
            .height(InventoryDimensions.dp20)
            .clip(RoundedCornerShape(InventoryDimensions.dp9))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(InventoryDimensions.dp9),
        color = if (selected) selectedColor else BgCard,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(InventoryDimensions.dp0_6, BorderColor),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(InventoryDimensions.dp19))
            if (dotColor != null) {
                Box(Modifier.size(InventoryDimensions.dp6).clip(CircleShape).background(dotColor))
            }
            if (icon != null || dotColor != null) Spacer(Modifier.width(InventoryDimensions.dp8))
            Text(label, color = if (selected) TextOnAccent else TextPrimary, fontSize = InventoryTextScale.sp12)
        }
    }
}
