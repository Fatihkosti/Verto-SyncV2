package com.verto.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.verto.app.money.Money
import java.io.File

// ─── Data classes for PDF report generation ───────────────────────────────────

data class InvoiceReport(
    val invoiceNumber: Int,
    val clientName: String,
    val totalSalesMinor: Long,
    val totalProfitMinor: Long,
    val currencyCode: String,
)

data class ClientAnalysis(
    val name: String,
    val invoiceCount: Int,
    val totalSalesMinor: Long,
    val totalProfitMinor: Long,
    val overdueAmountMinor: Long,
    val currencyCode: String,
)

data class ItemAnalysis(
    val name: String,
    val qty: Int,
    val revenueMinor: Long,
    val profitMinor: Long,
    val currencyCode: String
)

data class CategoryAnalysis(
    val category: String,
    val distinctItems: Int,
    val revenueMinor: Long,
    val profitMinor: Long,
    val currencyCode: String
)

// ══════════════════════════════════════════════════════════════════
// ١. تقرير قائمة الفواتير
// ══════════════════════════════════════════════════════════════════

fun generateInvoicesListPdf(
    context       : Context,
    invoices      : List<InvoiceReport>,
    title         : String,
    dateRange     : String,
    isProfitReport: Boolean
): File {
    val doc = PdfDocument(); var pageNum = 1
    val ptTitle  = paint("#1A1A2E", 18f, true); val ptInvHdr = paint("#FFFFFF", 12f, true)
    val ptBody   = paint("#333333", 11f);        val ptMuted  = paint("#666666", 10f)
    val ptTotal  = paint("#1A1A2E", 12f, true)
    val bgHeader = bgPaint("#1A1A2E"); val bgAlt = bgPaint("#F5F5F5"); val bgTotal = bgPaint("#E8EAF6")
    val lp = linePaint("#CCCCCC", 0.5f); val lpTotal = linePaint("#1A1A2E", 1f)
    val cols = if (isProfitReport)
        rtlCols(listOf("رقم" to 55f, "العميل" to 170f, "الإجمالي" to 100f, "التكلفة" to 100f, "الربح" to 90f))
    else
        rtlCols(listOf("رقم" to 55f, "العميل" to 195f, "الإجمالي" to 100f, "المدفوع" to 100f, "المتبقي" to 65f))
    val seps = colSeps(cols)
    val currency = invoices.map { it.currencyCode }.filter { it.isNotBlank() }.distinct().singleOrNull().orEmpty()

    fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create()
        val page = doc.startPage(info); val cv = page.canvas
        val rangeWithCurrency = if (currency.isBlank()) dateRange else "$dateRange  •  العملة الوظيفية: $currency"
        val y = drawReportHeader(cv, title, rangeWithCurrency, ptTitle, ptMuted)
        drawRowBox(cv, y, ROW_H, bgHeader, linePaint("#1A1A2E", 0f), seps)
        cols.forEach { col -> cellRight(cv, col.label, col.s, col.e, y, ROW_H, ptInvHdr) }
        return page to cv
    }

    var (curPage, cv) = newPage(); var y = 65f + 30f + 20f + ROW_H + 5f
    invoices.forEachIndexed { idx, inv ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage); pageNum++
            val np = newPage(); curPage = np.first; cv = np.second; y = 65f + 30f + 20f + ROW_H + 5f
        }
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lp, seps)
        cellRight(cv, "#${inv.invoiceNumber}", cols[0].s, cols[0].e, y, ROW_H, ptBody)
        cellRight(cv, inv.clientName,           cols[1].s, cols[1].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(inv.totalSalesMinor, inv.currencyCode), cols[2].s, cols[2].e, y, ROW_H, ptBody)
        if (isProfitReport) {
            cellRight(cv, formatReportMinor(Math.subtractExact(inv.totalSalesMinor, inv.totalProfitMinor), inv.currencyCode), cols[3].s, cols[3].e, y, ROW_H, ptBody)
            cellRight(cv, formatReportMinor(inv.totalProfitMinor, inv.currencyCode), cols[4].s, cols[4].e, y, ROW_H, ptBody)
        } else {
            cellRight(cv, formatReportMinor(0L, inv.currencyCode), cols[3].s, cols[3].e, y, ROW_H, ptBody)
            cellRight(cv, formatReportMinor(inv.totalSalesMinor, inv.currencyCode), cols[4].s, cols[4].e, y, ROW_H, ptBody)
        }
        y += ROW_H
    }
    y += 5f; drawRowBox(cv, y, ROW_H, bgTotal, lpTotal, seps)
    cellRight(cv, "الإجمالي", cols[1].s, cols[1].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(invoices.fold(0L) { acc, row -> Math.addExact(acc, row.totalSalesMinor) }, currency), cols[2].s, cols[2].e, y, ROW_H, ptTotal)
    drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage)
    val file = File(context.cacheDir, "invoices_report_${System.currentTimeMillis()}.pdf")
    try { file.outputStream().use { doc.writeTo(it) } } finally { doc.close() }
    return file
}

