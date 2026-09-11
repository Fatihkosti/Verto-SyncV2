package com.verto.app.feature.invoice.presentation.invoice

import com.verto.app.feature.invoice.application.ClientItem
import com.verto.app.feature.invoice.application.InvoiceSummary
import com.verto.app.utils.WhatsAppUtils

internal object InvoiceMessagePreviewBuilder {
    fun buildThankYouMessage(
        client: ClientItem,
        summary: InvoiceSummary,
        paidAmount: Double,
        isFullyPaid: Boolean,
    ): String = if (isFullyPaid) """
مرحباً ${client.name} 👋 🎉

نشكركم جزيل الشكر على سداد كامل مستحقاتكم!

فاتورة #${String.format("%04d", summary.invoice.invoiceNumber)}
✅ تم السداد الكامل: ${WhatsAppUtils.formatAmount(summary.invoice.totalAmount)} جنيه

نتمنى دوام التعاون والتوفيق 🤝

Verto — إدارة الديون
    """.trimIndent() else """
مرحباً ${client.name} 👋

نشكركم على دفعتكم الكريمة 🙏

فاتورة #${String.format("%04d", summary.invoice.invoiceNumber)}
💵 المبلغ المستلم: ${WhatsAppUtils.formatAmount(paidAmount)} جنيه
⏳ المتبقي:        ${WhatsAppUtils.formatAmount(summary.remaining)} جنيه

نقدر ثقتكم ونتطلع لاستكمال المعاملة 🤝

Verto — إدارة الديون
    """.trimIndent()
}
