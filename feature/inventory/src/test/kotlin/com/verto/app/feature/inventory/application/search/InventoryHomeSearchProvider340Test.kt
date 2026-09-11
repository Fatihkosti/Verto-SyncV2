package com.verto.app.feature.inventory.application.search

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
import org.junit.Test

class InventoryHomeSearchProvider340Test {
    @Test fun `denied permission performs zero source queries`()=runBlocking {val s=Source();InventoryHomeSearchProvider(s,Session()).search(HomeSearchQuery("ab"),context(emptySet()));assertEquals(0,s.calls)}
    @Test fun `allowed search is tenant scoped and bounded`()=runBlocking {val s=Source();InventoryHomeSearchProvider(s,Session()).search(HomeSearchQuery("ab",11),context(setOf(HomePermissionKeys.INVENTORY_VIEW)));assertEquals(1,s.calls);assertEquals("org-a",s.org);assertEquals(11,s.limit)}
    private class Source:InventoryHomeSearchSource {var calls=0;var org="";var limit=0;override suspend fun search(organizationId:String,textQuery:String,identifierQuery:String,limit:Int):List<InventoryHomeSearchRecord>{calls++;org=organizationId;this.limit=limit;return emptyList()}}
    private fun context(p:Set<String>)=HomePermissionContext("org-a","user-a",p)
    private class Session:SessionReader {private val u=CurrentUser("user-a","User","");private val o=CurrentOrganization("org-a");override val userId=flowOf(u.id);override val userName=flowOf(u.name);override val userPhone=flowOf(u.phone);override val role=flowOf("admin");override val permissionsJson=flowOf("");override val organizationId=flowOf(o.id);override val currentUser=flowOf(u);override val currentOrganization=flowOf(o);override suspend fun snapshot()=SessionState(u,o,"admin","")}
}