// ══════════════════════════════════════════════════════════════════
// ٢. تقرير تحليل العملاء
// ══════════════════════════════════════════════════════════════════

fun generateClientAnalysisPdf(
    context       : Context,
    clients       : List<ClientAnalysis>,
    title         : String,
    dateRange     : String,
    isProfitReport: Boolean = false
): File {
    val doc = PdfDocument(); var pageNum = 1
    val ptTitle  = paint("#1A1A2E", 18f, true); val ptInvHdr = paint("#FFFFFF", 12f, true)
    val ptBody   = paint("#333333", 11f);        val ptMuted  = paint("#666666", 10f)
    val ptTotal  = paint("#1A1A2E", 12f, true)
    val bgHeader = bgPaint("#1A1A2E"); val bgAlt = bgPaint("#F5F5F5"); val bgTotal = bgPaint("#E8EAF6")
    val lp = linePaint("#CCCCCC", 0.5f); val lpTotal = linePaint("#1A1A2E", 1f)
    val cols = rtlCols(listOf("العميل" to 175f, "عدد الفواتير" to 90f, "إجمالي المبيعات" to 115f, "الربح" to 100f, "المتأخر" to 75f))
    val seps = colSeps(cols)
    val currency = clients.map { it.currencyCode }.filter { it.isNotBlank() }.distinct().singleOrNull().orEmpty()

    fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create()
        val page = doc.startPage(info); val cv = page.canvas
        val rangeWithCurrency = if (currency.isBlank()) dateRange else "$dateRange  •  العملة الوظيفية: $currency"
        val y = drawReportHeader(cv, title, rangeWithCurrency, ptTitle, ptMuted)
        drawRowBox(cv, y, ROW_H, bgHeader, linePaint("#1A1A2E", 0f), seps)
        cols.forEach { col -> cellRight(cv, col.label, col.s, col.e, y, ROW_H, ptInvHdr) }
        return page to cv
    }

    var (curPage, cv) = newPage(); var y = 65f + 30f + 20f + ROW_H + 5f
    clients.forEachIndexed { idx, c ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage); pageNum++
            val np = newPage(); curPage = np.first; cv = np.second; y = 65f + 30f + 20f + ROW_H + 5f
        }
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lp, seps)
        cellRight(cv, c.name, cols[0].s, cols[0].e, y, ROW_H, ptBody)
        cellCenter(cv, c.invoiceCount.toString(), cols[1].s, cols[1].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(c.totalSalesMinor, c.currencyCode), cols[2].s, cols[2].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(c.totalProfitMinor, c.currencyCode), cols[3].s, cols[3].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(c.overdueAmountMinor, c.currencyCode), cols[4].s, cols[4].e, y, ROW_H, ptBody)
        y += ROW_H
    }
    y += 5f; drawRowBox(cv, y, ROW_H, bgTotal, lpTotal, seps)
    cellRight(cv, "الإجمالي", cols[0].s, cols[0].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(clients.fold(0L) { acc, row -> Math.addExact(acc, row.totalSalesMinor) }, currency), cols[2].s, cols[2].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(clients.fold(0L) { acc, row -> Math.addExact(acc, row.totalProfitMinor) }, currency), cols[3].s, cols[3].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(clients.fold(0L) { acc, row -> Math.addExact(acc, row.overdueAmountMinor) }, currency), cols[4].s, cols[4].e, y, ROW_H, ptTotal)
    drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage)
    val file = File(context.cacheDir, "clients_report_${System.currentTimeMillis()}.pdf")
    try { file.outputStream().use { doc.writeTo(it) } } finally { doc.close() }
    return file
}

