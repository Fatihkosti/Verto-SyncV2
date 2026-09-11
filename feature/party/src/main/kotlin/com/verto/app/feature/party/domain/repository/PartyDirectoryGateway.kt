package com.verto.app.feature.party.domain.repository

import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.PartyClientSummary
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.SupplierProfile
import kotlinx.coroutines.flow.Flow

/** Pure Party directory contract. Paging is intentionally owned by Application. */
interface PartyDirectoryGateway {
    fun getClientById(id: String): Flow<PartyClient?>
    suspend fun getClientByIdSync(id: String): PartyClient?

    fun getAllClients(): Flow<List<PartyClient>>
    suspend fun getAllClientsSync(): List<PartyClient>

    fun getAllClientSummaries(): Flow<List<PartyClientSummary>>
    fun searchClientSummaries(query: String): Flow<List<PartyClientSummary>>

    suspend fun insertClient(client: PartyClient): Result<Unit>
    suspend fun updateClient(client: PartyClient): Result<Unit>
    suspend fun insertParty(client: PartyClient, customerProfile: CustomerProfile?, supplierProfile: SupplierProfile?): Result<Unit>
    suspend fun updateParty(client: PartyClient, customerProfile: CustomerProfile?, supplierProfile: SupplierProfile?): Result<Unit>
    suspend fun archiveRole(id: String, role: com.verto.app.feature.party.domain.model.PartyRole): Result<Unit>
    suspend fun deleteClientPermanently(id: String): Result<Unit>
}
