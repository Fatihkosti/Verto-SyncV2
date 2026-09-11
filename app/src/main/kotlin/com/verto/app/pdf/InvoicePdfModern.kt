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

internal fun drawModern(
    doc: PdfDocument, client: PartyIdentityEntity, summary: InvoiceSummary, org: OrgSettings,
    orgName: String, orgAddr: String, orgPhone: String, employeeName: String,
    items: List<InvoiceItemEntity>,
    tf: Typeface, tfBold: Typeface, fsBase: Float, fsSmall: Float, fsTitle: Float,
    showCommission: Boolean = false
) {
    val invoice = summary.invoice; val accentHex = "#0F766E"; val headerHex = "#134E4A"
    val ptWhiteBold = paint("#FFFFFF", fsBase,  true,  tfBold)
    val ptWhite     = paint("#FFFFFF", fsSmall, false, tf)
    val ptBody      = paint("#1F2937", fsBase,  false, tf)
    val ptMuted     = paint("#6B7280", fsSmall, false, tf)
    val ptHdrCell   = paint("#FFFFFF", fsSmall, true,  tfBold)
    val ptTotal     = paint(accentHex, fsBase,  true,  tfBold)
    val bgHeader = bgPaint(headerHex); val bgRow = bgPaint(accentHex)
    val bgTot    = bgPaint("#CCFBF1"); val bgAlt = bgPaint("#F0FDF4")
    val lp       = linePaint("#99F6E4", 0.7f)
    val lpLight  = linePaint("#D1FAE5", 0.5f); val lpAccent = linePaint(accentHex, 1.5f)
    val usableBottom = PageSizes.A4.height.toFloat() - BOTTOM_MARGIN

    val iCols = rtlCols(listOf("الصنف" to 230f, "الكمية" to 65f, "السعر" to 115f, "الإجمالي" to 105f))
    val iSeps = colSeps(iCols)

    fun drawColHeaders(canvas: Canvas, startY: Float): Float {
        drawRowBox(canvas, startY, ROW_H, bgRow, lp, iSeps)
        iCols.forEach { col -> cellRight(canvas, col.label, col.s, col.e, startY, ROW_H, ptHdrCell) }
        return startY + ROW_H
    }

    var pageNum = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create())
    var cv = page.canvas

    // هيدر الصفحة الأولى فقط
    val headerH = 70f
    cv.drawRect(0f, 0f, PageSizes.A4.width.toFloat(), headerH, bgHeader)
    if (orgName.isNotBlank()) drawRightOld(cv, orgName, PageSizes.A4.width - MARGIN, 28f, ptWhiteBold)
    var headerInfoY = 28f + fsBase + 4f
    if (orgAddr.isNotBlank())  { drawRightOld(cv, orgAddr,            PageSizes.A4.width - MARGIN, headerInfoY, ptWhite); headerInfoY += fsSmall + 3f }
    if (orgPhone.isNotBlank()) { drawRightOld(cv, "هاتف: $orgPhone",  PageSizes.A4.width - MARGIN, headerInfoY, ptWhite) }
    val ptLeftWhite = paint("#FFFFFF", fsSmall, false, tf).apply { textAlign = Paint.Align.LEFT }
    cv.drawText("فاتورة #${invoice.invoiceNumber}", MARGIN, 28f, ptLeftWhite)
    cv.drawText(DateUtils.formatDate(invoice.createdAt), MARGIN, 28f + fsSmall + 4f, ptLeftWhite)

    var y = headerH + 34f
    drawRightOld(cv, client.name, PageSizes.A4.width - MARGIN, y, ptBody)
    if (client.phone.isNotBlank()) {
        val ptLeft = paint("#6B7280", fsSmall, false, tf).apply { textAlign = Paint.Align.LEFT }
        cv.drawText("هاتف: ${client.phone}", MARGIN, y, ptLeft)
    }
    y += fsBase + 8f
    cv.drawLine(MARGIN, y, PageSizes.A4.width - MARGIN, y, lpAccent); y += 10f

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
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lpLight, iSeps)
        cellRight(cv, item.itemName,                               iCols[0].s, iCols[0].e, y, ROW_H, ptBody)
        cellCenter(cv, item.quantity.toString(),                   iCols[1].s, iCols[1].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(if (item.isOwedToMe) item.sellPrice else item.buyPrice), iCols[2].s, iCols[2].e, y, ROW_H, ptBody)
        cellRight(cv, AmountFormatter.format(item.totalPrice), iCols[3].s, iCols[3].e, y, ROW_H, ptBody)
        y += ROW_H
    }

    // التحقق من أن الإجماليات والفوتر تسع في الصفحة الحالية
    val totalsH = 4f + 3 * ROW_H + 12f +
        (if (invoice.notes.isNotBlank()) fsSmall + 5f else 0f) +
        (if (org.taxNumber.isNotBlank()) fsSmall + 5f else 0f) +
        (if (org.invoiceFooter.isNotBlank()) fsSmall + 14f else 0f)
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
    if (org.taxNumber.isNotBlank()) { drawRightOld(cv, "رقم الضريبة: ${org.taxNumber}", PageSizes.A4.width - MARGIN, y, ptMuted); y += fsSmall + 5f }
    if (org.invoiceFooter.isNotBlank()) {
        val footerBg = bgPaint("#F0FDF4")
        cv.drawRect(MARGIN, y, PageSizes.A4.width - MARGIN, y + fsSmall + 14f, footerBg)
        drawCenteredOld(cv, org.invoiceFooter, y + fsSmall + 6f, ptMuted)
        y += fsSmall + 14f
    }
    if (showCommission && invoice.commission > 0) {
        drawRightOld(cv, "* عمولة: ${AmountFormatter.format(invoice.commission)} ج", PageSizes.A4.width - MARGIN, y, ptMuted)
    }

    doc.finishPage(page)
}

// ══════════════════════════════════════════════════════════════════
// قالب ٣: PROFESSIONAL
// ══════════════════════════════════════════════════════════════════

