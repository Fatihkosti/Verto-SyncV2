package com.verto.app.utils

import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.repository.InvoiceSummary

/** Invoice-only WhatsApp copy. The launcher itself lives in core/common. */
object InvoiceWhatsAppMessages {
    fun buildInvoiceMessage(client: PartyIdentityEntity, s: InvoiceSummary): String = """
مرحباً ${client.name} 👋

تفاصيل فاتورة رقم #${String.format("%04d", s.invoice.invoiceNumber)}
${s.invoice.type.label}: ${s.invoice.description}

💰 إجمالي الفاتورة:  ${WhatsAppUtils.formatAmount(s.invoice.totalAmount)} جنيه
✅ المسدَّد:           ${WhatsAppUtils.formatAmount(s.totalPaid)} جنيه
⏳ المتبقي:           ${WhatsAppUtils.formatAmount(s.remaining)} جنيه
📅 تاريخ الاستحقاق:   ${if (s.invoice.dueDate > 0) DateUtils.formatDate(s.invoice.dueDate) else "مفتوح"}

Verto — إدارة الديون
    """.trimIndent()

    fun buildReminderMessage(client: PartyIdentityEntity, s: InvoiceSummary): String {
        val (label, isOverdue, _) = DateUtils.dueDateLabel(s.invoice.dueDate)
        val statusText = if (isOverdue) "⚠️ الفاتورة متأخرة — $label" else "🔔 تذكير — $label"
        return """
مرحباً ${client.name} 👋

$statusText

فاتورة رقم #${String.format("%04d", s.invoice.invoiceNumber)} — ${s.invoice.description}
المتبقي: ${WhatsAppUtils.formatAmount(s.remaining)} جنيه

Verto — إدارة الديون
        """.trimIndent()
    }

    fun buildThankYouMessage(client: PartyIdentityEntity, s: InvoiceSummary, paidAmount: Double, isFullyPaid: Boolean): String =
        if (isFullyPaid) """
مرحباً ${client.name} 👋 🎉

نشكركم جزيل الشكر على سداد كامل مستحقاتكم!

فاتورة #${String.format("%04d", s.invoice.invoiceNumber)}
✅ تم السداد الكامل: ${WhatsAppUtils.formatAmount(s.invoice.totalAmount)} جنيه

نتمنى دوام التعاون والتوفيق 🤝

Verto — إدارة الديون
        """.trimIndent()
        else """
مرحباً ${client.name} 👋

نشكركم على دفعتكم الكريمة 🙏

فاتورة #${String.format("%04d", s.invoice.invoiceNumber)}
💵 المبلغ المستلم: ${WhatsAppUtils.formatAmount(paidAmount)} جنيه
⏳ المتبقي:        ${WhatsAppUtils.formatAmount(s.remaining - paidAmount)} جنيه

نقدر ثقتكم ونتطلع لاستكمال المعاملة 🤝

Verto — إدارة الديون
        """.trimIndent()
}
