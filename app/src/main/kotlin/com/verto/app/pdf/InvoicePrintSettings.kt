package com.verto.app.pdf

import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate

/**
 * كبسولة إعدادات الطباعة — تُجمع من PreferencesManager وتُمرَّر لدوال التوليد.
 *
 * [template]          → قالب فواتير المبيعات / المشتريات
 * [font]              → الخط المستخدم في فواتير المبيعات
 * [fontSize]          → حجم الخط (10..20)
 */
data class InvoicePrintSettings(
    val template          : InvoiceTemplate   = InvoiceTemplate.CLASSIC,
    val font              : InvoiceFont       = InvoiceFont.CAIRO,
    val fontSize          : Int               = 14
)
