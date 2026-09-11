package com.verto.app.ui.screens.usersdashboard

import androidx.compose.ui.res.stringResource

import com.verto.app.R

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.dashboard.application.BenzinePerformanceStatus
import com.verto.app.feature.dashboard.application.BenzineWeeklyPerformance
import com.verto.app.feature.dashboard.application.CommissionEligibilityItem
import com.verto.app.feature.dashboard.application.MarketerStatsItem
import com.verto.app.ui.screens.commission.ClientCommissionBalance
import com.verto.app.ui.screens.commission.CommissionViewModel
import com.verto.app.ui.screens.commission.WithdrawFlowDialogs
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BgSurface
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

internal fun joinedDateLabel(iso: String?): String {
    val ms = MarketerStatsSorter.parseMillis(iso) ?: return "غير معروف"
    return DateUtils.formatDate(ms)
}

internal fun relativeSeen(iso: String?, now: Long): String {
    val seenAt = MarketerStatsSorter.parseMillis(iso) ?: return "غير معروف"
    val elapsed = (now - seenAt).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "الآن"
        elapsed < 3_600_000L -> "منذ ${elapsed / 60_000L} دقيقة"
        elapsed < 86_400_000L -> "منذ ${elapsed / 3_600_000L} ساعة"
        else -> DateUtils.formatDate(seenAt)
    }
}

/** تحويل timestamptz من commission_eligibility إلى millis (أول 19 محرفاً بتوقيت UTC). */
internal fun parseEligibilityMillis(iso: String?): Long? = iso?.let {
    runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(it.take(19))?.time
    }.getOrNull()
}

