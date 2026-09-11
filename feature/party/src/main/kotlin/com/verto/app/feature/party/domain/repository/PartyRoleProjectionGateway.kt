package com.verto.app.feature.party.domain.repository

import com.verto.app.feature.party.domain.model.PartyClient
import kotlinx.coroutines.flow.Flow

data class PartyRoleProjection(
    val client: PartyClient,
    val hasCustomerRole: Boolean,
    val hasSupplierRole: Boolean,
    val customerSegment: String?,
    val supplierScope: String?,
)

interface PartyRoleProjectionGateway {
    fun observeRoleProjections(): Flow<List<PartyRoleProjection>>
}
