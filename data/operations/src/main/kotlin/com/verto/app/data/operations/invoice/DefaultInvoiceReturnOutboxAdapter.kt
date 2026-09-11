package com.verto.app.data.operations.invoice

import com.verto.app.data.operations.transaction.FinancialOutboxWriter
import com.verto.app.feature.invoice.domain.model.InvoiceReturnAggregate
import com.verto.app.feature.invoice.domain.port.InvoiceReturnOutboxPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultInvoiceReturnOutboxAdapter @Inject constructor(
    private val writer: FinancialOutboxWriter,
) : InvoiceReturnOutboxPort {
    override suspend fun append(aggregate: InvoiceReturnAggregate) = writer.appendReturn(aggregate)
}
