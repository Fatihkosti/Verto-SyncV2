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
import com.verto.app.feature.reports.presentation.EmployeePerformanceData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun EmployeePerformanceCard(
    data: EmployeePerformanceData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_429304df81ca), icon = "👨‍💼", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
        ) {
            if (data.employees.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp12),
                    contentAlignment = Alignment.Center
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_1e915890ae92), color = TextMuted, fontSize = ReportsTextScale.sp13)
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
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name), color = TextMuted, fontSize = ReportsTextScale.sp11, modifier = Modifier.weight(2f))
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_8172bd107286), color = TextMuted, fontSize = ReportsTextScale.sp11, modifier = Modifier.weight(2f), textAlign = TextAlign.Center)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_1dd63530b50e), color = TextMuted, fontSize = ReportsTextScale.sp11, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_bccc12bf8bc8), color = TextMuted, fontSize = ReportsTextScale.sp11, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
            }

            // ── صفوف الجدول ──────────────────────────────
            val maxTotal = data.employees.maxOfOrNull { it.totalCollected } ?: 1.0
            data.employees.forEachIndexed { i, emp ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ReportsDimensions.dp8, vertical = ReportsDimensions.dp6),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // الاسم
                        Row(
                            modifier = Modifier.weight(2f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)
                        ) {
                            if (i == 0) Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f16aae799706), fontSize = ReportsTextScale.sp14)
                            else if (i == 1) Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_fcb8c5cba3ce), fontSize = ReportsTextScale.sp14)
                            else if (i == 2) Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d7daab43e212), fontSize = ReportsTextScale.sp14)
                            Text(emp.name, color = TextPrimary, fontSize = ReportsTextScale.sp13, maxLines = 1)
                        }
                        // التحصيلات مع progress bar
                        Column(modifier = Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                CurrencyFormatter.formatNoSymbol(emp.totalCollected),
                                color = AccentPrimary,
                                fontSize = ReportsTextScale.sp12,
                                fontWeight = FontWeight.Medium
                            )
                            val pct = (emp.totalCollected / maxTotal).toFloat().coerceIn(0f, 1f)
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
                        // عدد المعاملات
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_c79f71243f41, emp.transactionCount),
                            color = TextSecondary,
                            fontSize = ReportsTextScale.sp12,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        // متوسط الفاتورة
                        Text(
                            CurrencyFormatter.formatNoSymbol(emp.avgTransaction),
                            color = TextSecondary,
                            fontSize = ReportsTextScale.sp12,
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.End
                        )
                    }
                    if (i < data.employees.size - 1) {
                        HorizontalDivider(color = BorderColor, thickness = ReportsDimensions.dp0_5)
                    }
                }
            }
        }
    }
}
