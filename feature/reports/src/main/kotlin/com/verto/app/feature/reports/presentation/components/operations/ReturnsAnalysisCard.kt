package com.verto.app.feature.reports.presentation.components.operations

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.ReturnsData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun ReturnsAnalysisCard(
    data: ReturnsData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_c1000291bdf7), icon = "🔄", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
        ) {
            // ── KPIs ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
            ) {
                ReturnKpi(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a448dd0c246d),
                    value = "${"%.1f".format(data.returnRate)}%",
                    color = if (data.returnRate > 5f) ErrorColor else SuccessColor,
                    modifier = Modifier.weight(1f)
                )
                ReturnKpi(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_2b06e354e8fe),
                    value = CurrencyFormatter.formatNoSymbol(data.totalReturnValue),
                    color = WarningColor,
                    modifier = Modifier.weight(1f)
                )
                ReturnKpi(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_e0cb8c698cd5),
                    value = "${data.returnCount}",
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── أكثر الأصناف مرتجعة ───────────────────
            if (data.topReturnedItems.isNotEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_ea7e7ad4abad),
                    color = TextSecondary,
                    fontSize = ReportsTextScale.sp12,
                    fontWeight = FontWeight.Medium
                )
                Column(verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)) {
                    data.topReturnedItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.itemName,
                                color = TextPrimary,
                                fontSize = ReportsTextScale.sp13,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_5876f2156395, item.returnCount),
                                    color = WarningColor,
                                    fontSize = ReportsTextScale.sp12,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    CurrencyFormatter.formatNoSymbol(item.returnValue),
                                    color = TextMuted,
                                    fontSize = ReportsTextScale.sp11
                                )
                            }
                        }
                        HorizontalDivider(color = BorderColor, thickness = ReportsDimensions.dp0_5)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp8),
                    contentAlignment = Alignment.Center
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_41523673adb8), color = TextMuted, fontSize = ReportsTextScale.sp13)
                }
            }
        }
    }
}

@Composable
private fun ReturnKpi(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ReportsDimensions.dp10))
            .background(BgCardAlt)
            .padding(ReportsDimensions.dp10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
    ) {
        Text(value, color = color, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp10)
    }
}
