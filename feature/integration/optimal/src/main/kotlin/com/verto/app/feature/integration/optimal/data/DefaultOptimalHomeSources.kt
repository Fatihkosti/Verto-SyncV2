package com.verto.app.feature.integration.optimal.data

import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.application.ObserveOptimalBadgesUseCase
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeLocalCounters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class DefaultOptimalHomeAccessSource @Inject constructor(
    permissionProvider: PermissionProvider,
) : OptimalHomeAccessSource {
    override val grantedPermissions: Flow<Set<ManagementOptimalPermission>> =
        permissionProvider.permissions
            .map { permissions ->
                ManagementOptimalPermission.entries
                    .filterTo(linkedSetOf()) { permission ->
                        permissions?.allows(permission) == true
                    }
            }
            .distinctUntilChanged()
}

/** Live local counters backed by the current Messages owner and durable Optimal Outbox. */
@Singleton
class DefaultOptimalHomeLocalCounters @Inject constructor(
    observeBadges: ObserveOptimalBadgesUseCase,
) : OptimalHomeLocalCounters {
    override val badgeCounts = observeBadges()
}
