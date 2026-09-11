package com.verto.app.feature.settings.bridge
import com.verto.app.data.remote.AuthRepository
import com.verto.app.feature.settings.domain.repository.PrintingSettingsGateway
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** محول انتقالي يبقي التفضيلات والتحقق الإداري الحاليين خلف عقد الطباعة. */
@Singleton
class PrintingSettingsGatewayAdapter @Inject constructor(
    private val preferences: PreferencesManager,
    private val authRepository: AuthRepository
) : PrintingSettingsGateway {
    override val invoiceTemplate: Flow<InvoiceTemplate> = preferences.invoiceTemplate
    override val invoiceFont: Flow<InvoiceFont> = preferences.invoiceFont
    override val invoiceFontSize: Flow<Int> = preferences.invoiceFontSize

    override val priceListFont: Flow<InvoiceFont> = preferences.priceListFont
    override val priceListFontSize: Flow<Int> = preferences.priceListFontSize

    override val statementTemplate: Flow<InvoiceTemplate> = preferences.statementTemplate
    override val statementFont: Flow<InvoiceFont> = preferences.statementFont
    override val statementFontSize: Flow<Int> = preferences.statementFontSize

    override val inventoryTemplate: Flow<InvoiceTemplate> = preferences.inventoryTemplate
    override val inventoryFont: Flow<InvoiceFont> = preferences.inventoryFont
    override val inventoryFontSize: Flow<Int> = preferences.inventoryFontSize

    override val reportsTemplate: Flow<InvoiceTemplate> = preferences.reportsTemplate
    override val reportsFont: Flow<InvoiceFont> = preferences.reportsFont
    override val reportsFontSize: Flow<Int> = preferences.reportsFontSize

    override suspend fun canManageOrganization(): Boolean =
        runCatching { authRepository.getMyProfile()?.role == "admin" }
            .getOrDefault(false)

    override suspend fun setInvoiceTemplate(value: InvoiceTemplate) = preferences.setInvoiceTemplate(value)
    override suspend fun setInvoiceFont(value: InvoiceFont) = preferences.setInvoiceFont(value)
    override suspend fun setInvoiceFontSize(value: Int) = preferences.setInvoiceFontSize(value)

    override suspend fun setPriceListFont(value: InvoiceFont) = preferences.setPriceListFont(value)
    override suspend fun setPriceListFontSize(value: Int) = preferences.setPriceListFontSize(value)

    override suspend fun setStatementTemplate(value: InvoiceTemplate) = preferences.setStatementTemplate(value)
    override suspend fun setStatementFont(value: InvoiceFont) = preferences.setStatementFont(value)
    override suspend fun setStatementFontSize(value: Int) = preferences.setStatementFontSize(value)

    override suspend fun setInventoryTemplate(value: InvoiceTemplate) = preferences.setInventoryTemplate(value)
    override suspend fun setInventoryFont(value: InvoiceFont) = preferences.setInventoryFont(value)
    override suspend fun setInventoryFontSize(value: Int) = preferences.setInventoryFontSize(value)

    override suspend fun setReportsTemplate(value: InvoiceTemplate) = preferences.setReportsTemplate(value)
    override suspend fun setReportsFont(value: InvoiceFont) = preferences.setReportsFont(value)
    override suspend fun setReportsFontSize(value: Int) = preferences.setReportsFontSize(value)
}
