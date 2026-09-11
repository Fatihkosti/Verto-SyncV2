package com.verto.app.feature.party.domain.repository

import com.verto.app.feature.party.domain.model.PartyInvoiceItem
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import kotlinx.coroutines.flow.Flow

/** Pure financial read contract owned by Party. */
interface PartyFinancialQuery {
    fun getInvoiceSummariesForClient(clientId: String): Flow<List<PartyInvoiceSummary>>
    fun getInvoiceSummariesForClientInRange(
        clientId: String,
        from: Long,
        to: Long
    ): Flow<List<PartyInvoiceSummary>>

    fun getItemsForClient(clientId: String): Flow<List<PartyInvoiceItem>>
    suspend fun getInvoiceItemsSync(invoiceId: String): List<PartyInvoiceItem>
    fun getTotalCollectedForClientInRange(clientId: String, from: Long, to: Long): Flow<Double>
}
