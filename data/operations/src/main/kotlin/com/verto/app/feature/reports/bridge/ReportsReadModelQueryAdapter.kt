package com.verto.app.feature.reports.bridge

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.dao.CashRegisterDao
import com.verto.app.data.local.dao.InvoiceReturnDao
import com.verto.app.data.local.dao.ReportsAnalyticsDao
import com.verto.app.data.local.entity.*
import com.verto.app.data.repository.*
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.reports.application.ReportsReadModelQuery
import com.verto.app.feature.reports.application.ReportsReadRequest
import com.verto.app.feature.reports.application.analytics.ClvCalculator
import com.verto.app.feature.reports.application.analytics.CostAllocationEngine
import com.verto.app.feature.reports.application.analytics.ForecastEngine
import com.verto.app.feature.reports.application.analytics.SmartInsightsEngine
import com.verto.app.feature.reports.application.model.*
import com.verto.app.feature.reports.application.port.ReportsLogisticsSource
import com.verto.app.feature.reports.application.port.ReportsShipmentCostSnapshot
import com.verto.app.feature.reports.application.port.ReportsShipmentSnapshot
import com.verto.app.money.Money
import com.verto.app.utils.ReportPeriod
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Report read adapter. F250 financial aggregates are calculated from persisted fixed-point snapshots.
 * Legacy Double values are produced only at the presentation boundary.
 */
