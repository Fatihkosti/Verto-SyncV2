package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.feature.inventory.application.model.LowStockSupplierItem

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
internal fun InventoryReferenceStats(
    total: Int,
    low: Int,
    out: Int,
    fresh: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp78),
        horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8),
    ) {
        InventoryReferenceStatCard(
            label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_32c448444746),
            value = total,
            icon = Icons.Outlined.Inventory2,
            tint = PermissionColor,
            fill = PermissionContainer,
        )
        InventoryReferenceStatCard(
            label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_15b8dd4725b4),
            value = low,
            icon = Icons.Outlined.Warning,
            tint = WarningColor,
            fill = WarningContainer,
        )
        InventoryReferenceStatCard(
            label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_cd67d6d941f1),
            value = out,
            icon = Icons.Outlined.Cancel,
            tint = MaterialTheme.colorScheme.error,
            fill = MaterialTheme.colorScheme.errorContainer,
        )
        InventoryReferenceStatCard(
            label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_76f6f159e463),
            value = fresh,
            icon = Icons.Outlined.LocalOffer,
            tint = WaitingColor,
            fill = WaitingContainer,
        )
    }
}

@Composable
internal fun RowScope.InventoryReferenceStatCard(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    fill: Color,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        shape = RoundedCornerShape(InventoryDimensions.dp9),
        color = fill,
        border = androidx.compose.foundation.BorderStroke(InventoryDimensions.dp0_6, tint.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(InventoryDimensions.dp20))
            Text(label, color = TextPrimary, fontSize = InventoryTextScale.sp12, maxLines = 1, softWrap = false)
            Text(value.toString(), color = if (value == 0) tint else TextPrimary, fontSize = InventoryTextScale.sp18, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun InventoryReferenceMetrics(
    item: InventoryItemView,
    margin: Int,
    movementCount: Int,
    showCost: Boolean,
    onShowMovements: () -> Unit,
) {
    val purchaseColor = when {
        item.quantity <= 0 -> MaterialTheme.colorScheme.error
        item.quantity <= item.minQuantity -> WarningColor
        else -> SuccessColor
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(InventoryDimensions.dp48),
        shape = RoundedCornerShape(InventoryDimensions.dp9),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(InventoryDimensions.dp0_6, BorderColor),
    ) {
        Row(Modifier.fillMaxSize()) {
            InventoryReferenceMetric(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_c48e5f785436),
                value = formatInventoryReferenceMoney(item.buyPrice),
                unit = "ج.م",
                icon = Icons.Default.ShoppingCart,
                valueColor = purchaseColor,
                modifier = Modifier.weight(1f),
            )
            InventoryReferenceMetricDivider()
            InventoryReferenceMetric(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_bf3a3673e472),
                value = formatInventoryReferenceMoney(item.sellPrice),
                unit = "ج.م",
                icon = Icons.Default.TrendingUp,
                valueColor = SuccessColor,
                modifier = Modifier.weight(1f),
            )
            if (showCost) {
                InventoryReferenceMetricDivider()
                InventoryReferenceMetric(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_b9f1f675ce91),
                    value = "$margin%",
                    icon = Icons.Default.Percent,
                    valueColor = WarningColor,
                    modifier = Modifier.weight(1f),
                )
            }
            InventoryReferenceMetricDivider()
            InventoryReferenceMetric(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_eac09a5b854a),
                value = movementCount.toString(),
                icon = Icons.Default.BarChart,
                valueColor = InfoColor,
                modifier = Modifier.weight(1f),
                onClick = onShowMovements,
            )
        }
    }
}

@Composable
internal fun RowScope.InventoryReferenceMetricDivider() {
    Box(
        modifier = Modifier
            .width(InventoryDimensions.dp1)
            .height(InventoryDimensions.dp20)
            .align(Alignment.CenterVertically)
            .background(BorderColor)
    )
}

@Composable
internal fun RowScope.InventoryReferenceMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
    onClick: (() -> Unit)? = null,
) {
                Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = InventoryDimensions.dp4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp5)) {
            Text(label, color = TextMuted, fontSize = InventoryTextScale.sp9)
            Icon(icon, null, tint = valueColor, modifier = Modifier.size(InventoryDimensions.dp13))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp3)) {
            Text(value, color = valueColor, fontSize = InventoryTextScale.sp11, fontWeight = FontWeight.Medium)
            if (unit != null) Text(unit, color = TextPrimary, fontSize = InventoryTextScale.sp8)
        }
    }
}

internal fun formatInventoryReferenceMoney(value: Double): String =
    if (value <= 0.0) "—" else String.format(Locale.US, "%,.0f", value)
