package com.verto.app.feature.invoice.presentation.invoice

import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.feature.invoice.presentation.InvoiceTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.invoice.application.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.feature.invoice.application.canEditInvoiceCategory
import com.verto.app.feature.invoice.application.canExportInvoiceCategory
import com.verto.app.feature.invoice.application.canViewInvoiceCategory
import com.verto.app.feature.invoice.application.viewInvoiceDeniedMessage
import com.verto.app.feature.invoice.application.InvoiceSummary
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import com.verto.app.ui.components.VertoIconButton

@Composable
private fun InvoiceDetailsHeader(invoiceNumber: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(InvoiceDimensions.dp56)
            .padding(horizontal = InvoiceDimensions.dp24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VertoIconButton(onClick = onBack, modifier = Modifier.size(InvoiceDimensions.dp48)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), tint = AccentPrimary, modifier = Modifier.size(InvoiceDimensions.dp30))
        }
        Spacer(Modifier.weight(1f))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_adebc50ade66, invoiceNumber),
                color = TextPrimary,
                fontSize = InvoiceTextScale.sp30,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

private enum class InvoiceWhatsAppAction350 { INVOICE, REMINDER }

@Composable
fun InvoiceScreen(
    invoiceId: String,
    onBack: () -> Unit,
    onAddPayment: (String, String) -> Unit,
    onPayFull: (String, String) -> Unit = onAddPayment,
    onEditInvoice: (String, String) -> Unit,
    onDeleteSuccess: () -> Unit,
    openCommission: Boolean = false,
    vm: InvoiceViewModel = hiltViewModel()
) {
    LaunchedEffect(invoiceId) { vm.init(invoiceId) }

    val summary  by vm.summary.collectAsStateWithLifecycle()
    val client   by vm.client.collectAsStateWithLifecycle()
    val commissionBeneficiary by vm.commissionBeneficiary.collectAsStateWithLifecycle()
    val items    by vm.invoiceItems.collectAsStateWithLifecycle()
    val communicationHistory by vm.communicationHistory.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    var deletePaymentTarget  by remember { mutableStateOf<PaymentItem?>(null) }
    var showDeleteInvoice    by remember { mutableStateOf(false) }
    var voidReason by rememberSaveable { mutableStateOf("") }
    var refundPaymentsConfirmed by rememberSaveable { mutableStateOf(false) }
    var showWaSelector       by remember { mutableStateOf(false) }
    var whatsappAction       by remember { mutableStateOf(InvoiceWhatsAppAction350.INVOICE) }
    var selectedTab          by rememberSaveable { mutableStateOf(InvoiceDetailsTab350.ITEMS) }
    var showThankYouPreview  by remember { mutableStateOf(false) }
    var thankYouPayment      by remember { mutableStateOf<PaymentItem?>(null) }
    var showCommissionDialog      by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }

    if (summary == null || permissions == null) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier.fillMaxSize().background(BgDeep),
                contentAlignment = Alignment.Center,
            ) {
                VertoLoadingState(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_ab50a49be35d), modifier = Modifier.padding(InvoiceDimensions.dp32))
            }
        }
        return
    }
    val s = checkNotNull(summary)
    val c = client
    val canDeleteInvoice = when (s.invoice.category) {
        InvoiceCategory.SALE -> permissions?.salesDelete == true
        InvoiceCategory.PURCHASE -> permissions?.purchasesDelete == true
    }
    val canReversePayment = permissions?.paymentsReverse == true
    val canViewInvoice = permissions?.canViewInvoiceCategory(s.invoice.category) == true
    val canEditInvoice = permissions?.canEditInvoiceCategory(s.invoice.category) == true
    val canExportInvoice = permissions?.canExportInvoiceCategory(s.invoice.category) == true

    if (!canViewInvoice) {
        AlertDialog(
            onDismissRequest = onBack,
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_e1ed7cb6a121), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(viewInvoiceDeniedMessage(s.invoice.category), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), color = AccentLight, fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    if (s.invoice.category == InvoiceCategory.PURCHASE) {
        PurchaseInvoiceScreen369(
            summary = s,
            supplier = c,
            items = items,
            permissions = checkNotNull(permissions),
            onBack = onBack,
            onAddPayment = onAddPayment,
            onPayFull = onPayFull,
            onEditInvoice = onEditInvoice,
            onDeleteSuccess = onDeleteSuccess,
            vm = vm,
        )
        return
    }

    val canManageCommission = permissions?.commissionManage == true
    var commissionAutoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openCommission, canManageCommission, c) {
        if (openCommission && !commissionAutoOpened && canManageCommission &&
            s.invoice.category == InvoiceCategory.SALE &&
            commissionBeneficiary != null
        ) {
            commissionAutoOpened = true
            showCommissionDialog = true
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = BgDeep,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Surface(
                    tonalElevation = InvoiceDimensions.dp8,
                    shadowElevation = InvoiceDimensions.dp12,
                    color = BgCard,
                ) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = InvoiceDimensions.dp8, vertical = InvoiceDimensions.dp8),
                        horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (canEditInvoice) {
                        BottomActionBtn(
                            icon = Icons.Filled.Edit,
                            label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit),
                            color = AccentPrimary,
                            onClick = { onEditInvoice(s.invoice.id, s.invoice.clientId) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (canExportInvoice) {
                        BottomActionBtn(
                            icon = Icons.Filled.Print,
                            label = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_a37bb682e55c),
                            color = AccentBlue,
                            onClick = { vm.generateAndSharePdf(context) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (canManageCommission &&
                        s.invoice.category == InvoiceCategory.SALE &&
                        commissionBeneficiary != null) {
                        BottomActionBtn(
                            icon = Icons.Filled.MonetizationOn,
                            label = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_6a33e15db096),
                            color = GoldPrimary,
                            onClick = { showCommissionDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val canCreateReturn = s.invoice.category == InvoiceCategory.SALE &&
                        permissions?.salesEdit == true && !s.invoice.voided && items.isNotEmpty()
                    if (canCreateReturn) {
                        BottomActionBtn(
                            icon = Icons.Filled.AssignmentReturn,
                            label = "مرتجع",
                            color = AccentPrimary,
                            onClick = { showReturnDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (canDeleteInvoice) {
                        BottomActionBtn(
                            icon = Icons.Filled.Delete,
                            label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete),
                            color = MaterialTheme.colorScheme.error,
                            onClick = { voidReason = ""; refundPaymentsConfirmed = false; showDeleteInvoice = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp10),
            ) {
                InvoiceDetailsHeader(
                    invoiceNumber = s.invoice.invoiceNumber,
                    onBack = onBack,
                )
                InvoiceOverview350(summary = s, client = c, commissionBeneficiary = commissionBeneficiary)
                InvoiceTabs350(selected = selectedTab, onSelected = { selectedTab = it })
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (selectedTab) {
                        InvoiceDetailsTab350.ITEMS -> InvoiceItemsTab350(
                            lines = items,
                            isSale = s.invoice.isOwedToMe,
                        )
                        InvoiceDetailsTab350.PAYMENTS -> InvoicePaymentsTab350(
                            summary = s,
                            client = c,
                            canReverse = canReversePayment,
                            onAddPayment = { onAddPayment(s.invoice.id, s.invoice.clientId) },
                            onPayFull = { onPayFull(s.invoice.id, s.invoice.clientId) },
                            onReverse = { deletePaymentTarget = it },
                            onThankYou = { payment ->
                                thankYouPayment = payment
                                showThankYouPreview = true
                            },
                        )
                        InvoiceDetailsTab350.COMMUNICATION -> InvoiceCommunicationTab350(
                            summary = s,
                            client = c,
                            history = communicationHistory,
                            canCommunicate = canExportInvoice && c?.phone?.isNotBlank() == true,
                            canThank = s.payments.any { it.amount > 0.0 && it.reversedPaymentId == null },
                            onReminder = {
                                whatsappAction = InvoiceWhatsAppAction350.REMINDER
                                showWaSelector = true
                            },
                            onThankYou = {
                                s.payments
                                    .filter { it.amount > 0.0 && it.reversedPaymentId == null }
                                    .maxByOrNull { it.paidAt }
                                    ?.let { payment ->
                                        thankYouPayment = payment
                                        showThankYouPreview = true
                                    }
                            },
                            onShareInvoice = {
                                whatsappAction = InvoiceWhatsAppAction350.INVOICE
                                showWaSelector = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showWaSelector) {
        val hasNormal = WhatsAppUtils.isWhatsAppInstalled(context)
        val hasBusiness = WhatsAppUtils.isWhatsAppBusinessInstalled(context)
        if (hasNormal && hasBusiness) {
            Dialog(onDismissRequest = { showWaSelector = false }) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(InvoiceDimensions.dp20))
                        .background(BgCard).border(InvoiceDimensions.dp1, BorderColor, RoundedCornerShape(InvoiceDimensions.dp20))
                        .padding(InvoiceDimensions.dp20)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp12)) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_437c04868fa8), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InvoiceTextScale.sp16)
                        listOf(false to "واتساب العادي", true to "واتساب بزنس").forEach { (isBiz, label) ->
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(InvoiceDimensions.dp12))
                                    .background(BgSurface).border(InvoiceDimensions.dp1, BorderColor, RoundedCornerShape(InvoiceDimensions.dp12))
                                    .clickable {
                                        when (whatsappAction) {
                                            InvoiceWhatsAppAction350.INVOICE -> vm.shareViaWhatsApp(context, isBiz)
                                            InvoiceWhatsAppAction350.REMINDER -> vm.shareReminder(isBiz)
                                        }
                                        showWaSelector = false
                                    }
                                    .padding(InvoiceDimensions.dp14)
                            ) {
                                Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            when (whatsappAction) {
                InvoiceWhatsAppAction350.INVOICE -> vm.shareViaWhatsApp(context, hasBusiness && !hasNormal)
                InvoiceWhatsAppAction350.REMINDER -> vm.shareReminder(hasBusiness && !hasNormal)
            }
            showWaSelector = false
        }
    }

    if (showThankYouPreview && thankYouPayment != null && summary != null && client != null) {
        val isFullyPaid = s.totalPaid >= s.invoice.totalAmount
        var messageText by remember {
            mutableStateOf(
                InvoiceMessagePreviewBuilder.buildThankYouMessage(checkNotNull(client), s, checkNotNull(thankYouPayment).amount, isFullyPaid)
            )
        }
        Dialog(onDismissRequest = { showThankYouPreview = false }) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(InvoiceDimensions.dp20))
                    .background(BgCard).border(InvoiceDimensions.dp1, BorderColor, RoundedCornerShape(InvoiceDimensions.dp20))
                    .padding(InvoiceDimensions.dp20)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp12)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_13401859a8f2), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InvoiceTextScale.sp16)
                    VertoOutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = InvoiceDimensions.dp120),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = BgSurface,
                            unfocusedContainerColor = BgSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(InvoiceDimensions.dp12)
                    )
                    val hasNormal = WhatsAppUtils.isWhatsAppInstalled(context)
                    val hasBusiness = WhatsAppUtils.isWhatsAppBusinessInstalled(context)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)) {
                        VertoOutlinedButton(
                            onClick = { showThankYouPreview = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(InvoiceDimensions.dp12)
                        ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary) }
                        if (hasNormal && hasBusiness) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp6)) {
                                VertoButton(
                                    onClick = { vm.sendThankYou(context, checkNotNull(thankYouPayment).amount, false); showThankYouPreview = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(InvoiceDimensions.dp12),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                                ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_whatsapp), color = TextPrimary, fontSize = InvoiceTextScale.sp12) }
                                VertoButton(
                                    onClick = { vm.sendThankYou(context, checkNotNull(thankYouPayment).amount, true); showThankYouPreview = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(InvoiceDimensions.dp12),
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppBrand)
                                ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_9c8f6517c12c), color = Color.White, fontSize = InvoiceTextScale.sp12) }
                            }
                        } else {
                            VertoButton(
                                onClick = { vm.sendThankYou(context, checkNotNull(thankYouPayment).amount, hasBusiness); showThankYouPreview = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(InvoiceDimensions.dp12),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppBrand)
                            ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_a3bbb0194ac6), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    deletePaymentTarget?.let { payment ->
        AlertDialog(
            onDismissRequest = { deletePaymentTarget = null },
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_fa374e2cb37b), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_17f84f2d5d1a),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.reversePaymentEntry(payment); deletePaymentTarget = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_30e483b4cc68), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePaymentTarget = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }

    val paymentStructuredError by vm.paymentStructuredError.collectAsStateWithLifecycle()
    paymentStructuredError?.let { error ->
        val resolved = error.resolveVertoError()
        AlertDialog(
            onDismissRequest = vm::clearPaymentStructuredError,
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(resolved.title, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(resolved.message, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = vm::clearPaymentStructuredError) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentLight, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val paymentError by vm.paymentError.collectAsStateWithLifecycle()
    paymentError?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearPaymentError() },
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_2ef6a4f3144e), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearPaymentError() }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentLight, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val exportError by vm.exportError.collectAsStateWithLifecycle()
    exportError?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearExportError() },
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_22a9f82efac2), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearExportError() }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentLight, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showReturnDialog) {
        InvoiceReturnDialog351(
            lines = items,
            onDismiss = { showReturnDialog = false },
            onConfirm = { quantities, reason, cashRefund ->
                vm.createSalesReturn(quantities, reason, cashRefund) { showReturnDialog = false }
            },
        )
    }

    if (showCommissionDialog && summary != null) {
        val profit = items.sumOf { (it.sellPrice - it.buyPrice) * it.quantity }
        CommissionDialog(
            currentCommission = s.invoice.commission,
            invoiceTotal = s.invoice.totalAmount,
            profit = profit,
            onDismiss = { showCommissionDialog = false },
            onSave = { amount ->
                vm.saveCommission(amount)
                showCommissionDialog = false
            }
        )
    }

    if (showDeleteInvoice) {
        val hasPayments = (summary?.totalPaid ?: 0.0) > 0.01
        val canConfirmVoid = voidReason.trim().length >= 3 && (!hasPayments || refundPaymentsConfirmed)
        AlertDialog(
            onDismissRequest = { showDeleteInvoice = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_008e2f198440), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp12)) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_936c69955313),
                        color = TextSecondary,
                    )
                    VertoOutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_2715575a2c6f)) },
                        singleLine = false,
                        minLines = 2,
                    )
                    if (hasPayments) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = refundPaymentsConfirmed,
                                onCheckedChange = { refundPaymentsConfirmed = it },
                            )
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_3dfa681200f8), color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirmVoid,
                    onClick = {
                        showDeleteInvoice = false
                        vm.deleteInvoiceById(
                            invoiceId = invoiceId,
                            reason = voidReason,
                            refundPayments = refundPaymentsConfirmed,
                            onSuccess = onDeleteSuccess,
                        )
                    },
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_e9fc420c6c41), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteInvoice = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), color = TextSecondary)
                }
            }
        )
    }

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.clearDeleteError() },
            containerColor = BgCard,
            shape = RoundedCornerShape(InvoiceDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_20ad9e2c962c), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearDeleteError() }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
