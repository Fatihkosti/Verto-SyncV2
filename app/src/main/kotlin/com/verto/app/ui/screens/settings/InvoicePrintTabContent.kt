package com.verto.app.ui.screens.settings

import com.verto.app.R

import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.settings.presentation.SettingsDimensions
import com.verto.app.feature.settings.presentation.SettingsTextScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.organization.domain.model.OrganizationSettings
import com.verto.app.feature.settings.presentation.FontSelector
import com.verto.app.feature.settings.presentation.InvoicePreviewCard
import com.verto.app.feature.settings.presentation.PriceListPreviewCard
import com.verto.app.feature.settings.presentation.TemplateSelector
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.components.SettingsSectionHeader
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate

internal data class InvoicePrintPreviewContext(
    val orgName: String,
    val orgAddr: String,
    val userName: String,
    val userPhone: String,
    val orgSettings: OrganizationSettings,
)

internal data class InvoicePrintApplyEvents(
    val invoice: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
    val priceList: (InvoiceFont, Int) -> Unit,
    val statement: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
    val inventory: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
    val reports: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
)

@Composable
internal fun InvoicePrintTabContent(
    state: InvoicePrintRouteState,
    preview: InvoicePrintPreviewContext,
    events: InvoicePrintApplyEvents,
) {
    when (state.selectedTab) {
        0 -> InvoiceStyleSection(state, preview, events.invoice)
        1 -> PriceListStyleSection(state, preview, events.priceList)
        2 -> StatementStyleSection(state, preview, events.statement)
        3 -> InventoryStyleSection(state, preview, events.inventory)
        4 -> ReportsStyleSection(state, preview, events.reports)
    }
}

@Composable
private fun InvoiceStyleSection(
    state: InvoicePrintRouteState,
    preview: InvoicePrintPreviewContext,
    onApply: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
) = with(state) {
    SettingsSectionHeader("شكل الفاتورة")
    TemplateSelector(selected = pendingTemplate, onSelect = { pendingTemplate = it })
    SettingsSectionHeader("خط الفاتورة")
    FontSelector(selected = pendingFont, onSelect = { pendingFont = it })
    PrintFontSizeSection(
        title = androidx.compose.ui.res.stringResource(R.string.ds_4e22e5de20b1),
        value = pendingFontSize,
        onValueChange = { pendingFontSize = it },
    )
    SettingsSectionHeader("معاينة")
    InvoicePreviewCard(
        template = pendingTemplate,
        font = pendingFont,
        fontSize = pendingFontSize,
        orgName = preview.orgName.ifBlank { "اسم المؤسسة" },
        orgAddress = preview.orgAddr.ifBlank { "العنوان، المدينة" },
        orgTax = preview.orgSettings.taxNumber,
        orgFooter = preview.orgSettings.invoiceFooter.ifBlank { "شكراً لتعاملكم معنا" },
        userName = preview.userName.ifBlank { "اسم الموظف" },
        userPhone = preview.userPhone.ifBlank { "05xxxxxxxx" },
    )
    PrintApplyControls(
        visible = invoiceHasChanges,
        onApply = { onApply(pendingTemplate, pendingFont, pendingFontSize) },
        onReset = {
            pendingTemplate = savedTemplate
            pendingFont = savedFont
            pendingFontSize = savedFontSize
        },
    )
}

@Composable
private fun PriceListStyleSection(
    state: InvoicePrintRouteState,
    preview: InvoicePrintPreviewContext,
    onApply: (InvoiceFont, Int) -> Unit,
) = with(state) {
    SettingsSectionHeader("خط الكشف")
    FontSelector(selected = pendingPLFont, onSelect = { pendingPLFont = it })
    PrintFontSizeSection(
        title = androidx.compose.ui.res.stringResource(R.string.ds_7fca33f2364e),
        value = pendingPLFontSize,
        onValueChange = { pendingPLFontSize = it },
    )
    SettingsSectionHeader("معاينة")
    PriceListPreviewCard(
        template = InvoiceTemplate.PROFESSIONAL,
        font = pendingPLFont,
        fontSize = pendingPLFontSize,
        orgName = preview.orgName.ifBlank { "اسم المؤسسة" },
        orgAddress = preview.orgAddr.ifBlank { "العنوان، المدينة" },
        orgTax = preview.orgSettings.taxNumber,
        orgFooter = preview.orgSettings.invoiceFooter.ifBlank { "شكراً لتعاملكم معنا" },
        userName = preview.userName.ifBlank { "اسم الموظف" },
    )
    PrintApplyControls(
        visible = plHasChanges,
        onApply = { onApply(pendingPLFont, pendingPLFontSize) },
        onReset = {
            pendingPLFont = savedPLFont
            pendingPLFontSize = savedPLFontSize
        },
    )
}

