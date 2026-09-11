package com.verto.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.feature.commission.application.MarketerCommissionReport
import com.verto.app.feature.commission.application.MarketerCommissionRow
import com.verto.app.utils.DateUtils
import com.verto.app.utils.MoneyMath
import com.verto.app.core.format.AmountFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ─── 5.2: تقرير عمولات شهري لكل مسوّق ─────────────────────────────────────────

// تحويل timestamptz (UTC من PostgREST) إلى epoch millis — أول 19 محرفاً تكفي لفلتر التاريخ.
private fun parseEligibilityIsoMillis(iso: String?): Long? = iso?.let {
    runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(it.take(19))?.time
    }.getOrNull()
}

/**
 * يبني تقرير عمولات مسوّق واحد من صفوف الأهلية السيرفرية (مصدر الحقيقة)، محترماً فلتر التاريخ
 * إن وُجد. دالة نقية مشتركة بين شاشة العمولات (PDF) وشاشة التقرير الداخلية.
 */
fun buildMarketerCommissionReport(
    rows: List<CommissionEligibilityDto>,
    clientId: String,
    clientName: String,
    from: Long?,
    to: Long?
): MarketerCommissionReport {
    val filtered = rows
        .filter { it.clientId == clientId }
        .filter { from == null || to == null || (parseEligibilityIsoMillis(it.createdAt)?.let { m -> m in from..to } ?: true) }
        .sortedByDescending { parseEligibilityIsoMillis(it.createdAt) ?: 0L }

    val reportRows = filtered.map { r ->
        MarketerCommissionRow(
            invoiceNumber = r.invoiceNumber,
            dateLabel     = parseEligibilityIsoMillis(r.createdAt)?.let { DateUtils.formatDate(it) } ?: "—",
            invoiceTotal  = r.totalAmount.toRemoteDouble(),
            commission    = r.commission.toRemoteDouble(),
            statusLabel   = when (r.eligibility) {
                "WITHDRAWABLE" -> "قابلة للسحب"
                "PAID"         -> "مدفوعة"
                "PENDING"      -> "قيد الانتظار"
                else           -> r.eligibility
            }
        )
    }

    fun sumWhere(pred: (CommissionEligibilityDto) -> Boolean): Double =
        with(MoneyMath) { filtered.filter(pred).map { it.commission.toRemoteDouble() }.moneySum() }

    val periodLabel = if (from != null && to != null)
        "${DateUtils.formatDate(from)} — ${DateUtils.formatDate(to)}" else "كل الفترات"

    return MarketerCommissionReport(
        marketerName      = clientName,
        periodLabel       = periodLabel,
        rows              = reportRows,
        totalCommission   = sumWhere { true },
        withdrawableTotal = sumWhere { it.eligibility == "WITHDRAWABLE" },
        paidTotal         = sumWhere { it.eligibility == "PAID" },
        pendingTotal      = sumWhere { it.eligibility == "PENDING" }
    )
}

/**
 * يولّد PDF لتقرير عمولات مسوّق واحد ضمن فترة محددة، معتمداً على بيانات الأهلية
 * السيرفرية (مصدر الحقيقة). الأرقام منسّقة بلا أي رمز عملة (قاعدة المالك).
 */
fun generateMarketerCommissionPdf(
    context: Context,
    report: MarketerCommissionReport
): File {
    val doc = PdfDocument(); var pageNum = 1
    val ptTitle = paint("#1A1A2E", 18f, true); val ptHdr = paint("#FFFFFF", 12f, true)
    val ptBody  = paint("#333333", 11f);        val ptMuted = paint("#666666", 10f)
    val ptTotal = paint("#1A1A2E", 12f, true)
    val bgHeader = bgPaint("#1A1A2E"); val bgAlt = bgPaint("#F5F5F5"); val bgTotal = bgPaint("#E8EAF6")
    val lp = linePaint("#CCCCCC", 0.5f); val lpTotal = linePaint("#1A1A2E", 1f)
    val cols = rtlCols(listOf(
        "رقم" to 55f, "التاريخ" to 110f, "الإجمالي" to 110f,
        "العمولة" to 100f, "الحالة" to 140f
    ))
    val seps = colSeps(cols)
    val title = "تقرير عمولات — ${report.marketerName}"

    fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create()
        val page = doc.startPage(info); val cv = page.canvas
        val y = drawReportHeader(cv, title, report.periodLabel, ptTitle, ptMuted)
        drawRowBox(cv, y, ROW_H, bgHeader, linePaint("#1A1A2E", 0f), seps)
        cols.forEach { col -> cellRight(cv, col.label, col.s, col.e, y, ROW_H, ptHdr) }
        return page to cv
    }

    var (curPage, cv) = newPage(); var y = 65f + 30f + 20f + ROW_H + 5f
    report.rows.forEachIndexed { idx, r ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage); pageNum++
            val np = newPage(); curPage = np.first; cv = np.second; y = 65f + 30f + 20f + ROW_H + 5f
        }
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lp, seps)
        cellRight(cv, "#${r.invoiceNumber}", cols[0].s, cols[0].e, y, ROW_H, ptBody)
        cellRight(cv, r.dateLabel, cols[1].s, cols[1].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(r.invoiceTotal), cols[2].s, cols[2].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(r.commission), cols[3].s, cols[3].e, y, ROW_H, ptBody)
        cellRight(cv, r.statusLabel, cols[4].s, cols[4].e, y, ROW_H, ptBody)
        y += ROW_H
    }

    // صف الإجمالي
    y += 5f; drawRowBox(cv, y, ROW_H, bgTotal, lpTotal, seps)
    cellRight(cv, "إجمالي العمولة", cols[1].s, cols[1].e, y, ROW_H, ptTotal)
    cellRight(cv, AmountFormatter.format(report.totalCommission), cols[3].s, cols[3].e, y, ROW_H, ptTotal)

    // ملخص الحالات أسفل الجدول
    y += ROW_H + 14f
    val ptSummary = paint("#1A1A2E", 11f, true)
    val summary = "قابلة للسحب: ${AmountFormatter.format(report.withdrawableTotal)}   |   " +
        "مدفوعة: ${AmountFormatter.format(report.paidTotal)}   |   " +
        "قيد الانتظار: ${AmountFormatter.format(report.pendingTotal)}"
    ptSummary.textAlign = android.graphics.Paint.Align.RIGHT
    cv.drawText(summary, PageSizes.A4.width - MARGIN, y, ptSummary)

    drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage)
    val file = File(context.cacheDir, "commission_report_${System.currentTimeMillis()}.pdf")
    try { file.outputStream().use { doc.writeTo(it) } } finally { doc.close() }
    return file
}