// ══════════════════════════════════════════════════════════════════
// ٣. تقرير تحليل الأصناف
// ══════════════════════════════════════════════════════════════════

fun generateItemsAnalysisPdf(
    context       : Context,
    items         : List<ItemAnalysis>,
    title         : String,
    dateRange     : String,
    isProfitReport: Boolean = false
): File {
    val doc = PdfDocument(); var pageNum = 1
    val ptTitle  = paint("#1A1A2E", 18f, true); val ptInvHdr = paint("#FFFFFF", 12f, true)
    val ptBody   = paint("#333333", 11f);        val ptMuted  = paint("#666666", 10f)
    val ptTotal  = paint("#1A1A2E", 12f, true)
    val bgHeader = bgPaint("#1A1A2E"); val bgAlt = bgPaint("#F5F5F5"); val bgTotal = bgPaint("#E8EAF6")
    val lp = linePaint("#CCCCCC", 0.5f); val lpTotal = linePaint("#1A1A2E", 1f)
    val currency = items.map { it.currencyCode }.filter { it.isNotBlank() }.distinct().singleOrNull().orEmpty()
    val cols = rtlCols(listOf("الصنف" to 175f, "الكمية المباعة" to 100f, "إجمالي المبيعات" to 115f, "التكلفة" to 100f, "الربح" to 65f))
    val seps = colSeps(cols)

    fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create()
        val page = doc.startPage(info); val cv = page.canvas
        val rangeWithCurrency = if (currency.isBlank()) dateRange else "$dateRange  •  العملة الوظيفية: $currency"
        val y = drawReportHeader(cv, title, rangeWithCurrency, ptTitle, ptMuted)
        drawRowBox(cv, y, ROW_H, bgHeader, linePaint("#1A1A2E", 0f), seps)
        cols.forEach { col -> cellRight(cv, col.label, col.s, col.e, y, ROW_H, ptInvHdr) }
        return page to cv
    }

    var (curPage, cv) = newPage(); var y = 65f + 30f + 20f + ROW_H + 5f
    items.forEachIndexed { idx, item ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage); pageNum++
            val np = newPage(); curPage = np.first; cv = np.second; y = 65f + 30f + 20f + ROW_H + 5f
        }
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lp, seps)
        cellRight(cv, item.name, cols[0].s, cols[0].e, y, ROW_H, ptBody)
        cellCenter(cv, item.qty.toString(), cols[1].s, cols[1].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(item.revenueMinor, item.currencyCode), cols[2].s, cols[2].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(Math.subtractExact(item.revenueMinor, item.profitMinor), item.currencyCode), cols[3].s, cols[3].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(item.profitMinor, item.currencyCode), cols[4].s, cols[4].e, y, ROW_H, ptBody)
        y += ROW_H
    }
    y += 5f; drawRowBox(cv, y, ROW_H, bgTotal, lpTotal, seps)
    cellRight(cv, "الإجمالي", cols[0].s, cols[0].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(items.fold(0L) { acc, row -> Math.addExact(acc, row.revenueMinor) }, currency), cols[2].s, cols[2].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(items.fold(0L) { acc, row -> Math.addExact(acc, Math.subtractExact(row.revenueMinor, row.profitMinor)) }, currency), cols[3].s, cols[3].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(items.fold(0L) { acc, row -> Math.addExact(acc, row.profitMinor) }, currency), cols[4].s, cols[4].e, y, ROW_H, ptTotal)
    drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage)
    val file = File(context.cacheDir, "items_report_${System.currentTimeMillis()}.pdf")
    try { file.outputStream().use { doc.writeTo(it) } } finally { doc.close() }
    return file
}

