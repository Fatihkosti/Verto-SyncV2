package com.verto.app.ui.screens.commission

import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoBottomSheet
import com.verto.app.ui.components.VertoTabRow
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

// ─────────────────────────────────────────────────────────────────────────────
// Detail Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommissionDetailSheet(
    state: CommissionUiState,
    onDismiss: () -> Unit,
    onSelectClient: (ClientCommissionBalance) -> Unit,
    onInvoiceClick: ((String) -> Unit)?
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    VertoBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = CommissionDimensions.dp32)) {
            Text(
                text = when (state.selectedCard) {
                    CommissionCard.TOTAL        -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_5eeb51622305)
                    CommissionCard.WITHDRAWABLE -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_32ed295ad1b3)
                    CommissionCard.PENDING      -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_2f00aa57fabb)
                    CommissionCard.PAID         -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_ca3493544f14)
                    CommissionCard.EARNING      -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_89d566dedee2)
                },
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp17,
                modifier = Modifier.padding(horizontal = CommissionDimensions.dp16, vertical = CommissionDimensions.dp12)
            )

            if (state.selectedCard == CommissionCard.WITHDRAWABLE) {
                VertoTabRow(selectedTabIndex = selectedTab, containerColor = BgSurface, contentColor = AccentPrimary) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_38bd1a4075c9)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_f89222ec2548)) })
                }
                when (selectedTab) {
                    0 -> InvoiceListContent(state.filteredInvoices, onInvoiceClick = null)
                    1 -> ClientBalancesContent(state.clientBalances, onSelectClient)
                }
            } else if (state.selectedCard == CommissionCard.EARNING) {
                EarningListContent(state.earningItems)
            } else {
                InvoiceListContent(state.filteredInvoices, onInvoiceClick = onInvoiceClick)
            }
        }
    }
}

@Composable
internal fun EarningListContent(
    items: List<EarningCommissionItem>
) {
    if (items.isEmpty()) {
        Text(
            androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_35bea59fe995), color = TextMuted,
            modifier = Modifier.fillMaxWidth().padding(CommissionDimensions.dp32),
            textAlign = TextAlign.Center
        )
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = CommissionDimensions.dp480),
        contentPadding = PaddingValues(horizontal = CommissionDimensions.dp16, vertical = CommissionDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)
    ) {
        items(items, key = { it.invoiceId }) { item ->
            EarningCommissionRow(item)
        }
    }
}

@Composable
internal fun EarningCommissionRow(
    item: EarningCommissionItem
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(modifier = Modifier.padding(CommissionDimensions.dp12), verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.invoiceNumber?.let { androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_ff99e6679347_2, it) } ?: androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_ff99e6679347),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.earningAmount.eng(),
                    color = GoldPrimary,
                    fontSize = CommissionTextScale.sp13,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)) {
                Text(item.clientName, color = TextSecondary, fontSize = CommissionTextScale.sp13)
                if (item.clientTypeLabel.isNotBlank()) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_1fdf0d90c37a), color = TextMuted)
                    Text(item.clientTypeLabel, color = TextMuted, fontSize = CommissionTextScale.sp12)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_85826ccce798, item.paidAmount.eng()), color = SuccessColor, fontSize = CommissionTextScale.sp12)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_d435352a681a, item.remainingAmount.eng()), color = WarningColor, fontSize = CommissionTextScale.sp12)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_1dcbddb089c8, item.commissionAmount.eng()), color = TextSecondary, fontSize = CommissionTextScale.sp12)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_23246ca0a8a5, item.creditedAmount.eng()), color = TextMuted, fontSize = CommissionTextScale.sp12)
            }
        }
    }
}

