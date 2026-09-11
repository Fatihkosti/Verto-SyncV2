package com.verto.app.feature.reports.application.model

import com.verto.app.data.model.EmployeePermissions

enum class ReportAccessLevel { SUMMARY, DETAILS, FULL }

fun reportAccessLevel(permissions: EmployeePermissions?): ReportAccessLevel? = when {
    permissions?.reportsFull == true -> ReportAccessLevel.FULL
    permissions?.reportsDetails == true -> ReportAccessLevel.DETAILS
    permissions?.reportsSummary == true -> ReportAccessLevel.SUMMARY
    else -> null
}