@Singleton
class DefaultReportsReadModelQuery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val invoiceRepo: InvoiceRepository,
    private val partyDirectory: PartyDirectoryGateway,
    private val expRepo: ExpenseRepository,
    private val inventoryRepo: InventoryRepository,
    private val auditRepo: AuditLogRepository,
    private val logisticsSource: ReportsLogisticsSource,
    private val sessionReader: SessionReader,
    private val budgetRepo: BudgetRepository,
    private val cashReconciliationRepo: CashReconciliationRepository,
    private val rfmRepo: RfmRepository,
    private val cashRegisterDao: CashRegisterDao,
    private val invoiceReturnDao: InvoiceReturnDao,
    private val reportsAnalyticsDao: ReportsAnalyticsDao,


) : ReportsReadModelQuery {

    private val insightsEngine = SmartInsightsEngine()
    private val clvCalculator = ClvCalculator()
    private val forecastEngine = ForecastEngine()
    private val costEngine = CostAllocationEngine()

    override fun observe(request: ReportsReadRequest): Flow<ReportsReadModel> =
        buildReadModel(request.period, request.from, request.to, request.filters)

    private fun buildReadModel(
        period: ReportPeriod,
        from: Long,
        to: Long,
        filters: ReportsFilters = ReportsFilters(),
    ): Flow<ReportsReadModel> {
        val (prevFrom, prevTo) = previousComparisonRange(period, from, to)
        val forecastFrom = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000

        val current = combine(
            invoiceRepo.getSalesInvoicesByRange(from, to),
            invoiceRepo.getSalesItemsInRange(from, to),
            invoiceRepo.getPaymentsByRange(from, to),
            expRepo.getExpensesByRange(from, to),
        ) { invoices, items, payments, expenses -> Quadruple(invoices, items, payments, expenses) }

        val previousBase = combine(
            invoiceRepo.getSalesInvoicesByRange(prevFrom, prevTo),
            invoiceRepo.getSalesItemsInRange(prevFrom, prevTo),
            partyDirectory.getAllClients(),
            invoiceRepo.getCreditOwedInvoices(),
            expRepo.getExpensesByRange(prevFrom, prevTo),
        ) { invoices, items, clients, creditInvoices, expenses ->
            PrevContext(invoices, items, clients, creditInvoices, expenses, emptyList())
        }
        val previous = combine(
            previousBase,
            invoiceRepo.getPurchaseInvoicesByRange(prevFrom, prevTo),
        ) { base, purchases -> base.copy(purchases = purchases) }

        val ops = combine(
            budgetRepo.getCurrentBudgets(),
            cashReconciliationRepo.getCurrentOpenSession(),
            auditRepo.getRecent(200),
            inventoryRepo.getLowStockCount(),
        ) { budgets, session, audit, lowStock -> OpsContext(budgets, session, audit, lowStock) }

        val phase2 = combine(
            inventoryRepo.getAllItems(),
            inventoryRepo.getAllMovements(),
            invoiceRepo.getPurchaseInvoicesByRange(from, to),
            sessionReader.organizationId.flatMapLatest { logisticsSource.observeShipments(it.trim()) },
            sessionReader.organizationId.flatMapLatest { logisticsSource.observeCosts(it.trim()) },
        ) { items, movements, purchases, shipments, costs -> Phase2Context(items, movements, purchases, shipments, costs) }

        val phase3 = combine(
            cashRegisterDao.getMovementsByRange(from, to),
            invoiceRepo.getSalesInvoicesByRange(forecastFrom, System.currentTimeMillis()),
            rfmRepo.getAll(),
        ) { cash, forecast, rfm -> Phase3Context(cash, forecast, rfm) }

        val cashAndReturns = combine(
            cashRegisterDao.getRegister(),
            cashRegisterDao.getAllMovements(),
            combine(invoiceReturnDao.observeAllDocuments(), invoiceReturnDao.observeAllLines()) { documents, lines ->
                ReturnFacts(documents, lines)
            },
        ) { register, movements, returns -> CashAndReturns(register, movements, returns) }

        val diagnosticCore = combine(
            invoiceRepo.observeAllInvoices(),
            invoiceRepo.observeAllInvoiceItems(),
            invoiceRepo.observeAllPayments(),
            invoiceRepo.observeAllPaymentAllocations(),
            cashAndReturns,
        ) { invoices, items, payments, allocations, financialFacts ->
            DiagnosticCore(
                invoices = invoices,
                invoiceItems = items,
                payments = payments,
                allocations = allocations,
                cashRegister = financialFacts.register,
                cashMovements = financialFacts.movements,
                returnDocuments = financialFacts.returns.documents,
                returnLines = financialFacts.returns.lines,
            )
        }

        val diagnosticSync = sessionReader.organizationId.flatMapLatest { rawOrg ->
            val organizationId = rawOrg.trim()
            combine(
                invoiceRepo.observeFinancialOutbox(organizationId),
                invoiceRepo.observeInvoiceWriteGuards(organizationId),
            ) { outbox, guards -> DiagnosticSync(organizationId, outbox, guards) }
        }

        val diagnostics = combine(diagnosticCore, diagnosticSync) { core, sync -> DiagnosticContext(core, sync) }
        val phase3AndDiagnostics = combine(phase3, diagnostics) { p3, diagnostic -> Phase3AndDiagnostics(p3, diagnostic) }

        val purchaseAnalytics = sessionReader.organizationId.flatMapLatest { rawOrg ->
            val organizationId = rawOrg.trim()
            combine(
                reportsAnalyticsDao.observePurchaseOrders(organizationId),
                reportsAnalyticsDao.observePurchaseInvoiceMatches(organizationId),
                reportsAnalyticsDao.observePurchaseInvoiceMatchLines(organizationId),
                reportsAnalyticsDao.observePurchaseReceiptAllocations(organizationId),
            ) { orders, matches, lines, allocations ->
                PurchaseAnalyticsContext(orders, matches, lines, allocations)
            }
        }
        val logisticsAnalytics = sessionReader.organizationId.flatMapLatest { rawOrg ->
            val organizationId = rawOrg.trim()
            combine(
                reportsAnalyticsDao.observeShipmentLines(organizationId),
                logisticsSource.observeCosts(organizationId),
                reportsAnalyticsDao.observeLandedCostAllocations(organizationId),
                reportsAnalyticsDao.observeReceivingLines(organizationId),
                reportsAnalyticsDao.observeLateCostAllocations(organizationId),
            ) { lines, costs, allocations, receiving, late ->
                LogisticsAnalyticsContext(lines, costs, allocations, receiving, late)
            }
        }
        val analytics = combine(purchaseAnalytics, logisticsAnalytics) { purchase, logistics ->
            AnalyticsContext(purchase, logistics)
        }
        val phase3DiagnosticsAnalytics = combine(phase3AndDiagnostics, analytics) { p3d, analyticsContext ->
            Phase3DiagnosticsAnalytics(p3d.phase3, p3d.diagnostics, analyticsContext)
        }

        return combine(current, previous, ops, phase2, phase3DiagnosticsAnalytics) { curr, prev, op, p2, p3da ->
            buildReadModel(period, from, to, filters, curr, prev, op, p2, p3da.phase3, p3da.diagnostics, p3da.analytics)
        }
    }

    private fun buildReadModel(
        period: ReportPeriod,
        from: Long,
        to: Long,
        filters: ReportsFilters,
        curr: Quadruple<List<InvoiceEntity>, List<InvoiceItemEntity>, List<PaymentEntity>, List<ExpenseEntity>>,
        prev: PrevContext,
        ops: OpsContext,
        p2: Phase2Context,
        p3: Phase3Context,
        diagnosticsContext: DiagnosticContext,
        analytics: AnalyticsContext,
    ): ReportsReadModel {
        val (rawInvoices, rawItems, rawPayments, expenses) = curr
        val diagnostic = diagnosticsContext.core
        val organizationId = diagnosticsContext.sync.organizationId
        val scopedDiagnosticInvoices = if (organizationId.isBlank()) diagnostic.invoices else diagnostic.invoices.filter {
            it.organizationId.isBlank() || it.organizationId == organizationId
        }
        val functionalCurrency = selectFunctionalCurrency(scopedDiagnosticInvoices)

        val salesInvoices = scopeSalesInvoices(rawInvoices, diagnostic.payments, filters)
        val filteredInvoiceIds = salesInvoices.mapTo(mutableSetOf()) { it.id }
        val eligibleSalesItems = rawItems.filter { it.invoiceId in filteredInvoiceIds }
        val invoicesById = salesInvoices.associateBy { it.id }
        val recognizedRevenueByLine = allocateRecognizedRevenueByLine(
            eligibleSalesItems, invoicesById, functionalCurrency,
        )
        val salesItems = filters.category?.let { category ->
            eligibleSalesItems.filter { matchesReportCategory(it.itemCategory, category) }
        } ?: eligibleSalesItems
        val payments = filters.cashierName?.let { cashier ->
            rawPayments.filter { it.employeeName.trim() == cashier.trim() }
        } ?: rawPayments

        // P&L uses the same filter scope for revenue and directly attributable costs.
        // Category revenue is allocated from the immutable invoice recognition snapshot, so invoice discounts/FX reconcile.
        val grossSalesMinor = if (filters.category == null) {
            sumMinor(salesInvoices.mapNotNull { it.functionalMinorOrNull(functionalCurrency) })
        } else {
            sumMinor(salesItems.map { recognizedRevenueByLine[it.id] ?: 0L })
        }
        val selectedCategory = filters.category
        val allInvoiceItemsById = diagnostic.invoiceItems.associateBy { it.id }
        val returnEligibleInvoiceIds = scopeSalesInvoices(
            scopedDiagnosticInvoices.filter { it.category == InvoiceCategory.SALE }, diagnostic.payments, filters,
        ).mapTo(mutableSetOf()) { it.id }

        fun scopedReturnLines(rangeFrom: Long, rangeTo: Long): Pair<List<InvoiceReturnDocumentEntity>, List<InvoiceReturnLineEntity>> {
            val documents = diagnostic.returnDocuments.filter { document ->
                (organizationId.isBlank() || document.organizationId == organizationId) &&
                    document.documentType == "SALES_RETURN_CREDIT_NOTE" &&
                    document.occurredAt in rangeFrom..rangeTo &&
                    document.functionalCurrencyCode.equals(functionalCurrency, ignoreCase = true) &&
                    document.originalInvoiceId in returnEligibleInvoiceIds
            }
            val ids = documents.mapTo(mutableSetOf()) { it.id }
            val lines = diagnostic.returnLines.filter { line ->
                line.returnId in ids && (selectedCategory == null ||
                    allInvoiceItemsById[line.originalInvoiceItemId]?.let {
                        matchesReportCategory(it.itemCategory, selectedCategory)
                    } == true)
            }
            return documents to lines
        }

        val (_, salesReturnLines) = scopedReturnLines(from, to)
        val returnsMinor = sumMinor(salesReturnLines.map { it.functionalAmountMinor })
        val returnCogsMinor = sumMinor(salesReturnLines.map { it.historicalCostAmountMinor })
        val netSalesMinor = Math.subtractExact(grossSalesMinor, returnsMinor)
        val knownCostLines = salesItems.filter { it.costSnapshotStatus != "LEGACY_UNKNOWN" }
        val segmentFilterActive = filters.category != null || filters.paymentMethod != null || filters.cashierName != null
        val salesCurrencyComplete = salesInvoices.all { it.functionalMinorOrNull(functionalCurrency) != null }
        val currentReturnCurrencyComplete = diagnostic.returnDocuments.asSequence()
            .filter { document ->
                (organizationId.isBlank() || document.organizationId == organizationId) &&
                    document.documentType == "SALES_RETURN_CREDIT_NOTE" &&
                    document.occurredAt in from..to &&
                    document.originalInvoiceId in returnEligibleInvoiceIds
            }
            .all { it.functionalCurrencyCode.equals(functionalCurrency, ignoreCase = true) }
        val currentReliabilityIssues = reportNetProfitReliabilityIssues(
            segmentFilterActive = segmentFilterActive,
            historicalCostComplete = knownCostLines.size == salesItems.size,
            salesCurrencyComplete = salesCurrencyComplete,
            returnsCurrencyComplete = currentReturnCurrencyComplete,
        )
        val grossCogsMinor = sumMinor(knownCostLines.map { it.lineCostSnapshotMinor })
        val cogsMinor = Math.subtractExact(grossCogsMinor, returnCogsMinor)
        val selectedRevenueByInvoice = salesItems.groupBy { it.invoiceId }.mapValues { (_, rows) ->
            sumMinor(rows.map { recognizedRevenueByLine[it.id] ?: 0L })
        }
        val commissionsMinor = sumMinor(salesInvoices.map { invoice ->
            val fullCommission = functionalCommissionMinor(invoice, functionalCurrency)
            if (filters.category == null || fullCommission == 0L) fullCommission else {
                val recognized = invoice.functionalMinorOrNull(functionalCurrency) ?: 0L
                val selected = selectedRevenueByInvoice[invoice.id] ?: 0L
                if (recognized > 0L && selected > 0L) scaleMinor(fullCommission, selected, recognized) else 0L
            }
        })
        val expenseMinorRows = expenses.map {
            Money.fromLegacyDouble(it.amount, functionalCurrency.ifBlank { Money.TRANSACTION_CURRENCY }).amountMinor
        }
        val expensesMinor = sumMinor(expenseMinorRows)
        val expenseBreakdown = expenses.groupBy { it.category.ifBlank { "أخرى" } }.mapValues { (_, rows) ->
            val amount = sumMinor(rows.map {
                Money.fromLegacyDouble(it.amount, functionalCurrency.ifBlank { Money.TRANSACTION_CURRENCY }).amountMinor
            })
            major(amount, functionalCurrency)
        }
        val grossProfitMinor = Math.subtractExact(netSalesMinor, cogsMinor)
        val netProfitMinor = Math.subtractExact(Math.subtractExact(grossProfitMinor, expensesMinor), commissionsMinor)
        val pnl = ProfitLossData(
            grossSales = major(grossSalesMinor, functionalCurrency),
            returns = major(returnsMinor, functionalCurrency),
            netSales = major(netSalesMinor, functionalCurrency),
            cogs = major(cogsMinor, functionalCurrency),
            shippingCosts = 0.0,
            grossProfit = major(grossProfitMinor, functionalCurrency),
            grossMargin = percent(grossProfitMinor, netSalesMinor),
            operatingExpenses = major(expensesMinor, functionalCurrency),
            expenseBreakdown = expenseBreakdown,
            commissions = major(commissionsMinor, functionalCurrency),
            netProfit = major(netProfitMinor, functionalCurrency),
            netMargin = percent(netProfitMinor, netSalesMinor),
            functionalCurrencyCode = functionalCurrency,
            isHistoricalCostComplete = knownCostLines.size == salesItems.size,
            excludedUnknownCostLines = salesItems.size - knownCostLines.size,
            netProfitReliabilityIssues = currentReliabilityIssues,
        )

        // Previous-period comparison must use the exact same accounting recipe and filters.
        val (prevFrom, prevTo) = previousComparisonRange(period, from, to)
        val prevSalesInvoices = scopeSalesInvoices(prev.invoices, diagnostic.payments, filters)
        val prevInvoiceIds = prevSalesInvoices.mapTo(mutableSetOf()) { it.id }
        val prevEligibleItems = prev.items.filter { it.invoiceId in prevInvoiceIds }
        val prevInvoicesById = prevSalesInvoices.associateBy { it.id }
        val prevRecognizedRevenueByLine = allocateRecognizedRevenueByLine(
            prevEligibleItems, prevInvoicesById, functionalCurrency,
        )
        val prevSalesItems = filters.category?.let { category ->
            prevEligibleItems.filter { matchesReportCategory(it.itemCategory, category) }
        } ?: prevEligibleItems
        val prevSalesMinor = if (filters.category == null) {
            sumMinor(prevSalesInvoices.mapNotNull { it.functionalMinorOrNull(functionalCurrency) })
        } else {
            sumMinor(prevSalesItems.map { prevRecognizedRevenueByLine[it.id] ?: 0L })
        }
        val (_, prevReturnLines) = scopedReturnLines(prevFrom, prevTo)
        val prevReturnsMinor = sumMinor(prevReturnLines.map { it.functionalAmountMinor })
        val prevReturnCogsMinor = sumMinor(prevReturnLines.map { it.historicalCostAmountMinor })
        val prevKnownCostLines = prevSalesItems.filter { it.costSnapshotStatus != "LEGACY_UNKNOWN" }
        val prevSalesCurrencyComplete = prevSalesInvoices.all { it.functionalMinorOrNull(functionalCurrency) != null }
        val prevReturnCurrencyComplete = diagnostic.returnDocuments.asSequence()
            .filter { document ->
                (organizationId.isBlank() || document.organizationId == organizationId) &&
                    document.documentType == "SALES_RETURN_CREDIT_NOTE" &&
                    document.occurredAt in prevFrom..prevTo &&
                    document.originalInvoiceId in returnEligibleInvoiceIds
            }
            .all { it.functionalCurrencyCode.equals(functionalCurrency, ignoreCase = true) }
        val previousProfitReliable = !segmentFilterActive &&
            prevKnownCostLines.size == prevSalesItems.size && prevSalesCurrencyComplete && prevReturnCurrencyComplete
        val prevGrossCogsMinor = sumMinor(prevKnownCostLines.map { it.lineCostSnapshotMinor })
        val prevCogsMinor = Math.subtractExact(prevGrossCogsMinor, prevReturnCogsMinor)
        val prevSelectedRevenueByInvoice = prevSalesItems.groupBy { it.invoiceId }.mapValues { (_, rows) ->
            sumMinor(rows.map { prevRecognizedRevenueByLine[it.id] ?: 0L })
        }
        val prevCommissionsMinor = sumMinor(prevSalesInvoices.map { invoice ->
            val fullCommission = functionalCommissionMinor(invoice, functionalCurrency)
            if (filters.category == null || fullCommission == 0L) fullCommission else {
                val recognized = invoice.functionalMinorOrNull(functionalCurrency) ?: 0L
                val selected = prevSelectedRevenueByInvoice[invoice.id] ?: 0L
                if (recognized > 0L && selected > 0L) scaleMinor(fullCommission, selected, recognized) else 0L
            }
        })
        val prevExpensesMinor = sumMinor(prev.expenses.map {
            Money.fromLegacyDouble(it.amount, functionalCurrency.ifBlank { Money.TRANSACTION_CURRENCY }).amountMinor
        })
        val prevNetSalesMinor = Math.subtractExact(prevSalesMinor, prevReturnsMinor)
        val prevGrossProfitMinor = Math.subtractExact(prevNetSalesMinor, prevCogsMinor)
        val prevProfitMinor = Math.subtractExact(
            Math.subtractExact(prevGrossProfitMinor, prevExpensesMinor), prevCommissionsMinor,
        )
        val salesChange = if (salesCurrencyComplete && prevSalesCurrencyComplete) {
            calcChangeMinor(grossSalesMinor, prevSalesMinor)
        } else 0f
        val netSalesReliable = salesCurrencyComplete && currentReturnCurrencyComplete
        val previousNetSalesReliable = prevSalesCurrencyComplete && prevReturnCurrencyComplete
        val netSalesComparisonAvailable = netSalesReliable && previousNetSalesReliable && prevNetSalesMinor != 0L
        val netSalesChange = if (netSalesComparisonAvailable) {
            calcChangeMinor(netSalesMinor, prevNetSalesMinor)
        } else 0f
        val profitChange = if (currentReliabilityIssues.isEmpty() && previousProfitReliable) {
            calcChangeMinor(netProfitMinor, prevProfitMinor)
        } else 0f

        // Session 365: purchase summary is the posted purchase invoice value in the selected period.
        // It is intentionally not mixed with supplier payables; comparison uses the same previous range.
        val currentPurchaseAmounts = p2.purchases.map { it.functionalMinorOrNull(functionalCurrency) }
        val previousPurchaseAmounts = prev.purchases.map { it.functionalMinorOrNull(functionalCurrency) }
        val purchasesReliable = currentPurchaseAmounts.all { it != null }
        val previousPurchasesReliable = previousPurchaseAmounts.all { it != null }
        val purchasesTotalMinor = if (purchasesReliable) sumMinor(currentPurchaseAmounts.filterNotNull()) else 0L
        val previousPurchasesTotalMinor = if (previousPurchasesReliable) sumMinor(previousPurchaseAmounts.filterNotNull()) else 0L
        val purchasesComparisonAvailable = purchasesReliable && previousPurchasesReliable && previousPurchasesTotalMinor != 0L
        val purchasesChange = if (purchasesComparisonAvailable) {
            calcChangeMinor(purchasesTotalMinor, previousPurchasesTotalMinor)
        } else 0f

        val periodDays = periodDaysInclusive(from, to)
        val periodCreditSalesMinor = netCreditSalesMinor(
            rawInvoices, diagnostic.returnDocuments, functionalCurrency, from, to,
        )
        val periodCreditPurchasesMinor = netCreditPurchasesMinor(
            p2.purchases, diagnostic.returnDocuments, functionalCurrency, from, to,
        )
        val agedReceivables = buildAgedReceivables(
            prev.clients, prev.allCreditInvoices, diagnostic.allocations, diagnostic.returnDocuments, functionalCurrency,
            window = AgingKpiWindow(periodCreditSalesMinor, periodDays, to),
        )
        val agedPayables = buildAgedPayables(
            suppliers = prev.clients,
            creditPurchases = scopedDiagnosticInvoices,
            allocations = diagnostic.allocations,
            returnDocuments = diagnostic.returnDocuments,
            functionalCurrencyCode = functionalCurrency,
            window = AgingKpiWindow(periodCreditPurchasesMinor, periodDays, to),
        )
        val purchasePriceVariance = buildPurchasePriceVariance(
            analytics.purchase.orders, analytics.purchase.matches, analytics.purchase.matchLines,
            scopedDiagnosticInvoices, diagnostic.invoiceItems, from..to,
        )
        val landedCostVariance = buildLandedCostVariance(
            p2.shipments, analytics.logistics.costs, analytics.logistics.lines, analytics.logistics.receivingLines,
            analytics.logistics.costAllocations, analytics.logistics.lateCostAllocations,
        )
        val supplierFxVariance = buildSupplierFxVariance(
            scopedDiagnosticInvoices, prev.clients, diagnostic.allocations, from, to,
        )
        val supplierPaymentTiming = buildSupplierPaymentTiming(
            scopedDiagnosticInvoices, prev.clients, diagnostic.allocations, diagnostic.returnDocuments, from, to,
        )
        val operationalAlerts = buildOperationalAnalyticsAlerts(
            facts = OperationalAlertFacts(
                invoices = scopedDiagnosticInvoices, clients = prev.clients, allocations = diagnostic.allocations,
                returnDocuments = diagnostic.returnDocuments,
                receiptMatching = ReceiptMatchAlertFacts(
                    analytics.purchase.matches, analytics.purchase.matchLines, analytics.purchase.receiptAllocations,
                ),
            ),
            ppv = purchasePriceVariance, fx = supplierFxVariance, outbox = diagnosticsContext.sync.outbox,
            resolveString = context::getString,
        )
        val heatmapRevenueByInvoice = if (filters.category == null) emptyMap() else selectedRevenueByInvoice
        val heatmap = buildHeatmap(salesInvoices, functionalCurrency, heatmapRevenueByInvoice)
        val topItems = buildTopItems(
            salesItems, invoicesById, grossSalesMinor, functionalCurrency, recognizedRevenueByLine,
        )
        val categories = buildCategories(
            salesItems, invoicesById, grossSalesMinor, functionalCurrency, recognizedRevenueByLine,
        )

        val currentBudget = when {
            filters.paymentMethod != null || filters.cashierName != null -> null
            filters.category != null -> ops.budgets.firstOrNull {
                it.budgetType == BudgetType.CATEGORY_TARGET && it.category == filters.category
            }
            else -> ops.budgets.firstOrNull { it.budgetType == BudgetType.SALES_TARGET }
        }
        val budgetTargetMinor = currentBudget?.targetAmount?.let {
            Money.fromLegacyDouble(it, functionalCurrency.ifBlank { Money.TRANSACTION_CURRENCY }).amountMinor
        } ?: 0L
        val budgetTarget = major(budgetTargetMinor, functionalCurrency)
        val budgetProgress = if (budgetTargetMinor > 0L) (percent(grossSalesMinor, budgetTargetMinor) / 100f).coerceIn(0f, 1f) else 0f

        val cashFlow = buildCashFlow(p3.cashMovements, functionalCurrency)
        val inventoryHealth = buildInventoryHealth(p2.items, p2.movements, functionalCurrency)
        val returnsData = buildReturnsData(
            salesReturnLines, salesReturnLines.map { it.returnId }.distinct().size, grossSalesMinor, functionalCurrency,
        )
        val employeePerformance = buildEmployeePerformance(payments, functionalCurrency)
        val topSuppliers = buildTopSuppliers(p2.purchases, prev.clients, functionalCurrency)
        val shipmentsSummary = buildShipmentsSummary(p2.shipments, p2.shipmentCosts)
        val shrinkage = buildShrinkage(p2.movements, p2.items, from, to, functionalCurrency)

        val reportDiagnostics = buildFinancialDiagnostics(
            organizationId = diagnosticsContext.sync.organizationId,
            invoices = diagnostic.invoices,
            invoiceItems = diagnostic.invoiceItems,
            payments = diagnostic.payments,
            allocations = diagnostic.allocations,
            cashRegister = diagnostic.cashRegister,
            cashMovements = diagnostic.cashMovements,
            inventoryItems = p2.items,
            inventoryMovements = p2.movements,
            outbox = diagnosticsContext.sync.outbox,
            writeGuards = diagnosticsContext.sync.writeGuards,
        )
        val supplierStatement = buildInternationalSupplierStatement(
            p2.purchases, prev.clients, diagnostic.payments, diagnostic.allocations, diagnostic.returnDocuments,
        )

        val forecastResult = forecastEngine.compute(
            p3.forecastSales.filter { it.category == InvoiceCategory.SALE }
                .mapNotNull { invoice -> invoice.functionalMinorOrNull(functionalCurrency)?.let { minor ->
                    ForecastInvoice(invoice.createdAt, major(minor, functionalCurrency))
                } }
        )

        val currentItemCost = p2.items.associateBy({ it.id }, { it.buyPriceMinor })
        val realMargins = costEngine.computeRealMargins(
            salesItems.mapNotNull { line ->
                val revenue = recognizedRevenueByLine[line.id]
                    ?: line.functionalRevenueMinor(invoicesById[line.invoiceId])
                    ?: return@mapNotNull null
                ReportInvoiceLine(
                    inventoryItemId = line.inventoryItemId,
                    itemName = line.itemName,
                    revenueMinor = revenue,
                    costAtSaleMinor = line.lineCostSnapshotMinor,
                    currentReplacementUnitCostMinor = currentItemCost[line.inventoryItemId] ?: line.unitCostAtSaleMinor,
                    quantity = line.quantity,
                    costSnapshotKnown = line.costSnapshotStatus != "LEGACY_UNKNOWN",
                    currencyCode = functionalCurrency,
                )
            }
        )

        val salesTransactionCurrencies = rawInvoices.asSequence()
            .filter { it.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN }
            .map { it.transactionCurrencyCode.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().toList()
        val rfmMonetarySafe = salesTransactionCurrencies.size <= 1
        val rfmSegments = if (rfmMonetarySafe) {
            p3.rfmCache.groupBy { it.segment }.map { (segment, rows) ->
                val spentMinor = sumMinor(rows.map { Money.fromLegacyDouble(it.totalSpent).amountMinor })
                RfmSegmentSummary(ReportRfmSegment.valueOf(segment.name), rows.size, major(spentMinor, functionalCurrency))
            }.sortedByDescending { it.count }
        } else emptyList()
        val clientsMap = prev.clients.associateBy { it.id }
        val topClvClients = if (rfmMonetarySafe) {
            clvCalculator.topByClv(p3.rfmCache.map { it.toReportModel() }, 5).map { (rfm, clv) ->
                ClientClvData(rfm, clientsMap[rfm.clientId]?.name ?: "", clv)
            }
        } else emptyList()
        val atRiskCount = p3.rfmCache.count { it.segment == RfmSegment.AT_RISK || it.segment == RfmSegment.CANT_LOSE }

        val days = periodDaysInclusive(from, to).toLong().coerceAtLeast(1L)
        val previousDays = periodDaysInclusive(prevFrom, prevTo).toLong().coerceAtLeast(1L)
        val dailyMinor = BigDecimal.valueOf(grossSalesMinor).divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP).longValueExact()
        val prevDailyMinor = BigDecimal.valueOf(prevSalesMinor).divide(BigDecimal.valueOf(previousDays), 0, RoundingMode.HALF_UP).longValueExact()
        val topItemName = topItems.firstOrNull()?.name.orEmpty()
        val previousTopItemMinor = if (topItemName.isBlank()) 0L else sumMinor(
            prevSalesItems.filter { it.itemName == topItemName }.map { prevRecognizedRevenueByLine[it.id] ?: 0L }
        )
        val topItemGrowthPct = topItems.firstOrNull()?.let {
            calcChangeMinor(it.revenueMinor, previousTopItemMinor).toDouble()
        } ?: 0.0
        val totalCreditMinor = sumMinor(prev.allCreditInvoices.mapNotNull { it.functionalMinorOrNull(functionalCurrency) })
        val insights = insightsEngine.generate(
            InsightInput(
                todaySales = major(if (period == ReportPeriod.TODAY) grossSalesMinor else dailyMinor, functionalCurrency),
                avgLast30DaySales = major(prevDailyMinor, functionalCurrency),
                periodSales = major(grossSalesMinor, functionalCurrency),
                // Shared operating expenses are not attributable to category/cashier/payment segments.
                expensesTotal = if (segmentFilterActive) 0.0 else major(expensesMinor, functionalCurrency),
                totalCreditAmount = major(totalCreditMinor, functionalCurrency),
                overdueAmount = agedReceivables.grandTotal,
                deadStockValue = inventoryHealth.deadStockValue,
                topItemName = topItemName,
                topItemGrowthPct = topItemGrowthPct,
                budgetTarget = budgetTarget,
                atRiskClientsCount = atRiskCount,
                // Forecast is organization-wide; never compare it with a segmented sales denominator.
                forecastMonthTotal = if (segmentFilterActive) 0.0 else forecastResult.monthTotal,
            )
        )

        val openSession = ops.openSession
        val shiftPayments = diagnostic.payments.filter { openSession != null && it.paidAt >= openSession.startedAt }
        val shiftSalesMinor = sumMinor(shiftPayments.map { it.functionalCashAmountMinor })
        val shiftCount = shiftPayments.size
        val shiftAvgMinor = if (shiftCount > 0) BigDecimal.valueOf(shiftSalesMinor)
            .divide(BigDecimal.valueOf(shiftCount.toLong()), 0, RoundingMode.HALF_UP).longValueExact() else 0L

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val auditTodayCount = ops.auditLogs.count { it.createdAt >= todayStart }
        val availableCategories = eligibleSalesItems.map { it.itemCategory.ifBlank { "غير مصنف" } }
            .distinct().sorted()
        val availableCashiers = rawPayments.map { it.employeeName.trim() }
            .filter { it.isNotEmpty() }.distinct().sorted()

        return ReportsReadModel(
            period = period, from = from, to = to, functionalCurrencyCode = functionalCurrency,
            netProfit = major(netProfitMinor, functionalCurrency), netProfitChange = profitChange, profitBreakdown = pnl,
            insights = insights, pnl = pnl, cashFlow = cashFlow, agedReceivables = agedReceivables,
            financialDiagnostics = reportDiagnostics, internationalSupplierStatement = supplierStatement,
            agedPayables = agedPayables, purchasePriceVariance = purchasePriceVariance,
            landedCostVariance = landedCostVariance, supplierFxVariance = supplierFxVariance,
            supplierPaymentTiming = supplierPaymentTiming, operationalAlerts = operationalAlerts,
            budgetTarget = budgetTarget, budgetProgress = budgetProgress, activeBudgets = ops.budgets.map { it.toReportModel() },
            salesTotal = major(grossSalesMinor, functionalCurrency), salesChange = salesChange,
            netSalesTotal = major(netSalesMinor, functionalCurrency), netSalesChange = netSalesChange,
            netSalesReliable = netSalesReliable, netSalesComparisonAvailable = netSalesComparisonAvailable,
            purchasesTotal = major(purchasesTotalMinor, functionalCurrency), purchasesChange = purchasesChange,
            purchasesReliable = purchasesReliable, purchasesComparisonAvailable = purchasesComparisonAvailable,
            topItems = topItems, categories = categories, heatmap = heatmap,
            currentShiftSales = major(shiftSalesMinor, functionalCurrency), currentShiftTransactions = shiftCount,
            currentShiftAvgInvoice = major(shiftAvgMinor, functionalCurrency), openSession = openSession?.toReportModel(),
            auditTodayCount = auditTodayCount, lowStockCount = ops.lowStockCount,
            inventoryHealth = inventoryHealth, returns = returnsData, employeePerformance = employeePerformance,
            shipmentsSummary = shipmentsSummary, shrinkage = shrinkage, topSuppliers = topSuppliers,
            rfmSegments = rfmSegments, topClvClients = topClvClients, atRiskClientsCount = atRiskCount,
            forecastResult = forecastResult, realMargins = realMargins,
            availableCategories = availableCategories, availableCashiers = availableCashiers,
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class PrevContext(
    val invoices: List<InvoiceEntity>, val items: List<InvoiceItemEntity>, val clients: List<PartyClient>,
    val allCreditInvoices: List<InvoiceEntity>, val expenses: List<ExpenseEntity>,
    val purchases: List<InvoiceEntity>,
)
private data class OpsContext(
    val budgets: List<BudgetEntity>, val openSession: CashReconciliationEntity?, val auditLogs: List<AuditLogEntity>, val lowStockCount: Int,
)
private data class Phase2Context(
    val items: List<InventoryItemEntity>, val movements: List<InventoryMovementEntity>, val purchases: List<InvoiceEntity>,
    val shipments: List<ReportsShipmentSnapshot>, val shipmentCosts: List<ReportsShipmentCostSnapshot>,
)
private data class Phase3Context(
    val cashMovements: List<CashRegisterMovementEntity>, val forecastSales: List<InvoiceEntity>, val rfmCache: List<RfmCacheEntity>,
)
private data class ReturnFacts(
    val documents: List<InvoiceReturnDocumentEntity>,
    val lines: List<InvoiceReturnLineEntity>,
)
private data class CashAndReturns(
    val register: CashRegisterEntity?,
    val movements: List<CashRegisterMovementEntity>,
    val returns: ReturnFacts,
)
private data class DiagnosticCore(
    val invoices: List<InvoiceEntity>, val invoiceItems: List<InvoiceItemEntity>, val payments: List<PaymentEntity>,
    val allocations: List<PaymentAllocationEntity>, val cashRegister: CashRegisterEntity?, val cashMovements: List<CashRegisterMovementEntity>,
    val returnDocuments: List<InvoiceReturnDocumentEntity>, val returnLines: List<InvoiceReturnLineEntity>,
)
private data class DiagnosticSync(
    val organizationId: String, val outbox: List<FinancialOutboxEntity>, val writeGuards: List<InvoiceWriteGuardEntity>,
)
private data class DiagnosticContext(val core: DiagnosticCore, val sync: DiagnosticSync)
private data class Phase3AndDiagnostics(val phase3: Phase3Context, val diagnostics: DiagnosticContext)
private data class PurchaseAnalyticsContext(
    val orders: List<PurchaseOrderEntity>, val matches: List<PurchaseInvoiceMatchEntity>,
    val matchLines: List<PurchaseInvoiceMatchLineEntity>, val receiptAllocations: List<PurchaseInvoiceReceiptAllocationEntity>,
)
private data class LogisticsAnalyticsContext(
    val lines: List<LogisticsShipmentLineEntity>, val costs: List<ReportsShipmentCostSnapshot>,
    val costAllocations: List<LogisticsCostAllocationEntity>, val receivingLines: List<LogisticsReceivingLineEntity>,
    val lateCostAllocations: List<LogisticsLateCostAllocationEntity>,
)
private data class AnalyticsContext(val purchase: PurchaseAnalyticsContext, val logistics: LogisticsAnalyticsContext)
private data class Phase3DiagnosticsAnalytics(
    val phase3: Phase3Context, val diagnostics: DiagnosticContext, val analytics: AnalyticsContext,
)
