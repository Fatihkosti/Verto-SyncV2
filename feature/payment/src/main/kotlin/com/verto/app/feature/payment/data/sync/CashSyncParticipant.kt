package com.verto.app.feature.payment.data.sync

import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.pullBudgets
import com.verto.app.data.sync.pullCashDenominations
import com.verto.app.data.sync.pullCashMovements
import com.verto.app.data.sync.pullCashReconciliations
import com.verto.app.data.sync.pullCashRegister
import com.verto.app.data.sync.pullCommissions
import com.verto.app.data.sync.pullExpenses
import com.verto.app.data.sync.pushBudgetDeletions
import com.verto.app.data.sync.pushBudgets
import com.verto.app.data.sync.pushCashDenominations
import com.verto.app.data.sync.pushCashMovements
import com.verto.app.data.sync.pushCashReconciliations
import com.verto.app.data.sync.pushCashRegister
import com.verto.app.data.sync.pushExpenseDeletions
import com.verto.app.data.sync.pushExpenses
import com.verto.app.data.sync.pushReconciliationDeletions

class CashSyncParticipant(
    private val runtime: SyncRuntime
) : SyncParticipant {
    override val key: String = "cash"

    override fun operations(context: SyncRunContext): List<SyncOperation> = listOf(
        SyncOperation(SyncOperationSlot.PUSH_EXPENSES, "push المصروفات", SyncFailureMode.ABORT, execute = {
            runtime.pushExpenses(context.organizationId, context.userId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_BUDGETS, "push الميزانيات", SyncFailureMode.COLLECT, execute = {
            runtime.pushBudgetDeletions(context.organizationId)
            runtime.pushBudgets(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_CASH_RECONCILIATION, "push جرد الصندوق", SyncFailureMode.COLLECT, execute = {
            runtime.pushReconciliationDeletions(context.organizationId)
            runtime.pushCashReconciliations(context.organizationId)
            runtime.pushCashDenominations(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_CASH_REGISTER, "push الصندوق", SyncFailureMode.COLLECT, execute = {
            runtime.pushCashRegister(context.organizationId)
            runtime.pushCashMovements(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.DELETE_EXPENSES, "حذف المصروفات", SyncFailureMode.COLLECT, execute = {
            runtime.pushExpenseDeletions(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_EXPENSES, "pull المصروفات", SyncFailureMode.ABORT, execute = {
            runtime.pullExpenses(context.organizationId, context.deletions.expenseIds)
        }),
        SyncOperation(SyncOperationSlot.PULL_BUDGETS, "pull الميزانيات", SyncFailureMode.COLLECT, execute = {
            runtime.pullBudgets(context.organizationId, context.deletions.budgetIds)
        }),
        SyncOperation(SyncOperationSlot.PULL_CASH_RECONCILIATION, "pull جرد الصندوق", SyncFailureMode.COLLECT, execute = {
            runtime.pullCashReconciliations(
                context.organizationId,
                context.deletions.reconciliationIds
            )
            runtime.pullCashDenominations(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_COMMISSIONS, "pull العمولات", SyncFailureMode.COLLECT, execute = {
            runtime.pullCommissions(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_CASH_REGISTER, "pull الصندوق", SyncFailureMode.COLLECT, execute = {
            runtime.pullCashRegister(context.organizationId)
            runtime.pullCashMovements(context.organizationId)
        })
    ).filterNot { v2FinanceOwned335(context.organizationId) && it.isLegacyFinance335() }
}
