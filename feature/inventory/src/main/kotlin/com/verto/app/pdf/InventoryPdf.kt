package com.verto.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.verto.app.feature.inventory.application.model.InventoryItemViewData
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import com.verto.app.core.format.AmountFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ══════════════════════════════════════════════════════════════════
// نوع تصدير المخزون
// ══════════════════════════════════════════════════════════════════

enum class InventoryExportType { BUY_PRICE, SELL_PRICE }

// ── أعمدة مشتركة (5 أعمدة) ───────────────────────────────────────

private fun inventoryCols(priceLabel: String) = listOf(
    "الرقم"    to 40f,
    "الصنف"    to 175f,
    "الكمية"   to 70f,
    priceLabel to 110f,
    "الإجمالي" to 120f
)

private fun slowMovingCols() = listOf(
    "الرقم"      to 40f,
    "الصنف"      to 175f,
    "الكمية"     to 70f,
    "سعر الشراء" to 110f,
    "الإجمالي"   to 120f
)

// ══════════════════════════════════════════════════════════════════
// تقرير المخزون — 4 قوالب
// ══════════════════════════════════════════════════════════════════

fun generateInventoryPdf(
    context       : Context,
    items         : List<InventoryItemViewData>,
    exportType    : InventoryExportType,
    categoryFilter: String = "الكل",
    template      : InvoiceTemplate = InvoiceTemplate.CLASSIC,
    font          : InvoiceFont = InvoiceFont.CAIRO,
    fontSize      : Int = 14
): File {
    val priceLabel = if (exportType == InventoryExportType.BUY_PRICE) "سعر الشراء" else "سعر البيع"
    val title = "تقرير المخزون — $priceLabel"
    val subtitle = if (categoryFilter == "الكل") "جميع التصنيفات" else "تصنيف: $categoryFilter"
    val getPrice: (InventoryItemViewData) -> Double = {
        if (exportType == InventoryExportType.BUY_PRICE) it.buyPrice else it.sellPrice
    }
    val name = "inventory_${if (exportType == InventoryExportType.BUY_PRICE) "buy" else "sell"}_${System.currentTimeMillis()}.pdf"
    return drawInventoryPdf(context, items, title, subtitle, inventoryCols(priceLabel), getPrice, name, template, font, fontSize)
}

// ══════════════════════════════════════════════════════════════════
// تقرير المخزون الراكد — 4 قوالب
// ══════════════════════════════════════════════════════════════════

fun generateSlowMovingInventoryPdf(
    context  : Context,
    items    : List<InventoryItemViewData>,
    days     : Int,
    template : InvoiceTemplate = InvoiceTemplate.CLASSIC,
    font     : InvoiceFont = InvoiceFont.CAIRO,
    fontSize : Int = 14
): File {
    val title    = "تقرير المخزون الراكد"
    val subtitle = "البضاعة التي لم تُباع خلال $days يوم"
    val getPrice: (InventoryItemViewData) -> Double = { it.buyPrice }
    val name     = "slow_moving_${days}d_${System.currentTimeMillis()}.pdf"
    return drawInventoryPdf(context, items, title, subtitle, slowMovingCols(), getPrice, name, template, font, fontSize)
}

// ══════════════════════════════════════════════════════════════════
// دالة الرسم المشتركة — تختار القالب
// ══════════════════════════════════════════════════════════════════

private fun drawInventoryPdf(
    ctx      : Context,
    items    : List<InventoryItemViewData>,
    title    : String,
    subtitle : String,
    colDefs  : List<Pair<String, Float>>,
    getPrice : (InventoryItemViewData) -> Double,
    fileName : String,
    template : InvoiceTemplate,
    font     : InvoiceFont,
    fontSize : Int
): File = when (template) {
    InvoiceTemplate.CLASSIC      -> drawInvClassic(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize)
    InvoiceTemplate.MODERN       -> drawInvModern(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize)
    InvoiceTemplate.PROFESSIONAL -> drawInvProfessional(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize)
    InvoiceTemplate.THERMAL      -> drawInvLuxury(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize)
}

// ══════════════════════════════════════════════════════════════════
// منطق مشترك لبناء الجدول داخل أي قالب
// ══════════════════════════════════════════════════════════════════

private data class InvColors(
    val headerBg   : String,
    val headerText : String,
    val titleText  : String,
    val subtitleText: String,
    val accentText : String,
    val bodyText   : String,
    val altRowBg   : String,
    val rowBorder  : String,
    val totalBg    : String,
    val footText   : String,
    val topBarBg   : String,
    val accentBar  : String
)

