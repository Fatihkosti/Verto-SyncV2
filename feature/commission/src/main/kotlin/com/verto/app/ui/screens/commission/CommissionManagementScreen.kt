package com.verto.app.ui.screens.commission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.commission.application.MarketerCommissionReport
import com.verto.app.ui.components.DateRangePickerDialog
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.components.VertoUserError
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal val numFmt = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
internal fun Double.eng() = numFmt.format(this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionManagementScreen(
    withdrawClientId: String? = null,
    onBack: () -> Unit = {},
    onNavigateToInvoice: (String) -> Unit = {},
    onNavigateToClientDashboard: (String) -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToUsersDashboard: () -> Unit = {},
    generateMarketerCommissionPdf: (MarketerCommissionReport) -> File,
    viewModel: CommissionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    fun shareMarketerPdf(clientId: String, clientName: String) {
        val report = viewModel.buildMarketerReport(clientId, clientName)
        val file = generateMarketerCommissionPdf(report)
        viewModel.sharePdf(file)
    }

    LaunchedEffect(permissions) {
        if (permissions != null && permissions?.commissionManage != true) onBack()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showWithdrawalSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadWithdrawalRequests() }

    var selectedClient by remember { mutableStateOf<ClientCommissionBalance?>(null) }
    var showWithdrawOptions by remember { mutableStateOf(false) }
    var withdrawAutoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(withdrawClientId, state.clientBalances) {
        if (withdrawClientId != null && !withdrawAutoOpened) {
            state.clientBalances.firstOrNull { it.clientId == withdrawClientId }?.let { match ->
                selectedClient = match
                showWithdrawOptions = true
                withdrawAutoOpened = true
            }
        }
    }

    LaunchedEffect(state.paymentSuccess) {
        if (state.paymentSuccess) {
            snackbarHost.showSnackbar("تم الدفع بنجاح ✓")
            viewModel.clearPaymentSuccess()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_4b20c54bf31d),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    if (state.filterFrom != null) {
                        TextButton(onClick = { viewModel.clearFilter() }) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_clear),
                                color = ErrorColor,
                                fontSize = CommissionTextScale.sp12,
                            )
                        }
                    }
                    VertoIconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(CommissionDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp16),
        ) {
            state.requestActionError?.let { structuredError ->
                item(key = "commission_request_error_${structuredError.incidentId}") {
                    VertoUserError(
                        error = structuredError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.codeError?.let { structuredError ->
                item(key = "commission_code_error_${structuredError.incidentId}") {
                    VertoUserError(
                        error = structuredError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.filterFrom != null && state.filterTo != null) {
                item {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.verto.feature.commission.R.string.commission_ds_98df579f0487,
                            DateUtils.formatDate(checkNotNull(state.filterFrom)),
                            DateUtils.formatDate(checkNotNull(state.filterTo)),
                        ),
                        color = AccentLight,
                        fontSize = CommissionTextScale.sp12,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (state.isApproximate) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CommissionDimensions.dp10))
                            .background(GoldPrimary.copy(alpha = 0.12f))
                            .padding(horizontal = CommissionDimensions.dp12, vertical = CommissionDimensions.dp8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(CommissionDimensions.dp16),
                        )
                        Spacer(Modifier.width(CommissionDimensions.dp8))
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_265bb529ff96),
                            color = GoldPrimary,
                            fontSize = CommissionTextScale.sp12,
                        )
                    }
                }
            }

            item { CommissionCurrentSummary(state) }

            item {
                CommissionAttentionSection(
                    attention = state.attention,
                    onOpenWithdrawalRequests = {
                        viewModel.loadWithdrawalRequests()
                        showWithdrawalSheet = true
                    },
                    onReadyPayout = { payout ->
                        state.clientBalances.firstOrNull { it.clientId == payout.clientId }?.let { client ->
                            selectedClient = client
                            showWithdrawOptions = true
                        }
                    },
                )
            }

            item {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_90d7f76e5c34),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = CommissionTextScale.sp15,
                )
            }
            item {
                CommissionDetailsList(
                    state,
                    onCardClick = { card ->
                        viewModel.selectCard(card)
                        showDetailSheet = true
                    },
                )
            }

            item {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_b57596f18b10),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = CommissionTextScale.sp15,
                )
            }
            if (state.activityLog.isEmpty()) {
                item {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_07c4c3ae426d),
                        color = TextMuted,
                        fontSize = CommissionTextScale.sp13,
                        modifier = Modifier.fillMaxWidth().padding(vertical = CommissionDimensions.dp24),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(state.activityLog) { log -> ActivityLogRow(log) }
            }
        }
    }

    if (showWithdrawalSheet) {
        WithdrawalRequestsSheet(
            items = state.withdrawalRequestItems,
            isActionInProgress = state.isRequestActionInProgress,
            onApprove = { id, txRef -> viewModel.approveRequest(id, txRef) },
            onReject = { id, note -> viewModel.rejectRequest(id, note) },
            onComplete = { id, note -> viewModel.completeRequest(id, note) },
            onDismiss = { showWithdrawalSheet = false },
        )
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            initialFrom = state.filterFrom,
            initialTo = state.filterTo,
            onConfirm = { f, t ->
                viewModel.setFilter(f, t)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showDetailSheet) {
        CommissionDetailSheet(
            state = state,
            onDismiss = { showDetailSheet = false },
            onSelectClient = { client ->
                selectedClient = client
                showWithdrawOptions = true
            },
            onInvoiceClick = if (state.selectedCard == CommissionCard.TOTAL) {
                { invoiceId ->
                    showDetailSheet = false
                    onNavigateToInvoice(invoiceId)
                }
            } else null,
        )
    }

    if (showWithdrawOptions) {
        selectedClient?.let { client ->
            WithdrawFlowDialogs(
                client = client,
                isPayingOut = state.isPayingOut,
                onWithdrawAll = { bank, tx ->
                    viewModel.withdrawAll(client.clientId, client.clientName, bank, tx)
                    showWithdrawOptions = false
                    showDetailSheet = false
                },
                onWithdrawSingle = { inv, bank, tx ->
                    viewModel.withdrawSingle(inv, client.clientName, bank, tx)
                    showWithdrawOptions = false
                    showDetailSheet = false
                },
                onWithdrawFree = { amt, bank, tx ->
                    viewModel.withdrawFreeAmount(client.clientId, client.clientName, amt, bank, tx)
                    showWithdrawOptions = false
                    showDetailSheet = false
                },
                onDismiss = { showWithdrawOptions = false },
            )
        }
    }
}
