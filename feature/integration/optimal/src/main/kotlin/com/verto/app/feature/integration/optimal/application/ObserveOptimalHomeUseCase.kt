package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeLocalCounters
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveOptimalHomeUseCase @Inject constructor(
    private val accessSource: OptimalHomeAccessSource,
    private val localCounters: OptimalHomeLocalCounters,
) {
    operator fun invoke(): Flow<OptimalHomeSnapshot> = combine(
        accessSource.grantedPermissions,
        localCounters.badgeCounts,
    ) { permissions, counts ->
        OptimalHomeSnapshot(
            grantedPermissions = permissions,
            unreadMessages = counts.unreadMessages,
            syncIssues = counts.syncIssues,
        )
    }
}

data class OptimalHomeSnapshot(
    val grantedPermissions: Set<ManagementOptimalPermission>,
    val unreadMessages: Int,
    val syncIssues: Int,
)