/** قائمة فواتير المسوّق (من صفوف الأهلية) — كل فاتورة قابلة للنقر تفتح شاشة الفاتورة. */
@Composable
internal fun MarketerInvoicesCard(
    invoices: List<CommissionEligibilityItem>,
    periodLabel: String,
    onInvoiceClick: (String) -> Unit
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_aabd26a57090, invoices.size), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp14)
                Text(periodLabel, color = TextSecondary, fontSize = UserAdminTextScale.sp11)
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            if (invoices.isEmpty()) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_07d9c3a0ca99), color = TextSecondary, fontSize = UserAdminTextScale.sp13,
                    modifier = Modifier.padding(vertical = UserAdminDimensions.dp12))
            } else {
                invoices.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onInvoiceClick(row.invoiceId) }
                            .padding(vertical = UserAdminDimensions.dp8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(androidx.compose.ui.res.stringResource(R.string.ds_a8d9bb06f339, row.invoiceNumber), color = TextPrimary, fontSize = UserAdminTextScale.sp13, fontWeight = FontWeight.Medium)
                            Text(
                                parseEligibilityMillis(row.createdAt)?.let { DateUtils.formatDate(it) } ?: stringResource(R.string.legacy_ui_ddf1ce35f4e4),
                                color = TextSecondary, fontSize = UserAdminTextScale.sp11
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(CurrencyFormatter.formatNoSymbol(row.totalAmount), color = TextSecondary, fontSize = UserAdminTextScale.sp12)
                            Text(androidx.compose.ui.res.stringResource(R.string.ds_acd162add6df, CurrencyFormatter.formatNoSymbol(row.commission)), color = SuccessColor, fontSize = UserAdminTextScale.sp12)
                        }
                        Spacer(Modifier.width(UserAdminDimensions.dp6))
                        Text(androidx.compose.ui.res.stringResource(R.string.ds_60ecbc7f40b3), color = AccentBlue, fontSize = UserAdminTextScale.sp18)
                    }
                    androidx.compose.material3.HorizontalDivider(color = BorderColor.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/** شاشات AutoDrive المتاحة لربط «تذكير»؛ الأول (home) هو الافتراضي. */
internal val REMINDER_SCREENS: List<Pair<String, String>> = listOf(
    "الرئيسية" to "home",
    "الرصيد" to "balance",
    "الإشعارات" to "notifications",
    "المنافسة الأسبوعية" to "weekly_competition",
    "العمولات الأسبوعية" to "weekly_commissions",
    "قائمة الفواتير" to "invoice_list",
    "أسابيع الفوز" to "win_weeks",
    "سجل المنافسات" to "competition_history",
    "الإعدادات" to "profile",
    "تقاريري" to "activity_log",
    "تقرير العمولات" to "commission_report"
)

@Composable
internal fun ReminderDialog(
    onDismiss: () -> Unit,
    onSend: (message: String, navRoute: String) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }  // 0 = الرئيسية (الافتراضي)
    var menuExpanded by remember { mutableStateOf(false) }
    val (screenLabel, screenRoute) = REMINDER_SCREENS[selectedIndex]

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurface,
        title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_e2dcb5df8224), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp12)) {
                VertoOutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ds_691773aa9290)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary, unfocusedBorderColor = BorderColor,
                        focusedLabelColor = AccentPrimary, focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Text(androidx.compose.ui.res.stringResource(R.string.ds_683616d50ada), color = TextSecondary, fontSize = UserAdminTextScale.sp12)
                Box {
                    VertoOutlinedButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(screenLabel, color = TextPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(UserAdminDimensions.dp18))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        REMINDER_SCREENS.forEachIndexed { idx, (label, _) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { selectedIndex = idx; menuExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            VertoButton(
                onClick = { onSend(message.trim(), screenRoute) },
                enabled = message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
        }
    )
}

@Composable
internal fun IdentityCard(stat: MarketerStatsItem, online: Boolean, now: Long) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(UserAdminDimensions.dp12).clip(CircleShape).background(if (online) SuccessColor else TextSecondary))
                Spacer(Modifier.width(UserAdminDimensions.dp8))
                Text(stat.fullName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp18)
            }
            Spacer(Modifier.height(UserAdminDimensions.dp8))
            InfoRow("النوع", if (stat.accountType == "WORKSHOP_OWNER") "صاحب ورشة" else "مسوّق")
            if (!stat.workshopName.isNullOrBlank()) InfoRow("الورشة", checkNotNull(stat.workshopName))
            InfoRow("الهاتف", stat.phone)
            InfoRow("انضم", joinedDateLabel(stat.joinedAt))
            InfoRow("آخر ظهور", if (online) "متصل الآن" else relativeSeen(stat.lastSeenAt, now))
        }
    }
}


@Composable
internal fun WeeklyPerformanceCard(performance: BenzineWeeklyPerformance) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_e92c0a4f9874), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp14)
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("المبيعات", CurrencyFormatter.formatNoSymbol(performance.currentWeekSales), Modifier.weight(1f))
                MetricCell("الفواتير", performance.currentWeekInvoices.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("العمولة", CurrencyFormatter.formatNoSymbol(performance.currentWeekCommission), Modifier.weight(1f))
                MetricCell("الحالة", performanceStatusLabel(performance.status), Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun WeekComparisonCard(performance: BenzineWeeklyPerformance) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_eb855028da59), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp14)
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_dac8c7a16811), color = TextSecondary, fontSize = UserAdminTextScale.sp11)
            Spacer(Modifier.height(UserAdminDimensions.dp4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("هذا الأسبوع", CurrencyFormatter.formatNoSymbol(performance.currentWeekSales), Modifier.weight(1f))
                MetricCell("الأسبوع السابق", CurrencyFormatter.formatNoSymbol(performance.previousWeekSales), Modifier.weight(1f))
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_b5bc47192dfb), color = TextSecondary, fontSize = UserAdminTextScale.sp11)
            Spacer(Modifier.height(UserAdminDimensions.dp4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("هذا الأسبوع", CurrencyFormatter.formatNoSymbol(performance.currentWeekCommission), Modifier.weight(1f))
                MetricCell("الأسبوع السابق", CurrencyFormatter.formatNoSymbol(performance.previousWeekCommission), Modifier.weight(1f))
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            InfoRow("الاتجاه", performance.salesTrendLabel ?: stringResource(R.string.legacy_ui_ddf1ce35f4e4))
        }
    }
}

