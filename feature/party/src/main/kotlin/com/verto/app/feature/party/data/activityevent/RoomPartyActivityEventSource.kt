package com.verto.app.feature.party.data.activityevent

import com.verto.app.data.local.dao.ClientDao
import com.verto.app.feature.party.application.activityevent.PartyActivityEventSource
import com.verto.app.feature.party.application.activityevent.PartyActivityRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPartyActivityEventSource @Inject constructor(
    private val clientDao: ClientDao,
) : PartyActivityEventSource {
    override fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<PartyActivityRecord>> =
        clientDao.observeActivityClients(organizationId, sinceEpochMillis, limit).map { clients ->
            clients.map { row ->
                val client = row.client
                val supplier = row.isSupplierRole
                PartyActivityRecord(
                    id = client.id,
                    name = client.name,
                    isSupplier = supplier,
                    createdAtEpochMillis = row.roleCreatedAt,
                    createdBy = client.createdBy.takeIf { client.createdAt == row.roleCreatedAt }.orEmpty(),
                )
            }
        }
}
