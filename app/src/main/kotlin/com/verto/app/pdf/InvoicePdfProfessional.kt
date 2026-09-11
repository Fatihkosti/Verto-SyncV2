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

internal fun drawProfessional(
    doc: PdfDocument, client: PartyIdentityEntity, summary: InvoiceSummary, org: OrgSettings,
    orgName: String, orgAddr: String, orgPhone: String, employeeName: String,
    items: List<InvoiceItemEntity>,
    tf: Typeface, tfBold: Typeface, fsBase: Float, fsSmall: Float, fsTitle: Float,
    showCommission: Boolean = false
) {
    val invoice = summary.invoice; val accentHex = "#1E3A5F"
    val ptAccentBase = paint(accentHex, fsBase,  true,  tfBold)
    val ptBody       = paint("#1F2937", fsBase,  false, tf)
    val ptMuted      = paint("#6B7280", fsSmall, false, tf)
    val ptHdrCell    = paint("#FFFFFF", fsSmall, true,  tfBold)
    val ptTotal      = paint(accentHex, fsBase,  true,  tfBold)
    val bgHdr    = bgPaint(accentHex); val bgTot = bgPaint("#E8EEF7"); val bgClient = bgPaint("#F8FAFC")
    val lp       = linePaint("#94A3B8", 0.7f)
    val lpAccent = linePaint(accentHex, 2f); val lpLight = linePaint("#E2E8F0", 0.5f)
    val usableBottom = PageSizes.A4.height.toFloat() - BOTTOM_MARGIN

    val iCols = rtlCols(listOf("الصنف" to 235f, "الكمية" to 65f, "السعر" to 110f, "الإجمالي" to 105f))
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
    var y = 50f
    val ptLeftBig = paint(accentHex, fsTitle, true, tfBold).apply { textAlign = Paint.Align.LEFT }
    cv.drawText("فاتورة مبيعات", MARGIN, y, ptLeftBig)
    val ptLeftSm = paint("#6B7280", fsSmall, false, tf).apply { textAlign = Paint.Align.LEFT }
    cv.drawText("رقم: #${invoice.invoiceNumber}  |  ${DateUtils.formatDate(invoice.createdAt)}", MARGIN, y + fsTitle + 4f, ptLeftSm)
    if (orgName.isNotBlank()) drawRightOld(cv, orgName, PageSizes.A4.width - MARGIN, y, ptAccentBase)
    if (orgAddr.isNotBlank())  drawRightOld(cv, orgAddr,           PageSizes.A4.width - MARGIN, y + fsBase + 4f, ptMuted)
    if (orgPhone.isNotBlank()) drawRightOld(cv, "هاتف: $orgPhone", PageSizes.A4.width - MARGIN, y + fsBase + 4f + fsSmall + 3f, ptMuted)
    y += fsTitle + 4f + fsSmall + 3f + fsSmall + 12f
    cv.drawRect(MARGIN, y, PageSizes.A4.width - MARGIN, y + 2.5f, bgPaint(accentHex)); y += 2.5f + 10f
    val boxH = fsBase + 22f
    cv.drawRect(MARGIN, y, PageSizes.A4.width - MARGIN, y + boxH, bgClient)
    cv.drawRect(MARGIN, y, PageSizes.A4.width - MARGIN, y + boxH, linePaint("#CBD5E1", 0.7f))
    cv.drawRect(PageSizes.A4.width - MARGIN - 3f, y, PageSizes.A4.width - MARGIN, y + boxH, bgPaint(accentHex))
    drawRightOld(cv, client.name, PageSizes.A4.width - MARGIN - 8f, y + fsBase + 8f, ptBody)
    if (client.phone.isNotBlank()) {
        val ptPhoneLeft = paint("#6B7280", fsSmall, false, tf).apply { textAlign = Paint.Align.LEFT }
        cv.drawText("هاتف: ${client.phone}", MARGIN + 8f, y + fsBase + 8f, ptPhoneLeft)
    }
    y += boxH + 12f

    y = drawColHeaders(cv, y)

    // البنود مع ترقيم الصفحات
    items.forEachIndexed { idx, item ->
        if (y + ROW_H > usableBottom) {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create())
            cv = page.canvas
            y = MARGIN
        }
        val rowBg = if (idx % 2 == 0) null else bgPaint("#F8FAFC")
        drawRowBox(cv, y, ROW_H, rowBg, lpLight, iSeps)
        cellRight(cv, item.itemName,                               iCols[0].s, iCols[0].e, y, ROW_H, ptBody)
        cellCenter(cv, item.quantity.toString(),                   iCols[1].s, iCols[1].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(if (item.isOwedToMe) item.sellPrice else item.buyPrice), iCols[2].s, iCols[2].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(item.totalPrice), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
        y += ROW_H
    }

    // التحقق من أن الإجماليات والفوتر تسع في الصفحة الحالية
    val totalsH = 2f + 3 * ROW_H + 12f +
        (if (invoice.notes.isNotBlank()) fsSmall + 5f else 0f) +
        8f + fsSmall + 5f
    if (y + totalsH > usableBottom) {
        doc.finishPage(page)
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create())
        cv = page.canvas
        y = MARGIN
    }

    // الإجماليات والفوتر في الصفحة الأخيرة فقط
    cv.drawRect(MARGIN, y, PageSizes.A4.width - MARGIN, y + 2f, bgPaint(accentHex)); y += 2f + 4f
    val totSeps = listOf(iCols[3].e)
    drawRowBox(cv, y, ROW_H, bgTot, lp, totSeps)
    cellRight(cv, "الإجمالي", iCols[3].e, PageSizes.A4.width - MARGIN, y, ROW_H, ptTotal)
    cellRight(cv, "${AmountFormatter.format(invoice.totalAmount)} ${invoice.transactionCurrencyCode.ifBlank { org.currency }}", iCols[3].s, iCols[3].e, y, ROW_H, ptTotal)
    y += ROW_H
    drawRowBox(cv, y, ROW_H, null, lpLight, totSeps)
    cellRight(cv, "المدفوع", iCols[3].e, PageSizes.A4.width - MARGIN, y, ROW_H, ptBody)
    cellRight(cv, AmountFormatter.format(summary.totalPaid), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
    y += ROW_H
    drawRowBox(cv, y, ROW_H, null, lpLight, totSeps)
    cellRight(cv, "المتبقي", iCols[3].e, PageSizes.A4.width - MARGIN, y, ROW_H, ptBody)
    cellRight(cv, AmountFormatter.format(summary.remaining), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
    y += ROW_H + 12f

    if (invoice.notes.isNotBlank()) {
        val h = drawArabicTextRTL(cv, "ملاحظات: ${invoice.notes}", MARGIN, y, ptMuted, (PageSizes.A4.width - 2 * MARGIN).toInt())
        y += h + 5f
    }
    cv.drawLine(MARGIN, y, PageSizes.A4.width - MARGIN, y, lpLight); y += 8f
    if (org.taxNumber.isNotBlank()) {
        val ptLeft = paint("#6B7280", fsSmall, false, tf).apply { textAlign = Paint.Align.LEFT }
        cv.drawText("رقم الضريبة: ${org.taxNumber}", MARGIN, y, ptLeft)
    }
    if (org.invoiceFooter.isNotBlank()) { drawCenteredOld(cv, org.invoiceFooter, y, ptMuted); y += fsSmall + 5f }
    if (employeeName.isNotBlank()) { drawRightOld(cv, "فاتورة محررة من : $employeeName", PageSizes.A4.width - MARGIN, y, ptMuted); y += fsSmall + 5f }
    if (showCommission && invoice.commission > 0) {
        drawRightOld(cv, "* عمولة: ${AmountFormatter.format(invoice.commission)} ج", PageSizes.A4.width - MARGIN, y, ptMuted)
    }

    doc.finishPage(page)
}

// ══════════════════════════════════════════════════════════════════
// قالب ٤: THERMAL
// ══════════════════════════════════════════════════════════════════

