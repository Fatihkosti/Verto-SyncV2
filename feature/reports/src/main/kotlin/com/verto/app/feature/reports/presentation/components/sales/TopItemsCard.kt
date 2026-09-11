package com.verto.app.feature.reports.presentation.components.sales

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
import com.verto.app.feature.reports.presentation.TopItem
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter

@Composable
fun TopItemsCard(
    items: List<TopItem>,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(
        title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d97b44e9e783),
        icon = "🏷️",
        modifier = modifier
    ) {
        if (items.isEmpty()) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_9051ae657ca8),
                color = TextMuted,
                fontSize = ReportsTextScale.sp13,
                modifier = Modifier.padding(ReportsDimensions.dp16)
            )
            return@CollapsibleSection
        }

        val maxRevenue = items.maxOf { it.revenue }.takeIf { it > 0 } ?: 1.0

        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10)
        ) {
            items.forEachIndexed { index, item ->
                ItemRow(rank = index + 1, item = item, maxRevenue = maxRevenue)
            }
        }
    }
}

@Composable
private fun ItemRow(rank: Int, item: TopItem, maxRevenue: Double) {
    val barPct = (item.revenue / maxRevenue).toFloat().coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_adebc50ade66, rank),
                    color = if (rank <= 3) GoldPrimary else TextMuted,
                    fontSize = ReportsTextScale.sp12,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(ReportsDimensions.dp24)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, color = TextPrimary, fontSize = ReportsTextScale.sp13, fontWeight = FontWeight.Medium)
                    if (item.category.isNotEmpty()) {
                        Text(item.category, color = TextMuted, fontSize = ReportsTextScale.sp11)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyFormatter.formatNoSymbol(item.revenue),
                    color = TextPrimary,
                    fontSize = ReportsTextScale.sp13,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_022426d7c890, item.margin.toInt()),
                    color = if (item.margin > 20) SuccessColor else if (item.margin > 0) WarningColor else ErrorColor,
                    fontSize = ReportsTextScale.sp11
                )
            }
        }

        // شريط بياني بسيط
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReportsDimensions.dp4)
                .clip(RoundedCornerShape(ReportsDimensions.dp2))
                .background(BgCardAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barPct)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(ReportsDimensions.dp2))
                    .background(AccentPrimary)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d66f4f34a1a6, item.qty), color = TextMuted, fontSize = ReportsTextScale.sp11)
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_d3832b182e1f, CurrencyFormatter.formatNoSymbol(item.profit)), color = SuccessColor, fontSize = ReportsTextScale.sp11)
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f002e52f1aec, item.pct.toInt()), color = TextMuted, fontSize = ReportsTextScale.sp11)
        }
    }
}
