package com.verto.app.feature.reports.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.verto.app.feature.reports.application.model.InsightTarget
import com.verto.app.feature.reports.application.model.InsightType
import com.verto.app.feature.reports.application.model.OperationalAlertSeverity
import com.verto.app.feature.reports.application.model.ReportAccessLevel
import com.verto.app.feature.reports.application.model.SmartInsight
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.WarningColor
import com.verto.app.utils.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal enum class ReportDecisionTarget(val title: String) {
    SALES("المبيعات"),
    PROFIT("الربح والهامش"),
    CASH_FLOW("التدفق النقدي"),
    RECEIVABLES("متأخرات العملاء"),
    INVENTORY("مخاطر المخزون"),
    PURCHASES("المشتريات والموردون"),
}

private enum class MetricTone { NEUTRAL, POSITIVE, WARNING, NEGATIVE }

private data class AttentionItem(
    val title: String,
    val message: String,
    val tone: MetricTone,
    val target: ReportDecisionTarget,
    val priority: Int,
)

@Composable
internal fun ReportsDecisionDashboard(
    state: ReportsUiState,
    accessLevel: ReportAccessLevel,
    onOpen: (ReportDecisionTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ReportsDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10),
    ) {
        FreshnessLine(state.lastUpdatedAt)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10),
        ) {
            DecisionMetricCard(
                label = "صافي المبيعات",
                value = if (state.netSalesReliable) compactAmount(state.netSalesTotal) else "غير متاح",
                meta = when {
                    !state.netSalesReliable -> "بيانات العملة غير مكتملة"
                    state.netSalesComparisonAvailable -> comparisonLabel(state.netSalesChange)
                    else -> "لا توجد مقارنة سابقة"
                },
                metaTone = if (!state.netSalesReliable) MetricTone.WARNING else if (state.netSalesComparisonAvailable) changeTone(state.netSalesChange) else MetricTone.NEUTRAL,
                large = true,
                onClick = { onOpen(ReportDecisionTarget.SALES) },
                modifier = Modifier.weight(1f),
            )
            DecisionMetricCard(
                label = "الربح والهامش",
                value = if (state.profitBreakdown.isNetProfitReliable) compactAmount(state.netProfit) else "غير متاح",
                meta = if (state.profitBreakdown.isNetProfitReliable) {
                    "هامش ${formatPercent(state.pnl.grossMargin)} · ${shortChange(state.netProfitChange)}"
                } else {
                    "هامش ${formatPercent(state.pnl.grossMargin)}"
                },
                metaTone = if (state.profitBreakdown.isNetProfitReliable) changeTone(state.netProfitChange) else MetricTone.WARNING,
                large = true,
                onClick = { onOpen(ReportDecisionTarget.PROFIT) },
                modifier = Modifier.weight(1f),
            )
        }

        if (accessLevel == ReportAccessLevel.FULL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10),
            ) {
                val flow = state.cashFlow.netFlow
                DecisionMetricCard(
                    label = "صافي التدفق النقدي",
                    value = signedAmount(flow),
                    meta = if (flow >= 0.0) "تدفق موجب خلال الفترة" else "تدفق سالب خلال الفترة",
                    metaTone = if (flow >= 0.0) MetricTone.POSITIVE else MetricTone.NEGATIVE,
                    onClick = { onOpen(ReportDecisionTarget.CASH_FLOW) },
                    modifier = Modifier.weight(1f),
                )
                DecisionMetricCard(
                    label = "متأخرات العملاء",
                    value = compactAmount(state.agedReceivables.grandTotal),
                    meta = "${state.agedReceivables.clients.size} عميل متأخر",
                    metaTone = if (state.agedReceivables.grandTotal > 0.0) MetricTone.WARNING else MetricTone.NEUTRAL,
                    onClick = { onOpen(ReportDecisionTarget.RECEIVABLES) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp10),
            ) {
                val stockout = state.inventoryHealth.stockoutRisk.size
                val dead = state.inventoryHealth.deadStockItems.size
                DecisionMetricCard(
                    label = "مخاطر المخزون",
                    value = stockout.toString(),
                    meta = "خطر نفاد · $dead صنف راكد",
                    metaTone = if (stockout > 0 || dead > 0) MetricTone.WARNING else MetricTone.NEUTRAL,
                    onClick = { onOpen(ReportDecisionTarget.INVENTORY) },
                    modifier = Modifier.weight(1f),
                )
                DecisionMetricCard(
                    label = "المشتريات",
                    value = if (state.purchasesReliable) compactAmount(state.purchasesTotal) else "غير متاح",
                    meta = when {
                        !state.purchasesReliable -> "بيانات العملة غير مكتملة"
                        state.purchasesComparisonAvailable -> comparisonLabel(state.purchasesChange)
                        else -> "لا توجد مقارنة سابقة"
                    },
                    // ارتفاع المشتريات ليس جيدًا أو سيئًا بذاته؛ المقارنة محايدة عمدًا.
                    metaTone = if (state.purchasesReliable) MetricTone.NEUTRAL else MetricTone.WARNING,
                    onClick = { onOpen(ReportDecisionTarget.PURCHASES) },
                    modifier = Modifier.weight(1f),
                )
            }

            AttentionSection(state = state, onOpen = onOpen)
        }
    }
}