@Composable
private fun StatementStyleSection(
    state: InvoicePrintRouteState,
    preview: InvoicePrintPreviewContext,
    onApply: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
) = with(state) {
    SettingsSectionHeader("شكل الكشف")
    TemplateSelector(selected = pendingSTTemplate, onSelect = { pendingSTTemplate = it })
    SettingsSectionHeader("خط الكشف")
    FontSelector(selected = pendingSTFont, onSelect = { pendingSTFont = it })
    PrintFontSizeSection(
        title = androidx.compose.ui.res.stringResource(R.string.ds_7fca33f2364e),
        value = pendingSTFontSize,
        onValueChange = { pendingSTFontSize = it },
    )
    SettingsSectionHeader("معاينة")
    InvoicePreviewCard(
        template = pendingSTTemplate,
        font = pendingSTFont,
        fontSize = pendingSTFontSize,
        orgName = preview.orgName.ifBlank { "اسم المؤسسة" },
        orgAddress = preview.orgAddr.ifBlank { "العنوان، المدينة" },
        orgTax = preview.orgSettings.taxNumber,
        orgFooter = preview.orgSettings.invoiceFooter.ifBlank { "شكراً لتعاملكم معنا" },
        userName = preview.userName.ifBlank { "اسم الموظف" },
        userPhone = preview.userPhone.ifBlank { "05xxxxxxxx" },
    )
    PrintApplyControls(
        visible = stHasChanges,
        onApply = { onApply(pendingSTTemplate, pendingSTFont, pendingSTFontSize) },
        onReset = {
            pendingSTTemplate = savedSTTemplate
            pendingSTFont = savedSTFont
            pendingSTFontSize = savedSTFontSize
        },
    )
}

@Composable
private fun InventoryStyleSection(
    state: InvoicePrintRouteState,
    preview: InvoicePrintPreviewContext,
    onApply: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
) = with(state) {
    SettingsSectionHeader("شكل التقرير")
    TemplateSelector(selected = pendingIVTemplate, onSelect = { pendingIVTemplate = it })
    SettingsSectionHeader("خط التقرير")
    FontSelector(selected = pendingIVFont, onSelect = { pendingIVFont = it })
    PrintFontSizeSection(
        title = androidx.compose.ui.res.stringResource(R.string.ds_13a23a5f37c8),
        value = pendingIVFontSize,
        onValueChange = { pendingIVFontSize = it },
    )
    SettingsSectionHeader("معاينة")
    InvoicePreviewCard(
        template = pendingIVTemplate,
        font = pendingIVFont,
        fontSize = pendingIVFontSize,
        orgName = preview.orgName.ifBlank { "اسم المؤسسة" },
        orgAddress = preview.orgAddr.ifBlank { "العنوان، المدينة" },
        orgTax = preview.orgSettings.taxNumber,
        orgFooter = preview.orgSettings.invoiceFooter.ifBlank { "شكراً لتعاملكم معنا" },
        userName = preview.userName.ifBlank { "اسم الموظف" },
        userPhone = preview.userPhone.ifBlank { "05xxxxxxxx" },
    )
    PrintApplyControls(
        visible = ivHasChanges,
        onApply = { onApply(pendingIVTemplate, pendingIVFont, pendingIVFontSize) },
        onReset = {
            pendingIVTemplate = savedIVTemplate
            pendingIVFont = savedIVFont
            pendingIVFontSize = savedIVFontSize
        },
    )
}

@Composable
private fun ReportsStyleSection(
    state: InvoicePrintRouteState,
    preview: InvoicePrintPreviewContext,
    onApply: (InvoiceTemplate, InvoiceFont, Int) -> Unit,
) = with(state) {
    SettingsSectionHeader("شكل التقارير")
    TemplateSelector(selected = pendingRPTemplate, onSelect = { pendingRPTemplate = it })
    SettingsSectionHeader("خط التقارير")
    FontSelector(selected = pendingRPFont, onSelect = { pendingRPFont = it })
    PrintFontSizeSection(
        title = androidx.compose.ui.res.stringResource(R.string.ds_149e7d826a2d),
        value = pendingRPFontSize,
        onValueChange = { pendingRPFontSize = it },
    )
    SettingsSectionHeader("معاينة")
    InvoicePreviewCard(
        template = pendingRPTemplate,
        font = pendingRPFont,
        fontSize = pendingRPFontSize,
        orgName = preview.orgName.ifBlank { "اسم المؤسسة" },
        orgAddress = preview.orgAddr.ifBlank { "العنوان، المدينة" },
        orgTax = preview.orgSettings.taxNumber,
        orgFooter = preview.orgSettings.invoiceFooter.ifBlank { "شكراً لتعاملكم معنا" },
        userName = preview.userName.ifBlank { "اسم الموظف" },
        userPhone = preview.userPhone.ifBlank { "05xxxxxxxx" },
    )
    PrintApplyControls(
        visible = rpHasChanges,
        onApply = { onApply(pendingRPTemplate, pendingRPFont, pendingRPFontSize) },
        onReset = {
            pendingRPTemplate = savedRPTemplate
            pendingRPFont = savedRPFont
            pendingRPFontSize = savedRPFontSize
        },
    )
}

@Composable
private fun PrintFontSizeSection(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    SettingsSectionHeader(title)
    SettingsCard {
        Column(
            Modifier.padding(SettingsDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_a4b583d7b49d), color = TextPrimary, fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Medium)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_78753ca38353, value), color = AccentPrimary, fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 10f..20f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = AccentPrimary,
                    activeTrackColor = AccentPrimary,
                    inactiveTrackColor = BorderColor,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_b1d5781111d8), color = TextMuted, fontSize = SettingsTextScale.sp10)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_91032ad7bbcb), color = TextMuted, fontSize = SettingsTextScale.sp10)
            }
        }
    }
}

@Composable
private fun PrintApplyControls(
    visible: Boolean,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)) {
            VertoButton(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(SettingsDimensions.dp12),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(SettingsDimensions.dp18))
                Spacer(Modifier.width(SettingsDimensions.dp8))
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_apply), fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_b3bc3f3e67b8), color = TextMuted, fontSize = SettingsTextScale.sp13)
            }
        }
    }
}
