package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetails
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceDetailsRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveMaintenanceDetailsUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalMaintenanceDetailsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(recordId: String): Flow<MaintenanceDetails?> {
        val normalizedRecordId = recordId.trim()
        if (normalizedRecordId.isEmpty()) return flowOf(null)
        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                if (organizationId.isBlank()) {
                    flowOf(null)
                } else {
                    repository.observeDetails(organizationId, normalizedRecordId)
                }
            }
    }
}
