package com.verto.app.feature.settings.presentation.printing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.settings.domain.repository.PrintingSettingsGateway
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrintDocumentStyle(
    val template: InvoiceTemplate = InvoiceTemplate.CLASSIC,
    val font: InvoiceFont = InvoiceFont.CAIRO,
    val fontSize: Int = 14
)

data class PrintingSettingsUiState(
    val isAdmin: Boolean = false,
    val invoice: PrintDocumentStyle = PrintDocumentStyle(),
    val priceList: PrintDocumentStyle = PrintDocumentStyle(template = InvoiceTemplate.PROFESSIONAL),
    val statement: PrintDocumentStyle = PrintDocumentStyle(),
    val inventory: PrintDocumentStyle = PrintDocumentStyle(),
    val reports: PrintDocumentStyle = PrintDocumentStyle()
) : UiState

sealed interface PrintingSettingsEvent : UiEvent {
    data class InvoiceTemplateChanged(val value: InvoiceTemplate) : PrintingSettingsEvent
    data class InvoiceFontChanged(val value: InvoiceFont) : PrintingSettingsEvent
    data class InvoiceFontSizeChanged(val value: Int) : PrintingSettingsEvent
    data class PriceListFontChanged(val value: InvoiceFont) : PrintingSettingsEvent
    data class PriceListFontSizeChanged(val value: Int) : PrintingSettingsEvent
    data class StatementTemplateChanged(val value: InvoiceTemplate) : PrintingSettingsEvent
    data class StatementFontChanged(val value: InvoiceFont) : PrintingSettingsEvent
    data class StatementFontSizeChanged(val value: Int) : PrintingSettingsEvent
    data class InventoryTemplateChanged(val value: InvoiceTemplate) : PrintingSettingsEvent
    data class InventoryFontChanged(val value: InvoiceFont) : PrintingSettingsEvent
    data class InventoryFontSizeChanged(val value: Int) : PrintingSettingsEvent
    data class ReportsTemplateChanged(val value: InvoiceTemplate) : PrintingSettingsEvent
    data class ReportsFontChanged(val value: InvoiceFont) : PrintingSettingsEvent
    data class ReportsFontSizeChanged(val value: Int) : PrintingSettingsEvent
}

private data class PrimaryPrintingStyles(
    val invoice: PrintDocumentStyle,
    val priceList: PrintDocumentStyle,
    val statement: PrintDocumentStyle,
    val inventory: PrintDocumentStyle
)

