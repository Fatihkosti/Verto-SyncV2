package com.verto.app.feature.reports.presentation.components.financial

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.AgedReceivablesData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun AgedReceivablesCard(
    data: AgedReceivablesData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_2fc0fdbf702e),
        icon = "📅",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
        ) {
            // DSO
            Row(
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp16)
            ) {
                InfoChip("إجمالي المتأخر", CurrencyFormatter.formatNoSymbol(data.grandTotal), ErrorColor)
                InfoChip("متوسط فترة التحصيل", androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_1e7fa7d276be, data.dso.toInt()), WarningColor)
            }

            // جدول الفترات
            AgingTable(data)

            // قائمة العملاء
            if (data.clients.isNotEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_53bf61acb0fd),
                    color = TextSecondary,
                    fontSize = ReportsTextScale.sp13,
                    fontWeight = FontWeight.Medium
                )
                data.clients.take(5).forEach { client ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ReportsDimensions.dp4),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(client.clientName, color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Medium)
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_520e177fb20a, client.oldestDueDays), color = ErrorColor, fontSize = ReportsTextScale.sp11)
                        }
                        Text(
                            CurrencyFormatter.formatNoSymbol(client.total),
                            color = ErrorColor,
                            fontSize = ReportsTextScale.sp14,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a315651d7fec),
                    color = SuccessColor,
                    fontSize = ReportsTextScale.sp14,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AgingTable(data: AgedReceivablesData) {
    val buckets = listOf(
        "0-30 يوم"  to data.total0_30,
        "31-60 يوم" to data.total31_60,
        "61-90 يوم" to data.total61_90,
        "+90 يوم"   to data.totalOver90
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)
    ) {
        buckets.forEach { (label, amount) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(ReportsDimensions.dp10))
                    .background(BgCardAlt)
                    .padding(ReportsDimensions.dp8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
            ) {
                Text(label, color = TextMuted, fontSize = ReportsTextScale.sp10)
                Text(
                    CurrencyFormatter.formatNoSymbol(amount),
                    color = if (amount > 0) ErrorColor else TextMuted,
                    fontSize = ReportsTextScale.sp12,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(ReportsDimensions.dp10))
            .background(BgCardAlt)
            .padding(horizontal = ReportsDimensions.dp12, vertical = ReportsDimensions.dp8)
    ) {
        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp11)
        Text(value, color = valueColor, fontSize = ReportsTextScale.sp14, fontWeight = FontWeight.Bold)
    }
}
