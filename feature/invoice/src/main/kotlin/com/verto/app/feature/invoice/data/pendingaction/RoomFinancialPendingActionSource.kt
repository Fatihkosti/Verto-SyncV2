package com.verto.app.feature.invoice.data.pendingaction

import com.verto.app.data.local.dao.InvoiceDao
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingActionSource
import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingInvoiceCategory
import com.verto.app.feature.invoice.application.pendingaction.FinancialPendingInvoiceRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomFinancialPendingActionSource @Inject constructor(
    private val invoiceDao: InvoiceDao,
) : FinancialPendingActionSource {
    override fun observeDueCreditInvoices(
        organizationId: String,
        nowEpochMillis: Long,
        includeSales: Boolean,
        includePurchases: Boolean,
    ): Flow<List<FinancialPendingInvoiceRecord>> =
        combine(
            invoiceDao.observeOutstandingCreditInvoicesForSchedule(
                organizationId = organizationId,
                includeSales = if (includeSales) 1 else 0,
                includePurchases = if (includePurchases) 1 else 0,
            ),
            invoiceDao.observeAllDueInstallments(),
        ) { rows, installments ->
            val byInvoice = installments.groupBy { it.invoiceId }
            rows.mapNotNull { row ->
                val effectiveDueDate = effectiveDueDate370(
                    invoiceTotal = row.totalAmount,
                    totalPaid = row.totalPaid,
                    legacyDueDate = row.dueDate,
                    installments = byInvoice[row.invoiceId].orEmpty(),
                )
                if (effectiveDueDate <= 0L || effectiveDueDate > nowEpochMillis) return@mapNotNull null
                FinancialPendingInvoiceRecord(
                    invoiceId = row.invoiceId,
                    invoiceNumber = row.invoiceNumber,
                    partyId = row.partyId,
                    partyName = row.partyName,
                    partyPhone = row.partyPhone,
                    totalAmount = row.totalAmount,
                    totalPaid = row.totalPaid,
                    dueDateEpochMillis = effectiveDueDate,
                    createdAtEpochMillis = row.createdAt,
                    category = when (row.category) {
                        InvoiceCategory.SALE -> FinancialPendingInvoiceCategory.SALE
                        InvoiceCategory.PURCHASE -> FinancialPendingInvoiceCategory.PURCHASE
                    },
                    isCredit = true,
                    voided = false,
                )
            }
        }
}

internal fun effectiveDueDate370(
    invoiceTotal: Double,
    totalPaid: Double,
    legacyDueDate: Long,
    installments: List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity>,
): Long {
    if (installments.isEmpty()) return legacyDueDate
    val scheduledTotalMinor = installments.fold(0L) { acc, row -> Math.addExact(acc, row.amountMinor) }
    val totalMinor = com.verto.app.money.Money.fromLegacyDouble(invoiceTotal).amountMinor
    val paidMinor = com.verto.app.money.Money.fromLegacyDouble(totalPaid).amountMinor
    val baselinePaidMinor = (totalMinor - scheduledTotalMinor).coerceAtLeast(0L)
    val paidTowardSchedule = (paidMinor - baselinePaidMinor).coerceAtLeast(0L)
    var cumulative = 0L
    return installments
        .sortedWith(compareBy({ it.dueDate }, { it.sequence }))
        .firstOrNull { row ->
            cumulative = Math.addExact(cumulative, row.amountMinor)
            cumulative > paidTowardSchedule
        }
        ?.dueDate
        ?: 0L
}
