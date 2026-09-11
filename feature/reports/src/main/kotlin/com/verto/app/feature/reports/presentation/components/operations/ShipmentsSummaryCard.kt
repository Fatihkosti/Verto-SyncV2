package com.verto.app.feature.reports.presentation.components.operations

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
import com.verto.app.feature.reports.presentation.ShipmentsSummaryData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun ShipmentsSummaryCard(
    data: ShipmentsSummaryData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_e30f0b31c618), icon = "🚚", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10)
        ) {
            if (data.totalShipments == 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp12),
                    contentAlignment = Alignment.Center
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_bdb4433e2fbc), color = TextMuted, fontSize = ReportsTextScale.sp13)
                }
                return@Column
            }

            // ── KPIs ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
            ) {
                ShipmentKpi("إجمالي", "${data.totalShipments}", "📦", TextPrimary, Modifier.weight(1f))
                ShipmentKpi("في الطريق", "${data.inTransit}", "🚢", AccentBlue, Modifier.weight(1f))
                ShipmentKpi("مكتملة", "${data.completed}", "✅", SuccessColor, Modifier.weight(1f))
                ShipmentKpi("معلقة", "${data.pending}", "⏳", WarningColor, Modifier.weight(1f))
            }

            // ── إجمالي التكاليف ────────────────────────
            if (data.totalCosts > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ReportsDimensions.dp10))
                        .background(BgCardAlt)
                        .padding(ReportsDimensions.dp14),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_649ef4fb3817), color = TextSecondary, fontSize = ReportsTextScale.sp13)
                    Text(
                        CurrencyFormatter.formatNoSymbol(data.totalCosts),
                        color = ErrorColor,
                        fontSize = ReportsTextScale.sp14,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ShipmentKpi(
    label: String,
    value: String,
    icon: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ReportsDimensions.dp10))
            .background(BgCardAlt)
            .padding(ReportsDimensions.dp8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
    ) {
        Text(icon, fontSize = ReportsTextScale.sp16)
        Text(value, color = color, fontSize = ReportsTextScale.sp14, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp10)
    }
}