@Composable
internal fun PurchasePeriodSummaryCard(
    state: ReportsUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ReportsDimensions.dp16),
        color = BgCard,
        border = BorderStroke(ReportsDimensions.dp1, BorderColor),
        tonalElevation = ReportsDimensions.dp0,
    ) {
        Column(modifier = Modifier.padding(ReportsDimensions.dp16)) {
            Text(
                "مشتريات الفترة",
                color = TextMuted,
                fontSize = ReportsTextScale.sp12,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(ReportsDimensions.dp8))
            Text(
                if (state.purchasesReliable) compactAmount(state.purchasesTotal) else "غير متاح",
                color = TextPrimary,
                fontSize = ReportsTextScale.sp22,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(ReportsDimensions.dp6))
            Text(
                when {
                    !state.purchasesReliable -> "بيانات العملة غير مكتملة"
                    state.purchasesComparisonAvailable -> comparisonLabel(state.purchasesChange)
                    else -> "لا توجد مقارنة سابقة"
                },
                color = if (state.purchasesReliable) TextMuted else WarningColor,
                fontSize = ReportsTextScale.sp11,
            )
        }
    }
}

@Composable
private fun FreshnessLine(lastUpdatedAt: Long) {
    val value = if (lastUpdatedAt > 0L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdatedAt))
    } else "—"
    Text(
        text = "آخر تحديث للعرض: $value",
        color = TextMuted,
        fontSize = ReportsTextScale.sp11,
        modifier = Modifier.padding(horizontal = ReportsDimensions.dp2),
    )
}

@Composable
private fun DecisionMetricCard(
    label: String,
    value: String,
    meta: String,
    metaTone: MetricTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Surface(
        modifier = modifier
            .height(if (large) ReportsDimensions.dp120 else ReportsDimensions.dp96)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(ReportsDimensions.dp16),
        color = BgCard,
        border = BorderStroke(ReportsDimensions.dp1, BorderColor),
        tonalElevation = ReportsDimensions.dp0,
        shadowElevation = ReportsDimensions.dp0,
    ) {
        Column(
            modifier = Modifier.padding(ReportsDimensions.dp14),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = ReportsTextScale.sp12,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(ReportsDimensions.dp8))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = if (large) ReportsTextScale.sp22 else ReportsTextScale.sp20,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(ReportsDimensions.dp8))
            Text(
                text = meta,
                color = toneColor(metaTone),
                fontSize = ReportsTextScale.sp11,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AttentionSection(
    state: ReportsUiState,
    onOpen: (ReportDecisionTarget) -> Unit,
) {
    val items = buildAttentionItems(state).take(3)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ReportsDimensions.dp8, bottom = ReportsDimensions.dp8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("يتطلب انتباهك", color = TextPrimary, fontSize = ReportsTextScale.sp16, fontWeight = FontWeight.SemiBold)
            if (items.isNotEmpty()) Text("أهم ${items.size}", color = TextMuted, fontSize = ReportsTextScale.sp11)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ReportsDimensions.dp16),
            color = BgCard,
            border = BorderStroke(ReportsDimensions.dp1, BorderColor),
            tonalElevation = ReportsDimensions.dp0,
        ) {
            if (items.isEmpty()) {
                Text(
                    "لا توجد عناصر حرجة حاليًا",
                    color = TextMuted,
                    fontSize = ReportsTextScale.sp13,
                    modifier = Modifier.padding(ReportsDimensions.dp16),
                )
            } else {
                Column {
                    items.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item.target) }
                                .padding(horizontal = ReportsDimensions.dp14, vertical = ReportsDimensions.dp12),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("●", color = toneColor(item.tone), fontSize = ReportsTextScale.sp10)
                                Spacer(Modifier.width(ReportsDimensions.dp8))
                                Text(
                                    item.title,
                                    color = TextPrimary,
                                    fontSize = ReportsTextScale.sp13,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.message.isNotBlank()) {
                                Text(
                                    item.message,
                                    color = TextMuted,
                                    fontSize = ReportsTextScale.sp11,
                                    modifier = Modifier.padding(top = ReportsDimensions.dp4, start = ReportsDimensions.dp14),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (index != items.lastIndex) HorizontalDivider(color = BorderColor)
                    }
                }
            }
        }
    }
}

