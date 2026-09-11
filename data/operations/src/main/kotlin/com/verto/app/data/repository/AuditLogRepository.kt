package com.verto.app.data.repository

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditMaintenancePort
import com.verto.app.core.audit.domain.AuditTable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.verto.app.data.local.dao.AuditLogDao
import com.verto.app.data.local.entity.AuditLogEntity
import com.verto.app.utils.FeatureFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogRepository(
    private val dao: AuditLogDao,
    private val maintenance: AuditMaintenancePort
) {

    // ── قراءة ─────────────────────────────────────────────────────────────────

    fun getAll(): Flow<List<AuditLogEntity>> = dao.getAll()

    fun getFilteredPaged(table: AuditTable?, undoOnly: Boolean) =
        dao.getFilteredPaged(table?.name ?: "", if (undoOnly) 1 else 0)

    fun getRecent(limit: Int = 100): Flow<List<AuditLogEntity>> = dao.getRecent(limit)

    fun getByTable(table: AuditTable): Flow<List<AuditLogEntity>> = dao.getByTable(table)

    fun getByRecord(recordId: String): Flow<List<AuditLogEntity>> = dao.getByRecord(recordId)

    fun getUndoable(): Flow<List<AuditLogEntity>> = dao.getUndoable()

    suspend fun getAllSync(): List<AuditLogEntity> = dao.getAllSync()

    // ── تقارير ───────────────────────────────────────────────────────────────

    /** الحذوفات فقط — "من حذف ماذا ومتى؟" */
    fun getDeletions(): Flow<List<AuditLogEntity>> =
        dao.getAll().map { list -> list.filter { it.action == AuditAction.DELETE } }

    suspend fun getDeletionsSummary(): List<AuditLogEntity> =
        dao.getAllSync().filter { it.action == AuditAction.DELETE }
            .sortedByDescending { it.createdAt }

    suspend fun getPaymentAuditTrail(): List<AuditLogEntity> =
        dao.getAllSync()
            .filter { it.auditTable == AuditTable.PAYMENT }
            .sortedByDescending { it.createdAt }

    suspend fun getActivityByEmployee(): Map<String, List<AuditLogEntity>> =
        dao.getAllSync()
            .groupBy { it.employeeName.ifBlank { "غير معروف" } }

    suspend fun getModifiedRecords(): List<AuditLogEntity> =
        dao.getAllSync().filter { it.action == AuditAction.UPDATE }
            .sortedByDescending { it.createdAt }

    // ── إجراءات ──────────────────────────────────────────────────────────────

    suspend fun markAsUndone(id: String) = dao.markAsUndone(id)

    suspend fun expireOldEntries() = maintenance.expireOldEntries()

    // ── تصدير PDF ────────────────────────────────────────────────────────────

    suspend fun exportToPdf(context: Context, entries: List<AuditLogEntity>): File =
        withContext(Dispatchers.IO) {
            if (!FeatureFlags.isAuditLogExportEnabled)
                error("تصدير سجل التعديلات غير متاح في هذه النسخة")

            val doc = PdfDocument()
            try {
                val pageWidth  = 595
                val pageHeight = 842
                val margin     = 32f
                val lineHeight = 18f
                var pageNum    = 1
                var y          = margin + 40f

                val titlePaint = Paint().apply {
                    color     = Color.BLACK
                    textSize  = 14f
                    isFakeBoldText = true
                    isAntiAlias = true
                    textAlign = Paint.Align.RIGHT
                }
                val textPaint = Paint().apply {
                    color    = Color.DKGRAY
                    textSize = 10f
                    isAntiAlias = true
                    textAlign = Paint.Align.RIGHT
                }
                val linePaint = Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 0.5f
                }

                fun newPage(): Canvas {
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create()
                    val page = doc.startPage(info)
                    val c    = page.canvas

                    c.drawText("سجل التعديلات — Verto", (pageWidth - margin), margin + 10f, titlePaint)
                    c.drawLine(margin, margin + 20f, (pageWidth - margin), margin + 20f, linePaint)
                    return c
                }

                val sdf  = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                var page = doc.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create()
                )
                var canvas = page.canvas
                canvas.drawText("سجل التعديلات — Verto", (pageWidth - margin), y - 20f, titlePaint)
                canvas.drawLine(margin, y - 8f, (pageWidth - margin), y - 8f, linePaint)

                entries.forEach { entry ->
                    if (y + lineHeight * 4 > pageHeight - margin) {
                        doc.finishPage(page)
                        page   = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create())
                        canvas = page.canvas
                        y      = margin + 20f
                    }

                    val actionLabel = "${entry.action.label} — ${entry.auditTable.label}"
                    canvas.drawText(actionLabel, (pageWidth - margin), y, titlePaint)
                    y += lineHeight

                    canvas.drawText(entry.recordSummary.take(80), (pageWidth - margin), y, textPaint)
                    y += lineHeight

                    val meta = "الموظف: ${entry.employeeName.ifBlank { "غير معروف" }}  |  ${sdf.format(Date(entry.createdAt))}"
                    canvas.drawText(meta, (pageWidth - margin), y, textPaint)
                    y += lineHeight

                    canvas.drawLine(margin, y, (pageWidth - margin), y, linePaint)
                    y += lineHeight * 0.5f
                }
                doc.finishPage(page)

                val dir  = File(context.cacheDir, "audit_exports").apply { mkdirs() }
                val name = "audit_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"
                val file = File(dir, name)
                file.outputStream().use { doc.writeTo(it) }
                file
            } finally {
                doc.close()
            }
        }
}
