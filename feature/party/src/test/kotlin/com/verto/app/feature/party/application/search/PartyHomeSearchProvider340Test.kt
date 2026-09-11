package com.verto.app.feature.party.application.search

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.HomeSearchQuery
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyHomeSearchProvider340Test {
    @Test
    fun `denied permission does not query source`() = runBlocking {
        val source = Source()
        val result = PartyHomeSearchProvider(source, Session()).search(
            HomeSearchQuery("ab"), context(emptySet()),
        )
        assertTrue(result.isEmpty()); assertEquals(0, source.calls)
    }

    @Test
    fun `wrong organization does not query source`() = runBlocking {
        val source = Source()
        PartyHomeSearchProvider(source, Session()).search(
            HomeSearchQuery("ab"), HomePermissionContext("org-b", "user-a", setOf(HomePermissionKeys.CLIENTS_VIEW)),
        )
        assertEquals(0, source.calls)
    }

    @Test
    fun `allowed search passes tenant and limit to source`() = runBlocking {
        val source = Source()
        PartyHomeSearchProvider(source, Session()).search(
            HomeSearchQuery("ab", 7), context(setOf(HomePermissionKeys.CLIENTS_VIEW)),
        )
        assertEquals(1, source.calls); assertEquals("org-a", source.organizationId); assertEquals(7, source.limit)
    }

    private class Source : PartyHomeSearchSource {
        var calls = 0; var organizationId = ""; var limit = 0
        override suspend fun search(organizationId: String, textQuery: String, phoneQuery: String, limit: Int): List<PartyHomeSearchRecord> {
            calls++; this.organizationId = organizationId; this.limit = limit; return emptyList()
        }
    }
    private fun context(p: Set<String>) = HomePermissionContext("org-a", "user-a", p)
    private class Session : SessionReader {
        private val user = CurrentUser("user-a", "User", ""); private val org = CurrentOrganization("org-a")
        override val userId=flowOf(user.id); override val userName=flowOf(user.name); override val userPhone=flowOf(user.phone)
        override val role=flowOf("admin"); override val permissionsJson=flowOf(""); override val organizationId=flowOf(org.id)
        override val currentUser=flowOf(user); override val currentOrganization=flowOf(org)
        override suspend fun snapshot()=SessionState(user, org, "admin", "")
    }
}
