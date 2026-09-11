package com.verto.app.feature.reports.presentation.components.financial

import com.verto.feature.reports.R
import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.ProfitLossData
import com.verto.app.feature.reports.application.model.NetProfitReliabilityIssue
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun ProfitLossStatement(
    pnl: ProfitLossData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_profit_loss_title),
        icon = "📋",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
        ) {
            val segmentScope = NetProfitReliabilityIssue.SEGMENT_FILTER_WITH_UNALLOCATED_EXPENSES in pnl.netProfitReliabilityIssues
            if (pnl.functionalCurrencyCode.isNotBlank()) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_4c23b54087b5, pnl.functionalCurrencyCode), color = TextMuted, fontSize = ReportsTextScale.sp11)
            }
            if (!pnl.isHistoricalCostComplete) {
                Text(
                    stringResource(R.string.legacy_ui_3ffefcbdb7f3, pnl.excludedUnknownCostLines),
                    color = WarningColor,
                    fontSize = ReportsTextScale.sp11,
                )
            }
            PnlRow("المبيعات الإجمالية", pnl.grossSales, positive = true, bold = false)
            if (pnl.returns > 0) {
                PnlRow("المرتجعات", pnl.returns, positive = false, bold = false, indent = true)
            }
            PnlRow("صافي المبيعات", pnl.netSales, positive = true, bold = true)

                SectionDivider()

            if (pnl.isHistoricalCostComplete) {
                PnlRow("تكلفة البضاعة المباعة", pnl.cogs, positive = false, bold = false)
                if (pnl.shippingCosts > 0) {
                    PnlRow("تكاليف الشحن", pnl.shippingCosts, positive = false, bold = false, indent = true)
                }
                PnlRow("مجمل الربح", pnl.grossProfit, positive = pnl.grossProfit >= 0, bold = true)
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_gross_margin_format, pnl.grossMargin.toInt()),
                    color = if (pnl.grossMargin >= 0) SuccessColor else ErrorColor,
                    fontSize = ReportsTextScale.sp12,
                    modifier = Modifier.padding(start = ReportsDimensions.dp8, bottom = ReportsDimensions.dp4)
                )
            } else {
                Text(stringResource(R.string.reports_v364_cost_incomplete), color = WarningColor, fontSize = ReportsTextScale.sp11)
            }

            SectionDivider()

            if (!segmentScope) {
                Text(
                    text = stringResource(R.string.legacy_ui_b594e895c815),
                    color = TextSecondary,
                    fontSize = ReportsTextScale.sp13,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = ReportsDimensions.dp4)
                )
                pnl.expenseBreakdown.forEach { (cat, amount) ->
                    PnlRow(cat, amount, positive = false, bold = false, indent = true)
                }
                PnlRow("إجمالي المصروفات", pnl.operatingExpenses, positive = false, bold = false)
                if (pnl.commissions > 0) {
                    PnlRow("العمولات المدفوعة", pnl.commissions, positive = false, bold = false)
                }
            } else {
                Text(stringResource(R.string.reports_v364_segment_profit_unavailable), color = WarningColor, fontSize = ReportsTextScale.sp11)
            }

            SectionDivider()

            if (pnl.isNetProfitReliable) {
                PnlRow("صافي الربح", pnl.netProfit, positive = pnl.netProfit >= 0, bold = true, large = true)
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_net_margin_format, pnl.netMargin.toInt()),
                    color = if (pnl.netMargin >= 0) SuccessColor else ErrorColor,
                    fontSize = ReportsTextScale.sp12,
                    modifier = Modifier.padding(start = ReportsDimensions.dp8, bottom = ReportsDimensions.dp8)
                )
            } else if (!segmentScope) {
                Text(stringResource(R.string.reports_v364_profit_unreliable), color = WarningColor, fontSize = ReportsTextScale.sp11)
            }
        }
    }
}

@Composable
private fun PnlRow(
    label: String,
    amount: Double,
    positive: Boolean,
    bold: Boolean,
    indent: Boolean = false,
    large: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (indent) ReportsDimensions.dp16 else ReportsDimensions.dp0,
                top = ReportsDimensions.dp2, bottom = ReportsDimensions.dp2
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (bold) TextPrimary else TextSecondary,
            fontSize = if (large) ReportsTextScale.sp16 else if (bold) ReportsTextScale.sp14 else ReportsTextScale.sp13,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = if (!positive && amount > 0) {
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_amount_negative_format, CurrencyFormatter.formatNoSymbol(amount))
            } else {
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_amount_plain_format, CurrencyFormatter.formatNoSymbol(amount))
            },
            color = if (positive) SuccessColor else ErrorColor,
            fontSize = if (large) ReportsTextScale.sp16 else if (bold) ReportsTextScale.sp14 else ReportsTextScale.sp13,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = ReportsDimensions.dp6),
        color = BorderColor,
        thickness = ReportsDimensions.dp0_5
    )
}
