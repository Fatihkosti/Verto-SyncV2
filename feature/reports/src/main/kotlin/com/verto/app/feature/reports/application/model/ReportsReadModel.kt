package com.verto.app.feature.reports.application.model

import com.verto.app.feature.reports.domain.model.ReportBudget
import com.verto.app.feature.reports.domain.model.ReportCashSession

import com.verto.app.utils.ReportPeriod

// ─────────────────────────────────────────────────────
// Data Models — Phase 1
// ─────────────────────────────────────────────────────

enum class NetProfitReliabilityIssue {
    SEGMENT_FILTER_WITH_UNALLOCATED_EXPENSES,
    UNKNOWN_HISTORICAL_COST,
    UNKNOWN_OR_MIXED_CURRENCY,
}

data class ProfitLossData(
    val grossSales: Double = 0.0,
    val returns: Double = 0.0,
    val netSales: Double = 0.0,
    val cogs: Double = 0.0,
    val shippingCosts: Double = 0.0,
    val grossProfit: Double = 0.0,
    val grossMargin: Float = 0f,
    val operatingExpenses: Double = 0.0,
    val expenseBreakdown: Map<String, Double> = emptyMap(),
    val commissions: Double = 0.0,
    val netProfit: Double = 0.0,
    val netMargin: Float = 0f,
    val functionalCurrencyCode: String = "",
    val isHistoricalCostComplete: Boolean = true,
    val excludedUnknownCostLines: Int = 0,
    val netProfitReliabilityIssues: Set<NetProfitReliabilityIssue> = emptySet(),
) {
    val isNetProfitReliable: Boolean
        get() = netProfitReliabilityIssues.isEmpty()
}

data class CashFlowData(
    val openingBalance: Double = 0.0,
    val totalIn: Double = 0.0,
    val cashSales: Double = 0.0,
    val debtCollections: Double = 0.0,
    val manualAdds: Double = 0.0,
    val totalOut: Double = 0.0,
    val cashPurchases: Double = 0.0,
    val supplierPayments: Double = 0.0,
    val expenses: Double = 0.0,
    val manualDeductions: Double = 0.0,
    val netFlow: Double = 0.0,
    val closingBalance: Double = 0.0,
    val currencyCode: String = ""
)

data class AgedReceivable(
    val clientId: String,
    val clientName: String,
    val bucket0_30: Double = 0.0,
    val bucket31_60: Double = 0.0,
    val bucket61_90: Double = 0.0,
    val bucketOver90: Double = 0.0,
    val total: Double = 0.0,
    val oldestDueDays: Int = 0
)

data class AgedReceivablesData(
    val total0_30: Double = 0.0,
    val total31_60: Double = 0.0,
    val total61_90: Double = 0.0,
    val totalOver90: Double = 0.0,
    val grandTotal: Double = 0.0,
    val dso: Float = 0f,
    val clients: List<AgedReceivable> = emptyList(),
    val currencyCode: String = ""
)

data class HeatmapCell(
    val dayOfWeek: Int,
    val hour: Int,
    val sales: Double,
    val invoiceCount: Int
)

data class TopItem(
    val name: String,
    val category: String,
    val qty: Int,
    val revenue: Double,
    val profit: Double,
    val margin: Float,
    val pct: Float,
    val revenueMinor: Long = 0L,
    val profitMinor: Long = 0L,
    val currencyCode: String = ""
)

data class CategorySummary(
    val category: String,
    val distinctItems: Int,
    val totalQty: Int,
    val revenue: Double,
    val profit: Double,
    val margin: Float,
    val pct: Float,
    val revenueMinor: Long = 0L,
    val profitMinor: Long = 0L,
    val currencyCode: String = ""
)

// ─────────────────────────────────────────────────────
// Data Models — Phase 2
// ─────────────────────────────────────────────────────

data class InventoryItemSummary(
    val id: String,
    val name: String,
    val quantity: Int,
    val minQuantity: Int,
    val buyPrice: Double,
    val lastMovementAt: Long = 0L,
    val turnoverCount: Int = 0
)

data class InventoryHealthData(
    val deadStockItems: List<InventoryItemSummary> = emptyList(),
    val fastMovers: List<InventoryItemSummary> = emptyList(),
    val stockoutRisk: List<InventoryItemSummary> = emptyList(),
    val totalValuation: Double = 0.0,
    val deadStockValue: Double = 0.0,
    val currencyCode: String = ""
)

data class ReturnedItem(
    val itemName: String,
    val returnCount: Int,
    val returnValue: Double
)

data class ReturnsData(
    val returnRate: Float = 0f,
    val totalReturnValue: Double = 0.0,
    val returnCount: Int = 0,
    val topReturnedItems: List<ReturnedItem> = emptyList()
)

data class EmployeeSummary(
    val name: String,
    val totalCollected: Double,
    val transactionCount: Int,
    val avgTransaction: Double
)

data class EmployeePerformanceData(
    val employees: List<EmployeeSummary> = emptyList()
)

