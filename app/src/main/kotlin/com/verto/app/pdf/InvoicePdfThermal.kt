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

internal fun drawThermal(
    doc: PdfDocument, client: PartyIdentityEntity, summary: InvoiceSummary, org: OrgSettings,
    orgName: String, orgAddr: String, orgPhone: String, employeeName: String,
    items: List<InvoiceItemEntity>,
    tf: Typeface, tfBold: Typeface, fsBase: Float, fsSmall: Float, fsTitle: Float,
    showCommission: Boolean = false
) {
    val invoice = summary.invoice
    val tW = PageSizes.THERMAL_80.width; val tH = PageSizes.THERMAL_80.height
    val tMargin = 10f
    val tLeft = tMargin; val tRight = tW - tMargin; val tMid = tW / 2f
    val ptTitle = paint("#000000", fsBase, true,  tfBold).also { it.textAlign = Paint.Align.CENTER }
    val ptSm    = paint("#000000", fsSmall, false, tf).also    { it.textAlign = Paint.Align.CENTER }
    val ptBold  = paint("#000000", fsBase, true,  tfBold).also { it.textAlign = Paint.Align.CENTER }
    val ptRight = paint("#000000", fsSmall, false, tf).also  { it.textAlign = Paint.Align.RIGHT }
    val ptLeft  = paint("#000000", fsSmall, false, tf).also  { it.textAlign = Paint.Align.LEFT  }
    val ptRightB= paint("#000000", fsSmall, true,  tfBold).also { it.textAlign = Paint.Align.RIGHT }
    val itemH   = fsSmall + 4f
    val usableBottom = tH.toFloat() - BOTTOM_MARGIN

    fun dash(canvas: Canvas, y: Float) = canvas.drawLine(tLeft, y, tRight, y, linePaint("#888888", 0.5f))

    fun drawColHeaders(canvas: Canvas, startY: Float): Float {
        var y = startY
        canvas.drawText("الصنف",   tRight - 2f, y, ptRightB.also { it.textAlign = Paint.Align.RIGHT })
        canvas.drawText("ك",       tMid,        y, ptSm.also     { it.textAlign = Paint.Align.CENTER })
        canvas.drawText("المجموع", tLeft + 2f,  y, ptLeft.also   { it.isFakeBoldText = true })
        y += itemH; dash(canvas, y); y += 6f
        return y
    }

    var pageNum = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(tW, tH, pageNum).create())
    var cv = page.canvas

    // هيدر الصفحة الأولى فقط
    var y = 55f
    if (orgName.isNotBlank()) { cv.drawText(orgName, tMid, y, ptTitle); y += fsBase + 5f }
    if (orgAddr.isNotBlank())  { cv.drawText(orgAddr, tMid, y, ptSm);           y += fsSmall + 3f }
    if (orgPhone.isNotBlank()) { cv.drawText("هاتف: $orgPhone", tMid, y, ptSm); y += fsSmall + 3f }
    if (org.taxNumber.isNotBlank()) { cv.drawText("رقم الضريبة: ${org.taxNumber}", tMid, y, ptSm); y += fsSmall + 3f }
    y += 4f; dash(cv, y); y += 8f
    cv.drawText("فاتورة مبيعات", tMid, y, ptBold); y += fsBase + 4f
    cv.drawText("رقم: #${invoice.invoiceNumber}  |  ${DateUtils.formatDate(invoice.createdAt)}", tMid, y, ptSm)
    y += fsSmall + 3f
    cv.drawText(client.name, tMid, y, ptSm); y += fsSmall + 3f
    if (client.phone.isNotBlank()) { cv.drawText("هاتف: ${client.phone}", tMid, y, ptSm); y += fsSmall + 3f }
    dash(cv, y); y += 8f

    y = drawColHeaders(cv, y)

    // البنود مع ترقيم الصفحات
    items.forEach { item ->
        if (y + itemH > usableBottom) {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(tW, tH, pageNum).create())
            cv = page.canvas
            y = tMargin
        }
        cv.drawText(item.itemName,                                  tRight - 2f, y, ptRight)
        cv.drawText(item.quantity.toString(),                       tMid,        y, ptSm.also { it.textAlign = Paint.Align.CENTER })
        cv.drawText(AmountFormatter.format(item.totalPrice),    tLeft + 2f,  y, ptLeft)
        y += itemH
    }

    // التحقق من أن الإجماليات والفوتر تسع في الصفحة الحالية
    val totalsH = 8f + 3 * itemH + 8f +
        (if (invoice.notes.isNotBlank()) itemH else 0f) +
        (if (org.invoiceFooter.isNotBlank()) itemH else 0f) +
        (if (employeeName.isNotBlank()) itemH else 0f)
    if (y + totalsH > usableBottom) {
        doc.finishPage(page)
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(tW, tH, pageNum).create())
        cv = page.canvas
        y = tMargin
    }

    // الإجماليات والفوتر في الصفحة الأخيرة فقط
    dash(cv, y); y += 8f
    listOf(
        "الإجمالي" to "${AmountFormatter.format(invoice.totalAmount)} ${invoice.transactionCurrencyCode.ifBlank { org.currency }}",
        "المدفوع"  to AmountFormatter.format(summary.totalPaid),
        "المتبقي"  to AmountFormatter.format(summary.remaining)
    ).forEach { (label, value) ->
        cv.drawText(label, tRight - 2f, y, ptRightB.also { it.textAlign = Paint.Align.RIGHT })
        cv.drawText(value, tLeft + 2f,  y, ptLeft)
        y += itemH
    }
    dash(cv, y); y += 8f

    if (invoice.notes.isNotBlank()) { cv.drawText("ملاحظات: ${invoice.notes}", tMid, y, ptSm); y += itemH }
    if (org.invoiceFooter.isNotBlank()) { cv.drawText(org.invoiceFooter, tMid, y, ptSm); y += itemH }
    if (employeeName.isNotBlank()) { cv.drawText("فاتورة محررة من : $employeeName", tMid, y, ptSm); y += itemH }
    if (showCommission && invoice.commission > 0) {
        cv.drawText("* عمولة: ${AmountFormatter.format(invoice.commission)} ج", tMid, y, ptSm)
    }

    doc.finishPage(page)
}
