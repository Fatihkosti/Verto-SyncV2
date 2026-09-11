package com.verto.app.feature.reports.application

import com.verto.app.feature.reports.application.port.ReportsAccessPort
import javax.inject.Inject

class ReportsAccessService @Inject constructor(
    private val access: ReportsAccessPort,
) {
    val permissions get() = access.permissions
    suspend fun canExportReports(): Boolean = access.canExportReports()
}
