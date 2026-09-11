package com.verto.app.feature.party.bridge

import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.feature.party.data.toPartyInvoiceItem
import com.verto.app.feature.party.data.toPartyInvoiceSummary
import com.verto.app.feature.party.domain.model.PartyInvoiceItem
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import com.verto.app.feature.party.domain.repository.PartyFinancialQuery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class AppPartyFinancialQuery @Inject constructor(
    private val invoices: InvoiceRepository,
) : PartyFinancialQuery {
    override fun getInvoiceSummariesForClient(clientId: String): Flow<List<PartyInvoiceSummary>> =
        combine(invoices.getInvoicesForClient(clientId), invoices.getPaymentsForClient(clientId)) { invoiceRows, payments ->
            val byInvoice = payments.groupBy { it.invoiceId }
            invoiceRows.map { it.toPartyInvoiceSummary(byInvoice[it.id].orEmpty()) }
        }

    override fun getInvoiceSummariesForClientInRange(clientId: String, from: Long, to: Long): Flow<List<PartyInvoiceSummary>> =
        combine(invoices.getInvoicesForClientInRange(clientId, from, to), invoices.getPaymentsForClient(clientId)) { invoiceRows, payments ->
            val byInvoice = payments.groupBy { it.invoiceId }
            invoiceRows.map { it.toPartyInvoiceSummary(byInvoice[it.id].orEmpty()) }
        }

    override fun getItemsForClient(clientId: String): Flow<List<PartyInvoiceItem>> =
        invoices.getItemsForClient(clientId).map { rows -> rows.map { it.toPartyInvoiceItem() } }

    override suspend fun getInvoiceItemsSync(invoiceId: String): List<PartyInvoiceItem> =
        invoices.getInvoiceItemsSync(invoiceId).map { it.toPartyInvoiceItem() }

    override fun getTotalCollectedForClientInRange(clientId: String, from: Long, to: Long): Flow<Double> =
        invoices.getTotalCollectedForClientInRange(clientId, from, to)
}
