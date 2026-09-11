package com.verto.app.feature.party.domain.repository

import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.PartyClientSummary
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.SupplierProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyDirectoryGatewayContractTest {
    @Test
    fun `unknown party returns null consistently for sync and flow reads`() = runBlocking {
        val subject: PartyDirectoryGateway = EmptyDirectory()

        assertNull(subject.getClientByIdSync("missing"))
        assertNull(subject.getClientById("missing").first())
        assertTrue(subject.getAllClientsSync().isEmpty())
    }

    private class EmptyDirectory : PartyDirectoryGateway {
        override fun getClientById(id: String): Flow<PartyClient?> = flowOf(null)
        override suspend fun getClientByIdSync(id: String): PartyClient? = null
        override fun getAllClients(): Flow<List<PartyClient>> = flowOf(emptyList())
        override suspend fun getAllClientsSync(): List<PartyClient> = emptyList()
        override fun getAllClientSummaries(): Flow<List<PartyClientSummary>> = flowOf(emptyList())
        override fun searchClientSummaries(query: String): Flow<List<PartyClientSummary>> = flowOf(emptyList())
        override suspend fun insertClient(client: PartyClient): Result<Unit> = Result.success(Unit)
        override suspend fun updateClient(client: PartyClient): Result<Unit> = Result.success(Unit)
        override suspend fun insertParty(client: PartyClient, customerProfile: CustomerProfile?, supplierProfile: SupplierProfile?): Result<Unit> = Result.success(Unit)
        override suspend fun updateParty(client: PartyClient, customerProfile: CustomerProfile?, supplierProfile: SupplierProfile?): Result<Unit> = Result.success(Unit)
        override suspend fun archiveRole(id: String, role: com.verto.app.feature.party.domain.model.PartyRole) = Result.success(Unit)
        override suspend fun deleteClientPermanently(id: String): Result<Unit> = Result.success(Unit)
    }
}
