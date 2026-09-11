package com.verto.app.feature.reports.application

import com.verto.app.feature.reports.domain.model.CloseReportShiftCommand
import com.verto.app.feature.reports.domain.model.DeleteReportBudgetCommand
import com.verto.app.feature.reports.domain.model.SaveReportBudgetCommand
import com.verto.app.feature.reports.domain.model.StartReportShiftCommand

/** Reports writes are accepted only as explicit feature-owned commands. */
interface ReportsOperationsGateway {
    suspend fun saveBudget(command: SaveReportBudgetCommand)
    suspend fun deleteBudget(command: DeleteReportBudgetCommand)
    suspend fun recalculateRfm()
    suspend fun startShift(command: StartReportShiftCommand)
    suspend fun closeShift(command: CloseReportShiftCommand)
}
