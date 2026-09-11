package com.verto.app.feature.party.data

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.dao.PartyRoleDao
import com.verto.app.feature.party.application.PartyRoleReadPort
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.customerSegmentFromStorage
import com.verto.app.feature.party.domain.model.SupplierProfile
import com.verto.app.feature.party.domain.model.SupplierScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class RoomPartyRoleReadAdapter @Inject constructor(
    private val dao: PartyRoleDao,
    private val sessionReader: SessionReader,
) : PartyRoleReadPort {
    private val organizationIds = sessionReader.organizationId
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctUntilChanged()

    override fun observeCustomerSegment(partyId: String) = organizationIds.flatMapLatest { organizationId ->
        dao.observeCustomerProfile(organizationId, partyId)
    }.map { row -> customerSegmentFromStorage(row?.segment) }

    override fun observeSupplierScope(partyId: String) = organizationIds.flatMapLatest { organizationId ->
        dao.observeSupplierProfile(organizationId, partyId)
    }.map { row -> row?.scope?.let { runCatching { SupplierScope.valueOf(it) }.getOrNull() } }

    override fun observeCustomerProfile(partyId: String) = organizationIds.flatMapLatest { organizationId ->
        dao.observeCustomerProfile(organizationId, partyId)
    }.map { row ->
        row?.let {
            CustomerProfile(
                partyId = it.partyId,
                segment = customerSegmentFromStorage(it.segment) ?: CustomerSegment.INDIVIDUAL,
                ageYears = it.ageYears,
                purchaseContactName = it.purchaseContactName,
                businessActivity = it.businessActivity,
                workplaceName = it.workplaceName,
                shopName = it.shopName,
                workshopName = it.workshopName,
                vehicleModels = it.vehicleModels.split("||").map(String::trim).filter(String::isNotBlank),
                workshopWorkerCount = it.workshopWorkerCount,
            )
        }
    }

    override fun observeSupplierProfile(partyId: String) = organizationIds.flatMapLatest { organizationId ->
        dao.observeSupplierProfile(organizationId, partyId)
    }.map { row ->
        row?.let {
            SupplierProfile(
                partyId = it.partyId,
                scope = runCatching { SupplierScope.valueOf(it.scope) }.getOrDefault(SupplierScope.UNKNOWN),
                country = it.country,
                currencyCode = it.currencyCode,
                specialty = it.specialty,
            )
        }
    }
    override fun observeIsCompetitor(partyId: String) = organizationIds.flatMapLatest { organizationId ->
        dao.observeIsCompetitor(organizationId, partyId)
    }.distinctUntilChanged()

}
