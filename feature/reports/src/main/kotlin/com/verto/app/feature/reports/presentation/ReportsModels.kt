package com.verto.app.feature.reports.presentation

import com.verto.app.core.presentation.UiState
import com.verto.app.feature.reports.application.model.ClientClvData
import com.verto.app.feature.reports.application.model.ForecastResult
import com.verto.app.feature.reports.application.model.ItemRealMargin
import com.verto.app.feature.reports.application.model.RfmSegmentSummary
import com.verto.app.feature.reports.application.model.SmartInsight
import com.verto.app.feature.reports.domain.model.ReportBudget
import com.verto.app.feature.reports.domain.model.ReportCashSession
import com.verto.app.utils.ReportPeriod

typealias ProfitLossData = com.verto.app.feature.reports.application.model.ProfitLossData
typealias CashFlowData = com.verto.app.feature.reports.application.model.CashFlowData
typealias AgedReceivable = com.verto.app.feature.reports.application.model.AgedReceivable
typealias AgedReceivablesData = com.verto.app.feature.reports.application.model.AgedReceivablesData
typealias HeatmapCell = com.verto.app.feature.reports.application.model.HeatmapCell
typealias TopItem = com.verto.app.feature.reports.application.model.TopItem
typealias CategorySummary = com.verto.app.feature.reports.application.model.CategorySummary
typealias InventoryItemSummary = com.verto.app.feature.reports.application.model.InventoryItemSummary
typealias InventoryHealthData = com.verto.app.feature.reports.application.model.InventoryHealthData
typealias ReturnedItem = com.verto.app.feature.reports.application.model.ReturnedItem
typealias ReturnsData = com.verto.app.feature.reports.application.model.ReturnsData
typealias EmployeeSummary = com.verto.app.feature.reports.application.model.EmployeeSummary
typealias EmployeePerformanceData = com.verto.app.feature.reports.application.model.EmployeePerformanceData
typealias SupplierSummary = com.verto.app.feature.reports.application.model.SupplierSummary
typealias ShipmentsSummaryData = com.verto.app.feature.reports.application.model.ShipmentsSummaryData
typealias ShrinkageData = com.verto.app.feature.reports.application.model.ShrinkageData
typealias FinancialDiagnosticsData = com.verto.app.feature.reports.application.model.FinancialDiagnosticsData
typealias InternationalSupplierStatementRow = com.verto.app.feature.reports.application.model.InternationalSupplierStatementRow
typealias AgedPayablesData = com.verto.app.feature.reports.application.model.AgedPayablesData
typealias PurchasePriceVarianceData = com.verto.app.feature.reports.application.model.PurchasePriceVarianceData
typealias LandedCostVarianceData = com.verto.app.feature.reports.application.model.LandedCostVarianceData
typealias SupplierFxVarianceData = com.verto.app.feature.reports.application.model.SupplierFxVarianceData
typealias SupplierPaymentTimingData = com.verto.app.feature.reports.application.model.SupplierPaymentTimingData
typealias OperationalAnalyticsAlert = com.verto.app.feature.reports.application.model.OperationalAnalyticsAlert
typealias KpiDefinition = com.verto.app.feature.reports.application.model.KpiDefinition

data class ReportsUiState(
    val isLoading: Boolean = true,
    val period: ReportPeriod = ReportPeriod.TODAY,
    val from: Long = 0L,
    val to: Long = 0L,
    val functionalCurrencyCode: String = "",
    val netProfit: Double = 0.0,
    val netProfitChange: Float = 0f,
    val profitBreakdown: ProfitLossData = ProfitLossData(),
    val insights: List<SmartInsight> = emptyList(),
    val pnl: ProfitLossData = ProfitLossData(),
    val cashFlow: CashFlowData = CashFlowData(),
    val agedReceivables: AgedReceivablesData = AgedReceivablesData(),
    val financialDiagnostics: FinancialDiagnosticsData = FinancialDiagnosticsData(),
    val internationalSupplierStatement: List<InternationalSupplierStatementRow> = emptyList(),
    val agedPayables: AgedPayablesData = AgedPayablesData(),
    val purchasePriceVariance: PurchasePriceVarianceData = PurchasePriceVarianceData(),
    val landedCostVariance: LandedCostVarianceData = LandedCostVarianceData(),
    val supplierFxVariance: SupplierFxVarianceData = SupplierFxVarianceData(),
    val supplierPaymentTiming: SupplierPaymentTimingData = SupplierPaymentTimingData(),
    val operationalAlerts: List<OperationalAnalyticsAlert> = emptyList(),
    val kpiDefinitions: List<KpiDefinition> = emptyList(),
    val budgetTarget: Double = 0.0,
    val budgetProgress: Float = 0f,
    val budgetNote: String = "",
    val activeBudgets: List<ReportBudget> = emptyList(),
    val salesTotal: Double = 0.0,
    val salesChange: Float = 0f,
    val netSalesTotal: Double = 0.0,
    val netSalesChange: Float = 0f,
    val netSalesReliable: Boolean = true,
    val netSalesComparisonAvailable: Boolean = false,
    val purchasesTotal: Double = 0.0,
    val purchasesChange: Float = 0f,
    val purchasesReliable: Boolean = true,
    val purchasesComparisonAvailable: Boolean = false,
    val lastUpdatedAt: Long = 0L,
    val topItems: List<TopItem> = emptyList(),
    val categories: List<CategorySummary> = emptyList(),
    val heatmap: List<HeatmapCell> = emptyList(),
    val currentShiftSales: Double = 0.0,
    val currentShiftTransactions: Int = 0,
    val currentShiftAvgInvoice: Double = 0.0,
    val openSession: ReportCashSession? = null,
    val auditTodayCount: Int = 0,
    val lowStockCount: Int = 0,
    val inventoryHealth: InventoryHealthData = InventoryHealthData(),
    val returns: ReturnsData = ReturnsData(),
    val employeePerformance: EmployeePerformanceData = EmployeePerformanceData(),
    val shipmentsSummary: ShipmentsSummaryData = ShipmentsSummaryData(),
    val shrinkage: ShrinkageData = ShrinkageData(),
    val topSuppliers: List<SupplierSummary> = emptyList(),
    val rfmSegments: List<RfmSegmentSummary> = emptyList(),
    val topClvClients: List<ClientClvData> = emptyList(),
    val atRiskClientsCount: Int = 0,
    val forecastResult: ForecastResult? = null,
    val realMargins: List<ItemRealMargin> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableCashiers: List<String> = emptyList()
) : UiState
