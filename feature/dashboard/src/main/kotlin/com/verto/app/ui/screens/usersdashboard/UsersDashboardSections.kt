package com.verto.app.ui.screens.usersdashboard

import androidx.compose.ui.res.stringResource

import com.verto.feature.dashboard.R

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.dashboard.application.BenzineFollowUpReason
import com.verto.app.feature.dashboard.application.BenzinePerformanceStatus
import com.verto.app.feature.dashboard.application.BenzineUserFilter
import com.verto.app.feature.dashboard.application.BenzineWeeklyPerformance
import com.verto.app.feature.dashboard.application.BenzineWeeklySummary
import com.verto.app.feature.dashboard.application.MarketerStatsItem
import com.verto.app.feature.dashboard.presentation.DashboardDimensions
import com.verto.app.feature.dashboard.presentation.DashboardTextScale
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor
import com.verto.app.utils.CurrencyFormatter
import java.util.Locale
import kotlin.math.abs
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.theme.VertoSpacing

@Composable
internal fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentPrimary)
    }
}

@Composable
internal fun ErrorState(message: String) {
    Box(
        Modifier.fillMaxSize().padding(DashboardDimensions.dp24),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = ErrorColor, fontSize = DashboardTextScale.sp14)
    }
}

@Composable
internal fun EmptyState(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(DashboardDimensions.dp32),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = TextSecondary, fontSize = DashboardTextScale.sp14)
    }
}

@Composable
internal fun WeeklyDataLoadingNotice() {
    Surface(
        color = BgCard,
        shape = RoundedCornerShape(DashboardDimensions.dp12),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DashboardDimensions.dp12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.width(DashboardDimensions.dp16).height(DashboardDimensions.dp16),
                strokeWidth = DashboardDimensions.dp2,
                color = AccentPrimary,
            )
            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_c2b1f1f1d267),
                color = TextSecondary,
                fontSize = DashboardTextScale.sp11,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun WeeklyDataNotice(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        color = WarningColor.copy(alpha = 0.10f),
        shape = RoundedCornerShape(DashboardDimensions.dp12),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DashboardDimensions.dp12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                color = WarningColor,
                fontSize = DashboardTextScale.sp11,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry), color = AccentPrimary, fontSize = DashboardTextScale.sp11)
            }
        }
    }
}

@Composable
internal fun WeeklySummarySection(
    summary: BenzineWeeklySummary,
    activeComparison: String,
    salesComparison: String,
    commissionsComparison: String,
    followUpComparison: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8)) {
        Text(androidx.compose.ui.res.stringResource(R.string.ds_e97122cea669), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = DashboardTextScale.sp16)
        VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(DashboardDimensions.dp16),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(DashboardDimensions.dp14)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12)) {
                    WeeklyMetric(
                        label = androidx.compose.ui.res.stringResource(R.string.ds_b05e63f979d0),
                        value = summary.activeCurrent.toString(),
                        comparison = activeComparison,
                        modifier = Modifier.weight(1f),
                    )
                    WeeklyMetric(
                        label = androidx.compose.ui.res.stringResource(R.string.ds_b917a3a61139),
                        value = CurrencyFormatter.formatNoSymbol(summary.salesCurrent),
                        comparison = salesComparison,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(DashboardDimensions.dp12))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.14f))
                Spacer(Modifier.height(DashboardDimensions.dp12))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12)) {
                    WeeklyMetric(
                        label = androidx.compose.ui.res.stringResource(R.string.ds_c1180316c19a),
                        value = CurrencyFormatter.formatNoSymbol(summary.commissionsCurrent),
                        comparison = commissionsComparison,
                        modifier = Modifier.weight(1f),
                    )
                    WeeklyMetric(
                        label = androidx.compose.ui.res.stringResource(R.string.ds_e1a8b141bf41),
                        value = summary.followUpCurrent.toString(),
                        comparison = followUpComparison,
                        comparisonColor = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun WeeklyMetric(
    label: String,
    value: String,
    comparison: String,
    modifier: Modifier = Modifier,
    comparisonColor: Color = AccentBlue,
) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = DashboardTextScale.sp11)
        Spacer(Modifier.height(DashboardDimensions.dp4))
        Text(value, color = TextPrimary, fontSize = DashboardTextScale.sp16, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(DashboardDimensions.dp2))
        Text(comparison, color = comparisonColor, fontSize = DashboardTextScale.sp10, maxLines = 1)
    }
}

