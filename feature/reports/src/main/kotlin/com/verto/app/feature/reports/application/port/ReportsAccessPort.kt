package com.verto.app.feature.reports.application.port

import com.verto.app.data.model.EmployeePermissions
import kotlinx.coroutines.flow.StateFlow

interface ReportsAccessPort {
    val permissions: StateFlow<EmployeePermissions?>
    suspend fun canExportReports(): Boolean
}
