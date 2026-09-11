package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScheduler
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScope
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules sync only for a complete tenant/account identity. */
@Singleton
class ScheduleOptimalSyncUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val scheduler: OptimalSyncScheduler,
) : OptimalSyncCoordinator {
    override suspend fun immediateForCurrentSession(): Boolean =
        currentScope()?.also(scheduler::enqueueImmediate) != null

    override suspend fun periodicForCurrentSession(): Boolean =
        currentScope()?.also(scheduler::ensurePeriodic) != null

    override fun immediate(scope: OptimalSyncScope) {
        scheduler.enqueueImmediate(scope)
    }

    override fun periodic(scope: OptimalSyncScope) {
        scheduler.ensurePeriodic(scope)
    }

    override fun cancel(scope: OptimalSyncScope) {
        scheduler.cancel(scope)
    }

    override fun cancelAll() {
        scheduler.cancelAll()
    }

    private suspend fun currentScope(): OptimalSyncScope? {
        val snapshot = sessionReader.snapshot()
        val organizationId = snapshot.organization.id.trim()
        val userId = snapshot.user.id.trim()
        if (organizationId.isEmpty() || userId.isEmpty()) return null
        return OptimalSyncScope(organizationId = organizationId, userId = userId)
    }
}
