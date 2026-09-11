package com.verto.app.feature.reports.presentation.components.operations

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import com.verto.app.feature.reports.application.model.*

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
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// X-Report — snapshot القراءة فقط للشفت الحالي (بدون أزرار إدارة)
@Composable
fun XReportSnapshot(
    openSession: CashReconciliationItem?,
    shiftSales: Double,
    shiftTransactions: Int,
    shiftAvgInvoice: Double,
    modifier: Modifier = Modifier
) {
    if (openSession == null) return

    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d118696f7e97),
        icon = "🧾",
        initiallyExpanded = false,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10)
        ) {
            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_4fe45f556503, sdf.format(Date(openSession.startedAt))),
                color = TextMuted,
                fontSize = ReportsTextScale.sp12
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
            ) {
                ShiftKpi("المبيعات", CurrencyFormatter.formatNoSymbol(shiftSales), Modifier.weight(1f))
                ShiftKpi("المعاملات", "$shiftTransactions", Modifier.weight(1f))
                ShiftKpi("متوسط الفاتورة", CurrencyFormatter.formatNoSymbol(shiftAvgInvoice), Modifier.weight(1f))
            }

            // رصيد الذروة المتوقع
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ReportsDimensions.dp10))
                    .background(BgCardAlt)
                    .padding(ReportsDimensions.dp10),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_26408c080648), color = TextMuted, fontSize = ReportsTextScale.sp12)
                Text(
                    CurrencyFormatter.formatNoSymbol(openSession.openingBalance),
                    color = TextSecondary,
                    fontSize = ReportsTextScale.sp13,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ShiftKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ReportsDimensions.dp10))
            .background(BgCardAlt)
            .padding(ReportsDimensions.dp10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
    ) {
        Text(value, color = TextPrimary, fontSize = ReportsTextScale.sp14, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp11)
    }
}
