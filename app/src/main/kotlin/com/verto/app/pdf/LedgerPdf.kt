package com.verto.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.verto.app.utils.DateUtils
import com.verto.app.core.format.AmountFormatter
import java.io.File

// ══════════════════════════════════════════════════════════════════
// بيانات كشف الحساب
// ══════════════════════════════════════════════════════════════════

data class LedgerEntry(
    val date       : Long,
    val description: String,
    val debit      : Double,   // علينا
    val credit     : Double,   // لنا
)

// ══════════════════════════════════════════════════════════════════
// كشف حساب العميل / المورد
// ══════════════════════════════════════════════════════════════════

fun generateLedgerPdf(
    context    : Context,
    clientName : String,
    isSupplier : Boolean,
    entries    : List<LedgerEntry>,
    orgName    : String = ""
): File {
    val doc = PdfDocument(); var pageNum = 1
    val ptTitle  = paint("#1A1A2E", 16f, true)
    val ptSub    = paint("#666666", 11f)
    val ptHdr    = paint("#FFFFFF", 11f, true)
    val ptBody   = paint("#333333", 11f)
    val ptBold   = paint("#1A1A2E", 11f, true)
    val ptMuted  = paint("#888888", 10f)
    val ptRed    = paint("#DC2626", 12f, true)
    val ptGreen  = paint("#16A34A", 12f, true)
    val bgHeader = bgPaint("#1A1A2E")
    val bgAlt    = bgPaint("#F8F8F8")
    val bgTotal  = bgPaint("#E8EAF6")
    val lp       = linePaint("#CCCCCC", 0.5f)
    val lpTotal  = linePaint("#1A1A2E", 1f)

    val cols = rtlCols(listOf(
        "الرصيد"  to 95f,
        "لنا"     to 85f,
        "علينا"   to 85f,
        "البيان"  to 195f,
        "التاريخ" to 95f
    ))
    val seps = colSeps(cols)

    fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create()
        val page = doc.startPage(info); val cv = page.canvas
        var y = 50f
        ptTitle.textAlign = Paint.Align.CENTER
        cv.drawText("كشف حساب — $clientName", PageSizes.A4.width / 2f, y, ptTitle); y += 24f
        if (orgName.isNotBlank()) {
            ptSub.textAlign = Paint.Align.CENTER
            cv.drawText(orgName, PageSizes.A4.width / 2f, y, ptSub); y += 18f
        }
        ptSub.textAlign = Paint.Align.CENTER
        cv.drawText(if (isSupplier) "مورد" else "عميل", PageSizes.A4.width / 2f, y, ptSub); y += 22f
        drawRowBox(cv, y, ROW_H, bgHeader, linePaint("#1A1A2E", 0f), seps)
        cols.forEach { col -> cellRight(cv, col.label, col.s, col.e, y, ROW_H, ptHdr) }
        return page to cv
    }

    var (curPage, cv) = newPage()
    var startY = 50f + 24f + 18f + 22f + ROW_H + 4f
    if (orgName.isBlank()) startY -= 18f
    var y = startY
    var runningBalance = 0.0

    entries.forEach { entry ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pageNum, ptMuted)
            doc.finishPage(curPage); pageNum++
            val np = newPage(); curPage = np.first; cv = np.second; y = startY
        }
        runningBalance += entry.debit - entry.credit
        val idx = entries.indexOf(entry)
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lp, seps)
        cellRight(cv, DateUtils.formatDate(entry.date), cols[4].s, cols[4].e, y, ROW_H, ptMuted)
        cellRight(cv, entry.description, cols[3].s, cols[3].e, y, ROW_H, ptBody)
        if (entry.debit > 0)
            cellRight(cv, AmountFormatter.format(entry.debit), cols[2].s, cols[2].e, y, ROW_H, ptRed)
        if (entry.credit > 0)
            cellRight(cv, AmountFormatter.format(entry.credit), cols[1].s, cols[1].e, y, ROW_H, ptGreen)
        val balPaint = if (runningBalance > 0) ptRed else ptGreen
        cellRight(cv, AmountFormatter.format(kotlin.math.abs(runningBalance)), cols[0].s, cols[0].e, y, ROW_H, balPaint)
        y += ROW_H
    }

    if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
        drawFooter(cv, pageNum, ptMuted)
        doc.finishPage(curPage); pageNum++
        val np = newPage(); curPage = np.first; cv = np.second; y = startY
    }
    y += 4f
    drawRowBox(cv, y, ROW_H, bgTotal, lpTotal, seps)
    cellRight(cv, "الإجمالي", cols[3].s, cols[3].e, y, ROW_H, ptBold)
    cellRight(cv, AmountFormatter.format(entries.sumOf { it.debit }),  cols[2].s, cols[2].e, y, ROW_H, ptRed)
    cellRight(cv, AmountFormatter.format(entries.sumOf { it.credit }), cols[1].s, cols[1].e, y, ROW_H, ptGreen)
    val finalBal = entries.sumOf { it.debit } - entries.sumOf { it.credit }
    val finalPaint = if (finalBal > 0) ptRed else ptGreen
    cellRight(cv, AmountFormatter.format(kotlin.math.abs(finalBal)), cols[0].s, cols[0].e, y, ROW_H, finalPaint)

    drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage)
    val file = File(context.cacheDir, "ledger_${clientName}_${System.currentTimeMillis()}.pdf")
    try {
        file.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    return file
}
