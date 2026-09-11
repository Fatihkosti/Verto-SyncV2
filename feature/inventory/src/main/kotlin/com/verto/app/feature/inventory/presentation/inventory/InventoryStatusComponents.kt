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
internal fun HeaderIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(InventoryDimensions.dp38).clip(RoundedCornerShape(InventoryDimensions.dp10))
            .background(BgCard).border(InventoryDimensions.dp1, BorderColor, RoundedCornerShape(InventoryDimensions.dp10))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
internal fun InfoChip(text: String, color: Color) {
    Text(
        text     = text,
        color    = color,
        fontSize = InventoryTextScale.sp11,
        modifier = Modifier
            .clip(RoundedCornerShape(InventoryDimensions.dp6))
            .background(color.copy(0.1f))
            .padding(horizontal = InventoryDimensions.dp6, vertical = InventoryDimensions.dp2)
    )
}

@Composable
internal fun SquarePriceCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val baseModifier = modifier
        .fillMaxHeight()
        .clip(RoundedCornerShape(InventoryDimensions.dp10))
        .background(color.copy(alpha = 0.1f))

    val finalModifier = if (onClick != null)
        baseModifier.clickable(onClick = onClick)
    else
        baseModifier

    Column(
        modifier            = finalModifier.padding(horizontal = InventoryDimensions.dp6, vertical = InventoryDimensions.dp8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text       = value,
            color      = color,
            fontSize   = InventoryTextScale.sp12,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
            softWrap   = false
        )
        Spacer(Modifier.height(InventoryDimensions.dp2))
        Text(
            text     = label,
            color    = color.copy(alpha = 0.75f),
            fontSize = InventoryTextScale.sp10,
            maxLines = 1
        )
    }
}
