package com.verto.app.feature.party.presentation.client

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPaymentScreen(
    clientId: String,
    onBack  : () -> Unit,
    vm      : ClientPaymentViewModel = hiltViewModel()
) {
    LaunchedEffect(clientId) { vm.init(clientId) }

    val client              by vm.client.collectAsStateWithLifecycle()
    val creditInvoices      by vm.creditInvoices.collectAsStateWithLifecycle()
    val totalDebt           by vm.totalDebt.collectAsStateWithLifecycle()
    val result              by vm.result.collectAsStateWithLifecycle()
    val isCompetitor        by vm.isCompetitor.collectAsStateWithLifecycle()
    val processing          by vm.processing.collectAsStateWithLifecycle()
    val clientCredit        by vm.clientCredit.collectAsStateWithLifecycle()
    val error               by vm.error.collectAsStateWithLifecycle()

    var amountText              by remember { mutableStateOf("") }
    var selectedInvoiceId       by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog        by remember { mutableStateOf(false) }
    var competitorDirection     by remember { mutableStateOf<Boolean?>(null) }

    val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0

    // عند اختيار فاتورة يحسب المتبقي تلقائياً
    val selectedSummary = creditInvoices.find { it.invoice.id == selectedInvoiceId }

    // معاينة
    val previewDebtAfter = maxOf(totalDebt - amount, 0.0)

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title  = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_client_payment_title, client?.name ?: androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_loading_name)),
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = PartyDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp14),
            contentPadding = PaddingValues(vertical = PartyDimensions.dp16)
        ) {
            // ── ملخص الديون ───────────────────────────
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PartyDimensions.dp12))
                        .background(BgCard)
                        .border(PartyDimensions.dp1, BorderColor, RoundedCornerShape(PartyDimensions.dp12))
                        .padding(PartyDimensions.dp14),
                    verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_b53abd3067ba), color = TextMuted, fontSize = PartyTextScale.sp12)
                    Text(
                        WhatsAppUtils.formatAmount(totalDebt) + androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_ae5a9f7f0614),
                        color = ErrorColor,
                        fontSize = PartyTextScale.sp18,
                        fontWeight = FontWeight.Black
                    )
                    if (clientCredit > 0.01) {
                        PayPreviewRow(
                            "رصيد العميل لديك (مقدَّم)",
                            WhatsAppUtils.formatAmount(clientCredit) + " جنيه",
                            SuccessColor
                        )
                    }
                }
            }

            // ── حقل المبلغ ────────────────────────────
            item {
                VertoOutlinedTextField(
                    value         = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_6bde36b19201)) },
                    leadingIcon   = { Icon(Icons.Filled.Payments, null, tint = AccentPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentPrimary,
                        unfocusedBorderColor = BorderColor
                    )
                )
            }

            // زر "سداد الكل"
            if (totalDebt > 0.01) {
                item {
                    TextButton(
                        onClick   = { amountText = fmtPay(totalDebt) },
                        modifier  = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_e775115db05e, fmtPay(totalDebt)),
                            color      = AccentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── معاينة ────────────────────────────────
            if (amount > 0.01) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PartyDimensions.dp12))
                            .background(BgCard)
                            .border(PartyDimensions.dp1, BorderColor, RoundedCornerShape(PartyDimensions.dp12))
                            .padding(PartyDimensions.dp14),
                        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_preview), color = AccentPrimary, fontSize = PartyTextScale.sp13, fontWeight = FontWeight.Bold)
                        PayPreviewRow(
                            "الديون بعد السداد",
                            WhatsAppUtils.formatAmount(previewDebtAfter) + " جنيه",
                            if (previewDebtAfter > 0.01) ErrorColor else SuccessColor
                        )
                    }
                }
            }

            // ── قائمة الفواتير الآجلة ─────────────────
            item {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_c5ab7e55aca1_2, if (selectedInvoiceId == null) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_c5ab7e55aca1) else ""),
                    color    = TextSecondary,
                    fontSize = PartyTextScale.sp13,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (creditInvoices.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = PartyDimensions.dp8),
                        contentAlignment = Alignment.Center
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_02f3b7d85f04), color = TextMuted, fontSize = PartyTextScale.sp13) }
                }
            } else {
                items(creditInvoices, key = { it.invoice.id }) { summary ->
                    InvoiceSelectRow(
                        summary    = summary,
                        isSelected = selectedInvoiceId == summary.invoice.id,
                        onClick    = {
                            selectedInvoiceId = if (selectedInvoiceId == summary.invoice.id) null
                            else summary.invoice.id
                        }
                    )
                }
            }

            // ── زر تأكيد السداد ───────────────────────
            item {
                Spacer(Modifier.height(PartyDimensions.dp8))
                VertoButton(
                    onClick  = { if (amount > 0.01) showConfirmDialog = true },
                    enabled  = amount > 0.01 && (if (isCompetitor) competitorDirection != null else creditInvoices.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth().height(PartyDimensions.dp52),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(PartyDimensions.dp8))
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_11b0cc7ecdb7), fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(PartyDimensions.dp32))
            }
        }
    }

    // ── dialog اتجاه السداد (للمنافس فقط) ──────────────
    if (isCompetitor && competitorDirection == null) {
        AlertDialog(
            onDismissRequest = { onBack() },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PartyDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_53383c97802a), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_34c281e54733), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { competitorDirection = true }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_9fccc80f9668), color = SuccessColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { competitorDirection = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_348ae40b33e4), color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── ديالوج تأكيد ─────────────────────────────────
    if (showConfirmDialog) {
        val targetLabel = selectedSummary?.let {
            "فاتورة #${String.format("%04d", it.invoice.invoiceNumber)}"
        } ?: "الفواتير الأقدم تلقائياً"

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor   = BgCard,
            title   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_11b0cc7ecdb7), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text    = {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_11b6c2f6e241, WhatsAppUtils.formatAmount(amount), targetLabel),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !processing,
                    onClick = {
                        if (isCompetitor) vm.payCompetitor(amount, competitorDirection == true)
                        else              vm.pay(amount, selectedInvoiceId)
                        showConfirmDialog = false
                    }
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }

    // ── نتيجة السداد ─────────────────────────────────
    result?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.clearResult(); onBack() },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PartyDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_f984bcbf2b80), color = SuccessColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)) {
                    PayPreviewRow(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_6bde36b19201),  WhatsAppUtils.formatAmount(r.paidAmount) + androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_3261ea023466), AccentPrimary)
                    PayPreviewRow(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_bee05588cb21_2), WhatsAppUtils.formatAmount(r.debtAfter)  + androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_bee05588cb21),
                        if (r.debtAfter > 0.01) ErrorColor else SuccessColor)
                    if (r.surplusCredit > 0.01) {
                        PayPreviewRow(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_218d2f0a0acd_2), WhatsAppUtils.formatAmount(r.surplusCredit) + androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_218d2f0a0acd), SuccessColor)
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_00d1d2effa2a),
                            color = SuccessColor, fontSize = PartyTextScale.sp11
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearResult(); onBack() }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── خطأ السداد ───────────────────────────────────
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PartyDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_78698483b2d9), color = ErrorColor, fontWeight = FontWeight.Bold) },
            text  = { Text(msg, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────
// Composables مساعدة
// ─────────────────────────────────────────────────────
