package com.verto.app.feature.party.bridge

import com.verto.app.feature.party.application.port.PartyCashBalancePort
import com.verto.app.feature.party.application.port.PartyPaymentAllocator
import com.verto.app.feature.party.application.port.PartyPaymentTarget
import com.verto.app.feature.party.application.port.PartyInvoiceVoidPort
import com.verto.app.feature.party.application.model.PartyPaymentAllocationResult as PartyPaymentAllocationResultModel
import com.verto.app.feature.invoice.application.command.InvoiceVoidCommand
import com.verto.app.feature.invoice.domain.model.InvoiceVoidPaymentDisposition
import com.verto.app.feature.invoice.domain.model.InvoiceVoidRequest
import com.verto.app.feature.payment.application.command.BulkPaymentAllocator
import com.verto.app.utils.CashRegisterManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPartyPaymentAllocator @Inject constructor(
    private val delegate: BulkPaymentAllocator
) : PartyPaymentAllocator {
    override suspend fun invoke(
        clientId: String,
        clientName: String,
        amount: Double,
        targets: List<PartyPaymentTarget>,
        note: String,
        moneyIn: Boolean,
        rate: Double,
        requireSupplierPermission: Boolean,
        requestId: String,
    ): PartyPaymentAllocationResultModel = when (val result = delegate(
        clientId = clientId,
        clientName = clientName,
        amount = amount,
        targets = targets.map { BulkPaymentAllocator.Target(it.invoiceId, it.invoiceNumber, it.remaining) },
        note = note,
        moneyIn = moneyIn,
        rate = rate,
        requireSupplierPermission = requireSupplierPermission,
        requestId = requestId,
    )) {
        is BulkPaymentAllocator.Result.Success -> PartyPaymentAllocationResultModel.Success(result.applied, result.surplusCredit)
        is BulkPaymentAllocator.Result.Error -> PartyPaymentAllocationResultModel.Error(result.message)
    }
}

@Singleton
class AppPartyCashBalanceAdapter @Inject constructor(
    cashRegisterManager: CashRegisterManager
) : PartyCashBalancePort {
    override val balance: Flow<Double> = cashRegisterManager.getBalance().map { it?.balance ?: 0.0 }
}

@Singleton
class AppPartyInvoiceVoidAdapter @Inject constructor(
    private val delegate: InvoiceVoidCommand
) : PartyInvoiceVoidPort {
    override suspend fun void(invoiceId: String, reason: String, refundPayments: Boolean) {
        delegate(
            InvoiceVoidRequest(
                invoiceId = invoiceId,
                reason = reason,
                paymentDisposition = if (refundPayments) InvoiceVoidPaymentDisposition.REFUND_TO_CASH else null,
                requestId = "void:$invoiceId",
            )
        )
    }
}
