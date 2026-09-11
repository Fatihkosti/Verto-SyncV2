package com.verto.app.feature.reports.application.port

import com.verto.app.feature.reports.application.model.ReportCategoryExportRow
import com.verto.app.feature.reports.application.model.ReportItemExportRow
import java.io.File

interface ReportsDocumentExportPort {
    suspend fun createItemsPdf(rows: List<ReportItemExportRow>, title: String, range: String): File
    suspend fun createCategoriesPdf(rows: List<ReportCategoryExportRow>, title: String, range: String): File
}
