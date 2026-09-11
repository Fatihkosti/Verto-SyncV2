package com.verto.app.feature.reports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.reports.application.model.ReportAccessLevel
import com.verto.app.feature.reports.presentation.components.core.ActiveFilterChipsRow
import com.verto.app.feature.reports.presentation.components.core.AdvancedFiltersSheet
import com.verto.app.feature.reports.presentation.components.core.PeriodSelectorPill
import com.verto.app.feature.reports.presentation.components.financial.AgedPayablesCard
import com.verto.app.feature.reports.presentation.components.financial.AgedReceivablesCard
import com.verto.app.feature.reports.presentation.components.financial.FinancialIntegrityCard
import com.verto.app.feature.reports.presentation.components.financial.InternationalSupplierStatementCard
import com.verto.app.feature.reports.presentation.components.financial.CashFlowStatement
import com.verto.app.feature.reports.presentation.components.financial.BudgetVsActualCard
import com.verto.app.feature.reports.presentation.components.financial.ProfitLossStatement
import com.verto.app.feature.reports.presentation.components.operations.AuditLogTodayCard
import com.verto.app.feature.reports.presentation.components.operations.EmployeePerformanceCard
import com.verto.app.feature.reports.presentation.components.operations.InventoryHealthCard
import com.verto.app.feature.reports.presentation.components.operations.InvoiceAnalyticsF255Card
import com.verto.app.feature.reports.presentation.components.operations.ReturnsAnalysisCard
import com.verto.app.feature.reports.presentation.components.operations.ShrinkageReportCard
import com.verto.app.feature.reports.presentation.components.operations.XReportSnapshot
import com.verto.app.feature.reports.presentation.components.operations.ShipmentsSummaryCard
import com.verto.app.feature.reports.presentation.components.operations.SuppliersAnalysisCard
import com.verto.app.feature.reports.presentation.components.sales.RealMarginCard
import com.verto.app.feature.reports.presentation.export.ReportsExportFab
import com.verto.app.feature.reports.presentation.tabs.SalesTab
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit = {},
    onClientClick: (String) -> Unit = {},
    vm: ReportsViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val period by vm.selectedPeriod.collectAsStateWithLifecycle()
    val filters by vm.activeFilters.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    // 365-1: report viewing/drill-down is temporarily public inside the Reports feature.
    // Export remains permission-gated independently.
    val accessLevel = ReportAccessLevel.FULL
    val canExport = permissions?.reportsExport == true

    var detailTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var showFiltersSheet by rememberSaveable { mutableStateOf(false) }
    val detailTarget = remember(detailTargetName) {
        detailTargetName?.let { runCatching { ReportDecisionTarget.valueOf(it) }.getOrNull() }
    }
    val dashboardListState = rememberLazyListState()
    val detailListState = rememberLazyListState()

    LaunchedEffect(detailTarget) {
        if (detailTarget != null) detailListState.scrollToItem(0)
        // Advanced filters are sales-segment filters, not global dashboard filters.
        // Clear them as soon as the user leaves Sales so other report domains never
        // appear partially filtered.
        if (detailTarget != ReportDecisionTarget.SALES && filters.isActive) {
            vm.clearFilters()
        }
        if (detailTarget != ReportDecisionTarget.SALES) {
            showFiltersSheet = false
        }
    }

    fun openDetail(target: ReportDecisionTarget) {
        detailTargetName = target.name
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(
                        text = detailTarget?.title ?: "التقارير",
                        color = TextPrimary,
                        fontSize = ReportsTextScale.sp18,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    VertoIconButton(
                        onClick = {
                            if (detailTarget != null) {
                                if (filters.isActive) vm.clearFilters()
                                detailTargetName = null
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = TextSecondary,
                        )
                    }
                },
                actions = {
                    if (detailTarget == ReportDecisionTarget.SALES) {
                        BadgedBox(
                            badge = {
                                if (filters.isActive) Badge { Text(filters.activeCount.toString()) }
                            },
                        ) {
                            VertoIconButton(onClick = { showFiltersSheet = true }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_v365_1_sales_filters_title),
                                    tint = if (filters.isActive) AccentPrimary else TextSecondary,
                                )
                            }
                        }
                    }
                    VertoIconButton(onClick = { vm.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
        floatingActionButton = {
            if (canExport) ReportsExportFab(onExport = { vm.handleExport(it) })
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AccentPrimary)
            }
            return@Scaffold
        }

        LazyColumn(
            state = if (detailTarget == null) dashboardListState else detailListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = ReportsDimensions.dp96),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp0),
        ) {
            item {
                PeriodSelectorPill(
                    selected = period,
                    activeFrom = uiState.from,
                    activeTo = uiState.to,
                    onSelect = { vm.selectPeriod(it) },
                    onCustomRange = { from, to -> vm.setCustomRange(from, to) },
                )
            }

            if (detailTarget == ReportDecisionTarget.SALES) {
                item {
                    ActiveFilterChipsRow(
                        filters = filters,
                        onFiltersChange = { vm.setFilters(it) },
                        modifier = Modifier.padding(bottom = ReportsDimensions.dp4),
                    )
                }
            }

            if (detailTarget == null) {
                item {
                    ReportsDecisionDashboard(
                        state = uiState,
                        accessLevel = accessLevel,
                        onOpen = ::openDetail,
                        modifier = Modifier.padding(bottom = ReportsDimensions.dp16),
                    )
                }
            } else {
                item {
                    ReportDecisionDetail(
                        target = detailTarget,
                        state = uiState,
                        vm = vm,
                        accessLevel = accessLevel,
                        onClientClick = onClientClick,
                    )
                }
            }
        }
    }

    if (showFiltersSheet && detailTarget == ReportDecisionTarget.SALES) {
        AdvancedFiltersSheet(
            filters = filters,
            availableCategories = uiState.availableCategories,
            availableCashiers = uiState.availableCashiers,
            onFiltersChange = { vm.setFilters(it) },
            onDismiss = { showFiltersSheet = false },
        )
    }
}