@Composable
internal fun InvoiceListContent(
    invoices: List<InvoiceWithClientName>,
    onInvoiceClick: ((String) -> Unit)?
) {
    if (invoices.isEmpty()) {
        Text(
            androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_e5ce6bd59016), color = TextMuted,
            modifier = Modifier.fillMaxWidth().padding(CommissionDimensions.dp32),
            textAlign = TextAlign.Center
        )
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = CommissionDimensions.dp480),
        contentPadding = PaddingValues(horizontal = CommissionDimensions.dp16, vertical = CommissionDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)
    ) {
        items(invoices) { item ->
            InvoiceCommissionRow(item, onInvoiceClick)
        }
    }
}

@Composable
internal fun InvoiceCommissionRow(
    item: InvoiceWithClientName,
    onInvoiceClick: ((String) -> Unit)?
) {
    val clickable = onInvoiceClick != null
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth().then(
            if (clickable) Modifier.clickable { onInvoiceClick?.invoke(item.invoice.id) }
            else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(modifier = Modifier.padding(CommissionDimensions.dp12)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_a8d9bb06f339, item.invoice.invoiceNumber),
                        color = if (clickable) AccentLight else TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (clickable) Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_60ecbc7f40b3), color = AccentLight, fontSize = CommissionTextScale.sp16)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp10)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_c79f71243f41, item.invoice.totalAmount.eng()), color = TextSecondary, fontSize = CommissionTextScale.sp12)
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_b1de7fbe216a, item.invoice.commission.eng()), color = SuccessColor, fontSize = CommissionTextScale.sp12, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(CommissionDimensions.dp4))
            Row(horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)) {
                Text(item.clientName, color = TextSecondary, fontSize = CommissionTextScale.sp13)
                if (item.clientTypeLabel.isNotBlank()) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_1fdf0d90c37a), color = TextMuted)
                    Text(item.clientTypeLabel, color = TextMuted, fontSize = CommissionTextScale.sp12)
                }
            }
        }
    }
}

@Composable
internal fun ClientBalancesContent(
    balances: List<ClientCommissionBalance>,
    onSelectClient: (ClientCommissionBalance) -> Unit
) {
    if (balances.isEmpty()) {
        Text(
            androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_5b9ac111a2ea), color = TextMuted,
            modifier = Modifier.fillMaxWidth().padding(CommissionDimensions.dp32),
            textAlign = TextAlign.Center
        )
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = CommissionDimensions.dp480),
        contentPadding = PaddingValues(horizontal = CommissionDimensions.dp16, vertical = CommissionDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)
    ) {
        items(balances) { balance ->
            VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Row(
                    Modifier.padding(CommissionDimensions.dp12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(balance.clientName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(CommissionDimensions.dp4))
                        Row(horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp12)) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_33f370dcf73f, balance.totalCommission.eng()), color = TextSecondary, fontSize = CommissionTextScale.sp12)
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_cbaba37ac4f0, balance.withdrawableCommission.eng()), color = SuccessColor, fontSize = CommissionTextScale.sp12, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    VertoButton(
                        onClick = { onSelectClient(balance) },
                        modifier = Modifier.padding(start = CommissionDimensions.dp8),
                        colors   = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                        contentPadding = PaddingValues(horizontal = CommissionDimensions.dp14, vertical = CommissionDimensions.dp6)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_facb5914f191), fontSize = CommissionTextScale.sp13) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity Log Row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun ActivityLogRow(log: CommissionActivityItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = CommissionDimensions.dp6), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(CommissionDimensions.dp8).clip(RoundedCornerShape(CommissionDimensions.dp4)).background(SuccessColor))
        Spacer(Modifier.width(CommissionDimensions.dp10))
        Column {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_c23bedfe66d4, log.totalAmount.eng(), log.clientName),
                color = TextPrimary, fontSize = CommissionTextScale.sp13
            )
            Text(
                DateUtils.formatDate(log.paidAt) + if (log.bankName.isNotBlank()) androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_4b512302bf23, log.bankName) else "",
                color = TextMuted, fontSize = CommissionTextScale.sp11
            )
        }
    }
}
