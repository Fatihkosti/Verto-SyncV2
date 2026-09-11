package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.SupplierProfile
import com.verto.app.feature.party.domain.model.SupplierScope
import kotlinx.coroutines.flow.Flow

interface PartyRoleReadPort {
    fun observeCustomerSegment(partyId: String): Flow<CustomerSegment?>
    fun observeSupplierScope(partyId: String): Flow<SupplierScope?>
    fun observeCustomerProfile(partyId: String): Flow<CustomerProfile?>
    fun observeSupplierProfile(partyId: String): Flow<SupplierProfile?>
    fun observeIsCompetitor(partyId: String): Flow<Boolean>
}