@Composable
internal fun FollowUpSection(
    rows: List<BenzineWeeklyPerformance>,
    onOpen: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(DashboardDimensions.dp16),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(DashboardDimensions.dp14)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_e1a8b141bf41),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = DashboardTextScale.sp14,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onShowAll) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_d36294e5abb3), color = AccentPrimary, fontSize = DashboardTextScale.sp12)
                }
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = TextSecondary.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = DashboardDimensions.dp8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.fullName, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = DashboardTextScale.sp13)
                        Spacer(Modifier.height(DashboardDimensions.dp2))
                        Text(followUpText(row), color = TextSecondary, fontSize = DashboardTextScale.sp11)
                    }
                    TextButton(onClick = { onOpen(row.clientId) }) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open), color = AccentPrimary, fontSize = DashboardTextScale.sp12)
                    }
                }
            }
        }
    }
}

internal fun followUpText(row: BenzineWeeklyPerformance): String = when (row.followUpReason) {
    BenzineFollowUpReason.INACTIVE -> "منقطع — آخر شراء ${relativePurchase(row.lastPurchaseAt)}"
    BenzineFollowUpReason.DECLINING -> {
        val percent = abs(row.salesDeltaPercent ?: 0.0)
        "متراجع ${formatPercentMagnitude(percent)} عن الأسبوع السابق"
    }
    BenzineFollowUpReason.NEW_NOT_STARTED -> "جديد لم يبدأ"
    null -> ""
}

@Composable
internal fun UserFilters(
    selected: BenzineUserFilter,
    onSelect: (BenzineUserFilter) -> Unit,
    weeklyAvailable: Boolean,
) {
    val basicLabels = listOf(
        BenzineUserFilter.ALL to "الكل",
        BenzineUserFilter.MARKETERS to "مسوقون",
        BenzineUserFilter.WORKSHOPS to "ورش",
    )
    val weeklyLabels = listOf(
        BenzineUserFilter.ACTIVE_THIS_WEEK to "نشطون",
        BenzineUserFilter.NEW to "جدد",
        BenzineUserFilter.NEEDS_FOLLOW_UP to "يحتاج متابعة",
    )
    val labels = if (weeklyAvailable) basicLabels + weeklyLabels else basicLabels
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8),
    ) {
        labels.forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label, fontSize = DashboardTextScale.sp11) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentPrimary,
                    selectedLabelColor = Color.White,
                    containerColor = BgCard,
                    labelColor = TextSecondary,
                ),
            )
        }
    }
}

@Composable
internal fun BasicUserRow(
    row: MarketerStatsItem,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        color = BgCard,
        shape = RoundedCornerShape(DashboardDimensions.dp14),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(DashboardDimensions.dp14)) {
            Text(row.fullName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = DashboardTextScale.sp14)
            Spacer(Modifier.height(DashboardDimensions.dp2))
            Text(
                accountTypeLabel(row.accountType, row.workshopName),
                color = TextSecondary,
                fontSize = DashboardTextScale.sp11,
            )

            Spacer(Modifier.height(DashboardDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12)) {
                CompactMetric("إجمالي المشتريات", CurrencyFormatter.formatNoSymbol(row.purchasesTotal), Modifier.weight(1f))
                CompactMetric("إجمالي العمولات", CurrencyFormatter.formatNoSymbol(row.commissionTotal), Modifier.weight(1f))
            }

            Spacer(Modifier.height(DashboardDimensions.dp8))
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCall, enabled = row.phone.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_call)) }
                TextButton(onClick = onWhatsApp, enabled = row.phone.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_whatsapp)) }
                TextButton(onClick = onOpen) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open)) }
            }
        }
    }
}

