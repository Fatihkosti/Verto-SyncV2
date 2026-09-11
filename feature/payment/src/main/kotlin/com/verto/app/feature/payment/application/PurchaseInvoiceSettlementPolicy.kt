package com.verto.app.feature.payment.application

import com.verto.app.money.Money

enum class PurchaseSettlementKind { FULLY_PAID, CREDIT }

data class PurchaseSettlementDecision(
    val total: Money,
    val paid: Money,
    val remaining: Money,
    val kind: PurchaseSettlementKind,
)

/** Derives purchase payment terms from the paid amount instead of asking for cash/credit up front. */
object PurchaseInvoiceSettlementPolicy {
    fun evaluate(total: Money, paid: Money): PurchaseSettlementDecision {
        require(total.isPositive()) { "إجمالي الفاتورة يجب أن يكون أكبر من صفر" }
        require(!paid.isNegative()) { "المبلغ المدفوع لا يمكن أن يكون سالباً" }
        require(paid <= total) { "المبلغ المدفوع لا يمكن أن يتجاوز قيمة الفاتورة" }
        val remaining = total - paid
        return PurchaseSettlementDecision(
            total = total,
            paid = paid,
            remaining = remaining,
            kind = if (remaining.isZero()) PurchaseSettlementKind.FULLY_PAID else PurchaseSettlementKind.CREDIT,
        )
    }
}
