package com.verto.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.PermissionColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.WarningColor
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.QuickActionIcon

@Composable
internal fun QuickActionPageIndicators(
    itemCount: Int,
    visibleCount: Int,
    listState: LazyListState,
) {
    val pageCount = ((itemCount + visibleCount - 1) / visibleCount).coerceAtLeast(1)
    if (pageCount <= 1) return

    val currentPage = (listState.firstVisibleItemIndex / visibleCount)
        .coerceIn(0, pageCount - 1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VertoSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = VertoSpacing.xxs)
                    .size(
                        if (index == currentPage) {
                            HomeDesignTokens.pagerIndicatorActive
                        } else {
                            HomeDesignTokens.pagerIndicator
                        },
                    )
                    .clip(RoundedCornerShape(VertoRadius.xs))
                    .background(if (index == currentPage) AccentPrimary else TextMuted.copy(alpha = VertoAlpha.soft)),
            )
        }
    }
}

@Composable
internal fun QuickActionTile(
    action: QuickAction,
    width: Dp,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    val visual = quickActionVisual(action.icon)
    Surface(
        modifier = Modifier
            .width(width)
            .aspectRatio(1f)
            .semantics {
                role = Role.Button
                contentDescription = action.label
            },
        onClick = onClick,
        enabled = !isBusy,
        color = visual.background,
        shape = QuickActionTicketShape,
        border = androidx.compose.foundation.BorderStroke(
            VertoStroke.thin,
            visual.foreground.copy(alpha = VertoAlpha.border),
        ),
        tonalElevation = VertoElevation.card,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(visual.brush(), QuickActionTicketShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(VertoSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(VertoSize.iconMedium),
                        strokeWidth = VertoStroke.progress,
                        color = visual.foreground,
                    )
                } else {
                    Icon(
                        imageVector = action.icon.imageVector(),
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(VertoSize.iconMedium),
                    )
                }
                Spacer(Modifier.height(VertoSpacing.xs))
                Text(
                    text = action.label,
                    color = visual.foreground,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

internal data class QuickActionVisual(val background: Color, val foreground: Color)

private val QuickActionTicketShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val corner = size.minDimension * 0.14f
    val cut = size.minDimension * 0.18f
    moveTo(corner, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height - corner)
    quadraticTo(size.width, size.height, size.width - corner, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height - cut)
    lineTo(0f, corner)
    quadraticTo(0f, 0f, corner, 0f)
    close()
}

internal fun QuickActionVisual.brush(): Brush = Brush.linearGradient(
    colors = listOf(
        background.copy(alpha = 0.96f),
        background.copy(alpha = 0.78f),
    ),
)

@Composable
internal fun quickActionVisual(icon: QuickActionIcon): QuickActionVisual {
    val foreground = when (icon) {
        QuickActionIcon.SALE_INVOICE -> SuccessColor
        QuickActionIcon.PURCHASE -> AccentBlue
        QuickActionIcon.INTERNATIONAL_PURCHASE -> AccentBlue
        QuickActionIcon.CLIENT -> PermissionColor
        QuickActionIcon.SUPPLIER -> PermissionColor
        QuickActionIcon.INVENTORY_ITEM -> AccentPrimary
        QuickActionIcon.EXPENSE -> WarningColor
        QuickActionIcon.PRICE_LIST -> AccentPrimary
        QuickActionIcon.PAYMENT -> AccentBlue
        QuickActionIcon.STOCK_COUNT -> AccentPrimary
        QuickActionIcon.GENERIC -> AccentPrimary
    }
    return QuickActionVisual(
        background = foreground,
        foreground = androidx.compose.ui.graphics.Color.White,
    )
}

internal fun QuickActionIcon.imageVector(): ImageVector = when (this) {
    QuickActionIcon.SALE_INVOICE -> Icons.Filled.ReceiptLong
    QuickActionIcon.PURCHASE -> Icons.Filled.AddBusiness
    QuickActionIcon.INTERNATIONAL_PURCHASE -> Icons.Filled.AddBusiness
    QuickActionIcon.CLIENT -> Icons.Filled.PersonAdd
    QuickActionIcon.SUPPLIER -> Icons.Filled.AddBusiness
    QuickActionIcon.INVENTORY_ITEM -> Icons.Filled.Inventory2
    QuickActionIcon.EXPENSE -> Icons.Filled.AttachMoney
    QuickActionIcon.PRICE_LIST -> Icons.Filled.ReceiptLong
    QuickActionIcon.PAYMENT -> Icons.Filled.Payments
    QuickActionIcon.STOCK_COUNT -> Icons.Filled.Inventory
    QuickActionIcon.GENERIC -> Icons.Filled.PointOfSale
}
