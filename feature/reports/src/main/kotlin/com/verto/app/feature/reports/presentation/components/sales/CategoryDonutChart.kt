package com.verto.app.feature.reports.presentation.components.sales

import com.verto.app.feature.reports.presentation.ReportChartDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale
import com.verto.app.feature.reports.presentation.reportCategorySeriesColors

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
import com.verto.app.feature.reports.presentation.CategorySummary
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter


@Composable
fun CategoryDonutChart(
    categories: List<CategorySummary>,
    modifier: Modifier = Modifier
) {
    val categoryColors = reportCategorySeriesColors()
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_e2ad4d762b46),
        icon = "🎯",
        initiallyExpanded = false,
        modifier = modifier
    ) {
        if (categories.isEmpty()) {
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_no_data), color = TextMuted, fontSize = ReportsTextScale.sp13, modifier = Modifier.padding(ReportChartDimensions.dp16))
            return@CollapsibleSection
        }

        Column(
            modifier = Modifier.padding(horizontal = ReportChartDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp8)
        ) {
            categories.take(8).forEachIndexed { index, cat ->
                CategoryRow(cat, categoryColors[index % categoryColors.size])
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: CategorySummary, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ReportChartDimensions.dp8),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(ReportChartDimensions.dp10)
                    .clip(RoundedCornerShape(ReportChartDimensions.dp2))
                    .background(color)
            )
            Column {
                Text(cat.category, color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Medium)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_a3ef0db469fc, cat.distinctItems, cat.totalQty), color = TextMuted, fontSize = ReportsTextScale.sp11)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(CurrencyFormatter.formatNoSymbol(cat.revenue), color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.SemiBold)
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_fada034d1e08, cat.margin.toInt(), cat.pct.toInt()), color = TextMuted, fontSize = ReportsTextScale.sp11)
        }
    }
}
