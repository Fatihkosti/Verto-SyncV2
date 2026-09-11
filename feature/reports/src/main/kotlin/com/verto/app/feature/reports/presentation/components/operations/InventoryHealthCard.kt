package com.verto.app.feature.reports.presentation.components.operations

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verto.app.feature.reports.presentation.InventoryItemSummary
import com.verto.app.utils.MoneyMath
import androidx.compose.ui.unit.sp
import com.verto.app.feature.reports.presentation.InventoryHealthData
import com.verto.app.feature.reports.presentation.components.core.CollapsibleSection
import com.verto.app.ui.theme.*
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.ui.components.VertoScrollableTabRow

@Composable
fun InventoryHealthCard(
    data: InventoryHealthData,
    modifier: Modifier = Modifier
) {
    CollapsibleSection(title = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_52aa609cc113), icon = "📦", modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
        ) {
            // ── KPIs ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp8)
            ) {
                InventoryKpi(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f2f1658f96b0),
                    value = CurrencyFormatter.formatNoSymbol(data.totalValuation),
                    icon  = "💰",
                    modifier = Modifier.weight(1f)
                )
                InventoryKpi(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_7e762c984cc7),
                    value = "${data.deadStockItems.size} صنف",
                    icon  = "💤",
                    valueColor = if (data.deadStockItems.isNotEmpty()) WarningColor else SuccessColor,
                    modifier = Modifier.weight(1f)
                )
                InventoryKpi(
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_fd07823a6191),
                    value = "${data.stockoutRisk.size} صنف",
                    icon  = "⚠️",
                    valueColor = if (data.stockoutRisk.isNotEmpty()) ErrorColor else SuccessColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Tabs ─────────────────────────────────────
            var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
            val subTabs = listOf("راكد", "الأكثر حركة", "نفاد وشيك")

            VertoScrollableTabRow(
                selectedTabIndex = selectedSubTab,
                containerColor   = BgCardAlt,
                contentColor     = AccentPrimary,
                edgePadding      = ReportsDimensions.dp0,
                modifier = Modifier.clip(RoundedCornerShape(ReportsDimensions.dp10))
            ) {
                subTabs.forEachIndexed { i, label ->
                    Tab(
                        selected  = selectedSubTab == i,
                        onClick   = { selectedSubTab = i },
                        modifier  = Modifier.height(ReportsDimensions.dp36)
                    ) {
                        Text(label, fontSize = ReportsTextScale.sp12, fontWeight = if (selectedSubTab == i) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            when (selectedSubTab) {
                0 -> InventoryList(
                    items = data.deadStockItems,
                    emptyLabel = "لا يوجد مخزون راكد",
                    showValue = true
                )
                1 -> InventoryList(
                    items = data.fastMovers,
                    emptyLabel = "لا توجد بيانات",
                    showTurnover = true
                )
                2 -> InventoryList(
                    items = data.stockoutRisk,
                    emptyLabel = "المخزون جيد",
                    showQuantity = true
                )
            }

            if (data.deadStockValue > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ReportsDimensions.dp8))
                        .background(WarningContainer)
                        .padding(ReportsDimensions.dp10),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_1033a8451ed4, CurrencyFormatter.formatNoSymbol(data.deadStockValue)),
                        color = WarningColor,
                        fontSize = ReportsTextScale.sp12
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryList(
    items: List<InventoryItemSummary>,
    emptyLabel: String,
    showValue: Boolean = false,
    showTurnover: Boolean = false,
    showQuantity: Boolean = false
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp12),
            contentAlignment = Alignment.Center
        ) {
            Text(emptyLabel, color = TextMuted, fontSize = ReportsTextScale.sp13)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp6)) {
        items.take(8).forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.name,
                    color = TextPrimary,
                    fontSize = ReportsTextScale.sp13,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                when {
                    showValue -> Text(
                        CurrencyFormatter.formatNoSymbol(
                            MoneyMath.multiply(item.buyPrice, item.quantity)
                        ),
                        color = WarningColor,
                        fontSize = ReportsTextScale.sp12,
                        fontWeight = FontWeight.Medium
                    )
                    showTurnover -> Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_5abc23950582, item.turnoverCount),
                        color = AccentPrimary,
                        fontSize = ReportsTextScale.sp12,
                        fontWeight = FontWeight.Medium
                    )
                    showQuantity -> Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_f44b3098ac94, item.quantity, item.minQuantity),
                        color = ErrorColor,
                        fontSize = ReportsTextScale.sp12,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            HorizontalDivider(color = BorderColor, thickness = ReportsDimensions.dp0_5)
        }
    }
}

@Composable
private fun InventoryKpi(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ReportsDimensions.dp10))
            .background(BgCardAlt)
            .padding(ReportsDimensions.dp10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp4)
    ) {
        Text(icon, fontSize = ReportsTextScale.sp18)
        Text(value, color = valueColor, fontSize = ReportsTextScale.sp12, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = TextMuted, fontSize = ReportsTextScale.sp10)
    }
}
