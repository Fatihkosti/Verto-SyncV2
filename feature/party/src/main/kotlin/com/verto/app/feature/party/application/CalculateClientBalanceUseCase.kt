package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.PartyClientStatus
import com.verto.app.feature.party.domain.model.PartyClientSummary
import com.verto.app.feature.party.domain.model.PartyInvoice
import com.verto.app.feature.party.domain.model.PartyPayment
import com.verto.app.utils.MoneyMath.add
import com.verto.app.utils.MoneyMath.isEffectivelyZero
import com.verto.app.utils.MoneyMath.isGreaterThan
import com.verto.app.utils.MoneyMath.moneySum
import com.verto.app.utils.MoneyMath.subtract
import javax.inject.Inject

class CalculateClientBalanceUseCase @Inject constructor() {
    operator fun invoke(client: PartyClient, invoices: List<PartyInvoice>, payments: List<PartyPayment>): PartyClientSummary {
        val now = System.currentTimeMillis()
        val clientInvoices = invoices.filter { it.clientId == client.id }
        val clientPayments = payments.filter { it.clientId == client.id }
        var netBalance = 0.0
        var totalPaid = 0.0
        var hasOverdue = false
        for (invoice in clientInvoices) {
            val paid = clientPayments.filter { it.invoiceId == invoice.id }.map { it.amount }.moneySum()
            val remaining = subtract(invoice.totalAmount, paid)
            totalPaid = add(totalPaid, paid)
            if (!invoice.isOwedToMe) netBalance = subtract(netBalance, remaining) else {
                netBalance = add(netBalance, remaining)
                if (invoice.dueDate > 0L && invoice.dueDate < now && isGreaterThan(remaining, 0.0)) hasOverdue = true
            }
        }
        val status = when {
            isEffectivelyZero(netBalance) -> PartyClientStatus.GREY
            hasOverdue -> PartyClientStatus.RED
            else -> PartyClientStatus.GREEN
        }
        return PartyClientSummary(
            client = client,
            totalDebt = add(netBalance, totalPaid),
            totalPaid = totalPaid,
            status = status,
            remaining = netBalance,
        )
    }
}
