package com.verto.app.feature.integration.optimal.application.pendingaction

import android.content.ContextWrapper
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUpStatus
import com.verto.app.feature.integration.optimal.domain.model.OperationalMaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.repository.MaintenanceFollowUpRepository
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenancePendingActionProvider337Test {
    @Test
    fun deniedPermission_doesNotCollectRepositoryOrClock() = runBlocking {
        val repository = CountingRepository()
        val clock = CountingClock()
        val provider = MaintenancePendingActionProvider(ContextWrapper(null), repository, clock, session())

        assertTrue(provider.observePendingActions(context()).first().isEmpty())
        assertEquals(0, repository.collections)
        assertEquals(0, clock.collections)
    }

    @Test
    fun wrongTenant_doesNotCollectRepositoryOrClock() = runBlocking {
        val repository = CountingRepository()
        val clock = CountingClock()
        val provider = MaintenancePendingActionProvider(ContextWrapper(null), repository, clock, session())

        val wrongTenant = HomePermissionContext(
            organizationId = "org-b",
            userId = "user-a",
            grantedPermissions = setOf(HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE),
        )
        assertTrue(provider.observePendingActions(wrongTenant).first().isEmpty())
        assertEquals(0, repository.collections)
        assertEquals(0, clock.collections)
    }

    @Test
    fun authorizedEmptyRepository_collectsOnce() = runBlocking {
        val repository = CountingRepository()
        val clock = CountingClock()
        val provider = MaintenancePendingActionProvider(ContextWrapper(null), repository, clock, session())

        assertTrue(
            provider.observePendingActions(
                context(setOf(HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE)),
            ).first().isEmpty(),
        )
        assertEquals(1, repository.collections)
        assertEquals(1, clock.collections)
    }

    private class CountingRepository : MaintenanceFollowUpRepository {
        var collections = 0

        override fun observeOperational(
            organizationId: String,
            nowMillis: Long,
        ): Flow<List<OperationalMaintenanceFollowUp>> = flow {
            collections++
            emit(emptyList())
        }

        override suspend fun start(
            organizationId: String,
            recordId: String,
            startedAt: Long,
            expectedAt: Long?,
        ): MaintenanceFollowUp = error("not used")

        override suspend fun updateStatus(
            organizationId: String,
            recordId: String,
            status: MaintenanceFollowUpStatus,
            updatedAt: Long,
        ): MaintenanceFollowUp? = error("not used")

        override suspend fun updateExpectedAt(
            organizationId: String,
            recordId: String,
            expectedAt: Long?,
            updatedAt: Long,
        ): MaintenanceFollowUp? = error("not used")
    }

    private class CountingClock : MaintenancePendingActionClock {
        var collections = 0
        override fun observeNowEpochMillis(): Flow<Long> = flow {
            collections++
            emit(1_000_000_000_000L)
        }
    }

    private fun context(permissions: Set<String> = emptySet()) =
        HomePermissionContext("org-a", "user-a", permissions)

    private fun session() = FakeSessionReader()

    private class FakeSessionReader : SessionReader {
        private val user = CurrentUser("user-a", "User", "")
        private val org = CurrentOrganization("org-a")
        override val userId = flowOf(user.id)
        override val userName = flowOf(user.name)
        override val userPhone = flowOf(user.phone)
        override val role = flowOf("admin")
        override val permissionsJson = flowOf("")
        override val organizationId = flowOf(org.id)
        override val currentUser = flowOf(user)
        override val currentOrganization = flowOf(org)
        override suspend fun snapshot() = SessionState(user, org, "admin", "")
    }
}