private fun buildInventoryDocument(
    ctx      : Context,
    items    : List<InventoryItemViewData>,
    title    : String,
    subtitle : String,
    colDefs  : List<Pair<String, Float>>,
    getPrice : (InventoryItemViewData) -> Double,
    fileName : String,
    font     : InvoiceFont,
    fontSize : Int,
    colors   : InvColors
): File {
    val doc  = PdfDocument()
    var pn   = 1
    val tf   = invoiceTypeface(ctx, font, false)
    val tfB  = invoiceTypeface(ctx, font, true)
    val cols = rtlCols(colDefs)
    val seps = colSeps(cols)

    val bs = fontSize.toFloat()
    val ss = (fontSize - 2).coerceAtLeast(9).toFloat()
    val fs = (fontSize - 5).coerceAtLeast(8).toFloat()

    val ptTitle = paint(colors.titleText,    bs + 4f, true,  tfB)
    val ptSub   = paint(colors.subtitleText, ss,      false, tf)
    val ptHdr   = paint(colors.headerText,   bs,      true,  tfB)
    val ptBody  = paint(colors.bodyText,     bs,      false, tf)
    val ptAccnt = paint(colors.accentText,   bs,      true,  tfB)
    val ptTotal = paint(colors.accentText,   bs,      true,  tfB)
    val ptFoot  = paint(colors.footText,     fs,      false, tf)

    val today  = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date())
    val hBarH  = 70f

    fun newPage(isFirst: Boolean): Pair<PdfDocument.Page, Canvas> {
        val p  = doc.startPage(PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pn).create())
        val cv = p.canvas
        if (isFirst) {
            cv.drawRect(0f, 0f, PageSizes.A4.width.toFloat(), hBarH, bgPaint(colors.topBarBg))
            cv.drawRect(0f, hBarH - 3f, PageSizes.A4.width.toFloat(), hBarH, bgPaint(colors.accentBar))
            drawCenteredOld(cv, title,    hBarH * 0.35f, ptTitle)
            drawCenteredOld(cv, subtitle, hBarH * 0.62f, ptSub)
            drawCenteredOld(cv, today,    hBarH * 0.86f, ptSub)
        }
        val colHdrY = if (isFirst) hBarH + 2f else MARGIN
        drawRowBox(cv, colHdrY, ROW_H, bgPaint(colors.headerBg), linePaint(colors.headerBg, 0f), seps)
        cols.forEach { c -> cellRight(cv, c.label, c.s, c.e, colHdrY, ROW_H, ptHdr) }
        return p to cv
    }

    val firstTableStart = hBarH + 2f + ROW_H + 4f
    val contTableStart  = MARGIN + ROW_H + 4f
    var (pg, cv)        = newPage(true)
    var y               = firstTableStart

    var totalValue = 0.0
    items.forEachIndexed { i, item ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pn, ptFoot); doc.finishPage(pg); pn++
            newPage(false).also { pg = it.first; cv = it.second }; y = contTableStart
        }
        val price     = getPrice(item)
        val lineTotal = price * item.quantity
        totalValue   += lineTotal

        val rowBg = if (i % 2 != 0) bgPaint(colors.altRowBg) else null
        drawRowBox(cv, y, ROW_H, rowBg, linePaint(colors.rowBorder, 0.5f), seps)
        cellCenter(cv, "${i + 1}",                          cols[0].s, cols[0].e, y, ROW_H, ptAccnt)
        cellRight(cv,  item.name,                           cols[1].s, cols[1].e, y, ROW_H, ptBody)
        cellCenter(cv, item.quantity.toString(),            cols[2].s, cols[2].e, y, ROW_H, ptBody)
        cellRight(cv,  AmountFormatter.format(price),   cols[3].s, cols[3].e, y, ROW_H, ptBody)
        cellRight(cv,  AmountFormatter.format(lineTotal),cols[4].s, cols[4].e, y, ROW_H, ptBody)
        y += ROW_H
    }

    if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
        drawFooter(cv, pn, ptFoot); doc.finishPage(pg); pn++
        newPage(false).also { pg = it.first; cv = it.second }; y = contTableStart
    }
    drawRowBox(cv, y, ROW_H, bgPaint(colors.totalBg), linePaint(colors.accentBar, 1f), seps)
    cellRight(cv, "الإجمالي",                             cols[1].s, cols[1].e, y, ROW_H, ptTotal)
    cellRight(cv, AmountFormatter.format(totalValue), cols[4].s, cols[4].e, y, ROW_H, ptTotal)

    drawFooter(cv, pn, ptFoot); doc.finishPage(pg)
    val file = File(ctx.cacheDir, fileName)
    try {
        file.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    return file
}

