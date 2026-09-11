package com.verto.app.utils

import com.verto.app.data.local.dao.CashRegisterDao
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashRegisterEntity
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class InsufficientCashException(current: Double, required: Double) :
    Exception("المبلغ في الصندوق لا يكفي — المتوفر ${CurrencyFormatter.formatNoSymbol(current)} والمطلوب ${CurrencyFormatter.formatNoSymbol(required)}")

/**
 * CashRegisterManager — fixed-point authority for Session 334 financial paths.
 * Legacy Double methods remain compatibility boundaries only.
 */








class CashRegisterManager(
    private val dao: CashRegisterDao,
    private val deps: CashRegisterDependencies
) {
    private suspend fun guardCashOut(amount: Double) = guardCashOutMinor(Money.fromLegacyDouble(amount).amountMinor)
    private suspend fun guardCashOutMinor(amountMinor: Long) {
        require(amountMinor > 0L) { "Cash movement amount must be positive" }; if (deps.prefs.allowCashOverdraft.first()) return
        val current = dao.getRegisterSync()?.balanceMinor ?: 0L
        if (!cashOutAllowedMinor(current, amountMinor, false)) throw InsufficientCashException(Money.ofMinor(current).toLegacyDouble(), Money.ofMinor(amountMinor).toLegacyDouble())
    }
    fun getBalance(): Flow<CashRegisterEntity?> = dao.getRegister()
    suspend fun getCurrentBalance(): Double = Money.ofMinor(getCurrentBalanceMinor()).toLegacyDouble()
    suspend fun getCurrentBalanceMinor(): Long = dao.getRegisterSync()?.balanceMinor ?: 0L
    fun getMovements(): Flow<List<CashRegisterMovementEntity>> = dao.getAllMovements()
    fun getRecentMovements(limit: Int = 50): Flow<List<CashRegisterMovementEntity>> = dao.getRecentMovements(limit)




























    suspend fun recordMovement(
        type: CashMovementType,
        amount: Double,
        referenceId: String = "",
        note: String = "",
        sourceType: String = "",
        sourceId: String = referenceId,
        sourceVersion: Int = 1,
        writeId: String = "",
    ) = deps.writer.record(CashMovementIntent(type, Money.fromLegacyDouble(amount).amountMinor, referenceId, note, CashMovementSource(sourceType, sourceId, sourceVersion), CashMovementIdentity(writeId)))
    suspend fun recordMovementMinor(type: CashMovementType, amountMinor: Long, referenceId: String = "", note: String = "", writeId: String = "", sourceType: String = "") =
        deps.writer.record(CashMovementIntent(type, amountMinor, referenceId, note, CashMovementSource(sourceType, referenceId, 1), CashMovementIdentity(writeId, dependsOnMutationId = writeId.takeIf { sourceType.startsWith("EXPENSE") && it.isNotBlank() })))
    suspend fun onSaleCash(amount: Double, invoiceId: String, writeId: String = "") =
        recordMovement(CashMovementType.SALE_CASH, amount, invoiceId, sourceType="INVOICE", sourceId=invoiceId, writeId=writeId)
    suspend fun onPurchaseCash(amount: Double, invoiceId: String, writeId: String = "") { guardCashOut(amount); recordMovement(CashMovementType.PURCHASE_CASH, amount, invoiceId, sourceType="INVOICE", sourceId=invoiceId, writeId=writeId) }
    suspend fun onPaymentReceived(amount: Double, paymentId: String, writeId: String = "") =
        recordMovement(CashMovementType.PAYMENT_RECEIVED, amount, paymentId, sourceType="PAYMENT", sourceId=paymentId, writeId=writeId)
    suspend fun onPaymentMade(amount: Double, paymentId: String) { guardCashOut(amount); recordMovement(CashMovementType.PAYMENT_MADE, amount, paymentId) }
    suspend fun onExpenseMinor(amountMinor: Long, expenseId: String, writeId: String = "") {
        guardCashOutMinor(amountMinor); recordMovementMinor(CashMovementType.EXPENSE, amountMinor, expenseId, writeId=writeId, sourceType="EXPENSE")
    }
    suspend fun onExpense(amount: Double, expenseId: String) = onExpenseMinor(Money.fromLegacyDouble(amount).amountMinor, expenseId)

    /** B12: product-owned expense delta. The signed delta is persisted exactly once and joins the expense batch. */
    internal suspend fun recordExpenseDeltaB12(
        cashDeltaMinor: Long,
        expenseId: String,
        writeId: String,
        commandBatchId: String,
        commandOrder: Int = 1,
    ): CashMovementWriteResult? {
        if (cashDeltaMinor == 0L) return null
        val amountMinor = if (cashDeltaMinor < 0L) Math.negateExact(cashDeltaMinor) else cashDeltaMinor
        val movementType = if (cashDeltaMinor < 0L) CashMovementType.EXPENSE else CashMovementType.MANUAL_ADD
        if (cashDeltaMinor < 0L) guardCashOutMinor(amountMinor)
        return deps.writer.recordWithResult(
            CashMovementIntent(
                type = movementType,
                amountMinor = amountMinor,
                referenceId = expenseId,
                note = "B12 expense revision cash effect",
                source = CashMovementSource(
                    type = if (cashDeltaMinor < 0L) "EXPENSE" else "EXPENSE_REVISION_REFUND",
                    id = expenseId,
                    version = 1,
                ),
                identity = CashMovementIdentity(
                    writeId = writeId,
                    operation = "COMMAND",
                    dependsOnMutationId = writeId,
                    commandBatchId = commandBatchId,
                    commandOrder = commandOrder,
                ),
            )
        ).also { result ->
            check(result.signedAmountMinor == cashDeltaMinor) { "BLOCKED_EXPENSE_DOMAIN_DRIFT" }
        }
    }
    suspend fun onInvoiceReturnCashOutMinor(amountMinor: Long, returnId: String, writeId: String, note: String) {
        guardCashOutMinor(amountMinor); recordMovementMinor(CashMovementType.MANUAL_DEDUCT, amountMinor, returnId, note, writeId, "INVOICE_RETURN")
    }
    suspend fun onInvoiceReturnCashInMinor(amountMinor: Long, returnId: String, writeId: String, note: String) =
        recordMovementMinor(CashMovementType.MANUAL_ADD, amountMinor, returnId, note, writeId, "INVOICE_RETURN")
    suspend fun manualAddMinor(amountMinor: Long, note: String, referenceId: String = "", writeId: String = "") =
        recordMovementMinor(CashMovementType.MANUAL_ADD, amountMinor, referenceId, note, writeId, "MANUAL_CASH_ADJUSTMENT")
    suspend fun manualDeductMinor(amountMinor: Long, note: String, allowOverdraft: Boolean = false, referenceId: String = "", writeId: String = "") {
        require(amountMinor > 0L); if (!allowOverdraft) guardCashOutMinor(amountMinor)
        recordMovementMinor(CashMovementType.MANUAL_DEDUCT, amountMinor, referenceId, note, writeId, "MANUAL_CASH_ADJUSTMENT")
    }
    suspend fun manualAdd(amount: Double, note: String) = manualAddMinor(Money.fromLegacyDouble(amount).amountMinor, note)
    suspend fun manualDeduct(amount: Double, note: String, allowOverdraft: Boolean = false) =
        manualDeductMinor(Money.fromLegacyDouble(amount).amountMinor, note, allowOverdraft)
    suspend fun reverseMovementMinor(originalType: CashMovementType, amountMinor: Long, referenceId: String, writeId: String = "", context: CashReverseContext336 = CashReverseContext336()) {
        val reverseType = reverseType(originalType); if (reverseType == CashMovementType.MANUAL_DEDUCT) guardCashOutMinor(amountMinor)
        deps.writer.record(CashMovementIntent(reverseType, amountMinor, referenceId, "إلغاء عملية سابقة", CashMovementSource(context.sourceType, referenceId, 1), CashMovementIdentity(writeId, "REVERSE", context.dependsOnMutationId)))
    }
    private fun reverseType(type: CashMovementType) = when (type) {
        CashMovementType.SALE_CASH, CashMovementType.PAYMENT_RECEIVED, CashMovementType.MANUAL_ADD -> CashMovementType.MANUAL_DEDUCT
        CashMovementType.PURCHASE_CASH, CashMovementType.PAYMENT_MADE, CashMovementType.EXPENSE, CashMovementType.MANUAL_DEDUCT -> CashMovementType.MANUAL_ADD
    }





















































    suspend fun reverseMovement(
        originalType: CashMovementType,
        amount: Double,
        referenceId: String,
        note: String = "إلغاء عملية سابقة",
        writeId: String = "",
        sourceType: String = "",
        sourceId: String = referenceId,
    ) {
        val reverse = reverseType(originalType); val minor = Money.fromLegacyDouble(amount).amountMinor
        if (reverse == CashMovementType.MANUAL_DEDUCT) guardCashOutMinor(minor)
        deps.writer.record(CashMovementIntent(reverse, minor, referenceId, note, CashMovementSource(sourceType, sourceId, 1), CashMovementIdentity(writeId, "REVERSE")))
    }
}

internal fun cashOutAllowedMinor(currentMinor:Long,requiredMinor:Long,allowOverdraft:Boolean):Boolean =
    allowOverdraft || currentMinor >= requiredMinor
