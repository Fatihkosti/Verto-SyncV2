package com.verto.app.feature.reports.presentation.components.sales

import com.verto.app.feature.reports.presentation.ReportChartDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.HeatmapCell
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*

private val DAYS = listOf("أحد", "اثن", "ثلا", "أرب", "خمس", "جمع", "سبت")
private val HOURS = (6..22).toList()

@Composable
fun SalesHeatmap(
    cells: List<HeatmapCell>,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_3332cb35af0d),
        icon = "🔥",
        initiallyExpanded = false,
        modifier = modifier
    ) {
        val maxSales = remember(cells) { cells.maxOfOrNull { it.sales } ?: 1.0 }
        val cellMap  = remember(cells) { cells.associateBy { it.dayOfWeek to it.hour } }

        Column(
            modifier = Modifier.padding(horizontal = ReportChartDimensions.dp8, vertical = ReportChartDimensions.dp4),
            verticalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp2)
        ) {
            // صف الساعات
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(ReportChartDimensions.dp30))
                HOURS.forEachIndexed { i, h ->
                    Text(
                        text = if (i % 4 == 0) androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v298_d45d64e016e5, h) else "",
                        color = TextMuted,
                        fontSize = ReportsTextScale.sp8,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // الخلايا
            DAYS.forEachIndexed { dayIdx, dayName ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dayName,
                        color = TextMuted,
                        fontSize = ReportsTextScale.sp9,
                        modifier = Modifier.width(ReportChartDimensions.dp30)
                    )
                    HOURS.forEach { hour ->
                        val cell  = cellMap[dayIdx to hour]
                        val sales = cell?.sales ?: 0.0
                        val alpha = if (maxSales > 0) (sales / maxSales).toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(ReportChartDimensions.dp1)
                                .clip(RoundedCornerShape(ReportChartDimensions.dp2))
                                .background(
                                    if (alpha > 0.05f)
                                        AccentPrimary.copy(alpha = alpha.coerceIn(0.1f, 1f))
                                    else BgCardAlt
                                )
                        )
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.padding(top = ReportChartDimensions.dp8, start = ReportChartDimensions.dp30),
                horizontalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_15b8dd4725b4), color = TextMuted, fontSize = ReportsTextScale.sp9)
                listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { a ->
                    Box(
                        modifier = Modifier
                            .size(ReportChartDimensions.dp12)
                            .clip(RoundedCornerShape(ReportChartDimensions.dp2))
                            .background(AccentPrimary.copy(alpha = a))
                    )
                }
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_76d896309955), color = TextMuted, fontSize = ReportsTextScale.sp9)
            }
        }
    }
}

