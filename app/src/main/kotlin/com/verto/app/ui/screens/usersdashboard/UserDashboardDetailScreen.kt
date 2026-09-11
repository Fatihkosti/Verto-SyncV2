package com.verto.app.ui.screens.usersdashboard

import androidx.compose.ui.res.stringResource
import com.verto.app.R
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.components.resolveVertoError

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UserDashboardDetailScreen(
    clientId: String,
    onBack: () -> Unit = {},
    onOpenChat: (conversationId: String, clientId: String, clientName: String) -> Unit = { _, _, _ -> },
    onOpenReport: (clientId: String, from: Long?, to: Long?) -> Unit = { _, _, _ -> },
    onOpenInvoice: (invoiceId: String) -> Unit = {},
    viewModel: UsersDashboardViewModel = hiltViewModel(),
    commissionVm: CommissionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val commissionState by commissionVm.uiState.collectAsStateWithLifecycle()
    val stat = state.stats.firstOrNull { it.clientId == clientId }
    val performance = state.performanceRows.firstOrNull { it.clientId == clientId }
    val now = remember(state.stats) { System.currentTimeMillis() }
    val snackbarHost = remember { SnackbarHostState() }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var filterFrom by remember { mutableStateOf<Long?>(null) }
    var filterTo by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { commissionVm.showAllPeriods() }

    LaunchedEffect(state.reminderMessage) {
        state.reminderMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeReminderMessage()
        }
    }
    LaunchedEffect(commissionState.paymentSuccess) {
        if (commissionState.paymentSuccess) {
            showWithdraw = false
            snackbarHost.showSnackbar("تم الصرف بنجاح ✓")
            commissionVm.clearPaymentSuccess()
            viewModel.refresh()
        }
    }
    val requestActionError = commissionState.requestActionError
    val resolvedRequestActionError = requestActionError?.resolveVertoError()
    LaunchedEffect(requestActionError?.incidentId) {
        resolvedRequestActionError?.let {
            snackbarHost.showSnackbar(it.message)
            commissionVm.clearRequestActionError()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            VertoTopAppBar(
                title = { Text(stat?.fullName ?: stringResource(R.string.legacy_ui_fc76ee83af76), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.legacy_ui_70b3c9ca59e9), tint = TextPrimary)
                    }
                },
                actions = {
                    if (filterFrom != null) {
                        TextButton(onClick = { filterFrom = null; filterTo = null }) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_clear), color = WarningColor, fontSize = UserAdminTextScale.sp12)
                        }
                    }
                    VertoIconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, stringResource(R.string.legacy_ui_db4acfda86b5), tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        if (stat == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_ed9156556fce), color = TextSecondary, fontSize = UserAdminTextScale.sp14)
            }
            return@Scaffold
        }

        val online = MarketerStatsSorter.isOnline(stat, now)

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(UserAdminDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(UserAdminDimensions.dp14)
        ) {
            val periodActive = filterFrom != null && filterTo != null
            val marketerInvoices = remember(state.eligibilityRows, clientId, filterFrom, filterTo) {
                state.eligibilityRows
                    .filter { it.clientId == clientId }
                    .filter { row ->
                        val f = filterFrom; val t = filterTo
                        if (f != null && t != null) (parseEligibilityMillis(row.createdAt)?.let { it in f..t } ?: true) else true
                    }
                    .sortedByDescending { parseEligibilityMillis(it.createdAt) ?: 0L }
            }
            val periodLabel = if (periodActive)
                "${DateUtils.formatDate(checkNotNull(filterFrom))} — ${DateUtils.formatDate(checkNotNull(filterTo))}" else null

            IdentityCard(stat, online, now)
            performance?.let { weekly ->
                WeeklyPerformanceCard(weekly)
                WeekComparisonCard(weekly)
            }
            TimelineCard(stat, performance, now)
            FinancialCard(
                stat = stat,
                periodActive = periodActive,
                periodInvoicesCount = marketerInvoices.size,
                periodPurchases = marketerInvoices.sumOf { it.totalAmount },
                periodCommission = marketerInvoices.sumOf { it.commission },
                periodLabel = periodLabel
            )
            MarketerInvoicesCard(
                invoices = marketerInvoices,
                periodLabel = periodLabel ?: "كل الفترات",
                onInvoiceClick = onOpenInvoice
            )

            ActionsRow(
                hasWithdrawable = (state.withdrawableByClient[clientId] ?: 0.0) > 0.0,
                onOpenChat = {
                    viewModel.startConversation(clientId) { convId ->
                        onOpenChat(convId, clientId, stat.fullName)
                    }
                },
                onOpenWithdraw = { showWithdraw = true },
                onRemind = { showReminderDialog = true },
                onOpenReport = { onOpenReport(clientId, filterFrom, filterTo) }
            )
        }
    }

    if (showDatePicker) {
        com.verto.app.ui.components.DateRangePickerDialog(
            initialFrom = filterFrom, initialTo = filterTo,
            onConfirm = { f, t -> filterFrom = f; filterTo = t; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showWithdraw && stat != null) {
        val balance: ClientCommissionBalance? =
            commissionState.clientBalances.firstOrNull { it.clientId == clientId }
        if (balance != null) {
            WithdrawFlowDialogs(
                client = balance,
                isPayingOut = commissionState.isPayingOut,
                onWithdrawAll = { bank, tx -> commissionVm.withdrawAll(balance.clientId, balance.clientName, bank, tx) },
                onWithdrawSingle = { inv, bank, tx -> commissionVm.withdrawSingle(inv, balance.clientName, bank, tx) },
                onWithdrawFree = { amt, bank, tx -> commissionVm.withdrawFreeAmount(balance.clientId, balance.clientName, amt, bank, tx) },
                onDismiss = { showWithdraw = false }
            )
        } else {
            LaunchedEffect(Unit) {
                snackbarHost.showSnackbar("لا يوجد رصيد قابل للسحب لهذا المسوّق")
                showWithdraw = false
            }
        }
    }

    if (showReminderDialog && stat != null) {
        val target = stat
        ReminderDialog(
            onDismiss = { showReminderDialog = false },
            onSend = { message, navRoute ->
                viewModel.sendAdminReminder(target, message, navRoute)
                showReminderDialog = false
            }
        )
    }
}
