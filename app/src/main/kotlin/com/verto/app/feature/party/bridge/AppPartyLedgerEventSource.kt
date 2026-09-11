package com.verto.app.feature.party.bridge

import com.verto.app.data.local.dao.ClientCreditDao
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.party.application.ledger.PartyLedgerEventSource
import com.verto.app.feature.party.domain.ledger.LedgerEvent
import com.verto.app.feature.party.domain.ledger.LedgerEventKey
import com.verto.app.feature.party.domain.ledger.LedgerEventType
import com.verto.app.feature.party.domain.ledger.LedgerSide
import com.verto.app.money.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class AppPartyLedgerEventSource @Inject constructor(
    invoiceRepository: InvoiceRepository,
    creditDao: ClientCreditDao,
) : PartyLedgerEventSource {
    private val invoices = invoiceRepository.observeAllInvoices()
    private val payments = invoiceRepository.observeAllPayments()
    private val credits = creditDao.observeAllCredits()

    override fun observeEvents(partyId: String): Flow<List<LedgerEvent>> =
        combine(invoices, payments, credits) { invoiceRows, paymentRows, creditRows ->
            val partyInvoices = invoiceRows.filter { it.clientId == partyId && it.lifecycleStatus != InvoiceLifecycleStatus.DRAFT }
            val invoiceById = partyInvoices.associateBy { it.id }
            val partyPayments = paymentRows.filter { it.clientId == partyId && it.invoiceId in invoiceById }
            buildList {
                partyInvoices.forEach { invoice ->
                    val side = invoice.category.toLedgerSide()
                    val currency = invoice.transactionCurrencyCode.ifBlank { invoice.functionalCurrencyCode.ifBlank { Money.TRANSACTION_CURRENCY } }
                    val amountMinor = invoice.transactionAmountMinor.takeIf { it != 0L } ?: invoice.totalAmountMinor
                    add(event(LedgerEventKey("INVOICE", invoice.id, "PRINCIPAL"), partyId, side, LedgerEventType.INVOICE, Money.ofMinor(amountMinor, currency), (invoice.createdAt) to (invoice.postedAt.takeIf { it > 0 } ?: invoice.createdAt)))
                    if (invoice.status == InvoiceStatus.CLOSED_CASH && partyPayments.none { it.invoiceId == invoice.id }) {
                        add(event(LedgerEventKey("INVOICE", invoice.id, "CASH_SETTLEMENT"), partyId, side, LedgerEventType.CASH_SETTLEMENT, Money.ofMinor(-amountMinor, currency), (invoice.createdAt) to (invoice.createdAt)))
                    }
                    if (invoice.lifecycleStatus == InvoiceLifecycleStatus.VOID || invoice.voided) {
                        val voidAt = invoice.voidedAt.takeIf { it > 0 } ?: invoice.createdAt
                        add(event(LedgerEventKey("INVOICE", invoice.id, "VOID"), partyId, side, LedgerEventType.VOID, Money.ofMinor(-amountMinor, currency), (voidAt) to (voidAt)))
                    }
                }
                partyPayments.forEach { payment ->
                    val invoice = checkNotNull(invoiceById[payment.invoiceId])
                    val currency = payment.paymentCurrencyCode.ifBlank { invoice.transactionCurrencyCode.ifBlank { Money.TRANSACTION_CURRENCY } }
                    val amountMinor = payment.supplierAmountMinor.takeIf { it != 0L } ?: payment.amountMinor
                    val type = if (payment.reversedPaymentId == null) LedgerEventType.PAYMENT else LedgerEventType.PAYMENT_REVERSAL
                    add(event(LedgerEventKey("PAYMENT", payment.id, "SETTLEMENT"), partyId, invoice.category.toLedgerSide(), type, Money.ofMinor(-amountMinor, currency), (payment.paidAt) to (payment.paidAt)))
                }
                creditRows.filter { it.clientId == partyId }.forEach { credit ->
                    val sourcePayment = partyPayments.firstOrNull { it.id == credit.sourcePaymentId }
                    val sourceInvoice = sourcePayment?.let { invoiceById[it.invoiceId] }
                    val side = sourceInvoice?.category?.toLedgerSide() ?: LedgerSide.CUSTOMER
                    val currency = sourcePayment?.paymentCurrencyCode?.takeIf(String::isNotBlank)
                        ?: sourceInvoice?.transactionCurrencyCode?.takeIf(String::isNotBlank)
                        ?: Money.TRANSACTION_CURRENCY
                    add(event(LedgerEventKey("CREDIT", credit.id, "CREDIT"), partyId, side, LedgerEventType.MANUAL_CREDIT, Money.ofMinor(credit.amountMinor, currency), (credit.createdAt) to (credit.createdAt))
                        .copy(derivedFromPaymentId = credit.sourcePaymentId.takeIf(String::isNotBlank)))
                }
            }
        }

    private fun InvoiceCategory.toLedgerSide() = if (this == InvoiceCategory.SALE) LedgerSide.CUSTOMER else LedgerSide.SUPPLIER

    private fun event(
        key: LedgerEventKey, partyId: String, side: LedgerSide, type: LedgerEventType,
        amount: Money, times: Pair<Long, Long>
    ) = LedgerEvent(key, partyId, side, type, amount, times.first, times.second,
        "${key.sourceType}:${key.sourceId}:${key.component}")
}
