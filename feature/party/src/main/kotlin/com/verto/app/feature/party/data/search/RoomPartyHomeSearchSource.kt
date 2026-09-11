package com.verto.app.feature.party.data.search

import com.verto.app.data.local.dao.ClientDao
import com.verto.app.feature.party.application.search.PartyHomeSearchRecord
import com.verto.app.feature.party.application.search.PartyHomeSearchSource
import com.verto.app.utils.MoneyMath
import javax.inject.Inject

class RoomPartyHomeSearchSource @Inject constructor(
    private val clientDao: ClientDao,
) : PartyHomeSearchSource {
    override suspend fun search(
        organizationId: String,
        textQuery: String,
        phoneQuery: String,
        limit: Int,
    ): List<PartyHomeSearchRecord> = clientDao
        .searchClientsWithBalanceByPrefix(organizationId, textQuery, phoneQuery, limit)
        .map { row ->
            val summary = row.summary
            val client = summary.client
            val isCompetitor = row.isCompetitor
            val isSupplier = row.isSupplierRole
            val balance = when {
                isCompetitor -> summary.remaining // search never publishes a bilateral net as a ledger truth
                isSupplier -> MoneyMath.subtract(summary.totalPurchaseDebt, summary.totalPurchasePaid)
                else -> summary.remaining
            }
            PartyHomeSearchRecord(
                id = client.id,
                name = client.name,
                phone = client.phone,
                balance = balance,
                isSupplier = isSupplier,
                isCompetitor = isCompetitor,
                updatedAtEpochMillis = client.createdAt,
            )
        }
}
