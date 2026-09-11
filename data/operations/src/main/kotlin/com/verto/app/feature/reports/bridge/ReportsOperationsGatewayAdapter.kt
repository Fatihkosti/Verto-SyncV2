package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.BudgetEntity
import com.verto.app.data.local.entity.BudgetPeriodType
import com.verto.app.data.local.entity.BudgetType
import com.verto.app.data.local.entity.CashDenominationEntity
import com.verto.app.data.local.entity.CashReconciliationEntity
import com.verto.app.data.local.entity.ReconciliationStatus
import com.verto.app.data.repository.BudgetRepository
import com.verto.app.data.repository.CashReconciliationRepository
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.reports.application.ReportsOperationsGateway
import com.verto.app.feature.reports.domain.model.CloseReportShiftCommand
import com.verto.app.feature.reports.domain.model.DeleteReportBudgetCommand
import com.verto.app.feature.reports.domain.model.ReportBudget
import com.verto.app.feature.reports.domain.model.ReportBudgetPeriod
import com.verto.app.feature.reports.domain.model.ReportBudgetType
import com.verto.app.feature.reports.domain.model.ReportCashDenomination
import com.verto.app.feature.reports.domain.model.ReportCashSession
import com.verto.app.feature.reports.domain.model.ReportCashStatus
import com.verto.app.feature.reports.domain.model.SaveReportBudgetCommand
import com.verto.app.feature.reports.domain.model.StartReportShiftCommand
import com.verto.app.feature.reports.infrastructure.analytics.RfmCalculator
import com.verto.app.utils.MoneyMath
import javax.inject.Inject
import javax.inject.Singleton

/** App bridge: converts report-owned commands to current persistence models. */
@Singleton
class DefaultReportsOperationsGateway @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val cashReconciliationRepository: CashReconciliationRepository,
    private val partyDirectory: PartyDirectoryGateway,
    private val invoiceRepository: InvoiceRepository,
    private val rfmCalculator: RfmCalculator
) : ReportsOperationsGateway {

    override suspend fun saveBudget(command: SaveReportBudgetCommand) {
        budgetRepository.saveBudget(command.budget.toEntity())
    }

    override suspend fun deleteBudget(command: DeleteReportBudgetCommand) {
        val existing = budgetRepository.getById(command.budgetId) ?: return
        budgetRepository.deleteBudget(existing)
    }

    override suspend fun recalculateRfm() {
        val clients = partyDirectory.getAllClientsSync()
        val invoices = invoiceRepository.getAllInvoicesSync().filter { !it.voided }
        val items = invoiceRepository.getAllInvoiceItemsSync()
        rfmCalculator.recalculate(clients, invoices, items)
    }

    override suspend fun startShift(command: StartReportShiftCommand) {
        cashReconciliationRepository.startSession(
            CashReconciliationEntity(
                employeeId = command.employeeId,
                employeeName = command.employeeName,
                openingBalance = command.openingBalance,
                startedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun closeShift(command: CloseReportShiftCommand) {
        val updated = command.session.toEntity().copy(
            actualCountedBalance = command.actualCounted,
            expectedBalance = command.expectedBalance,
            variance = MoneyMath.subtract(command.actualCounted, command.expectedBalance),
            varianceReason = command.varianceReason,
            totalSales = command.totalSales
        )
        cashReconciliationRepository.closeSession(
            updated,
            command.denominations.map(ReportCashDenomination::toEntity)
        )
    }
}

internal fun ReportBudget.toEntity() = BudgetEntity(
    id = id,
    periodType = BudgetPeriodType.valueOf(periodType.name),
    periodStart = periodStart,
    periodEnd = periodEnd,
    budgetType = BudgetType.valueOf(budgetType.name),
    category = category,
    targetAmount = targetAmount,
    note = note,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun BudgetEntity.toReportModel() = ReportBudget(
    id = id,
    periodType = ReportBudgetPeriod.valueOf(periodType.name),
    periodStart = periodStart,
    periodEnd = periodEnd,
    budgetType = ReportBudgetType.valueOf(budgetType.name),
    category = category,
    targetAmount = targetAmount,
    note = note,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ReportCashSession.toEntity() = CashReconciliationEntity(
    id = id,
    employeeId = employeeId,
    employeeName = employeeName,
    openingBalance = openingBalance,
    totalSales = totalSales,
    totalRefunds = totalRefunds,
    totalCashIn = totalCashIn,
    totalCashOut = totalCashOut,
    expectedBalance = expectedBalance,
    actualCountedBalance = actualCountedBalance,
    variance = variance,
    varianceReason = varianceReason,
    status = ReconciliationStatus.valueOf(status.name),
    startedAt = startedAt,
    endedAt = endedAt,
    notes = notes
)

internal fun CashReconciliationEntity.toReportModel() = ReportCashSession(
    id = id,
    employeeId = employeeId,
    employeeName = employeeName,
    openingBalance = openingBalance,
    totalSales = totalSales,
    totalRefunds = totalRefunds,
    totalCashIn = totalCashIn,
    totalCashOut = totalCashOut,
    expectedBalance = expectedBalance,
    actualCountedBalance = actualCountedBalance,
    variance = variance,
    varianceReason = varianceReason,
    status = ReportCashStatus.valueOf(status.name),
    startedAt = startedAt,
    endedAt = endedAt,
    notes = notes
)

internal fun ReportCashDenomination.toEntity() = CashDenominationEntity(
    id = id,
    reconciliationId = reconciliationId,
    denominationValue = denominationValue,
    count = count,
    subtotal = subtotal,
    isCoin = isCoin
)
