package com.verto.app.feature.payment.application

import com.verto.app.money.Money

enum class SaleSettlementKind { FULLY_PAID, CREDIT }

data class SaleSettlementDecision(
    val total: Money,
    val paid: Money,
    val remaining: Money,
    val kind: SaleSettlementKind,
)

/**
 * Session 349: the editor never asks the user to choose cash/credit up front.
 * Payment terms are derived from the amount actually paid at save time.
 */
object SaleInvoiceSettlementPolicy {
    fun evaluate(total: Money, paid: Money): SaleSettlementDecision {
        require(total.isPositive()) { "إجمالي الفاتورة يجب أن يكون أكبر من صفر" }
        require(!paid.isNegative()) { "المبلغ المدفوع لا يمكن أن يكون سالباً" }
        require(paid <= total) { "المبلغ المدفوع لا يمكن أن يتجاوز قيمة الفاتورة" }
        val remaining = total - paid
        return SaleSettlementDecision(
            total = total,
            paid = paid,
            remaining = remaining,
            kind = if (remaining.isZero()) SaleSettlementKind.FULLY_PAID else SaleSettlementKind.CREDIT,
        )
    }
}
