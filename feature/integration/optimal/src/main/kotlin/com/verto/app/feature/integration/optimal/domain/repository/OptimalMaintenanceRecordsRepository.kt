package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordQuery
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordsSnapshot
import kotlinx.coroutines.flow.Flow

interface OptimalMaintenanceRecordsRepository {
    fun observeRecords(
        organizationId: String,
        query: MaintenanceRecordQuery,
    ): Flow<MaintenanceRecordsSnapshot>
}