// ══════════════════════════════════════════════════════════════════
// قالب 1 — كلاسيك  ·  Navy Blue
// ══════════════════════════════════════════════════════════════════

private fun drawInvClassic(
    ctx: Context, items: List<InventoryItemViewData>,
    title: String, subtitle: String, colDefs: List<Pair<String, Float>>,
    getPrice: (InventoryItemViewData) -> Double, fileName: String,
    font: InvoiceFont, fontSize: Int
) = buildInventoryDocument(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize,
    InvColors(
        headerBg    = "#2C5282", headerText   = "#FFFFFF",
        titleText   = "#FFFFFF", subtitleText = "#BDD5EA",
        accentText  = "#1E3A5F", bodyText     = "#111827",
        altRowBg    = "#EBF0F8", rowBorder    = "#CBD5E0",
        totalBg     = "#D6E4F7", footText     = "#9CA3AF",
        topBarBg    = "#1E3A5F", accentBar    = "#4299E1"
    )
)

// ══════════════════════════════════════════════════════════════════
// قالب 2 — مودرن  ·  Teal / Emerald
// ══════════════════════════════════════════════════════════════════

private fun drawInvModern(
    ctx: Context, items: List<InventoryItemViewData>,
    title: String, subtitle: String, colDefs: List<Pair<String, Float>>,
    getPrice: (InventoryItemViewData) -> Double, fileName: String,
    font: InvoiceFont, fontSize: Int
) = buildInventoryDocument(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize,
    InvColors(
        headerBg    = "#0D9488", headerText   = "#FFFFFF",
        titleText   = "#FFFFFF", subtitleText = "#CCFBF1",
        accentText  = "#065F46", bodyText     = "#0D1F17",
        altRowBg    = "#D1FAF0", rowBorder    = "#99F6E4",
        totalBg     = "#CCFBF1", footText     = "#9CA3AF",
        topBarBg    = "#134E4A", accentBar    = "#2DD4BF"
    )
)

// ══════════════════════════════════════════════════════════════════
// قالب 3 — بروفيشنال  ·  Deep Purple / Indigo
// ══════════════════════════════════════════════════════════════════

private fun drawInvProfessional(
    ctx: Context, items: List<InventoryItemViewData>,
    title: String, subtitle: String, colDefs: List<Pair<String, Float>>,
    getPrice: (InventoryItemViewData) -> Double, fileName: String,
    font: InvoiceFont, fontSize: Int
) = buildInventoryDocument(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize,
    InvColors(
        headerBg    = "#4C1D95", headerText   = "#FFFFFF",
        titleText   = "#FFFFFF", subtitleText = "#DDD6FE",
        accentText  = "#5B21B6", bodyText     = "#0F0A2E",
        altRowBg    = "#EDE9FE", rowBorder    = "#C4B5FD",
        totalBg     = "#E0D9FF", footText     = "#9CA3AF",
        topBarBg    = "#1E1B4B", accentBar    = "#A78BFA"
    )
)

// ══════════════════════════════════════════════════════════════════
// قالب 4 — فاخر  ·  Black & Gold Luxury
// ══════════════════════════════════════════════════════════════════

private fun drawInvLuxury(
    ctx: Context, items: List<InventoryItemViewData>,
    title: String, subtitle: String, colDefs: List<Pair<String, Float>>,
    getPrice: (InventoryItemViewData) -> Double, fileName: String,
    font: InvoiceFont, fontSize: Int
) = buildInventoryDocument(ctx, items, title, subtitle, colDefs, getPrice, fileName, font, fontSize,
    InvColors(
        headerBg    = "#1A1208", headerText   = "#D4AF37",
        titleText   = "#D4AF37", subtitleText = "#C9A84C",
        accentText  = "#78570A", bodyText     = "#1A1208",
        altRowBg    = "#FFF3CC", rowBorder    = "#E8D5A3",
        totalBg     = "#FFF0B3", footText     = "#999999",
        topBarBg    = "#0D0D0D", accentBar    = "#D4AF37"
    )
)
