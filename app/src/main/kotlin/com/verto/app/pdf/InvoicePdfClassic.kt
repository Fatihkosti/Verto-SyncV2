package com.verto.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.repository.InvoiceSummary
import com.verto.app.data.repository.OrgSettings
import com.verto.app.utils.DateUtils
import com.verto.app.utils.InvoiceTemplate
import com.verto.app.core.format.AmountFormatter
import java.io.File

// ══════════════════════════════════════════════════════════════════
// توليد فاتورة مبيعات / مشتريات — 4 قوالب
// ══════════════════════════════════════════════════════════════════

internal fun drawClassic(
    doc: PdfDocument, client: PartyIdentityEntity, summary: InvoiceSummary, org: OrgSettings,
    orgName: String, orgAddr: String, orgPhone: String, employeeName: String,
    items: List<InvoiceItemEntity>,
    tf: Typeface, tfBold: Typeface, fsBase: Float, fsSmall: Float, fsTitle: Float,
    showCommission: Boolean = false
) {
    val invoice = summary.invoice; val accentHex = "#2563EB"
    val ptTitle    = paint("#000000", fsTitle, true,  tfBold)
    val ptSubtitle = paint(accentHex, fsBase,  true,  tfBold)
    val ptBody     = paint("#333333", fsBase,  false, tf)
    val ptMuted    = paint("#666666", fsSmall, false, tf)
    val ptHdrCell  = paint("#000000", fsSmall, true,  tfBold)
    val ptTotal    = paint("#000000", fsBase,  true,  tfBold)
    val ptTotalVal = paint(accentHex, fsBase,  true,  tfBold)
    val bgHdr    = bgPaint("#EFF6FF"); val bgTot = bgPaint("#DBEAFE")
    val lp       = linePaint("#93C5FD", 0.7f)
    val lpAccent = linePaint(accentHex, 1.5f); val lpLight = linePaint("#E5E7EB", 0.5f)
    val usableBottom = PageSizes.A4.height.toFloat() - BOTTOM_MARGIN

    val iCols = rtlCols(listOf("الصنف" to 240f, "الكمية" to 65f, "السعر" to 110f, "الإجمالي" to 100f))
    val iSeps = colSeps(iCols)

    fun drawColHeaders(canvas: Canvas, startY: Float): Float {
        drawRowBox(canvas, startY, ROW_H, bgHdr, lp, iSeps)
        iCols.forEach { col -> cellRight(canvas, col.label, col.s, col.e, startY, ROW_H, ptHdrCell) }
        return startY + ROW_H
    }

    var pageNum = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create())
    var cv = page.canvas

    // هيدر الصفحة الأولى فقط
    var y = 55f
    if (orgName.isNotBlank()) { drawCenteredOld(cv, orgName, y, ptTitle); y += fsTitle + 6f }
    if (orgAddr.isNotBlank())  { drawCenteredOld(cv, orgAddr, y, ptMuted);           y += fsSmall + 4f }
    if (orgPhone.isNotBlank()) { drawCenteredOld(cv, "هاتف: $orgPhone", y, ptMuted); y += fsSmall + 4f }
    if (org.taxNumber.isNotBlank()) { drawCenteredOld(cv, "رقم الضريبة: ${org.taxNumber}", y, ptMuted); y += fsSmall + 4f }
    y += 6f; cv.drawLine(MARGIN, y, PageSizes.A4.width - MARGIN, y, lpAccent); y += 12f
    drawCenteredOld(cv, "فاتورة مبيعات", y, ptSubtitle); y += fsBase + 8f
    val col1X = PageSizes.A4.width - MARGIN; val col2X = PageSizes.A4.width / 2f + 20f
    drawRightOld(cv, "رقم الفاتورة: #${invoice.invoiceNumber}", col1X, y, ptMuted)
    drawRightOld(cv, "التاريخ: ${DateUtils.formatDate(invoice.createdAt)}", col2X, y, ptMuted)
    y += fsSmall + 5f
    drawRightOld(cv, client.name, col1X, y, ptBody)
    if (client.phone.isNotBlank()) drawRightOld(cv, "هاتف: ${client.phone}", col2X, y, ptMuted)
    y += fsBase + 5f; cv.drawLine(MARGIN, y, PageSizes.A4.width - MARGIN, y, lpLight); y += 10f

    y = drawColHeaders(cv, y)

    // البنود مع ترقيم الصفحات
    items.forEach { item ->
        if (y + ROW_H > usableBottom) {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create())
            cv = page.canvas
            y = MARGIN
        }
        drawRowBox(cv, y, ROW_H, null, lpLight, iSeps)
        cellRight(cv, item.itemName,                               iCols[0].s, iCols[0].e, y, ROW_H, ptBody)
        cellCenter(cv, item.quantity.toString(),                   iCols[1].s, iCols[1].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(if (item.isOwedToMe) item.sellPrice else item.buyPrice), iCols[2].s, iCols[2].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(item.totalPrice), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
        y += ROW_H
    }

    // التحقق من أن الإجماليات والفوتر تسع في الصفحة الحالية
    val totalsH = 4f + 3 * ROW_H + 10f +
        (if (invoice.notes.isNotBlank()) fsSmall + 5f else 0f) +
        (if (org.invoiceFooter.isNotBlank()) fsSmall + 26f else 0f) +
        (if (employeeName.isNotBlank()) fsSmall + 5f else 0f)
    if (y + totalsH > usableBottom) {
        doc.finishPage(page)
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create())
        cv = page.canvas
        y = MARGIN
    }

    // الإجماليات والفوتر في الصفحة الأخيرة فقط
    cv.drawLine(MARGIN, y, PageSizes.A4.width - MARGIN, y, lpAccent); y += 4f
    val totSeps = listOf(iCols[3].e)
    drawRowBox(cv, y, ROW_H, bgTot, lp, totSeps)
    cellRight(cv, "الإجمالي", iCols[3].e, PageSizes.A4.width - MARGIN, y, ROW_H, ptTotal)
    cellRight(cv, "${AmountFormatter.format(invoice.totalAmount)} ${invoice.transactionCurrencyCode.ifBlank { org.currency }}", iCols[3].s, iCols[3].e, y, ROW_H, ptTotalVal)
    y += ROW_H
    drawRowBox(cv, y, ROW_H, null, lpLight, totSeps)
    cellRight(cv, "المدفوع", iCols[3].e, PageSizes.A4.width - MARGIN, y, ROW_H, ptBody)
    cellRight(cv, AmountFormatter.format(summary.totalPaid), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
    y += ROW_H
    drawRowBox(cv, y, ROW_H, null, lpLight, totSeps)
    cellRight(cv, "المتبقي", iCols[3].e, PageSizes.A4.width - MARGIN, y, ROW_H, ptBody)
    cellRight(cv, AmountFormatter.format(summary.remaining), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
    y += ROW_H + 10f

    if (invoice.notes.isNotBlank()) {
        val h = drawArabicTextRTL(cv, "ملاحظات: ${invoice.notes}", MARGIN, y, ptMuted, (PageSizes.A4.width - 2 * MARGIN).toInt())
        y += h + 5f
    }
    if (org.invoiceFooter.isNotBlank()) {
        y += 6f; cv.drawLine(MARGIN, y, PageSizes.A4.width - MARGIN, y, lpLight); y += 10f
        drawCenteredOld(cv, org.invoiceFooter, y, ptMuted); y += fsSmall + 5f
    }
    if (employeeName.isNotBlank()) { drawRightOld(cv, "فاتورة محررة من : $employeeName", PageSizes.A4.width - MARGIN, y, ptMuted); y += fsSmall + 5f }
    if (showCommission && invoice.commission > 0) {
        drawRightOld(cv, "* عمولة: ${AmountFormatter.format(invoice.commission)} ج", PageSizes.A4.width - MARGIN, y, ptMuted)
    }

    doc.finishPage(page)
}

// ══════════════════════════════════════════════════════════════════
// قالب ٢: MODERN
// ══════════════════════════════════════════════════════════════════

