package com.verto.app.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formatter موحَّد للمبالغ — أرقام منسّقة فقط بلا أي رمز عملة (قرار المالك 2026-06-13).
 *
 * استخدام: CurrencyFormatter.formatNoSymbol(1500.50)  → "1,500.5" · (1000.0) → "1,000"
 * (بند 7) لا تُكتب الكسور الصفرية: 1000 بدل 1000.00.
 */
object CurrencyFormatter {

    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("ar-SA")).apply {
        groupingSeparator = ','
        decimalSeparator  = '.'
        zeroDigit         = '0'
    }
    private val numberFmt = DecimalFormat("#,##0.##", symbols).apply {
        negativePrefix = "-"
    }

    /** المصدر الوحيد لتنسيق المبالغ: رقم منسّق بلا رمز عملة */
    fun formatNoSymbol(amount: Double): String = numberFmt.format(amount)
}