/** يملك تفضيلات الطباعة وصلاحية تعديل بيانات المؤسسة المعروضة في شاشة الطباعة. */
@HiltViewModel
class PrintingSettingsViewModel @Inject constructor(
    private val gateway: PrintingSettingsGateway
) : ViewModel(),
    UiStateHolder<PrintingSettingsUiState>,
    UiEventHandler<PrintingSettingsEvent> {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val invoiceTemplate = gateway.invoiceTemplate.asState(InvoiceTemplate.CLASSIC)
    val invoiceFont = gateway.invoiceFont.asState(InvoiceFont.CAIRO)
    val invoiceFontSize = gateway.invoiceFontSize.asState(14)

    val priceListFont = gateway.priceListFont.asState(InvoiceFont.CAIRO)
    val priceListFontSize = gateway.priceListFontSize.asState(14)

    val statementTemplate = gateway.statementTemplate.asState(InvoiceTemplate.CLASSIC)
    val statementFont = gateway.statementFont.asState(InvoiceFont.CAIRO)
    val statementFontSize = gateway.statementFontSize.asState(14)

    val inventoryTemplate = gateway.inventoryTemplate.asState(InvoiceTemplate.CLASSIC)
    val inventoryFont = gateway.inventoryFont.asState(InvoiceFont.CAIRO)
    val inventoryFontSize = gateway.inventoryFontSize.asState(14)

    val reportsTemplate = gateway.reportsTemplate.asState(InvoiceTemplate.CLASSIC)
    val reportsFont = gateway.reportsFont.asState(InvoiceFont.CAIRO)
    val reportsFontSize = gateway.reportsFontSize.asState(14)

    private val invoiceStyle = combine(invoiceTemplate, invoiceFont, invoiceFontSize) { template, font, size ->
        PrintDocumentStyle(template, font, size)
    }
    private val priceListStyle = combine(priceListFont, priceListFontSize) { font, size ->
        PrintDocumentStyle(InvoiceTemplate.PROFESSIONAL, font, size)
    }
    private val statementStyle = combine(statementTemplate, statementFont, statementFontSize) { template, font, size ->
        PrintDocumentStyle(template, font, size)
    }
    private val inventoryStyle = combine(inventoryTemplate, inventoryFont, inventoryFontSize) { template, font, size ->
        PrintDocumentStyle(template, font, size)
    }
    private val reportsStyle = combine(reportsTemplate, reportsFont, reportsFontSize) { template, font, size ->
        PrintDocumentStyle(template, font, size)
    }

    private val primaryStyles = combine(
        invoiceStyle,
        priceListStyle,
        statementStyle,
        inventoryStyle
    ) { invoice, priceList, statement, inventory ->
        PrimaryPrintingStyles(invoice, priceList, statement, inventory)
    }

    override val uiState: StateFlow<PrintingSettingsUiState> = combine(
        primaryStyles,
        reportsStyle,
        isAdmin
    ) { primary, reports, admin ->
        PrintingSettingsUiState(
            isAdmin = admin,
            invoice = primary.invoice,
            priceList = primary.priceList,
            statement = primary.statement,
            inventory = primary.inventory,
            reports = reports
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PrintingSettingsUiState()
    )

    init {
        viewModelScope.launch {
            _isAdmin.value = gateway.canManageOrganization()
        }
    }

    override fun onEvent(event: PrintingSettingsEvent) {
        when (event) {
            is PrintingSettingsEvent.InvoiceTemplateChanged -> launchUpdate { gateway.setInvoiceTemplate(event.value) }
            is PrintingSettingsEvent.InvoiceFontChanged -> launchUpdate { gateway.setInvoiceFont(event.value) }
            is PrintingSettingsEvent.InvoiceFontSizeChanged -> launchUpdate { gateway.setInvoiceFontSize(event.value) }
            is PrintingSettingsEvent.PriceListFontChanged -> launchUpdate { gateway.setPriceListFont(event.value) }
            is PrintingSettingsEvent.PriceListFontSizeChanged -> launchUpdate { gateway.setPriceListFontSize(event.value) }
            is PrintingSettingsEvent.StatementTemplateChanged -> launchUpdate { gateway.setStatementTemplate(event.value) }
            is PrintingSettingsEvent.StatementFontChanged -> launchUpdate { gateway.setStatementFont(event.value) }
            is PrintingSettingsEvent.StatementFontSizeChanged -> launchUpdate { gateway.setStatementFontSize(event.value) }
            is PrintingSettingsEvent.InventoryTemplateChanged -> launchUpdate { gateway.setInventoryTemplate(event.value) }
            is PrintingSettingsEvent.InventoryFontChanged -> launchUpdate { gateway.setInventoryFont(event.value) }
            is PrintingSettingsEvent.InventoryFontSizeChanged -> launchUpdate { gateway.setInventoryFontSize(event.value) }
            is PrintingSettingsEvent.ReportsTemplateChanged -> launchUpdate { gateway.setReportsTemplate(event.value) }
            is PrintingSettingsEvent.ReportsFontChanged -> launchUpdate { gateway.setReportsFont(event.value) }
            is PrintingSettingsEvent.ReportsFontSizeChanged -> launchUpdate { gateway.setReportsFontSize(event.value) }
        }
    }

    fun setInvoiceTemplate(value: InvoiceTemplate) = onEvent(PrintingSettingsEvent.InvoiceTemplateChanged(value))
    fun setInvoiceFont(value: InvoiceFont) = onEvent(PrintingSettingsEvent.InvoiceFontChanged(value))
    fun setInvoiceFontSize(value: Int) = onEvent(PrintingSettingsEvent.InvoiceFontSizeChanged(value))
    fun setPriceListFont(value: InvoiceFont) = onEvent(PrintingSettingsEvent.PriceListFontChanged(value))
    fun setPriceListFontSize(value: Int) = onEvent(PrintingSettingsEvent.PriceListFontSizeChanged(value))
    fun setStatementTemplate(value: InvoiceTemplate) = onEvent(PrintingSettingsEvent.StatementTemplateChanged(value))
    fun setStatementFont(value: InvoiceFont) = onEvent(PrintingSettingsEvent.StatementFontChanged(value))
    fun setStatementFontSize(value: Int) = onEvent(PrintingSettingsEvent.StatementFontSizeChanged(value))
    fun setInventoryTemplate(value: InvoiceTemplate) = onEvent(PrintingSettingsEvent.InventoryTemplateChanged(value))
    fun setInventoryFont(value: InvoiceFont) = onEvent(PrintingSettingsEvent.InventoryFontChanged(value))
    fun setInventoryFontSize(value: Int) = onEvent(PrintingSettingsEvent.InventoryFontSizeChanged(value))
    fun setReportsTemplate(value: InvoiceTemplate) = onEvent(PrintingSettingsEvent.ReportsTemplateChanged(value))
    fun setReportsFont(value: InvoiceFont) = onEvent(PrintingSettingsEvent.ReportsFontChanged(value))
    fun setReportsFontSize(value: Int) = onEvent(PrintingSettingsEvent.ReportsFontSizeChanged(value))

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asState(default: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)
}
