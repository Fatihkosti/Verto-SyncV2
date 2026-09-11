package com.verto.app.feature.reports.presentation.components.operations

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.SupplierSummary
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun SuppliersAnalysisCard(
    suppliers: List<SupplierSummary>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_6dc1e9f0c507), icon = "🏭", initiallyExpanded = initiallyExpanded, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)
        ) {
            if (suppliers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp12),
                    contentAlignment = Alignment.Center
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_1bd5e624c485), color = TextMuted, fontSize = ReportsTextScale.sp13)
                }
                return@Column
            }

            // ── رأس الجدول ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ReportsDimensions.dp8))
                    .background(BgCardAlt)
                    .padding(horizontal = ReportsDimensions.dp8, vertical = ReportsDimensions.dp6),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_70ff5f7b99e0), color = TextMuted, fontSize = ReportsTextScale.sp11, modifier = Modifier.weight(2f))
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_62e5021947c6), color = TextMuted, fontSize = ReportsTextScale.sp11,
                    modifier = Modifier.weight(2f), textAlign = TextAlign.Center)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_21da4e22df73), color = TextMuted, fontSize = ReportsTextScale.sp11,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }

            val maxTotal = suppliers.maxOfOrNull { it.totalPurchases } ?: 1.0

            suppliers.forEachIndexed { i, sup ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ReportsDimensions.dp8, vertical = ReportsDimensions.dp8),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // اسم المورد
                        Text(
                            sup.name,
                            color = TextPrimary,
                            fontSize = ReportsTextScale.sp13,
                            modifier = Modifier.weight(2f),
                            maxLines = 1
                        )
                        // المبلغ مع progress bar
                        Column(modifier = Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                CurrencyFormatter.formatNoSymbol(sup.totalPurchases),
                                color = AccentPrimary,
                                fontSize = ReportsTextScale.sp12,
                                fontWeight = FontWeight.Medium
                            )
                            val pct = (sup.totalPurchases / maxTotal).toFloat().coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(ReportsDimensions.dp4)
                                    .clip(RoundedCornerShape(ReportsDimensions.dp2))
                                    .background(BgCardAlt)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pct)
                                        .height(ReportsDimensions.dp4)
                                        .clip(RoundedCornerShape(ReportsDimensions.dp2))
                                        .background(AccentPrimary)
                                )
                            }
                        }
                        // عدد الفواتير
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_c79f71243f41, sup.invoiceCount),
                            color = TextSecondary,
                            fontSize = ReportsTextScale.sp12,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }
                    if (i < suppliers.size - 1) {
                        HorizontalDivider(color = BorderColor, thickness = ReportsDimensions.dp0_5)
                    }
                }
            }
        }
    }
}
