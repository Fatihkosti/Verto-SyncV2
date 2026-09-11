package com.verto.app.ui.screens.commission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.commission.application.CommissionCard
import com.verto.app.feature.commission.application.CommissionUiState
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@Composable
internal fun CommissionDetailsList(
    state: CommissionUiState,
    onCardClick: (CommissionCard) -> Unit,
) {
    val rows = listOf(
        Triple(CommissionCard.TOTAL, "الكل", state.totalCommission),
        Triple(CommissionCard.WITHDRAWABLE, "قابل للسحب", state.withdrawableCommission),
        Triple(CommissionCard.PENDING, "قيد الاستحقاق", state.pendingCommission),
        Triple(CommissionCard.PAID, "مدفوع", state.paidCommission),
        Triple(CommissionCard.EARNING, "قيد التحصيل", state.earningCommission),
    )

    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommissionDimensions.dp14),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = androidx.compose.foundation.BorderStroke(CommissionDimensions.dp0_5, BorderColor),
    ) {
        Column {
            rows.forEachIndexed { index, (card, label, amount) ->
                VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
                    onClick = { onCardClick(card) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    shape = RoundedCornerShape(CommissionDimensions.dp0),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CommissionDimensions.dp14, vertical = CommissionDimensions.dp12),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            color = when (card) {
                                CommissionCard.WITHDRAWABLE -> SuccessColor
                                CommissionCard.PENDING -> WarningColor
                                CommissionCard.PAID -> AccentBlue
                                CommissionCard.EARNING -> GoldPrimary
                                CommissionCard.TOTAL -> TextPrimary
                            },
                            fontSize = CommissionTextScale.sp13,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = amount.eng(),
                            color = TextSecondary,
                            fontSize = CommissionTextScale.sp13,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = CommissionDimensions.dp14),
                        thickness = CommissionDimensions.dp0_5,
                        color = BorderColor,
                    )
                }
            }
        }
    }
}
