package com.verto.app.ui.screens.commission

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.commission.application.CommissionAttentionItem
import com.verto.app.feature.commission.application.CommissionAttentionSummary
import com.verto.app.feature.commission.application.CommissionUiState
import com.verto.app.feature.commission.application.CommissionWithdrawalStatus
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@Composable
internal fun CommissionCurrentSummary(state: CommissionUiState) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommissionDimensions.dp14),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = androidx.compose.foundation.BorderStroke(CommissionDimensions.dp0_5, BorderColor),
    ) {
        Column(Modifier.padding(CommissionDimensions.dp14)) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_ba2cea6ca9fa), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp15)
            Spacer(Modifier.height(CommissionDimensions.dp10))
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_3ec0f859c17a), color = TextSecondary, fontSize = CommissionTextScale.sp12)
            Text(
                state.withdrawableCommission.eng(),
                color = SuccessColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = CommissionTextScale.sp20,
            )
            Spacer(Modifier.height(CommissionDimensions.dp12))
            HorizontalDivider(thickness = CommissionDimensions.dp0_5, color = BorderColor)
            Spacer(Modifier.height(CommissionDimensions.dp10))
            SummaryLine(
                label = androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_201676c8d66f),
                value = "${state.attention.openWithdrawalRequestsCount} · ${state.attention.openWithdrawalRequestsAmount.eng()}",
            )
            SummaryLine("قيد الاستحقاق", state.pendingCommission.eng())
            SummaryLine("مدفوع خلال الفترة", state.paidCommission.eng())
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CommissionDimensions.dp3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = CommissionTextScale.sp12)
        Text(value, color = TextPrimary, fontSize = CommissionTextScale.sp12, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun CommissionAttentionSection(
    attention: CommissionAttentionSummary,
    onOpenWithdrawalRequests: () -> Unit,
    onReadyPayout: (CommissionAttentionItem.ReadyPayout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp8)) {
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_0c7e2fb48420), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp15)
        if (attention.items.isEmpty()) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_4f4c712aa034),
                color = TextMuted,
                fontSize = CommissionTextScale.sp13,
                modifier = Modifier.fillMaxWidth().padding(vertical = CommissionDimensions.dp12),
            )
        } else {
            attention.items.forEach { item ->
                when (item) {
                    is CommissionAttentionItem.WithdrawalRequest -> WithdrawalAttentionRow(
                        item = item,
                        onOpen = onOpenWithdrawalRequests,
                    )
                    is CommissionAttentionItem.ReadyPayout -> ReadyPayoutAttentionRow(
                        item = item,
                        onOpen = { onReadyPayout(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WithdrawalAttentionRow(
    item: CommissionAttentionItem.WithdrawalRequest,
    onOpen: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommissionDimensions.dp12),
        colors = CardDefaults.cardColors(containerColor = BgCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CommissionDimensions.dp12),
            verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp4),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.clientName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = CommissionTextScale.sp13)
                Text(item.amount.eng(), color = SuccessColor, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp14)
            }
            Text(
                when (item.status) {
                    CommissionWithdrawalStatus.PENDING -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_0fba5254f69b)
                    CommissionWithdrawalStatus.APPROVED -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_5d9908f63dcd)
                    else -> androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_eb972489ce70)
                },
                color = if (item.status == CommissionWithdrawalStatus.PENDING) WarningColor else AccentBlue,
                fontSize = CommissionTextScale.sp11,
            )
            Text(item.requestedAt.take(10), color = TextMuted, fontSize = CommissionTextScale.sp11)
            VertoOutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(if (item.status == CommissionWithdrawalStatus.PENDING) androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_be874c0f46aa_2) else androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_be874c0f46aa))
            }
        }
    }
}

@Composable
private fun ReadyPayoutAttentionRow(
    item: CommissionAttentionItem.ReadyPayout,
    onOpen: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommissionDimensions.dp12),
        colors = CardDefaults.cardColors(containerColor = BgCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CommissionDimensions.dp12),
            verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp4),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.clientName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = CommissionTextScale.sp13)
                Text(item.amount.eng(), color = SuccessColor, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp14)
            }
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_9310cd4dac07, item.invoicesCount), color = TextSecondary, fontSize = CommissionTextScale.sp11)
            VertoButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_efb0540fb7e5))
            }
        }
    }
}
