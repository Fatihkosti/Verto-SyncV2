package com.verto.app.data.operations.transaction

import com.verto.app.core.transaction.DatabaseTransactionRunner
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import com.verto.app.feature.payment.domain.port.PaymentTransactionPort
import javax.inject.Inject

class InvoiceTransactionOwnerAdapter @Inject constructor(
    private val runner: DatabaseTransactionRunner,
) : InvoiceTransactionPort {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        runner.inTransaction(block)
}

class PaymentTransactionOwnerAdapter @Inject constructor(
    private val runner: DatabaseTransactionRunner,
) : PaymentTransactionPort {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        runner.inTransaction(block)
}
