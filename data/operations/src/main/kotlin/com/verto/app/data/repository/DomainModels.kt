package com.verto.app.data.repository

import com.verto.app.feature.invoice.data.toFinancialState

import com.verto.app.data.local.dao.ClientWithBalance
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.ClientStatus
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.feature.invoice.domain.model.InvoiceFinancialState
import com.verto.app.utils.MoneyMath

// ── عميل مع ملخص حساباته + حالته ───────────────────
data class ClientSummary(
    val client            : PartyIdentityEntity,
    val totalDebt         : Double,
    val totalPaid         : Double,
    val status            : ClientStatus,
    val competitorBalance : Double = 0.0
) {
    val remaining: Double get() = MoneyMath.subtract(totalDebt, totalPaid)
}

/**
 * تحويل نتيجة SQL إلى ClientSummary مع حساب الحالة.
 * المنطق:
 *   GREY  = لا يوجد رصيد متبقي
 *   RED   = يوجد دين متأخر
 *   GREEN = يوجد دين لكن لم يتأخر بعد
 */
fun ClientWithBalance.toSummary(): ClientSummary {
    val status = when {
        MoneyMath.isEffectivelyZero(remaining) || remaining < 0 -> ClientStatus.GREY
        hasOverdue                                               -> ClientStatus.RED
        else                                                     -> ClientStatus.GREEN
    }
    return ClientSummary(client, totalDebt, totalPaid, status, competitorBalance)
}

// ── فاتورة مع ملخص مدفوعاتها ────────────────────────
data class InvoiceSummary(
    val invoice: InvoiceEntity,
    val totalPaid: Double,
    val payments: List<PaymentEntity> = emptyList()
) {
    /** مصدر الحالة المالية الموحَّد — لا تُعِد حساب التأخير/السداد في الشاشات. */
    val financial: InvoiceFinancialState
        get() = invoice.toFinancialState(totalPaid)

    val remaining: Double get() = financial.remaining
    val isOverdue: Boolean get() = financial.isOverdue
    val isPaid: Boolean get() = financial.isPaid
    val progressPercent: Float get() = financial.progressPercent
}
