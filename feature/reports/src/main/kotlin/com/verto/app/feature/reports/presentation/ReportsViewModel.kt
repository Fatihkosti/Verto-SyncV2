package com.verto.app.feature.reports.presentation

import com.verto.app.feature.reports.application.model.*

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.export.domain.DocumentSharePort
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.reports.application.ReportsOperationsGateway
import com.verto.app.feature.reports.application.ReportsAccessService
import com.verto.app.feature.reports.application.ReportsDocumentExportService
import com.verto.app.feature.reports.application.model.ReportItemExportRow
import com.verto.app.feature.reports.application.model.ReportCategoryExportRow
import com.verto.app.feature.reports.application.ReportsReadModelQuery
import com.verto.app.feature.reports.application.ReportsReadRequest
import com.verto.app.feature.reports.application.model.ReportsFilters
import com.verto.app.feature.reports.application.model.ReportsReadModel
import com.verto.app.feature.reports.domain.model.DeleteReportBudgetCommand
import com.verto.app.feature.reports.domain.model.SaveReportBudgetCommand
import com.verto.app.feature.reports.presentation.export.ExportOption
import com.verto.app.feature.reports.presentation.export.WhatsAppShareUtil
import com.verto.app.utils.DateUtils
import com.verto.app.utils.ReportPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReportsEvent : UiEvent {
    data class SelectPeriod(val period: ReportPeriod) : ReportsEvent
    data class SetCustomRange(val from: Long, val to: Long) : ReportsEvent
    data object Refresh : ReportsEvent
    data class SetFilters(val filters: ReportsFilters) : ReportsEvent
    data object ClearFilters : ReportsEvent
    data class Export(val option: ExportOption) : ReportsEvent
    data class SaveBudget(val budget: BudgetItem) : ReportsEvent
    data class DeleteBudget(val budget: BudgetItem) : ReportsEvent
    data object RecalculateRfm : ReportsEvent
}

