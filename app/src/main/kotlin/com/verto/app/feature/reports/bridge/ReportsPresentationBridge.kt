package com.verto.app.feature.reports.bridge

import android.content.Context
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.feature.reports.application.model.ReportCategoryExportRow
import com.verto.app.feature.reports.application.model.ReportItemExportRow
import com.verto.app.feature.reports.application.port.ReportsAccessPort
import com.verto.app.feature.reports.application.port.ReportsDocumentExportPort
import com.verto.app.pdf.CategoryAnalysis
import com.verto.app.pdf.ItemAnalysis
import com.verto.app.pdf.generateCategoryAnalysisPdf
import com.verto.app.pdf.generateItemsAnalysisPdf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ReportsAccessBridge @Inject constructor(
    private val permissionProvider: PermissionProvider,
) : ReportsAccessPort {
    override val permissions: StateFlow<com.verto.app.data.model.EmployeePermissions?> = permissionProvider.permissions
    override suspend fun canExportReports(): Boolean = permissionProvider.canNow { it.reportsExport }
}

class ReportsDocumentExportBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReportsDocumentExportPort {
    override suspend fun createItemsPdf(rows: List<ReportItemExportRow>, title: String, range: String): File =
        generateItemsAnalysisPdf(
            context,
            rows.map { ItemAnalysis(it.name, it.quantity, it.revenueMinor, it.profitMinor, it.currencyCode) },
            title,
            range,
        )

    override suspend fun createCategoriesPdf(rows: List<ReportCategoryExportRow>, title: String, range: String): File =
        generateCategoryAnalysisPdf(
            context,
            rows.map { CategoryAnalysis(it.category, it.distinctItems, it.revenueMinor, it.profitMinor, it.currencyCode) },
            title,
            range,
        )
}
