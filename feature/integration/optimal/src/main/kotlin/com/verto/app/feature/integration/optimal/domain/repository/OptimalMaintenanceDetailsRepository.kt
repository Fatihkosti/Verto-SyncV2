package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetails
import kotlinx.coroutines.flow.Flow

interface OptimalMaintenanceDetailsRepository {
    fun observeDetails(
        organizationId: String,
        recordId: String,
    ): Flow<MaintenanceDetails?>
}
