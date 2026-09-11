package com.verto.app.feature.party.presentation.client

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale
import com.verto.app.feature.party.presentation.shared.formatPartyCurrencyAmounts
import com.verto.app.feature.party.application.PartyBalanceDirection

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.components.WhatsAppIcon
import com.verto.app.feature.party.presentation.shared.DashboardInvoiceRow
import com.verto.app.feature.party.presentation.shared.CustomerDecisionCard
import com.verto.app.ui.components.InfoChip
import com.verto.app.ui.components.KpiMini
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTabRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDashboardScreen(
    clientId: String,
    onBack: () -> Unit,
    onInvoice: (String) -> Unit,
    onEditClient: () -> Unit = {},
    onAddPayment: (clientId: String) -> Unit = {},
    onStatement: (clientId: String) -> Unit = {},
    vm: ClientDashboardViewModel = hiltViewModel()
) {
    LaunchedEffect(clientId) { vm.init(clientId) }

    val client              by vm.client.collectAsStateWithLifecycle()
    val customerProfile     by vm.customerProfile.collectAsStateWithLifecycle()
    val summaries           by vm.invoiceSummariesForDisplay.collectAsStateWithLifecycle()
    val balanceAmounts      by vm.balanceAmounts.collectAsStateWithLifecycle()
    val balanceDirection    by vm.balanceDirection.collectAsStateWithLifecycle()
    val totalCommissions    by vm.totalCommissionsByCurrency.collectAsStateWithLifecycle()
    val totalSales          by vm.totalSalesByCurrency.collectAsStateWithLifecycle()
    val totalProfit         by vm.totalProfitByCurrency.collectAsStateWithLifecycle()
    val profitComplete      by vm.profitComplete.collectAsStateWithLifecycle()
    val newDebtInPeriod     by vm.newDebtInPeriodByCurrency.collectAsStateWithLifecycle()
    val collectedInPeriod   by vm.collectedInPeriodByCurrency.collectAsStateWithLifecycle()
    val commissionsInPeriod by vm.commissionsInPeriodByCurrency.collectAsStateWithLifecycle()
    val invoiceCount        by vm.invoiceCount.collectAsStateWithLifecycle()
    val lastActivity        by vm.lastActivity.collectAsStateWithLifecycle()
    val filterFrom          by vm.filterFrom.collectAsStateWithLifecycle()
    val filterTo            by vm.filterTo.collectAsStateWithLifecycle()
    val isDateFiltered      by vm.isDateFiltered.collectAsStateWithLifecycle()
    val permissions         by vm.permissions.collectAsStateWithLifecycle()
    val decision            by vm.decision.collectAsStateWithLifecycle()
    val canEditClients      = permissions?.clientsEdit == true
    val canAddClientPayment = permissions?.clientsAddPayment == true

    var activeTab      by rememberSaveable { mutableStateOf(0) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    // ── لون ونص الرصيد ────────────────────────────────
    // موجب = نحن نطلب (أحمر) | سالب = العميل دفع أكثر (أخضر)
    val balanceColor = when (balanceDirection) {
        PartyBalanceDirection.RECEIVABLE -> ErrorColor
        PartyBalanceDirection.PAYABLE -> SuccessColor
        PartyBalanceDirection.MIXED -> TextSecondary
        PartyBalanceDirection.SETTLED -> TextSecondary
    }
    val balanceText = formatPartyCurrencyAmounts(
        balanceAmounts,
        absolute = balanceDirection != PartyBalanceDirection.MIXED,
    )
    val balanceIcon = if (balanceDirection == PartyBalanceDirection.RECEIVABLE ||
        balanceDirection == PartyBalanceDirection.MIXED
    ) Icons.Filled.Warning else Icons.Filled.AccountBalanceWallet

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title   =  androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                onBack  = onBack,
                actions = {
                    // ترتيب من الشمال لليمين: تعديل | سداد | كشف حساب | تقويم
                    if (canEditClients) {
                        VertoIconButton(onClick = onEditClient) {
                            Icon(Icons.Filled.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit), tint = AccentPrimary)
                        }
                    }
                    if (canAddClientPayment) {
                        VertoIconButton(onClick = { onAddPayment(clientId) }) {
                            Icon(Icons.Filled.Payments, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a8436ce4b250), tint = AccentPrimary)
                        }
                    }
                    VertoIconButton(onClick = { onStatement(clientId) }) {
                        Icon(Icons.Filled.Description, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ebfeae4e577a), tint = AccentPrimary)
                    }
                    VertoIconButton(onClick = { showFromPicker = true }) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_0a249d4772ec),
                            tint = if (isDateFiltered) AccentPrimary else TextMuted
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(PartyDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp12)
        ) {

            // ── بطاقة الاتصال ─────────────────────────
            item {
                client?.let { c ->
                    ClientContactCard(client = c)
                }
            }

            customerProfile?.let { profile ->
                item { CustomerProfileCard(profile = profile) }
            }

            // ── قرار العميل المبني على السجل الفعلي ─────
            decision?.let { snapshot ->
                item { CustomerDecisionCard(snapshot) }
            }

            // ── صف: الرصيد + العمولات ─────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
                ) {
                    // بطاقة الرصيد
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(PartyDimensions.dp14))
                            .background(balanceColor.copy(0.08f))
                            .border(PartyDimensions.dp1, balanceColor.copy(0.3f), RoundedCornerShape(PartyDimensions.dp14))
                            .padding(PartyDimensions.dp14),
                        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                        ) {
                            Icon(balanceIcon, null,
                                tint = balanceColor, modifier = Modifier.size(PartyDimensions.dp16))
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_f96a754ed8d1),
                                color = balanceColor,
                                fontSize = PartyTextScale.sp12,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            balanceText,
                            color = balanceColor,
                            fontSize = PartyTextScale.sp18,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // بطاقة العمولات
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(PartyDimensions.dp14))
                            .background(GoldPrimary.copy(0.08f))
                            .border(PartyDimensions.dp1, GoldPrimary.copy(0.3f), RoundedCornerShape(PartyDimensions.dp14))
                            .padding(PartyDimensions.dp14),
                        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                        ) {
                            Icon(Icons.Filled.MonetizationOn, null,
                                tint = GoldPrimary, modifier = Modifier.size(PartyDimensions.dp16))
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_6dee7dfa8898),
                                color = GoldPrimary,
                                fontSize = PartyTextScale.sp12,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            formatPartyCurrencyAmounts(totalCommissions),
                            color = GoldPrimary,
                            fontSize = PartyTextScale.sp18,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // ── ٦ بطاقات KPI (٣ صفوف × ٢) ───────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {

                    // الصف ١: المبيعات + الأرباح
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_7db74e774f7c),
                            value    = formatPartyCurrencyAmounts(totalSales),
                            color    = AccentPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_4a327dae4b45),
                            value    = if (profitComplete) formatPartyCurrencyAmounts(totalProfit) else "—",
                            color    = SuccessColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف ٢: الديون المستحقة + المحصّلة
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_b5a60fad7803),
                            value    = formatPartyCurrencyAmounts(newDebtInPeriod),
                            color    = ErrorColor,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_d60cb6693cde),
                            value    = formatPartyCurrencyAmounts(collectedInPeriod),
                            color    = SuccessColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // الصف ٣: العمولات + عدد الفواتير
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_b7deadbeaa38),
                            value    = formatPartyCurrencyAmounts(commissionsInPeriod),
                            color    = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_aff299f08da2),
                            value    = invoiceCount.toString(),
                            color    = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── آخر تعامل + أيقونة الحساب البنكي ─────
            item {
                val clipboardManager = LocalClipboardManager.current
                var copiedBank by remember { mutableStateOf(false) }
                val bankAccounts = client?.bankAccount?.toBankAccountList() ?: emptyList()
                val firstAccount = bankAccounts.firstOrNull()

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // chip آخر تعامل
                    InfoChip(
                        icon     = Icons.Filled.Schedule,
                        label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a33fa614b279),
                        value    = lastActivity?.let { DateUtils.formatDate(it) } ?: "—",
                        color    = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )

                    // أيقونة الحساب البنكي — تظهر فقط إذا يوجد حساب
                    if (firstAccount != null) {
                        Box(
                            Modifier
                                .size(PartyDimensions.dp44)
                                .clip(RoundedCornerShape(PartyDimensions.dp12))
                                .background(
                                    if (copiedBank) AccentPrimary.copy(0.2f) else BgCard
                                )
                                .border(
                                    PartyDimensions.dp1,
                                    if (copiedBank) AccentPrimary.copy(0.5f) else BorderColor.copy(0.3f),
                                    RoundedCornerShape(PartyDimensions.dp12)
                                )
                                .clickable {
                                    clipboardManager.setText(
                                        AnnotatedString(firstAccount.accountNumber)
                                    )
                                    copiedBank = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (copiedBank) Icons.Filled.Check
                                else Icons.Filled.AccountBalance,
                                contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_17afb7d7e0b1),
                                tint = if (copiedBank) AccentPrimary else TextMuted,
                                modifier = Modifier.size(PartyDimensions.dp20)
                            )
                        }
                    }
                }
            }

            // ── تبويبات الفواتير ──────────────────────
            item {
                VertoTabRow(
                    selectedTabIndex = activeTab,
                    containerColor   = BgCard,
                    contentColor     = AccentPrimary
                ) {
                    listOf("كل الفواتير", "آجل", "متأخر").forEachIndexed { i, label ->
                        Tab(
                            selected = activeTab == i,
                            onClick  = { activeTab = i },
                            text = { Text(label, fontSize = PartyTextScale.sp13) }
                        )
                    }
                }
            }

            // ── قائمة الفواتير ────────────────────────
            val filtered = when (activeTab) {
                1    -> summaries.filter { it.invoice.status == PartyInvoiceStatus.CLOSED_CREDIT }
                2    -> summaries.filter { it.financial.isOverdue }
                else -> summaries
            }.sortedByDescending { it.invoice.createdAt }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = PartyDimensions.dp32),
                        contentAlignment = Alignment.Center
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_e5ce6bd59016), color = TextMuted) }
                }
            } else {
                items(filtered, key = { it.invoice.id }) { s ->
                    DashboardInvoiceRow(summary = s, onClick = { onInvoice(s.invoice.id) })
                }
            }
        }
    }

    // ── Date Pickers ─────────────────────────────────
    if (showFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (filterFrom > 0L) filterFrom
            else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        val newFrom = c.timeInMillis
                        val newTo   = if (filterTo > newFrom) filterTo
                        else newFrom + 86_400_000L - 1L
                        vm.setDateRange(newFrom, newTo)
                        showFromPicker = false
                        showToPicker   = true   // يفتح تقويم النهاية تلقائياً
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (filterTo < Long.MAX_VALUE / 2) filterTo
            else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                        }
                        vm.setDateRange(filterFrom, c.timeInMillis)
                        showToPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        ) { DatePicker(state = state) }
    }
}

// ─────────────────────────────────────────────────────
// بطاقة اتصال العميل — محدّثة
// ─────────────────────────────────────────────────────