private fun performanceStatusLabel(status: BenzinePerformanceStatus): String = when (status) {
    BenzinePerformanceStatus.NEW -> "جديد"
    BenzinePerformanceStatus.ACTIVE -> "نشط"
    BenzinePerformanceStatus.GROWING -> "متنامٍ"
    BenzinePerformanceStatus.DECLINING -> "متراجع"
    BenzinePerformanceStatus.INACTIVE -> "غير نشط"
}

@Composable
internal fun FinancialCard(
    stat: MarketerStatsItem,
    periodActive: Boolean,
    periodInvoicesCount: Int,
    periodPurchases: Double,
    periodCommission: Double,
    periodLabel: String?
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_add17b0595e9), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp14)
                // عند تفعيل الفلتر نوضّح أن الأرقام أعلاه (الفواتير/المشتريات/العمولات) تخص الفترة فقط
                if (periodActive && periodLabel != null) {
                    Spacer(Modifier.weight(1f))
                    Text(periodLabel, color = AccentBlue, fontSize = UserAdminTextScale.sp11)
                }
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("الفواتير", (if (periodActive) periodInvoicesCount else stat.invoicesCount).toString(), Modifier.weight(1f))
                MetricCell("المشتريات", CurrencyFormatter.formatNoSymbol(if (periodActive) periodPurchases else stat.purchasesTotal), Modifier.weight(1f))
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("العمولات", CurrencyFormatter.formatNoSymbol(if (periodActive) periodCommission else stat.commissionTotal), Modifier.weight(1f))
                MetricCell("الرصيد", CurrencyFormatter.formatNoSymbol(stat.balance), Modifier.weight(1f))
            }
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp10)) {
                MetricCell("سحوبات معلّقة", "${stat.pendingWithdrawalsCount} (${CurrencyFormatter.formatNoSymbol(stat.pendingWithdrawalsAmount)})", Modifier.weight(1f))
                MetricCell("إجمالي المسحوب", CurrencyFormatter.formatNoSymbol(stat.completedWithdrawalsAmount), Modifier.weight(1f))
            }
        }
    }
}

/**
 * شريط 12 أسبوعاً يعرض السلسلة الحالية (`streak`) من اليمين، مع عدد الأسابيع النشطة كنص.
 * ملاحظة: RPC `get_marketer_stats` يُرجع تجميعاً (streak/active_weeks) لا سلسلة أسبوعية
 * تفصيلية؛ مخطّط المشتريات الأسبوعي الكامل يحتاج RPC لاحقاً (المرحلة 5).
 */
@Composable
internal fun StreakStrip(streak: Int, activeWeeks: Int) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_5de44afd6b56), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp14)
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp4)) {
                val cells = 12
                val activeFromRight = streak.coerceIn(0, cells)
                for (i in 0 until cells) {
                    // الخلايا النشطة هي آخر `streak` خلية من اليمين (الأحدث)
                    val isActive = i >= cells - activeFromRight
                    Box(
                        Modifier.weight(1f).height(UserAdminDimensions.dp28).clip(RoundedCornerShape(UserAdminDimensions.dp6))
                            .background(if (isActive) SuccessColor else TextSecondary.copy(alpha = 0.18f))
                    )
                }
            }
            Spacer(Modifier.height(UserAdminDimensions.dp8))
            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_3f238bee44ee, streak, activeWeeks),
                color = TextSecondary, fontSize = UserAdminTextScale.sp12
            )
        }
    }
}

