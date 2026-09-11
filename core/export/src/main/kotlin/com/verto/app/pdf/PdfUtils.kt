package com.verto.app.pdf

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.verto.core.designsystem.R
import com.verto.app.utils.InvoiceFont

// ══════════════════════════════════════════════════════════════════
// أبعاد الصفحات
// ══════════════════════════════════════════════════════════════════

data class PageDimensions(val width: Int, val height: Int)

object PageSizes {
    val A4         = PageDimensions(595, 842)
    val THERMAL_80 = PageDimensions(226, 1000)
}

// ══════════════════════════════════════════════════════════════════
// ثوابت مشتركة
// ══════════════════════════════════════════════════════════════════

const val MARGIN        = 40f
const val BOTTOM_MARGIN = 60f
const val ROW_H         = 32f
const val ITEM_H        = 28f

// ══════════════════════════════════════════════════════════════════
// هيكل عمود الجدول
// ══════════════════════════════════════════════════════════════════

data class Col(val s: Float, val e: Float, val label: String)

// ══════════════════════════════════════════════════════════════════
// دوال مساعدة للجداول
// ══════════════════════════════════════════════════════════════════

fun rtlCols(cols: List<Pair<String, Float>>): List<Col> {
    var x = PageSizes.A4.width - MARGIN
    return cols.map { (label, width) ->
        Col(s = x - width, e = x, label = label).also { x -= width }
    }
}

fun colSeps(cols: List<Col>): List<Float> = cols.dropLast(1).map { it.s }

// ══════════════════════════════════════════════════════════════════
// دوال Paint
// ══════════════════════════════════════════════════════════════════

fun invoiceTypeface(context: Context, font: InvoiceFont, bold: Boolean = false): Typeface {
    val resId: Int? = when (font) {
        InvoiceFont.CAIRO    -> if (bold) R.font.cairo_bold    else R.font.cairo_regular
        InvoiceFont.TAJAWAL  -> if (bold) R.font.tajawal_bold  else R.font.tajawal_regular
        InvoiceFont.AMIRI    -> if (bold) R.font.amiri_bold    else R.font.amiri_regular
        InvoiceFont.ALMARAI  -> if (bold) R.font.almarai_bold  else R.font.almarai_regular
    }
    return resId?.let { runCatching { checkNotNull(ResourcesCompat.getFont(context, it)) }.getOrNull() }
        ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

fun paint(hex: String, size: Float, bold: Boolean = false, tf: Typeface? = null) =
    Paint().apply {
        color          = Color.parseColor(hex)
        textSize       = size
        isFakeBoldText = bold
        isAntiAlias    = true
        typeface       = tf ?: (if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
    }

fun bgPaint(hex: String) = Paint().apply { color = Color.parseColor(hex) }

fun linePaint(hex: String = "#AAAAAA", width: Float = 0.7f) =
    Paint().apply {
        color       = Color.parseColor(hex)
        strokeWidth = width
        style       = Paint.Style.STROKE
        isAntiAlias = true
    }

// ══════════════════════════════════════════════════════════════════
// دوال رسم الخلايا
// ══════════════════════════════════════════════════════════════════

fun cellRight(cv: Canvas, text: String, cellStart: Float, cellEnd: Float, rowY: Float, rowH: Float, p: Paint) {
    val saved = p.textAlign; p.textAlign = Paint.Align.RIGHT
    cv.drawText(text, cellEnd - 6f, rowY + rowH * 0.65f, p); p.textAlign = saved
}

fun cellCenter(cv: Canvas, text: String, cellStart: Float, cellEnd: Float, rowY: Float, rowH: Float, p: Paint) {
    val saved = p.textAlign; p.textAlign = Paint.Align.CENTER
    cv.drawText(text, cellStart + (cellEnd - cellStart) / 2f, rowY + rowH * 0.65f, p); p.textAlign = saved
}

fun drawColSeparators(cv: Canvas, seps: List<Float>, rowY: Float, rowH: Float, lp: Paint) {
    seps.forEach { x -> cv.drawLine(x, rowY, x, rowY + rowH, lp) }
}

fun drawRowBox(cv: Canvas, rowY: Float, rowH: Float, bg: Paint?, border: Paint, seps: List<Float>) {
    bg?.let { cv.drawRect(MARGIN, rowY, PageSizes.A4.width - MARGIN, rowY + rowH, it) }
    cv.drawRect(MARGIN, rowY, PageSizes.A4.width - MARGIN, rowY + rowH, border)
    drawColSeparators(cv, seps, rowY, rowH, border)
}

// ══════════════════════════════════════════════════════════════════
// دوال رسم الترويسة والتذييل
// ══════════════════════════════════════════════════════════════════

fun drawFooter(cv: Canvas, pageNum: Int, p: Paint) {
    val saved = p.textAlign; p.textAlign = Paint.Align.CENTER
    cv.drawText("صفحة $pageNum", PageSizes.A4.width / 2f, PageSizes.A4.height - 20f, p); p.textAlign = saved
}

fun drawReportHeader(cv: Canvas, title: String, dateRange: String, ptTitle: Paint, ptDate: Paint): Float {
    var y = 65f
    ptTitle.textAlign = Paint.Align.CENTER; ptDate.textAlign = Paint.Align.CENTER
    cv.drawText(title, PageSizes.A4.width / 2f, y, ptTitle); y += 30f
    cv.drawText("الفترة: $dateRange", PageSizes.A4.width / 2f, y, ptDate); y += 35f
    return y
}

fun drawRightOld(cv: Canvas, text: String, rightX: Float, y: Float, p: Paint) {
    val saved = p.textAlign; p.textAlign = Paint.Align.RIGHT
    cv.drawText(text, rightX, y, p); p.textAlign = saved
}

fun drawCenteredOld(cv: Canvas, text: String, y: Float, p: Paint) {
    val saved = p.textAlign; p.textAlign = Paint.Align.CENTER
    cv.drawText(text, PageSizes.A4.width / 2f, y, p); p.textAlign = saved
}

// ══════════════════════════════════════════════════════════════════
// دعم RTL الكامل للنصوص العربية متعددة الأسطر
// يحل مشاكل: انفصال الحروف، عكس الكلمات، كسر الأسطر الخاطئ
// ══════════════════════════════════════════════════════════════════

fun drawArabicTextRTL(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    paint: Paint,
    maxWidth: Int,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
): Float {
    val tp = TextPaint(paint)
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, tp, maxWidth)
        .setTextDirection(TextDirectionHeuristics.RTL)
        .setAlignment(alignment)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
    return layout.height.toFloat()
}
