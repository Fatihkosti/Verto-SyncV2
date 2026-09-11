package com.verto.app.feature.integration.optimal.data

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScope
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncSessionGuard
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionOptimalSyncSessionGuard @Inject constructor(
    private val sessionReader: SessionReader,
) : OptimalSyncSessionGuard {
    override suspend fun isActive(scope: OptimalSyncScope): Boolean {
        val current = sessionReader.snapshot()
        return current.organization.id == scope.organizationId &&
            current.user.id == scope.userId &&
            current.organization.isKnown &&
            current.user.isKnown
    }
}
