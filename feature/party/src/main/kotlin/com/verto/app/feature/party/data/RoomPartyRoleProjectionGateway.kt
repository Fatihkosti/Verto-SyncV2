package com.verto.app.feature.party.data

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.dao.ClientDao
import com.verto.app.feature.party.domain.repository.PartyRoleProjection
import com.verto.app.feature.party.domain.repository.PartyRoleProjectionGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class RoomPartyRoleProjectionGateway @Inject constructor(
    private val clientDao: ClientDao,
    private val sessionReader: SessionReader,
) : PartyRoleProjectionGateway {
    override fun observeRoleProjections(): Flow<List<PartyRoleProjection>> =
        sessionReader.organizationId
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctUntilChanged()
            .flatMapLatest(clientDao::observeClientRoleProjections)
            .map { rows ->
                rows.map { row ->
                    PartyRoleProjection(
                        client = row.client.toPartyClient(row.customerSegment, row.supplierScope),
                        hasCustomerRole = row.hasCustomerRole,
                        hasSupplierRole = row.hasSupplierRole,
                        customerSegment = row.customerSegment,
                        supplierScope = row.supplierScope,
                    )
                }
            }
}