@Composable
private fun ReportDecisionDetail(
    target: ReportDecisionTarget,
    state: ReportsUiState,
    vm: ReportsViewModel,
    accessLevel: ReportAccessLevel,
    onClientClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ReportsDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12),
    ) {
        when (target) {
            ReportDecisionTarget.SALES -> {
                SalesTab(
                    state = state,
                    vm = vm,
                    onClientClick = onClientClick,
                    showFullAnalytics = accessLevel == ReportAccessLevel.FULL,
                )
                if (accessLevel == ReportAccessLevel.FULL) {
                    EmployeePerformanceCard(
                        data = state.employeePerformance,
                        modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                    )
                }
            }

            ReportDecisionTarget.PROFIT -> {
                BudgetVsActualCard(
                    target = state.budgetTarget,
                    actual = state.salesTotal,
                    progress = state.budgetProgress,
                    budgets = state.activeBudgets,
                    onSaveBudget = { vm.saveBudget(it) },
                    onDeleteBudget = { vm.deleteBudget(it) },
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
                ProfitLossStatement(pnl = state.pnl, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                RealMarginCard(margins = state.realMargins, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                FinancialIntegrityCard(data = state.financialDiagnostics, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                AuditLogTodayCard(
                    todayCount = state.auditTodayCount,
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
            }

            ReportDecisionTarget.CASH_FLOW -> {
                CashFlowStatement(
                    data = state.cashFlow,
                    initiallyExpanded = true,
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
                XReportSnapshot(
                    openSession = state.openSession,
                    shiftSales = state.currentShiftSales,
                    shiftTransactions = state.currentShiftTransactions,
                    shiftAvgInvoice = state.currentShiftAvgInvoice,
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
            }

            ReportDecisionTarget.RECEIVABLES -> {
                AgedReceivablesCard(data = state.agedReceivables, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
            }

            ReportDecisionTarget.INVENTORY -> {
                InventoryHealthCard(data = state.inventoryHealth, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                ReturnsAnalysisCard(data = state.returns, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                ShrinkageReportCard(data = state.shrinkage, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
            }

            ReportDecisionTarget.PURCHASES -> {
                PurchasePeriodSummaryCard(state = state, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                SuppliersAnalysisCard(
                    suppliers = state.topSuppliers,
                    initiallyExpanded = true,
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
                AgedPayablesCard(data = state.agedPayables, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
                InternationalSupplierStatementCard(
                    rows = state.internationalSupplierStatement,
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
                InvoiceAnalyticsF255Card(
                    ppv = state.purchasePriceVariance,
                    landed = state.landedCostVariance,
                    fx = state.supplierFxVariance,
                    paymentTiming = state.supplierPaymentTiming,
                    alerts = state.operationalAlerts,
                    modifier = Modifier.padding(horizontal = ReportsDimensions.dp16),
                )
                ShipmentsSummaryCard(data = state.shipmentsSummary, modifier = Modifier.padding(horizontal = ReportsDimensions.dp16))
            }
        }
        Spacer(Modifier.height(ReportsDimensions.dp16))
    }
}
