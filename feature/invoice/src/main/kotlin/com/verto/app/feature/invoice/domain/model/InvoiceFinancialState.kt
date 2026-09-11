package com.verto.app.feature.invoice.domain.model

import com.verto.app.money.Money

/** Financial state calculated only from fixed-point minor units. */
data class InvoiceFinancialState(
    val totalMinor: Long,
    val paidMinor: Long,
    val isCredit: Boolean,
    val dueDate: Long,
    val now: Long = System.currentTimeMillis(),
) {
    private val totalMoney: Money get() = Money.ofMinor(totalMinor)
    private val paidMoney: Money get() = Money.ofMinor(paidMinor)
    private val remainingMoney: Money get() = totalMoney - paidMoney

    /** Presentation compatibility only. */
    val total: Double get() = totalMoney.toLegacyDouble()
    /** Presentation compatibility only. */
    val paid: Double get() = paidMoney.toLegacyDouble()
    /** Presentation compatibility only. */
    val remaining: Double get() = remainingMoney.toLegacyDouble()

    val isPaid: Boolean get() = remainingMoney.amountMinor <= 0L
    val isUnpaid: Boolean get() = paidMinor <= 0L && !isPaid
    val isPartiallyPaid: Boolean get() = !isUnpaid && !isPaid
    val isActiveDebt: Boolean get() = isCredit && !isPaid
    val isOverdue: Boolean get() = isActiveDebt && dueDate > 0L && dueDate < now
    val overdueDays: Int get() = if (isOverdue) ((now - dueDate) / DAY_MS).toInt() else 0
    val progressPercent: Float
        get() = if (totalMinor > 0L) {
            (paidMinor.toDouble() / totalMinor.toDouble()).toFloat().coerceIn(0f, 1f)
        } else 0f

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
