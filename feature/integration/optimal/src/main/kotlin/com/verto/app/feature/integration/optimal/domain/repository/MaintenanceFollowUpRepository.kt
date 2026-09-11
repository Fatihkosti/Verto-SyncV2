package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUpStatus
import com.verto.app.feature.integration.optimal.domain.model.OperationalMaintenanceFollowUp
import kotlinx.coroutines.flow.Flow

interface MaintenanceFollowUpRepository {
    suspend fun start(
        organizationId: String,
        recordId: String,
        startedAt: Long,
        expectedAt: Long?,
    ): MaintenanceFollowUp

    suspend fun updateStatus(
        organizationId: String,
        recordId: String,
        status: MaintenanceFollowUpStatus,
        updatedAt: Long,
    ): MaintenanceFollowUp?

    suspend fun updateExpectedAt(
        organizationId: String,
        recordId: String,
        expectedAt: Long?,
        updatedAt: Long,
    ): MaintenanceFollowUp?

    fun observeOperational(
        organizationId: String,
        nowMillis: Long,
    ): Flow<List<OperationalMaintenanceFollowUp>>
}