data class SupplierSummary(
    val name: String,
    val totalPurchases: Double,
    val invoiceCount: Int,
    val currencyCode: String = ""
)

data class ShipmentsSummaryData(
    val totalShipments: Int = 0,
    val inTransit: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val totalCosts: Double = 0.0
)

data class ShrinkageData(
    val totalLoss: Double = 0.0,
    val adjustmentCount: Int = 0
)

data class ReconciliationCheck(
    val code: String,
    val label: String,
    val differenceMinor: Long = 0L,
    val affectedCount: Int = 0,
)

data class DataQualityIssue(
    val code: String,
    val label: String,
    val count: Int,
)

data class FinancialDiagnosticsData(
    val functionalCurrencyCode: String = "",
    val checks: List<ReconciliationCheck> = emptyList(),
    val dataQualityIssues: List<DataQualityIssue> = emptyList(),
    val inventoryOperationalValueMinor: Long = 0L,
    val outboxRequiresReviewCount: Int = 0,
) {
    val isHealthy: Boolean
        get() = checks.all { it.differenceMinor == 0L && it.affectedCount == 0 } &&
            dataQualityIssues.all { it.count == 0 } && outboxRequiresReviewCount == 0
}

data class InternationalSupplierStatementRow(
    val invoiceId: String,
    val invoiceNumber: Int,
    val supplierName: String,
    val transactionCurrencyCode: String,
    val originalAmountMinor: Long,
    val paidTransactionAmountMinor: Long,
    val remainingTransactionAmountMinor: Long,
    val functionalCurrencyCode: String,
    val functionalCashPaidMinor: Long,
    val realizedFxDifferenceMinor: Long,
    val invoiceExchangeRateSnapshot: String,
)

// ─────────────────────────────────────────────────────
// Application Read Model
// ─────────────────────────────────────────────────────

data class ReportsReadModel(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val from: Long = 0L,
    val to: Long = 0L,
    val functionalCurrencyCode: String = "",

    // Hero
    val netProfit: Double = 0.0,
    val netProfitChange: Float = 0f,
    val profitBreakdown: ProfitLossData = ProfitLossData(),

    // Smart Insights
    val insights: List<SmartInsight> = emptyList(),

    // Financial tab
    val pnl: ProfitLossData = ProfitLossData(),
    val cashFlow: CashFlowData = CashFlowData(),
    val agedReceivables: AgedReceivablesData = AgedReceivablesData(),
    val financialDiagnostics: FinancialDiagnosticsData = FinancialDiagnosticsData(),
    val internationalSupplierStatement: List<InternationalSupplierStatementRow> = emptyList(),
    // F255 — operational invoice analytics (currency-safe, read-only)
    val agedPayables: AgedPayablesData = AgedPayablesData(),
    val purchasePriceVariance: PurchasePriceVarianceData = PurchasePriceVarianceData(),
    val landedCostVariance: LandedCostVarianceData = LandedCostVarianceData(),
    val supplierFxVariance: SupplierFxVarianceData = SupplierFxVarianceData(),
    val supplierPaymentTiming: SupplierPaymentTimingData = SupplierPaymentTimingData(),
    val operationalAlerts: List<OperationalAnalyticsAlert> = emptyList(),
    val kpiDefinitions: List<KpiDefinition> = InvoiceAnalyticsKpiCatalog.definitions,
    val budgetTarget: Double = 0.0,
    val budgetProgress: Float = 0f,
    val budgetNote: String = "",
    val activeBudgets: List<ReportBudget> = emptyList(),

    // Sales tab
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
    val topItems: List<TopItem> = emptyList(),
    val categories: List<CategorySummary> = emptyList(),
    val heatmap: List<HeatmapCell> = emptyList(),

    // Operations tab — Phase 1
    val currentShiftSales: Double = 0.0,
    val currentShiftTransactions: Int = 0,
    val currentShiftAvgInvoice: Double = 0.0,
    val openSession: ReportCashSession? = null,
    val auditTodayCount: Int = 0,
    val lowStockCount: Int = 0,

    // Operations tab — Phase 2
    val inventoryHealth: InventoryHealthData = InventoryHealthData(),
    val returns: ReturnsData = ReturnsData(),
    val employeePerformance: EmployeePerformanceData = EmployeePerformanceData(),
    val shipmentsSummary: ShipmentsSummaryData = ShipmentsSummaryData(),
    val shrinkage: ShrinkageData = ShrinkageData(),
    val topSuppliers: List<SupplierSummary> = emptyList(),

    // Phase 3 — التحليلات المتقدمة
    val rfmSegments: List<RfmSegmentSummary> = emptyList(),
    val topClvClients: List<ClientClvData> = emptyList(),
    val atRiskClientsCount: Int = 0,
    val forecastResult: ForecastResult? = null,
    val realMargins: List<ItemRealMargin> = emptyList(),

    // Phase 4 — الفلاتر
    val availableCategories: List<String> = emptyList(),
    val availableCashiers: List<String> = emptyList()
)
