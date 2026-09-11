package com.verto.app.feature.reports.presentation.components.financial

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.CashFlowData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun CashFlowStatement(
    data: CashFlowData,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_cash_flow_title),
        icon = "💵",
        initiallyExpanded = initiallyExpanded,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp4),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)
        ) {
            if (data.openingBalance == 0.0 && data.totalIn == 0.0 && data.totalOut == 0.0) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_17695405cae1),
                    color = TextMuted,
                    fontSize = ReportsTextScale.sp13,
                    modifier = Modifier.padding(bottom = ReportsDimensions.dp8)
                )
                return@CollapsibleSection
            }

            CashFlowRow("رصيد البداية", data.openingBalance, neutral = true)
            Spacer(Modifier.height(ReportsDimensions.dp4))

            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_5342007749c0), color = SuccessColor, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)
            CashFlowRow("مبيعات نقدية", data.cashSales, positive = true, indent = true)
            CashFlowRow("تحصيل ديون", data.debtCollections, positive = true, indent = true)
            CashFlowRow("إضافات يدوية", data.manualAdds, positive = true, indent = true)
            CashFlowRow("الإجمالي الداخل", data.totalIn, positive = true)
            Spacer(Modifier.height(ReportsDimensions.dp4))

            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_8d932e63b02a), color = ErrorColor, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Medium)
            CashFlowRow("مشتريات نقدية", data.cashPurchases, positive = false, indent = true)
            CashFlowRow("سداد موردين", data.supplierPayments, positive = false, indent = true)
            CashFlowRow("مصروفات", data.expenses, positive = false, indent = true)
            CashFlowRow("خصم يدوي", data.manualDeductions, positive = false, indent = true)
            CashFlowRow("الإجمالي الخارج", data.totalOut, positive = false)
            Spacer(Modifier.height(ReportsDimensions.dp4))

            CashFlowRow("صافي التدفق", data.netFlow, positive = data.netFlow >= 0, bold = true)
            CashFlowRow("رصيد النهاية", data.closingBalance, positive = true, bold = true)
        }
    }
}

@Composable
private fun CashFlowRow(
    label: String,
    amount: Double,
    positive: Boolean = true,
    neutral: Boolean = false,
    bold: Boolean = false,
    indent: Boolean = false
) {
    if (amount == 0.0 && !bold && !neutral) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) ReportsDimensions.dp16 else ReportsDimensions.dp0),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (bold) TextPrimary else TextSecondary,
            fontSize = if (bold) ReportsTextScale.sp14 else ReportsTextScale.sp12,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = CurrencyFormatter.formatNoSymbol(amount),
            color = if (neutral) TextPrimary else if (positive) SuccessColor else ErrorColor,
            fontSize = if (bold) ReportsTextScale.sp14 else ReportsTextScale.sp12,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
