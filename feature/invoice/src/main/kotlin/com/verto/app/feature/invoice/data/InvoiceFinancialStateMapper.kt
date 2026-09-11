package com.verto.app.feature.invoice.data

import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.feature.invoice.domain.model.InvoiceFinancialState
import com.verto.app.money.Money

fun InvoiceEntity.toFinancialState(
    totalPaid: Double,
    now: Long = System.currentTimeMillis()
): InvoiceFinancialState = InvoiceFinancialState(
    totalMinor = totalAmountMinor,
    paidMinor = Money.fromLegacyDouble(totalPaid).amountMinor,
    isCredit = status == InvoiceStatus.CLOSED_CREDIT,
    dueDate = dueDate,
    now = now
)
