package com.verto.app.feature.reports.application

import com.verto.app.feature.reports.application.model.ReportCategoryExportRow
import com.verto.app.feature.reports.application.model.ReportItemExportRow
import com.verto.app.feature.reports.application.port.ReportsDocumentExportPort
import javax.inject.Inject

class ReportsDocumentExportService @Inject constructor(
    private val exportPort: ReportsDocumentExportPort,
) {
    suspend fun createItemsPdf(rows: List<ReportItemExportRow>, title: String, range: String) =
        exportPort.createItemsPdf(rows, title, range)

    suspend fun createCategoriesPdf(rows: List<ReportCategoryExportRow>, title: String, range: String) =
        exportPort.createCategoriesPdf(rows, title, range)
}