@Composable
internal fun BenzineUserRow(
    row: BenzineWeeklyPerformance,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        color = BgCard,
        shape = RoundedCornerShape(DashboardDimensions.dp14),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(DashboardDimensions.dp14)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(row.fullName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = DashboardTextScale.sp14)
                    Spacer(Modifier.height(DashboardDimensions.dp2))
                    Text(accountTypeLabel(row), color = TextSecondary, fontSize = DashboardTextScale.sp11)
                }
                StatusLabel(row.status)
            }

            Spacer(Modifier.height(DashboardDimensions.dp10))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3d2867839ae7, relativePurchase(row.lastPurchaseAt)), color = TextSecondary, fontSize = DashboardTextScale.sp11)
            Spacer(Modifier.height(DashboardDimensions.dp8))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12)) {
                CompactMetric("مبيعات الأسبوع", CurrencyFormatter.formatNoSymbol(row.currentWeekSales), Modifier.weight(1f))
                CompactMetric("عمولة الأسبوع", CurrencyFormatter.formatNoSymbol(row.currentWeekCommission), Modifier.weight(1f))
            }
            Spacer(Modifier.height(DashboardDimensions.dp6))
            Text(
                stringResource(
                    R.string.dashboard_previous_week_trend,
                    row.salesTrendLabel ?: stringResource(R.string.legacy_ui_28437c4fab50),
                ),
                color = TextSecondary,
                fontSize = DashboardTextScale.sp11,
            )

            Spacer(Modifier.height(DashboardDimensions.dp8))
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCall, enabled = row.phone.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_call)) }
                TextButton(onClick = onWhatsApp, enabled = row.phone.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_whatsapp)) }
                TextButton(onClick = onOpen) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open)) }
            }
        }
    }
}

@Composable
internal fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = DashboardTextScale.sp10)
        Text(value, color = TextPrimary, fontSize = DashboardTextScale.sp13, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun StatusLabel(status: BenzinePerformanceStatus) {
    val (label, color) = when (status) {
        BenzinePerformanceStatus.NEW -> "جديد" to AccentBlue
        BenzinePerformanceStatus.ACTIVE -> "نشط" to AccentPrimary
        BenzinePerformanceStatus.GROWING -> "ينمو" to SuccessColor
        BenzinePerformanceStatus.DECLINING -> "متراجع" to WarningColor
        BenzinePerformanceStatus.INACTIVE -> "منقطع" to TextSecondary
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(DashboardDimensions.dp8)) {
        Text(
            label,
            color = color,
            fontSize = DashboardTextScale.sp11,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = DashboardDimensions.dp8, vertical = DashboardDimensions.dp4),
        )
    }
}

internal fun accountTypeLabel(row: BenzineWeeklyPerformance): String =
    accountTypeLabel(row.accountType, row.workshopName)

internal fun accountTypeLabel(accountType: String, workshopName: String?): String = when (accountType) {
    "WORKSHOP_OWNER" -> workshopName?.takeIf { it.isNotBlank() }?.let { "ورشة — $it" } ?: "ورشة"
    else -> "مسوق"
}

internal fun relativePurchase(epochMillis: Long?): String {
    if (epochMillis == null) return "لا يوجد شراء بعد"
    val now = System.currentTimeMillis()
    val diff = (now - epochMillis).coerceAtLeast(0L)
    val days = diff / 86_400_000L
    return when {
        days == 0L -> "اليوم"
        days == 1L -> "منذ يوم"
        days < 30L -> "منذ $days يوم"
        days < 365L -> "منذ ${days / 30L} شهر"
        else -> "منذ ${days / 365L} سنة"
    }
}

internal fun formatPercentMagnitude(value: Double): String {
    val rounded = String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    return "$rounded%"
}
