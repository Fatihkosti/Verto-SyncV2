package com.verto.app.feature.reports.presentation.tabs

import com.verto.app.feature.reports.presentation.ReportsDimensions

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.app.feature.reports.presentation.ReportsUiState
import com.verto.app.feature.reports.presentation.ReportsViewModel
import com.verto.app.feature.reports.presentation.components.sales.*

@Composable
fun SalesTab(
    state: ReportsUiState,
    vm: ReportsViewModel,
    onClientClick: (String) -> Unit = {},
    showFullAnalytics: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
    ) {
        if (showFullAnalytics) {
            SalesForecastCard(forecast = state.forecastResult)
        }

        // 2. Heatmap المبيعات
        SalesHeatmap(cells = state.heatmap)

        // 3. أعلى الأصناف
        TopItemsCard(items = state.topItems)

        // 4. تحليل التصنيفات
        CategoryDonutChart(categories = state.categories)

        if (showFullAnalytics) {
            ClientSegmentsCard(
                segments      = state.rfmSegments,
                topClvClients = state.topClvClients,
                onSegmentClick = { /* TODO: drill-down لكل شريحة */ }
            )

            RealMarginCard(margins = state.realMargins)
        }
    }
}
