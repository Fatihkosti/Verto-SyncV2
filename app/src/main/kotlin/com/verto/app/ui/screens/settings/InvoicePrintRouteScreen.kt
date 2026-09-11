package com.verto.app.ui.screens.settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.settings.presentation.PriceListPreviewCard
import com.verto.app.feature.settings.presentation.OrgDataRow

import com.verto.app.feature.settings.presentation.SettingsDimensions
import com.verto.app.feature.settings.presentation.SettingsTextScale
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.components.SettingsDivider
import com.verto.app.ui.components.SettingsSectionHeader
import com.verto.app.feature.settings.presentation.InvoicePreviewCard
import com.verto.app.feature.settings.presentation.TemplateSelector
import com.verto.app.feature.settings.presentation.FontSelector
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.R
import com.verto.app.feature.organization.presentation.OrganizationSaveState
import com.verto.app.feature.organization.presentation.OrganizationSettingsViewModel
import com.verto.app.feature.profile.presentation.ProfileSettingsViewModel
import com.verto.app.feature.settings.presentation.printing.PrintingSettingsViewModel
import com.verto.app.ui.theme.*
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import androidx.compose.ui.text.font.Font
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoScrollableTabRow
import com.verto.app.ui.components.VertoTopAppBar

private val PRINT_TABS = listOf("فواتير", "كشف أسعار", "كشف حساب", "المخزون", "التقارير")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePrintScreen(
    onBack : () -> Unit = {},
    vm     : PrintingSettingsViewModel,
    profileVm: ProfileSettingsViewModel,
    organizationVm: OrganizationSettingsViewModel
) {
    val printingUiState by vm.uiState.collectAsStateWithLifecycle()
    val organizationUiState by organizationVm.uiState.collectAsStateWithLifecycle()
    val profileUiState by profileVm.uiState.collectAsStateWithLifecycle()

    val isAdmin = printingUiState.isAdmin
    val orgSettings = organizationUiState.settings
    val organizationSaveState = organizationUiState.saveState
    val profile = profileUiState.profile
    val userName = profile.name
    val userPhone = profile.phone
    val orgName = orgSettings.shopName
    val orgAddr = orgSettings.address

    val routeState = rememberInvoicePrintRouteState(printingUiState)
    with(routeState) {

    if (showOrgDialog) {
        OrgSettingsDialog(
            current   = orgSettings,
            isSaving  = organizationSaveState is OrganizationSaveState.Loading,
            onSave    = { updated -> organizationVm.save(updated); showOrgDialog = false },
            onDismiss = { showOrgDialog = false }
        )
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_4218f4cd8cc9), color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VertoScrollableTabRow(
                selectedTabIndex  = selectedTab,
                containerColor    = BgDeep,
                contentColor      = AccentPrimary,
                edgePadding       = SettingsDimensions.dp8,
                indicator         = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentPrimary
                        )
                    }
                }
            ) {
                PRINT_TABS.forEachIndexed { index, title ->
                    Tab(
                        selected  = selectedTab == index,
                        onClick   = { selectedTab = index },
                        text      = {
                            Text(
                                title,
                                fontSize   = SettingsTextScale.sp13,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color      = if (selectedTab == index) AccentPrimary else TextMuted
                            )
                        }
                    )
                }
            }

            HorizontalDivider(color = BorderColor, thickness = SettingsDimensions.dp0_5)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp8),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)
            ) {
                InvoicePrintTabContent(
                    state = routeState,
                    preview = InvoicePrintPreviewContext(
                        orgName = orgName,
                        orgAddr = orgAddr,
                        userName = userName,
                        userPhone = userPhone,
                        orgSettings = orgSettings,
                    ),
                    events = InvoicePrintApplyEvents(
                        invoice = { template, font, size ->
                            vm.setInvoiceTemplate(template)
                            vm.setInvoiceFont(font)
                            vm.setInvoiceFontSize(size)
                        },
                        priceList = { font, size ->
                            vm.setPriceListFont(font)
                            vm.setPriceListFontSize(size)
                        },
                        statement = { template, font, size ->
                            vm.setStatementTemplate(template)
                            vm.setStatementFont(font)
                            vm.setStatementFontSize(size)
                        },
                        inventory = { template, font, size ->
                            vm.setInventoryTemplate(template)
                            vm.setInventoryFont(font)
                            vm.setInventoryFontSize(size)
                        },
                        reports = { template, font, size ->
                            vm.setReportsTemplate(template)
                            vm.setReportsFont(font)
                            vm.setReportsFontSize(size)
                        },
                    ),
                )

                if (selectedTab == 0) {
                    Spacer(Modifier.height(SettingsDimensions.dp8))
                    HorizontalDivider(color = BorderColor.copy(0.5f))
                    Spacer(Modifier.height(SettingsDimensions.dp8))
                    SettingsSectionHeader("بيانات المنشأة")

                    if (isAdmin) {
                        SettingsCard {
                            OrgDataRow("العملة",              orgSettings.currency)
                            SettingsDivider()
                            OrgDataRow("رقم الضريبة / السجل", orgSettings.taxNumber.ifBlank { "—" })
                            SettingsDivider()
                            OrgDataRow("نص التذييل",           orgSettings.invoiceFooter.ifBlank { "—" })
                            SettingsDivider()
                            OrgDataRow("الشعار (URL)",         orgSettings.logoUrl.ifBlank { "لم يُضف بعد" })
                            SettingsDivider()
                            OrgDataRow("التوقيع (URL)",        orgSettings.signatureUrl.ifBlank { "لم يُضف بعد" })
                            SettingsDivider()
                            TextButton(
                                onClick  = { showOrgDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.dp8, vertical = SettingsDimensions.dp4)
                            ) {
                                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(SettingsDimensions.dp16))
                                Spacer(Modifier.width(SettingsDimensions.dp6))
                                Text(androidx.compose.ui.res.stringResource(R.string.ds_39892370ecce), fontSize = SettingsTextScale.sp13)
                            }
                        }
                    } else {
                        SettingsCard {
                            OrgDataRow("العملة",              orgSettings.currency)
                            SettingsDivider()
                            OrgDataRow("رقم الضريبة / السجل", orgSettings.taxNumber.ifBlank { "—" })
                            SettingsDivider()
                            OrgDataRow("نص التذييل",          orgSettings.invoiceFooter.ifBlank { "—" })
                        }
                        Spacer(Modifier.height(SettingsDimensions.dp4))
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.ds_4cdad7167571),
                            color    = TextMuted,
                            fontSize = SettingsTextScale.sp11,
                            modifier = Modifier.padding(horizontal = SettingsDimensions.dp4)
                        )
                    }
                }

                Spacer(Modifier.height(SettingsDimensions.dp24))
            }
        }
    }
    }
}
