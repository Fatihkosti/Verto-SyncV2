package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.domain.model.OptimalBadgeCounts
import kotlinx.coroutines.flow.Flow

/** Read-only permission stream used by the Optimal home feature. */
interface OptimalHomeAccessSource {
    val grantedPermissions: Flow<Set<ManagementOptimalPermission>>
}

/** Local-only badge snapshot backed by the current persistent owners. */
interface OptimalHomeLocalCounters {
    val badgeCounts: Flow<OptimalBadgeCounts>
}