private fun buildAttentionItems(state: ReportsUiState): List<AttentionItem> {
    val operational = state.operationalAlerts.map { alert ->
        val priority = when (alert.severity) {
            OperationalAlertSeverity.CRITICAL -> 0
            OperationalAlertSeverity.WARNING -> 1
            OperationalAlertSeverity.INFO -> 3
        }
        AttentionItem(
            title = alert.title,
            message = alert.message,
            tone = when (alert.severity) {
                OperationalAlertSeverity.CRITICAL -> MetricTone.NEGATIVE
                OperationalAlertSeverity.WARNING -> MetricTone.WARNING
                OperationalAlertSeverity.INFO -> MetricTone.NEUTRAL
            },
            target = ReportDecisionTarget.PURCHASES,
            priority = priority,
        )
    }

    val insights = state.insights
        .filter { it.type != InsightType.ACHIEVEMENT }
        .map { insight -> insight.toAttentionItem() }

    return (operational + insights)
        .distinctBy { it.title to it.message }
        .sortedBy { it.priority }
}

private fun SmartInsight.toAttentionItem(): AttentionItem = AttentionItem(
    title = title,
    message = message,
    tone = when (type) {
        InsightType.WARNING, InsightType.ANOMALY -> MetricTone.WARNING
        InsightType.OPPORTUNITY -> MetricTone.NEUTRAL
        InsightType.ACHIEVEMENT -> MetricTone.POSITIVE
    },
    target = when (actionTarget) {
        InsightTarget.SALES -> ReportDecisionTarget.SALES
        InsightTarget.RECEIVABLES -> ReportDecisionTarget.RECEIVABLES
        InsightTarget.INVENTORY -> ReportDecisionTarget.INVENTORY
        InsightTarget.OPERATIONS -> ReportDecisionTarget.INVENTORY
        InsightTarget.FINANCIAL, InsightTarget.BUDGET, null -> ReportDecisionTarget.PROFIT
    },
    priority = when (type) {
        InsightType.WARNING, InsightType.ANOMALY -> 1
        InsightType.OPPORTUNITY -> 2
        InsightType.ACHIEVEMENT -> 4
    },
)

@Composable
private fun toneColor(tone: MetricTone): Color = when (tone) {
    MetricTone.NEUTRAL -> TextMuted
    MetricTone.POSITIVE -> SuccessColor
    MetricTone.WARNING -> WarningColor
    MetricTone.NEGATIVE -> ErrorColor
}

private fun changeTone(change: Float): MetricTone = when {
    change > 0.05f -> MetricTone.POSITIVE
    change < -0.05f -> MetricTone.NEGATIVE
    else -> MetricTone.NEUTRAL
}

private fun comparisonLabel(change: Float): String = "${shortChange(change)} عن الفترة السابقة"

private fun shortChange(change: Float): String = when {
    change > 0.05f -> "↑ ${formatPercent(change)}"
    change < -0.05f -> "↓ ${formatPercent(abs(change))}"
    else -> "— 0%"
}

private fun formatPercent(value: Float): String {
    val rounded = if (abs(value % 1f) < 0.05f) "%.0f".format(Locale.US, value) else "%.1f".format(Locale.US, value)
    return "$rounded%"
}

private fun compactAmount(value: Double): String {
    val absolute = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absolute >= 1_000_000_000 -> "$sign${trimCompact(absolute / 1_000_000_000)} مليار"
        absolute >= 1_000_000 -> "$sign${trimCompact(absolute / 1_000_000)} م"
        absolute >= 1_000 -> "$sign${trimCompact(absolute / 1_000)} أ"
        else -> CurrencyFormatter.formatNoSymbol(value)
    }
}

private fun signedAmount(value: Double): String = when {
    value > 0.0 -> "+${compactAmount(value)}"
    else -> compactAmount(value)
}

private fun trimCompact(value: Double): String = when {
    value >= 100 -> "%.0f".format(Locale.US, value)
    value >= 10 -> "%.1f".format(Locale.US, value).trimEnd('0').trimEnd('.')
    else -> "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')
}
