package com.verto.app.feature.payment.application.search

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentHomeSearchProvider340Test {
    @Test fun `denied permission performs zero source queries`() = runBlocking {
        val source=Source(); PaymentHomeSearchProvider(source, Session()).search(HomeSearchQuery("ab"), context(emptySet())); assertEquals(0, source.calls)
    }
    @Test fun `purchase-only permission is pushed into tenant query`() = runBlocking {
        val source=Source(); PaymentHomeSearchProvider(source, Session()).search(HomeSearchQuery("ab", 9), context(setOf(HomePermissionKeys.PURCHASES_VIEW)))
        assertEquals(1, source.calls); assertEquals("org-a", source.org); assertFalse(source.sales); assertTrue(source.purchases); assertEquals(9, source.limit)
    }
    private class Source:PaymentHomeSearchSource {var calls=0;var org="";var sales=false;var purchases=false;var limit=0
        override suspend fun search(organizationId:String,textQuery:String,identifierQuery:String,includeSales:Boolean,includePurchases:Boolean,limit:Int):List<PaymentHomeSearchRecord>{calls++;org=organizationId;sales=includeSales;purchases=includePurchases;this.limit=limit;return emptyList()}}
    private fun context(p:Set<String>)=HomePermissionContext("org-a","user-a",p)
    private class Session:SessionReader {private val u=CurrentUser("user-a","User","");private val o=CurrentOrganization("org-a");override val userId=flowOf(u.id);override val userName=flowOf(u.name);override val userPhone=flowOf(u.phone);override val role=flowOf("admin");override val permissionsJson=flowOf("");override val organizationId=flowOf(o.id);override val currentUser=flowOf(u);override val currentOrganization=flowOf(o);override suspend fun snapshot()=SessionState(u,o,"admin","")}
}
