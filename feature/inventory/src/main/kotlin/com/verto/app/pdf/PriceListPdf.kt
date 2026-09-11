package com.verto.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.verto.app.core.format.AmountFormatter
import com.verto.app.utils.InvoiceFont
import java.io.File

/** Organization/user metadata shown on the customer-facing price list. */
data class ShopInfo(
    val name: String,
    val address: String,
    val phone: String,
    val userName: String = "",
    val userPhone: String = "",
)

/** Live inventory projection used only during export. */
data class PriceItem(
    val name: String,
    val price: Double,
    val partNumber: String = "",
)

private const val HEADER_HEIGHT = 118f
private const val TABLE_ROW_HEIGHT = 30f
private val PRICE_COLUMNS_WITH_PRICE = listOf(
    "#" to 35f,
    "الصنف" to 275f,
    "رقم القطعة" to 105f,
    "السعر" to 100f,
)
private val PRICE_COLUMNS_WITHOUT_PRICE = listOf(
    "#" to 35f,
    "الصنف" to 375f,
    "رقم القطعة" to 105f,
)

/**
 * One canonical A4 layout for price lists.
 * Template names never enter the PDF: selection templates are an internal authoring tool only.
 */
fun generatePriceListPdf(
    context: Context,
    shop: ShopInfo,
    items: List<PriceItem>,
    dateString: String? = null,
    includePrices: Boolean = true,
    font: InvoiceFont = InvoiceFont.CAIRO,
    fontSize: Int = 14,
): File {
    require(items.isNotEmpty()) { "PRICE_LIST_EMPTY" }

    val document = PdfDocument()
    val regular = invoiceTypeface(context, font, false)
    val bold = invoiceTypeface(context, font, true)
    val bodySize = fontSize.coerceIn(10, 18).toFloat()
    val titlePaint = paint("#162A46", bodySize + 5f, true, bold)
    val subtitlePaint = paint("#556274", (bodySize - 2f).coerceAtLeast(9f), false, regular)
    val tableHeaderPaint = paint("#FFFFFF", bodySize - 1f, true, bold)
    val bodyPaint = paint("#172033", bodySize - 1f, false, regular)
    val pricePaint = paint("#162A46", bodySize - 1f, true, bold)
    val mutedPaint = paint("#758195", (bodySize - 3f).coerceAtLeast(8f), false, regular)
    val borderPaint = linePaint("#D7DDE5", 0.6f)
    val columns = rtlCols(if (includePrices) PRICE_COLUMNS_WITH_PRICE else PRICE_COLUMNS_WITHOUT_PRICE)
    val separators = colSeps(columns)

    var pageNumber = 1

    fun cellRightFitted(canvas: Canvas, text: String, start: Float, end: Float, rowY: Float, rowH: Float, p: Paint) {
        val availableWidth = (end - start - 12f).coerceAtLeast(1f)
        val fitted = TextUtils.ellipsize(text, TextPaint(p), availableWidth, TextUtils.TruncateAt.END).toString()
        cellRight(canvas, fitted, start, end, rowY, rowH, p)
    }

    fun drawPageHeader(canvas: Canvas): Float {
        canvas.drawRect(0f, 0f, PageSizes.A4.width.toFloat(), 5f, bgPaint("#162A46"))
        drawCenteredOld(canvas, shop.name.ifBlank { "Verto" }, 34f, titlePaint)
        drawCenteredOld(canvas, "كشف أسعار", 58f, pricePaint)

        var metaY = 78f
        val locationLine = listOf(shop.address, shop.phone).filter { it.isNotBlank() }.joinToString("  |  ")
        if (locationLine.isNotBlank()) {
            drawCenteredOld(canvas, locationLine, metaY, subtitlePaint)
            metaY += 16f
        }
        val authorLine = listOf(shop.userName, shop.userPhone).filter { it.isNotBlank() }.joinToString("  |  ")
        if (authorLine.isNotBlank()) {
            drawCenteredOld(canvas, authorLine, metaY, mutedPaint)
        }
        dateString?.let {
            val saved = mutedPaint.textAlign
            mutedPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(it, MARGIN, 103f, mutedPaint)
            mutedPaint.textAlign = saved
        }

        val headerY = HEADER_HEIGHT
        drawRowBox(canvas, headerY, ROW_H, bgPaint("#162A46"), linePaint("#162A46", 0f), separators)
        columns.forEach { col -> cellRight(canvas, col.label, col.s, col.e, headerY, ROW_H, tableHeaderPaint) }
        return headerY + ROW_H + 4f
    }

    fun startPage(): Pair<PdfDocument.Page, Canvas> {
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNumber).create()
        )
        return page to page.canvas
    }

    fun drawPageFooter(canvas: Canvas) {
        val footerY = PageSizes.A4.height - 22f
        val saved = mutedPaint.textAlign
        mutedPaint.textAlign = Paint.Align.CENTER
        val phone = shop.phone.ifBlank { shop.userPhone }
        val line = if (phone.isBlank()) "صفحة $pageNumber" else "$phone   •   صفحة $pageNumber"
        canvas.drawText(line, PageSizes.A4.width / 2f, footerY, mutedPaint)
        mutedPaint.textAlign = saved
    }

    var (page, canvas) = startPage()
    var y = drawPageHeader(canvas)

    items.forEachIndexed { index, item ->
        if (y + TABLE_ROW_HEIGHT > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawPageFooter(canvas)
            document.finishPage(page)
            pageNumber += 1
            val next = startPage()
            page = next.first
            canvas = next.second
            y = drawPageHeader(canvas)
        }

        val rowBackground = if (index % 2 == 1) bgPaint("#F7F9FC") else null
        drawRowBox(canvas, y, TABLE_ROW_HEIGHT, rowBackground, borderPaint, separators)
        cellCenter(canvas, (index + 1).toString(), columns[0].s, columns[0].e, y, TABLE_ROW_HEIGHT, mutedPaint)
        cellRightFitted(canvas, item.name, columns[1].s, columns[1].e, y, TABLE_ROW_HEIGHT, bodyPaint)
        cellRightFitted(canvas, item.partNumber, columns[2].s, columns[2].e, y, TABLE_ROW_HEIGHT, mutedPaint)
        if (includePrices) {
            cellRight(canvas, AmountFormatter.format(item.price), columns[3].s, columns[3].e, y, TABLE_ROW_HEIGHT, pricePaint)
        }
        y += TABLE_ROW_HEIGHT
    }

    drawPageFooter(canvas)
    document.finishPage(page)

    return File(context.cacheDir, "price_list_${System.currentTimeMillis()}.pdf").also { file ->
        try {
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
