package com.verto.app.feature.party.presentation.shared

import com.verto.app.feature.party.domain.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSharedPrimitiveTokens
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import com.verto.feature.party.R
import com.verto.app.ui.components.VertoIconButton

/** Party-owned dashboard components that depend on party/invoice data. */

@Composable
private fun PartyDateFilterChip(
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(VertoRadius.xs)
    Box(
        Modifier
            .heightIn(min = VertoSize.minTouchTarget)
            .clip(shape)
            .background(if (active) color.copy(alpha = VertoAlpha.faintBorder) else BgDeep)
            .border(
                VertoStroke.thin,
                if (active) color.copy(alpha = VertoAlpha.activeBorder) else BorderColor,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = VertoSharedPrimitiveTokens.infoChipHorizontalPadding,
                vertical = VertoSharedPrimitiveTokens.infoChipVerticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) color else TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun DashboardInvoiceRow(summary: PartyInvoiceSummary, onClick: () -> Unit) {
    val financial = summary.financial
    val isCredit = financial.isCredit
    val remaining = financial.remaining
    val isOverdue = financial.isOverdue
    val isPaid = isCredit && financial.isPaid

    val statusColor = when {
        isOverdue -> ErrorColor
        isPaid -> SuccessColor
        isCredit -> AccentBlue
        else -> AccentPrimary
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp12))
            .background(BgCard)
            .border(PartyDimensions.dp1, statusColor.copy(alpha = 0.15f), RoundedCornerShape(PartyDimensions.dp12))
            .clickable(onClick = onClick)
            .padding(PartyDimensions.dp12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_adebc50ade66, summary.invoice.invoiceNumber),
            color = statusColor,
            fontSize = PartyTextScale.sp12,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(PartyDimensions.dp40)
        )
        Column(Modifier.weight(1f).padding(horizontal = PartyDimensions.dp8)) {
            Text(
                stringResource(
                    R.string.party_invoice_title,
                    summary.invoice.invoiceNumber,
                ),
                color = TextPrimary,
                fontSize = PartyTextScale.sp13,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(DateUtils.formatDate(summary.invoice.createdAt), color = TextMuted, fontSize = PartyTextScale.sp11)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                WhatsAppUtils.formatAmount(summary.invoice.totalAmount),
                color = TextPrimary,
                fontSize = PartyTextScale.sp13,
                fontWeight = FontWeight.Bold
            )
            if (isCredit) {
                val label = when {
                    isPaid -> "مسدَّد ✓"
                    isOverdue -> "متأخر"
                    else -> "متبقي: ${WhatsAppUtils.formatAmount(remaining)}"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp2)
                ) {
                    if (isOverdue) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(PartyDimensions.dp10)
                        )
                    }
                    Text(label, color = statusColor, fontSize = PartyTextScale.sp10)
                }
            }
        }
    }
}

@Composable
fun ClientDateFilterBar(
    filterFrom: Long,
    filterTo: Long,
    isFiltered: Boolean,
    accentColor: Color,
    onShowFrom: () -> Unit,
    onShowTo: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp12))
            .background(BgCard)
            .padding(horizontal = PartyDimensions.dp12, vertical = PartyDimensions.dp8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
    ) {
        Icon(Icons.Filled.DateRange, contentDescription = null, tint = accentColor, modifier = Modifier.size(PartyDimensions.dp16))
        PartyDateFilterChip(
            label = if (filterFrom > 0L) DateUtils.formatDate(filterFrom) else stringResource(R.string.party_filter_from_date),
            active = filterFrom > 0L,
            color = accentColor,
            onClick = onShowFrom
        )
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_8179643cdc80), color = TextMuted, fontSize = PartyTextScale.sp12)
        PartyDateFilterChip(
            label = if (filterTo < Long.MAX_VALUE / 2) DateUtils.formatDate(filterTo) else stringResource(R.string.party_filter_to_date),
            active = filterTo < Long.MAX_VALUE / 2,
            color = accentColor,
            onClick = onShowTo
        )
        if (isFiltered) {
            Spacer(Modifier.weight(1f))
            VertoIconButton(onClick = onClear, modifier = Modifier.size(PartyDimensions.dp48)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(PartyDimensions.dp16))
            }
        }
    }
}
