package com.verto.app.data.workers

import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.data.sync.rfm.RfmCalculationPort
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.reports.infrastructure.analytics.RfmCalculator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRfmCalculationAdapter @Inject constructor(
    private val partyDirectory: PartyDirectoryGateway,
    private val invoiceRepository: InvoiceRepository,
    private val calculator: RfmCalculator
) : RfmCalculationPort {
    override suspend fun recalculate() {
        val clients = partyDirectory.getAllClientsSync()
        val invoices = invoiceRepository.getAllInvoicesSync().filter { !it.voided }
        val items = invoiceRepository.getAllInvoiceItemsSync()
        calculator.recalculate(clients, invoices, items)
    }
}
