package com.verto.app.feature.reports.application

import com.verto.app.feature.reports.application.model.ReportsFilters
import com.verto.app.feature.reports.application.model.ReportsReadModel
import com.verto.app.utils.ReportPeriod
import kotlinx.coroutines.flow.Flow

/**
 * Reports application read boundary. Infrastructure returns an application-owned read model;
 * presentation remains responsible for converting it to UI state.
 */
data class ReportsReadRequest(
    val period: ReportPeriod,
    val from: Long,
    val to: Long,
    val filters: ReportsFilters = ReportsFilters()
)

interface ReportsReadModelQuery {
    fun observe(request: ReportsReadRequest): Flow<ReportsReadModel>
}
