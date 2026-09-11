package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecord
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordDraft

interface OptimalMaintenanceRepository {
    suspend fun create(
        organizationId: String,
        draft: MaintenanceRecordDraft,
    ): MaintenanceRecord

    suspend fun getByInvoice(
        organizationId: String,
        invoiceId: String,
    ): MaintenanceRecord?
}
