package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordQuery
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordsSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRecordsRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMaintenanceRecordsUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalMaintenanceRecordsRepository,
) {
    operator fun invoke(
        query: MaintenanceRecordQuery = MaintenanceRecordQuery(),
    ): Flow<MaintenanceRecordsSnapshot> {
        val normalizedQuery = query.normalized()
        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                if (organizationId.isBlank()) {
                    flowOf(MaintenanceRecordsSnapshot())
                } else {
                    repository.observeRecords(
                        organizationId = organizationId,
                        query = normalizedQuery,
                    )
                }
            }
    }
}