// ══════════════════════════════════════════════════════════════════
// ٤. تقرير تحليل الفئات
// ══════════════════════════════════════════════════════════════════

fun generateCategoryAnalysisPdf(
    context       : Context,
    categories    : List<CategoryAnalysis>,
    title         : String,
    dateRange     : String,
    isProfitReport: Boolean = false
): File {
    val doc = PdfDocument(); var pageNum = 1
    val ptTitle  = paint("#1A1A2E", 18f, true); val ptInvHdr = paint("#FFFFFF", 12f, true)
    val ptBody   = paint("#333333", 11f);        val ptMuted  = paint("#666666", 10f)
    val ptTotal  = paint("#1A1A2E", 12f, true)
    val bgHeader = bgPaint("#1A1A2E"); val bgAlt = bgPaint("#F5F5F5"); val bgTotal = bgPaint("#E8EAF6")
    val lp = linePaint("#CCCCCC", 0.5f); val lpTotal = linePaint("#1A1A2E", 1f)
    val currency = categories.map { it.currencyCode }.filter { it.isNotBlank() }.distinct().singleOrNull().orEmpty()
    val cols = rtlCols(listOf("الفئة" to 200f, "عدد الفواتير" to 100f, "إجمالي المبيعات" to 120f, "الربح" to 95f))
    val seps = colSeps(cols)

    fun newPage(): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(PageSizes.A4.width, PageSizes.A4.height, pageNum).create()
        val page = doc.startPage(info); val cv = page.canvas
        val rangeWithCurrency = if (currency.isBlank()) dateRange else "$dateRange  •  العملة الوظيفية: $currency"
        val y = drawReportHeader(cv, title, rangeWithCurrency, ptTitle, ptMuted)
        drawRowBox(cv, y, ROW_H, bgHeader, linePaint("#1A1A2E", 0f), seps)
        cols.forEach { col -> cellRight(cv, col.label, col.s, col.e, y, ROW_H, ptInvHdr) }
        return page to cv
    }

    var (curPage, cv) = newPage(); var y = 65f + 30f + 20f + ROW_H + 5f
    categories.forEachIndexed { idx, cat ->
        if (y + ROW_H > PageSizes.A4.height - BOTTOM_MARGIN) {
            drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage); pageNum++
            val np = newPage(); curPage = np.first; cv = np.second; y = 65f + 30f + 20f + ROW_H + 5f
        }
        val bg = if (idx % 2 == 0) null else bgAlt
        drawRowBox(cv, y, ROW_H, bg, lp, seps)
        cellRight(cv, cat.category, cols[0].s, cols[0].e, y, ROW_H, ptBody)
        cellCenter(cv, cat.distinctItems.toString(), cols[1].s, cols[1].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(cat.revenueMinor, cat.currencyCode), cols[2].s, cols[2].e, y, ROW_H, ptBody)
        cellRight(cv, formatReportMinor(cat.profitMinor, cat.currencyCode), cols[3].s, cols[3].e, y, ROW_H, ptBody)
        y += ROW_H
    }
    y += 5f; drawRowBox(cv, y, ROW_H, bgTotal, lpTotal, seps)
    cellRight(cv, "الإجمالي", cols[0].s, cols[0].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(categories.fold(0L) { acc, row -> Math.addExact(acc, row.revenueMinor) }, currency), cols[2].s, cols[2].e, y, ROW_H, ptTotal)
    cellRight(cv, formatReportMinor(categories.fold(0L) { acc, row -> Math.addExact(acc, row.profitMinor) }, currency), cols[3].s, cols[3].e, y, ROW_H, ptTotal)
    drawFooter(cv, pageNum, ptMuted); doc.finishPage(curPage)
    val file = File(context.cacheDir, "categories_report_${System.currentTimeMillis()}.pdf")
    try { file.outputStream().use { doc.writeTo(it) } } finally { doc.close() }
    return file
}

private fun formatReportMinor(amountMinor: Long, currencyCode: String): String =
    Money.ofMinor(amountMinor, currencyCode.ifBlank { Money.TRANSACTION_CURRENCY }).toPlainString() +
        if (currencyCode.isBlank()) "" else " $currencyCode"