@Composable
internal fun TimelineCard(
    stat: MarketerStatsItem,
    performance: BenzineWeeklyPerformance?,
    now: Long,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(UserAdminDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(UserAdminDimensions.dp16)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_59ca981f2cc4), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = UserAdminTextScale.sp14)
            Spacer(Modifier.height(UserAdminDimensions.dp10))
            TimelineRow(
                "آخر شراء",
                performance?.lastPurchaseAt?.let { DateUtils.formatDate(it) } ?: "لا يوجد شراء مؤهل",
            )
            TimelineRow("آخر ظهور", relativeSeen(stat.lastSeenAt, now))
            if (!stat.lastMessageBody.isNullOrBlank()) {
                TimelineRow("آخر رسالة", "${relativeSeen(stat.lastMessageAt, now)} — ${stat.lastMessageBody}")
            }
        }
    }
}

@Composable
internal fun ActionsRow(
    hasWithdrawable: Boolean,
    onOpenChat: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onRemind: () -> Unit,
    onOpenReport: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp8)) {
        VertoButton(
            onClick = onOpenChat,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = UserAdminDimensions.dp6, vertical = UserAdminDimensions.dp8),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Chat, null, modifier = Modifier.size(UserAdminDimensions.dp16))
            Spacer(Modifier.width(UserAdminDimensions.dp4))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_f9cfe076a5b5), fontSize = UserAdminTextScale.sp12)
        }
        // صرف: معطّل/باهت إن لا يوجد رصيد عمولة قابل للسحب لهذا المسوّق
        VertoButton(
            onClick = onOpenWithdraw,
            enabled = hasWithdrawable,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPrimary,
                disabledContainerColor = AccentPrimary.copy(alpha = 0.3f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = UserAdminDimensions.dp6, vertical = UserAdminDimensions.dp8),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Payments, null, modifier = Modifier.size(UserAdminDimensions.dp16))
            Spacer(Modifier.width(UserAdminDimensions.dp4))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_efb0540fb7e5), fontSize = UserAdminTextScale.sp12)
        }
        VertoOutlinedButton(
            onClick = onRemind,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = UserAdminDimensions.dp6, vertical = UserAdminDimensions.dp8),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.NotificationsActive, null, tint = WarningColor, modifier = Modifier.size(UserAdminDimensions.dp16))
            Spacer(Modifier.width(UserAdminDimensions.dp4))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_ff24c2cf98c7), color = WarningColor, fontSize = UserAdminTextScale.sp12)
        }
        VertoOutlinedButton(
            onClick = onOpenReport,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = UserAdminDimensions.dp6, vertical = UserAdminDimensions.dp8),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Description, null, tint = AccentBlue, modifier = Modifier.size(UserAdminDimensions.dp16))
            Spacer(Modifier.width(UserAdminDimensions.dp4))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_a25392db3cd2), color = AccentBlue, fontSize = UserAdminTextScale.sp12)
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = UserAdminDimensions.dp3)) {
        Text(label, color = TextSecondary, fontSize = UserAdminTextScale.sp13, modifier = Modifier.width(UserAdminDimensions.dp90))
        Text(value, color = TextPrimary, fontSize = UserAdminTextScale.sp13, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(UserAdminDimensions.dp12)).background(BgDeep).padding(UserAdminDimensions.dp12)
    ) {
        Text(label, color = TextSecondary, fontSize = UserAdminTextScale.sp11)
        Spacer(Modifier.height(UserAdminDimensions.dp2))
        Text(value, color = TextPrimary, fontSize = UserAdminTextScale.sp15, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun TimelineRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = UserAdminDimensions.dp4), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = UserAdminDimensions.dp5).size(UserAdminDimensions.dp8).clip(CircleShape).background(AccentPrimary))
        Spacer(Modifier.width(UserAdminDimensions.dp8))
        Column {
            Text(label, color = TextSecondary, fontSize = UserAdminTextScale.sp11)
            Text(value, color = TextPrimary, fontSize = UserAdminTextScale.sp13)
        }
    }
}
