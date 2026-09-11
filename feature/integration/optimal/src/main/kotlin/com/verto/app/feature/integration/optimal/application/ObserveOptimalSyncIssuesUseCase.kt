package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssue
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.domain.repository.OptimalOutboxRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOptimalSyncIssuesUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val accessSource: OptimalHomeAccessSource,
    private val repository: OptimalOutboxRepository,
) {
    operator fun invoke(): Flow<List<OptimalSyncIssue>> = combine(
        sessionReader.organizationId.map(String::trim).distinctUntilChanged(),
        accessSource.grantedPermissions,
    ) { organizationId, permissions ->
        organizationId to (ManagementOptimalPermission.VIEW_OPTIMAL_SYNC_ISSUES in permissions)
    }
        .distinctUntilChanged()
        .flatMapLatest { (organizationId, canView) ->
            if (organizationId.isBlank() || !canView) flowOf(emptyList())
            else repository.observeIssues(organizationId)
        }
}
