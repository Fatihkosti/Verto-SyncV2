package com.verto.app.feature.invoice.application.pendingaction

import android.content.ContextWrapper
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.feature.dashboard.api.HomePermissionContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialPendingActionProvider337Test {
    @Test
    fun deniedPermissions_doNotCollectInvoiceSourceOrClock() = runBlocking {
        val source = CountingSource()
        val clock = CountingClock()
        val provider = FinancialPendingActionProvider(ContextWrapper(null), source, clock, session())

        assertTrue(provider.observePendingActions(HomePermissionContext("org-a", "user-a", emptySet())).first().isEmpty())
        assertEquals(0, source.collections)
        assertEquals(0, clock.collections)
    }

    @Test
    fun wrongTenant_doesNotCollectInvoiceSourceOrClock() = runBlocking {
        val source = CountingSource()
        val clock = CountingClock()
        val provider = FinancialPendingActionProvider(ContextWrapper(null), source, clock, session())

        assertTrue(provider.observePendingActions(HomePermissionContext("org-b", "user-a", setOf("sales_view"))).first().isEmpty())
        assertEquals(0, source.collections + clock.collections)
    }

    private class CountingSource : FinancialPendingActionSource {
        var collections = 0
        override fun observeDueCreditInvoices(
            organizationId: String,
            nowEpochMillis: Long,
            includeSales: Boolean,
            includePurchases: Boolean,
        ): Flow<List<FinancialPendingInvoiceRecord>> = flow {
            collections++
            emit(emptyList())
        }
    }

    private class CountingClock : FinancialPendingActionClock {
        var collections = 0
        override fun observeNowEpochMillis(): Flow<Long> = flow { collections++; emit(1_000_000_000_000L) }
    }

    private fun session() = FakeSessionReader()
    private class FakeSessionReader : SessionReader {
        private val user = CurrentUser("user-a", "User", "")
        private val org = CurrentOrganization("org-a")
        override val userId = flowOf(user.id); override val userName = flowOf(user.name); override val userPhone = flowOf(user.phone)
        override val role = flowOf("admin"); override val permissionsJson = flowOf(""); override val organizationId = flowOf(org.id)
        override val currentUser = flowOf(user); override val currentOrganization = flowOf(org)
        override suspend fun snapshot() = SessionState(user, org, "admin", "")
    }
}
