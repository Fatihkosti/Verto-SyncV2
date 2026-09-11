package com.verto.app.feature.settings.domain.repository

import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import kotlinx.coroutines.flow.Flow

/** الحد الذي تحتاجه واجهة الطباعة دون معرفة التخزين أو مصدر صلاحية الإدارة. */
interface PrintingSettingsGateway {
    val invoiceTemplate: Flow<InvoiceTemplate>
    val invoiceFont: Flow<InvoiceFont>
    val invoiceFontSize: Flow<Int>

    val priceListFont: Flow<InvoiceFont>
    val priceListFontSize: Flow<Int>

    val statementTemplate: Flow<InvoiceTemplate>
    val statementFont: Flow<InvoiceFont>
    val statementFontSize: Flow<Int>

    val inventoryTemplate: Flow<InvoiceTemplate>
    val inventoryFont: Flow<InvoiceFont>
    val inventoryFontSize: Flow<Int>

    val reportsTemplate: Flow<InvoiceTemplate>
    val reportsFont: Flow<InvoiceFont>
    val reportsFontSize: Flow<Int>

    suspend fun canManageOrganization(): Boolean

    suspend fun setInvoiceTemplate(value: InvoiceTemplate)
    suspend fun setInvoiceFont(value: InvoiceFont)
    suspend fun setInvoiceFontSize(value: Int)

    suspend fun setPriceListFont(value: InvoiceFont)
    suspend fun setPriceListFontSize(value: Int)

    suspend fun setStatementTemplate(value: InvoiceTemplate)
    suspend fun setStatementFont(value: InvoiceFont)
    suspend fun setStatementFontSize(value: Int)

    suspend fun setInventoryTemplate(value: InvoiceTemplate)
    suspend fun setInventoryFont(value: InvoiceFont)
    suspend fun setInventoryFontSize(value: Int)

    suspend fun setReportsTemplate(value: InvoiceTemplate)
    suspend fun setReportsFont(value: InvoiceFont)
    suspend fun setReportsFontSize(value: Int)
}