/**
 * يحتفظ بحالة العرض وأحداث الواجهة فقط.
 * قراءة المجالات والحسابات مفوضة إلى [ReportsReadModelQuery]، والكتابة إلى [ReportsOperationsGateway].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val readModelQuery: ReportsReadModelQuery,
    private val operationsGateway: ReportsOperationsGateway,
    private val reportsAccess: ReportsAccessService,
    private val reportsDocumentExport: ReportsDocumentExportService,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val documentSharePort: DocumentSharePort
) : ViewModel(),
    UiStateHolder<ReportsUiState>,
    UiEventHandler<ReportsEvent> {

    private val _activeFilters = MutableStateFlow(ReportsFilters())
    val activeFilters: StateFlow<ReportsFilters> = _activeFilters.asStateFlow()
    val permissions: StateFlow<EmployeePermissions?> = reportsAccess.permissions

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.TODAY)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()
    private val _customFrom = MutableStateFlow(0L)
    val customFrom: StateFlow<Long> = _customFrom.asStateFlow()
    private val _customTo = MutableStateFlow(0L)
    val customTo: StateFlow<Long> = _customTo.asStateFlow()
    private val _refreshGeneration = MutableStateFlow(0L)

    private val dateRange: Flow<Triple<ReportPeriod, Long, Long>> =
        combine(_selectedPeriod, _customFrom, _customTo) { period, from, to ->
            val range = if (period == ReportPeriod.CUSTOM) from to to
            else DateUtils.rangeForPeriod(period)
            Triple(period, range.first, range.second)
        }

    override val uiState: StateFlow<ReportsUiState> = combine(dateRange, _activeFilters, _refreshGeneration) { range, filters, _ ->
        range to filters
    }
        .flatMapLatest { (range, filters) ->
            readModelQuery.observe(
                ReportsReadRequest(
                    period = range.first,
                    from = range.second,
                    to = range.third,
                    filters = filters
                )
            ).map { it.toUiState() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(isLoading = true)
        )

    override fun onEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.SelectPeriod -> selectPeriod(event.period)
            is ReportsEvent.SetCustomRange -> setCustomRange(event.from, event.to)
            ReportsEvent.Refresh -> refresh()
            is ReportsEvent.SetFilters -> setFilters(event.filters)
            ReportsEvent.ClearFilters -> clearFilters()
            is ReportsEvent.Export -> handleExport(event.option)
            is ReportsEvent.SaveBudget -> saveBudget(event.budget)
            is ReportsEvent.DeleteBudget -> deleteBudget(event.budget)
            ReportsEvent.RecalculateRfm -> recalculateRfm()
        }
    }

    fun selectPeriod(period: ReportPeriod) {
        _selectedPeriod.value = period
    }

    fun setCustomRange(from: Long, to: Long) {
        if (from <= 0L || to < from) return
        _customFrom.value = from
        _customTo.value = to
        _selectedPeriod.value = ReportPeriod.CUSTOM
    }

    fun refresh() {
        _refreshGeneration.value = _refreshGeneration.value + 1L
    }

    fun setFilters(filters: ReportsFilters) {
        _activeFilters.value = filters
    }

    fun clearFilters() {
        _activeFilters.value = ReportsFilters()
    }

    fun handleExport(option: ExportOption) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!reportsAccess.canExportReports()) {
                runCatching {
                    auditLogger.logPermissionDenied(
                        "reports_export",
                        "option=$option",
                        sessionReader
                    )
                }
                return@launch
            }

            val state = uiState.value
            when (option) {
                ExportOption.PDF_ITEMS -> {
                    if (state.topItems.isEmpty()) return@launch
                    val items = state.topItems.map {
                        ReportItemExportRow(it.name, it.qty, it.revenueMinor, it.profitMinor, it.currencyCode)
                    }
                    val range = "${DateUtils.formatDate(state.from)} — ${DateUtils.formatDate(state.to)}"
                    val file = reportsDocumentExport.createItemsPdf(items, "تقرير الأصناف", range)
                    documentSharePort.sharePdf(file)
                }

                ExportOption.PDF_CATEGORIES -> {
                    if (state.categories.isEmpty()) return@launch
                    val categories = state.categories.map {
                        ReportCategoryExportRow(
                            it.category,
                            it.distinctItems,
                            it.revenueMinor,
                            it.profitMinor,
                            it.currencyCode
                        )
                    }
                    val range = "${DateUtils.formatDate(state.from)} — ${DateUtils.formatDate(state.to)}"
                    val file = reportsDocumentExport.createCategoriesPdf(categories, "تقرير التصنيفات", range)
                    documentSharePort.sharePdf(file)
                }

                ExportOption.WHATSAPP -> WhatsAppShareUtil.shareToWhatsApp(appContext, state)
                ExportOption.SHARE_TEXT -> WhatsAppShareUtil.shareAsText(appContext, state)
            }
        }
    }

    fun saveBudget(budget: BudgetItem) {
        viewModelScope.launch(Dispatchers.IO) {
            operationsGateway.saveBudget(SaveReportBudgetCommand(budget))
        }
    }

    fun deleteBudget(budget: BudgetItem) {
        viewModelScope.launch(Dispatchers.IO) {
            operationsGateway.deleteBudget(DeleteReportBudgetCommand(budget.id))
        }
    }

    fun recalculateRfm() {
        viewModelScope.launch(Dispatchers.IO) {
            operationsGateway.recalculateRfm()
        }
    }


}

private fun ReportsReadModel.toUiState(): ReportsUiState = ReportsUiState(
    isLoading = false,
    period = period,
    from = from,
    to = to,
    functionalCurrencyCode = functionalCurrencyCode,
    netProfit = netProfit,
    netProfitChange = netProfitChange,
    profitBreakdown = profitBreakdown,
    insights = insights,
    pnl = pnl,
    cashFlow = cashFlow,
    agedReceivables = agedReceivables,
    financialDiagnostics = financialDiagnostics,
    internationalSupplierStatement = internationalSupplierStatement,
    agedPayables = agedPayables,
    purchasePriceVariance = purchasePriceVariance,
    landedCostVariance = landedCostVariance,
    supplierFxVariance = supplierFxVariance,
    supplierPaymentTiming = supplierPaymentTiming,
    operationalAlerts = operationalAlerts,
    kpiDefinitions = kpiDefinitions,
    budgetTarget = budgetTarget,
    budgetProgress = budgetProgress,
    budgetNote = budgetNote,
    activeBudgets = activeBudgets,
    salesTotal = salesTotal,
    salesChange = salesChange,
    netSalesTotal = netSalesTotal,
    netSalesChange = netSalesChange,
    netSalesReliable = netSalesReliable,
    netSalesComparisonAvailable = netSalesComparisonAvailable,
    purchasesTotal = purchasesTotal,
    purchasesChange = purchasesChange,
    purchasesReliable = purchasesReliable,
    purchasesComparisonAvailable = purchasesComparisonAvailable,
    lastUpdatedAt = System.currentTimeMillis(),
    topItems = topItems,
    categories = categories,
    heatmap = heatmap,
    currentShiftSales = currentShiftSales,
    currentShiftTransactions = currentShiftTransactions,
    currentShiftAvgInvoice = currentShiftAvgInvoice,
    openSession = openSession,
    auditTodayCount = auditTodayCount,
    lowStockCount = lowStockCount,
    inventoryHealth = inventoryHealth,
    returns = returns,
    employeePerformance = employeePerformance,
    shipmentsSummary = shipmentsSummary,
    shrinkage = shrinkage,
    topSuppliers = topSuppliers,
    rfmSegments = rfmSegments,
    topClvClients = topClvClients,
    atRiskClientsCount = atRiskClientsCount,
    forecastResult = forecastResult,
    realMargins = realMargins,
    availableCategories = availableCategories,
    availableCashiers = availableCashiers
)
