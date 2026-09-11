package com.verto.app.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.verto.app.feature.settings.presentation.printing.PrintDocumentStyle
import com.verto.app.feature.settings.presentation.printing.PrintingSettingsUiState
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate

internal class InvoicePrintRouteState(
    invoice: PrintDocumentStyle,
    priceList: PrintDocumentStyle,
    statement: PrintDocumentStyle,
    inventory: PrintDocumentStyle,
    reports: PrintDocumentStyle,
    selectedTabState: MutableState<Int>,
    showOrgDialogState: MutableState<Boolean>
) {
    val savedTemplate = invoice.template
    val savedFont = invoice.font
    val savedFontSize = invoice.fontSize
    var pendingTemplate by mutableStateOf(savedTemplate)
    var pendingFont by mutableStateOf(savedFont)
    var pendingFontSize by mutableIntStateOf(savedFontSize)
    val invoiceHasChanges get() = pendingTemplate != savedTemplate || pendingFont != savedFont || pendingFontSize != savedFontSize

    val savedPLFont = priceList.font
    val savedPLFontSize = priceList.fontSize
    var pendingPLFont by mutableStateOf(savedPLFont)
    var pendingPLFontSize by mutableIntStateOf(savedPLFontSize)
    val plHasChanges get() = pendingPLFont != savedPLFont || pendingPLFontSize != savedPLFontSize

    val savedSTTemplate = statement.template
    val savedSTFont = statement.font
    val savedSTFontSize = statement.fontSize
    var pendingSTTemplate by mutableStateOf(savedSTTemplate)
    var pendingSTFont by mutableStateOf(savedSTFont)
    var pendingSTFontSize by mutableIntStateOf(savedSTFontSize)
    val stHasChanges get() = pendingSTTemplate != savedSTTemplate || pendingSTFont != savedSTFont || pendingSTFontSize != savedSTFontSize

    val savedIVTemplate = inventory.template
    val savedIVFont = inventory.font
    val savedIVFontSize = inventory.fontSize
    var pendingIVTemplate by mutableStateOf(savedIVTemplate)
    var pendingIVFont by mutableStateOf(savedIVFont)
    var pendingIVFontSize by mutableIntStateOf(savedIVFontSize)
    val ivHasChanges get() = pendingIVTemplate != savedIVTemplate || pendingIVFont != savedIVFont || pendingIVFontSize != savedIVFontSize

    val savedRPTemplate = reports.template
    val savedRPFont = reports.font
    val savedRPFontSize = reports.fontSize
    var pendingRPTemplate by mutableStateOf(savedRPTemplate)
    var pendingRPFont by mutableStateOf(savedRPFont)
    var pendingRPFontSize by mutableIntStateOf(savedRPFontSize)
    val rpHasChanges get() = pendingRPTemplate != savedRPTemplate || pendingRPFont != savedRPFont || pendingRPFontSize != savedRPFontSize

    var selectedTab by selectedTabState
    var showOrgDialog by showOrgDialogState
}

@Composable
internal fun rememberInvoicePrintRouteState(ui: PrintingSettingsUiState): InvoicePrintRouteState {
    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val showOrgDialog = remember { mutableStateOf(false) }
    return remember(ui.invoice, ui.priceList, ui.statement, ui.inventory, ui.reports, selectedTab, showOrgDialog) {
        InvoicePrintRouteState(ui.invoice, ui.priceList, ui.statement, ui.inventory, ui.reports, selectedTab, showOrgDialog)
    }
}
