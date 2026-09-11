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

fun generateInvoicePdf(
    context        : Context,
    client         : PartyIdentityEntity,
    summary        : InvoiceSummary,
    orgSettings    : OrgSettings,
    employeeName   : String,
    employeePhone  : String,
    items          : List<InvoiceItemEntity>,
    printSettings  : InvoicePrintSettings = InvoicePrintSettings(),
    showCommission : Boolean = false
): File {
    val orgName  = orgSettings.shopName.ifBlank { "" }
    val orgAddr  = listOfNotNull(
        orgSettings.city.takeIf    { it.isNotBlank() },
        orgSettings.address.takeIf { it.isNotBlank() }
    ).joinToString(" — ")
    val orgPhone = orgSettings.shopPhone.ifBlank { employeePhone }

    val tf     = invoiceTypeface(context, printSettings.font, bold = false)
    val tfBold = invoiceTypeface(context, printSettings.font, bold = true)
    val fsBase  = printSettings.fontSize.toFloat()
    val fsSmall = (printSettings.fontSize - 2).coerceAtLeast(8).toFloat()
    val fsTitle = (printSettings.fontSize + 4).toFloat()

    val doc  = PdfDocument()
    val file = File(context.cacheDir, "invoice_${summary.invoice.invoiceNumber}_${System.currentTimeMillis()}.pdf")
    try {
        when (printSettings.template) {
            InvoiceTemplate.CLASSIC      -> drawClassic(doc, client, summary, orgSettings, orgName, orgAddr, orgPhone, employeeName, items, tf, tfBold, fsBase, fsSmall, fsTitle, showCommission)
            InvoiceTemplate.MODERN       -> drawModern(doc, client, summary, orgSettings, orgName, orgAddr, orgPhone, employeeName, items, tf, tfBold, fsBase, fsSmall, fsTitle, showCommission)
            InvoiceTemplate.PROFESSIONAL -> drawProfessional(doc, client, summary, orgSettings, orgName, orgAddr, orgPhone, employeeName, items, tf, tfBold, fsBase, fsSmall, fsTitle, showCommission)
            InvoiceTemplate.THERMAL      -> drawThermal(doc, client, summary, orgSettings, orgName, orgAddr, orgPhone, employeeName, items, tf, tfBold, fsBase, fsSmall, fsTitle, showCommission)
        }
        file.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    return file
}

// ══════════════════════════════════════════════════════════════════
// قالب ١: CLASSIC
// ══════════════════════════════════════════════════════════════════
