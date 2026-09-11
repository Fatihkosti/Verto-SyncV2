package com.verto.app.feature.invoice.data

import android.content.Context
import com.verto.app.core.format.AmountFormatter
import com.verto.app.feature.invoice.domain.repository.InvoiceMessageShareGateway
import com.verto.app.feature.invoice.domain.repository.InvoiceSharePayload
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppInvoiceMessageShareAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) : InvoiceMessageShareGateway {
    override fun shareInvoice(payload: InvoiceSharePayload, useBusiness: Boolean) {
        val dueDate = if (payload.dueDate > 0) DateUtils.formatDate(payload.dueDate) else "مفتوح"
        val message = """
مرحباً ${payload.clientName} 👋

تفاصيل فاتورة رقم #${String.format("%04d", payload.invoiceNumber)}
${payload.invoiceTypeLabel}: ${payload.description}

💰 إجمالي الفاتورة:  ${AmountFormatter.format(payload.totalAmount)} جنيه
✅ المسدَّد:           ${AmountFormatter.format(payload.totalPaid)} جنيه
⏳ المتبقي:           ${AmountFormatter.format(payload.remaining)} جنيه
📅 تاريخ الاستحقاق:   $dueDate

Verto — إدارة الديون
        """.trimIndent()
        WhatsAppUtils.openWhatsApp(context, WhatsAppUtils.formatPhone(payload.clientPhone), message, useBusiness)
    }

    override fun shareReminder(payload: InvoiceSharePayload, useBusiness: Boolean) {
        val dueDate = if (payload.dueDate > 0) DateUtils.formatDate(payload.dueDate) else "غير محدد"
        val message = """
مرحباً ${payload.clientName}،

تذكير بفاتورة #${String.format("%04d", payload.invoiceNumber)}.
المتبقي: ${AmountFormatter.format(payload.remaining)} جنيه
تاريخ الاستحقاق: $dueDate

شكراً لتعاونكم.
        """.trimIndent()
        WhatsAppUtils.openWhatsApp(context, WhatsAppUtils.formatPhone(payload.clientPhone), message, useBusiness)
    }

    override fun shareThankYou(
        payload: InvoiceSharePayload,
        paidAmount: Double,
        isFullyPaid: Boolean,
        useBusiness: Boolean
    ) {
        val message = if (isFullyPaid) {
            """
مرحباً ${payload.clientName} 👋 🎉

نشكركم جزيل الشكر على سداد كامل مستحقاتكم!

فاتورة #${String.format("%04d", payload.invoiceNumber)}
✅ تم السداد الكامل: ${AmountFormatter.format(payload.totalAmount)} جنيه

نتمنى دوام التعاون والتوفيق 🤝

Verto — إدارة الديون
            """.trimIndent()
        } else {
            """
مرحباً ${payload.clientName} 👋

نشكركم على دفعتكم الكريمة 🙏

فاتورة #${String.format("%04d", payload.invoiceNumber)}
💵 المبلغ المستلم: ${AmountFormatter.format(paidAmount)} جنيه
⏳ المتبقي:        ${AmountFormatter.format(payload.remaining)} جنيه

نقدر ثقتكم ونتطلع لاستكمال المعاملة 🤝

Verto — إدارة الديون
            """.trimIndent()
        }
        WhatsAppUtils.openWhatsApp(context, WhatsAppUtils.formatPhone(payload.clientPhone), message, useBusiness)
    }
}
