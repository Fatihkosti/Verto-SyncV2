package com.verto.app.feature.party.presentation.supplier

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale
import com.verto.app.feature.party.presentation.shared.formatPartyCurrencyAmounts
import com.verto.app.feature.party.application.PartyBalanceDirection

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.components.WhatsAppIcon
import com.verto.app.feature.party.presentation.shared.DashboardInvoiceRow
import com.verto.app.feature.party.presentation.shared.SupplierDecisionCard
import com.verto.app.ui.components.InfoChip
import com.verto.app.ui.components.KpiMini
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTabRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDashboardScreen(
    supplierId: String,
    onBack: () -> Unit,
    onInvoice: (String) -> Unit,
    onEditSupplier: () -> Unit = {},
    onAddPayment: (supplierId: String) -> Unit = {},
    onStatement: (supplierId: String) -> Unit = {},
    vm: SupplierDashboardViewModel = hiltViewModel()
) {
    LaunchedEffect(supplierId) { vm.init(supplierId) }

    val supplier            by vm.supplier.collectAsStateWithLifecycle()
    val summaries           by vm.invoiceSummaries.collectAsStateWithLifecycle()
    val balanceAmounts      by vm.balanceAmounts.collectAsStateWithLifecycle()
    val balanceDirection    by vm.balanceDirection.collectAsStateWithLifecycle()
    val totalPurchases      by vm.totalPurchasesByCurrency.collectAsStateWithLifecycle()
    val newDebtInPeriod     by vm.newDebtInPeriodByCurrency.collectAsStateWithLifecycle()
    val collectedInPeriod   by vm.collectedInPeriodByCurrency.collectAsStateWithLifecycle()
    val invoiceCount        by vm.invoiceCount.collectAsStateWithLifecycle()
    val lastActivity        by vm.lastActivity.collectAsStateWithLifecycle()
    val filterFrom          by vm.filterFrom.collectAsStateWithLifecycle()
    val filterTo            by vm.filterTo.collectAsStateWithLifecycle()
    val isDateFiltered      by vm.isDateFiltered.collectAsStateWithLifecycle()
    val permissions         by vm.permissions.collectAsStateWithLifecycle()
    val intelligence        by vm.intelligence.collectAsStateWithLifecycle()
    val canAddSupplierPayment = permissions?.suppliersAddPayment == true
    val canEditClients = permissions?.clientsEdit == true

    var activeTab      by rememberSaveable { mutableStateOf(0) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title   =  androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                onBack  = onBack,
                actions = {
                    VertoIconButton(onClick = { showFromPicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_0a249d4772ec), tint = if (isDateFiltered) AccentBlue else TextMuted)
                    }
                    // زر كشف الحساب
                    VertoIconButton(onClick = { onStatement(supplierId) }) {
                        Icon(Icons.Filled.Description, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ebfeae4e577a), tint = AccentBlue)
                    }
                    if (canAddSupplierPayment) {
                        VertoIconButton(onClick = { onAddPayment(supplierId) }) {
                            Icon(Icons.Filled.Payments, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a8436ce4b250), tint = AccentBlue)
                        }
                    }
                    if (canEditClients) {
                        VertoIconButton(onClick = onEditSupplier) {
                            Icon(Icons.Filled.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit), tint = AccentBlue)
                        }
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
                supplier?.let { s ->
                    SupplierContactCard(supplier = s)
                }
            }

            // ── قرار المورد من PO / GRN / Match ─────────
            intelligence?.let { snapshot ->
                item { SupplierDecisionCard(snapshot) }
            }

            // ── الرصيد الموحد + أيقونة الحساب البنكي ──
            item {
                val balanceColor = when (balanceDirection) {
                    PartyBalanceDirection.RECEIVABLE -> ErrorColor
                    PartyBalanceDirection.PAYABLE -> SuccessColor
                    PartyBalanceDirection.MIXED -> TextSecondary
                    PartyBalanceDirection.SETTLED -> TextSecondary
                }
                val balanceIcon = if (balanceDirection == PartyBalanceDirection.RECEIVABLE ||
                    balanceDirection == PartyBalanceDirection.MIXED
                ) Icons.Filled.Warning else Icons.Filled.AccountBalanceWallet
                val balanceText = formatPartyCurrencyAmounts(
                    balanceAmounts,
                    absolute = balanceDirection != PartyBalanceDirection.MIXED,
                )

                val clipboardManager = LocalClipboardManager.current
                var copied by remember { mutableStateOf(false) }
                val bankAccounts = supplier?.bankAccount?.toBankAccountList() ?: emptyList()
                val firstAccount = bankAccounts.firstOrNull()

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // بطاقة الرصيد — تمتد كاملاً إذا لا يوجد حساب بنكي
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(PartyDimensions.dp14))
                            .background(balanceColor.copy(0.08f))
                            .border(PartyDimensions.dp1, balanceColor.copy(0.3f), RoundedCornerShape(PartyDimensions.dp14))
                            .padding(PartyDimensions.dp14),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
                        ) {
                            Icon(balanceIcon, null, tint = balanceColor, modifier = Modifier.size(PartyDimensions.dp18))
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_f96a754ed8d1), color = balanceColor, fontSize = PartyTextScale.sp13, fontWeight = FontWeight.Bold)
                        }
                        Text(balanceText, color = balanceColor, fontSize = PartyTextScale.sp18, fontWeight = FontWeight.Black)
                    }

                    // أيقونة الحساب البنكي — تظهر فقط إذا يوجد حساب مسجل
                    if (firstAccount != null) {
                        Box(
                            Modifier
                                .size(PartyDimensions.dp48)
                                .clip(RoundedCornerShape(PartyDimensions.dp14))
                                .background(if (copied) AccentBlue.copy(0.2f) else BgCard)
                                .border(
                                    PartyDimensions.dp1,
                                    if (copied) AccentBlue.copy(0.5f) else BorderColor.copy(0.3f),
                                    RoundedCornerShape(PartyDimensions.dp14)
                                )
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(firstAccount.accountNumber))
                                    copied = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (copied) Icons.Filled.Check else Icons.Filled.AccountBalance,
                                contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_17afb7d7e0b1),
                                tint = if (copied) AccentBlue else TextMuted,
                                modifier = Modifier.size(PartyDimensions.dp20)
                            )
                        }
                    }
                }
            }



            // ── البطاقات الأربعة بنفس المقاس (2×2) ────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_62e5021947c6),
                            value    = formatPartyCurrencyAmounts(totalPurchases),
                            color    = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_aff299f08da2),
                            value    = invoiceCount.toString(),
                            color    = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)) {
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_181238f87187),
                            value    = formatPartyCurrencyAmounts(newDebtInPeriod),
                            color    = ErrorColor,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMini(
                            label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ad29ae3715f1),
                            value    = formatPartyCurrencyAmounts(collectedInPeriod),
                            color    = SuccessColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── آخر تعامل — يظهر دائماً حتى لو خارج الفترة ──
            item {
                InfoChip(
                    icon     = Icons.Filled.Schedule,
                    label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a33fa614b279),
                    value    = lastActivity?.let { DateUtils.formatDate(it) } ?: "—",
                    color    = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── تبويبات الفواتير ──────────────────────
            item {
                VertoTabRow(
                    selectedTabIndex = activeTab,
                    containerColor   = BgCard,
                    contentColor     = AccentBlue
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
            initialSelectedDateMillis = if (filterFrom > 0L) filterFrom else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                        }
                        val newFrom = c.timeInMillis
                        vm.setDateRange(newFrom, filterTo)
                        showFromPicker = false
                        showToPicker = true
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentBlue) }
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
            initialSelectedDateMillis = if (filterTo < Long.MAX_VALUE / 2) filterTo else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                        }
                        vm.setDateRange(filterFrom, c.timeInMillis)
                        showToPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentBlue) }
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
// بطاقة اتصال المورد
// ─────────────────────────────────────────────────────
