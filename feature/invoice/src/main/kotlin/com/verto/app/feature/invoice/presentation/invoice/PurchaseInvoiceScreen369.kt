package com.verto.app.feature.invoice.presentation.invoice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.invoice.application.ClientItem
import com.verto.app.feature.invoice.application.InvoiceLineView
import com.verto.app.feature.invoice.application.InvoiceSummary
import com.verto.app.feature.invoice.application.PaymentItem
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.money.Money
import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.feature.invoice.presentation.InvoiceTextScale
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoEmptyStateVariant
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoButton
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.resolveVertoError
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.InfoColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils

private enum class PurchaseInvoiceTab369 { ITEMS, PAYMENTS }

/** Dedicated purchase invoice detail surface. */
@Composable
internal fun PurchaseInvoiceScreen369(
    summary: InvoiceSummary,
    supplier: ClientItem?,
    items: List<InvoiceLineView>,
    permissions: EmployeePermissions,
    onBack: () -> Unit,
    onAddPayment: (String, String) -> Unit,
    onPayFull: (String, String) -> Unit,
    onEditInvoice: (String, String) -> Unit,
    onDeleteSuccess: () -> Unit,
    vm: InvoiceViewModel,
) {
    val context = LocalContext.current
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    val paymentError by vm.paymentError.collectAsStateWithLifecycle()
    val paymentStructuredError by vm.paymentStructuredError.collectAsStateWithLifecycle()
    val exportError by vm.exportError.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(PurchaseInvoiceTab369.ITEMS) }
    var reverseTarget by remember { mutableStateOf<PaymentItem?>(null) }
    var showVoid by rememberSaveable { mutableStateOf(false) }
    var voidReason by rememberSaveable { mutableStateOf("") }
    var refundPaymentsConfirmed by rememberSaveable { mutableStateOf(false) }

    val canEdit = permissions.purchasesEdit
    val canExport = permissions.purchasesExport
    val canDelete = permissions.purchasesDelete
    val canReverse = permissions.paymentsReverse
    val canAddSupplierPayment = permissions.suppliersAddPayment

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = BgDeep,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Surface(color = BgCard) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = InvoiceDimensions.dp8, vertical = InvoiceDimensions.dp8),
                        horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canEdit) {
                            BottomActionBtn(
                                icon = Icons.Filled.Edit,
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit),
                                color = AccentPrimary,
                                onClick = { onEditInvoice(summary.invoice.id, summary.invoice.clientId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (canExport) {
                            BottomActionBtn(
                                icon = Icons.Filled.Print,
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_a37bb682e55c),
                                color = AccentBlue,
                                onClick = { vm.generateAndSharePdf(context) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (canDelete) {
                            BottomActionBtn(
                                icon = Icons.Filled.Delete,
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete),
                                color = MaterialTheme.colorScheme.error,
                                onClick = {
                                    voidReason = ""
                                    refundPaymentsConfirmed = false
                                    showVoid = true
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp10),
            ) {
                PurchaseInvoiceHeader369(summary.invoice.invoiceNumber, onBack)
                PurchaseInvoiceOverview369(summary, supplier)
                PurchaseInvoiceTabs369(selectedTab) { selectedTab = it }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (selectedTab) {
                        PurchaseInvoiceTab369.ITEMS -> InvoiceItemsTab350(
                            lines = items,
                            isSale = false,
                        )
                        PurchaseInvoiceTab369.PAYMENTS -> PurchasePaymentsTab369(
                            summary = summary,
                            canAddPayment = canAddSupplierPayment,
                            canReverse = canReverse,
                            onAddPayment = { onAddPayment(summary.invoice.id, summary.invoice.clientId) },
                            onPayFull = { onPayFull(summary.invoice.id, summary.invoice.clientId) },
                            onReverse = { reverseTarget = it },
                        )
                    }
                }
            }
        }
    }

    reverseTarget?.let { payment ->
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            containerColor = BgCard,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_fa374e2cb37b), fontWeight = FontWeight.Bold) },
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_17f84f2d5d1a), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.reversePaymentEntry(payment); reverseTarget = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_30e483b4cc68), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { reverseTarget = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            },
        )
    }

    if (showVoid) {
        val hasPayments = summary.totalPaid > 0.01
        val canConfirmVoid = voidReason.trim().length >= 3 && (!hasPayments || refundPaymentsConfirmed)
        AlertDialog(
            onDismissRequest = { showVoid = false },
            containerColor = BgCard,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_008e2f198440), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp12)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_936c69955313), color = TextSecondary)
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
                            Checkbox(checked = refundPaymentsConfirmed, onCheckedChange = { refundPaymentsConfirmed = it })
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_3dfa681200f8), color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirmVoid,
                    onClick = {
                        showVoid = false
                        vm.deleteInvoiceById(
                            invoiceId = summary.invoice.id,
                            reason = voidReason,
                            refundPayments = refundPaymentsConfirmed,
                            onSuccess = onDeleteSuccess,
                        )
                    },
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_e9fc420c6c41), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoid = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), color = TextSecondary)
                }
            },
        )
    }

    paymentStructuredError?.let { error ->
        val resolved = error.resolveVertoError()
        PurchaseInvoiceErrorDialog369(resolved.message, vm::clearPaymentStructuredError)
    }
    paymentError?.let { message ->
        PurchaseInvoiceErrorDialog369(message, vm::clearPaymentError)
    }
    exportError?.let { message ->
        PurchaseInvoiceErrorDialog369(message, vm::clearExportError)
    }
    deleteError?.let { message ->
        PurchaseInvoiceErrorDialog369(message, vm::clearDeleteError)
    }
}

@Composable
private fun PurchaseInvoiceHeader369(invoiceNumber: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(InvoiceDimensions.dp56)
            .padding(horizontal = InvoiceDimensions.dp20),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8),
    ) {
        VertoIconButton(onClick = onBack, modifier = Modifier.size(InvoiceDimensions.dp48)) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back),
                tint = AccentPrimary,
            )
        }
        Text(
            androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_purchase_title),
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            fontSize = InvoiceTextScale.sp18,
            fontWeight = FontWeight.Bold,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_number, invoiceNumber),
                color = TextPrimary,
                fontSize = InvoiceTextScale.sp22,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun PurchaseInvoiceOverview369(summary: InvoiceSummary, supplier: ClientItem?) {
    val statusColor = when {
        summary.invoice.voided -> MaterialTheme.colorScheme.error
        summary.isPaid -> SuccessColor
        summary.isOverdue -> MaterialTheme.colorScheme.error
        else -> InfoColor
    }
    val statusText = when {
        summary.invoice.voided -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_d96db3cf6533)
        summary.isPaid -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_8318093e209a)
        summary.isOverdue -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_overdue_days, summary.financial.overdueDays)
        summary.totalPaid > 0.0 -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_partial)
        else -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_credit)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = InvoiceDimensions.dp20),
        color = BgCard,
        shape = RoundedCornerShape(InvoiceDimensions.dp16),
        border = BorderStroke(InvoiceDimensions.dp1, BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(InvoiceDimensions.dp14),
            verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp10),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        supplier?.name ?: androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_cash_purchase),
                        color = TextPrimary,
                        fontSize = InvoiceTextScale.sp16,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(DateUtils.formatDate(summary.invoice.createdAt), color = TextMuted, fontSize = InvoiceTextScale.sp11)
                }
                Surface(color = statusColor.copy(alpha = .12f), shape = RoundedCornerShape(InvoiceDimensions.dp8)) {
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = InvoiceTextScale.sp11,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = InvoiceDimensions.dp10, vertical = InvoiceDimensions.dp5),
                    )
                }
            }
            HorizontalDivider(color = BorderColor.copy(alpha = .45f))
            Row(Modifier.fillMaxWidth()) {
                PurchaseFinancialCell369(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_total), summary.invoice.totalAmount, AccentPrimary, Modifier.weight(1f), summary.invoice.transactionCurrencyCode)
                PurchaseFinancialCell369(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_paid), summary.totalPaid, SuccessColor, Modifier.weight(1f), summary.invoice.transactionCurrencyCode)
                PurchaseFinancialCell369(
                    androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_remaining),
                    summary.remaining,
                    if (summary.remaining > 0.0) MaterialTheme.colorScheme.error else SuccessColor,
                    Modifier.weight(1f),
                    summary.invoice.transactionCurrencyCode,
                )
            }
            if (summary.invoice.purchaseScope == PurchaseScope.INTERNATIONAL) {
                val transactionCurrency = summary.invoice.transactionCurrencyCode.ifBlank { "—" }
                val functionalCurrency = summary.invoice.functionalCurrencyCode.ifBlank { "—" }
                val functionalAmount = Money.ofMinor(
                    summary.invoice.functionalAmountAtRecognitionMinor,
                    summary.invoice.functionalCurrencyCode.ifBlank { Money.TRANSACTION_CURRENCY },
                ).toLegacyDouble()
                Text(
                    androidx.compose.ui.res.stringResource(
                        com.verto.feature.invoice.R.string.invoice_v370_international_money,
                        transactionCurrency,
                        summary.invoice.invoiceExchangeRateSnapshot.ifBlank { "—" },
                        functionalCurrency,
                    ),
                    color = TextSecondary,
                    fontSize = InvoiceTextScale.sp11,
                )
                Text(
                    androidx.compose.ui.res.stringResource(
                        com.verto.feature.invoice.R.string.invoice_v370_functional_value,
                        WhatsAppUtils.formatAmount(functionalAmount),
                        functionalCurrency,
                    ),
                    color = TextPrimary,
                    fontSize = InvoiceTextScale.sp12,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (summary.invoice.notes.isNotBlank()) {
                Text(summary.invoice.notes, color = TextSecondary, fontSize = InvoiceTextScale.sp11, maxLines = 2)
            }
        }
    }
}

@Composable
private fun PurchaseFinancialCell369(label: String, value: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier, currencyCode: String = "") {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildString {
                append(WhatsAppUtils.formatAmount(value))
                if (currencyCode.isNotBlank()) append(" ").append(currencyCode)
            },
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = InvoiceTextScale.sp15,
            maxLines = 1,
        )
        Text(label, color = TextMuted, fontSize = InvoiceTextScale.sp10)
    }
}

@Composable
private fun PurchaseInvoiceTabs369(selected: PurchaseInvoiceTab369, onSelected: (PurchaseInvoiceTab369) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier.fillMaxWidth(),
        containerColor = BgDeep,
        contentColor = AccentPrimary,
        divider = { HorizontalDivider(color = BorderColor.copy(alpha = .5f)) },
    ) {
        PurchaseInvoiceTab369.values().forEach { tab ->
            val label = when (tab) {
                PurchaseInvoiceTab369.ITEMS -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_items)
                PurchaseInvoiceTab369.PAYMENTS -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_payments)
            }
            Tab(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                text = { Text(label, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Medium) },
            )
        }
    }
}

@Composable
private fun PurchasePaymentsTab369(
    summary: InvoiceSummary,
    canAddPayment: Boolean,
    canReverse: Boolean,
    onAddPayment: () -> Unit,
    onPayFull: () -> Unit,
    onReverse: (PaymentItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = InvoiceDimensions.dp20, vertical = InvoiceDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8),
    ) {
        if (canAddPayment && !summary.isPaid && !summary.invoice.voided) {
            item(key = "payment-actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)) {
                    VertoOutlinedButton(onClick = onAddPayment, modifier = Modifier.weight(1f)) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_partial_payment))
                    }
                    VertoButton(onClick = onPayFull, modifier = Modifier.weight(1f)) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_full_payment))
                    }
                }
            }
        }
        if (summary.dueInstallments.isNotEmpty()) {
            item(key = "due-title") {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v370_due_schedule),
                    color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InvoiceTextScale.sp14,
                )
            }
            items(summary.dueInstallments.sortedBy { it.sequence }, key = { "due-${it.sequence}" }) { installment ->
                Surface(
                    modifier = Modifier.fillMaxWidth(), color = BgCard,
                    shape = RoundedCornerShape(InvoiceDimensions.dp12),
                    border = BorderStroke(InvoiceDimensions.dp1, BorderColor),
                ) {
                    Row(Modifier.padding(InvoiceDimensions.dp12), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v370_due_number, installment.sequence),
                            modifier = Modifier.weight(1f), color = TextPrimary, fontWeight = FontWeight.SemiBold,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${WhatsAppUtils.formatAmount(installment.amount)} ${installment.currencyCode}", color = AccentPrimary, fontWeight = FontWeight.Bold)
                            Text(DateUtils.formatDate(installment.dueDate), color = TextMuted, fontSize = InvoiceTextScale.sp11)
                        }
                    }
                }
            }
        }
        if (summary.payments.isEmpty()) {
            item(key = "no-payments") {
                VertoEmptyState(
                    message = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_no_payments),
                    iconText = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_payment_icon),
                    variant = VertoEmptyStateVariant.Plain,
                )
            }
        } else {
            items(summary.payments.sortedByDescending { it.paidAt }, key = { it.id }) { payment ->
                val isReversal = payment.reversedPaymentId != null || payment.amount < 0.0
                PurchasePaymentRow369(
                    payment = payment,
                    isReversal = isReversal,
                    canReverse = canReverse && !isReversal,
                    onReverse = { onReverse(payment) },
                )
            }
        }
    }
}

@Composable
private fun PurchasePaymentRow369(
    payment: PaymentItem,
    isReversal: Boolean,
    canReverse: Boolean,
    onReverse: () -> Unit,
) {
    val accent = if (isReversal) MaterialTheme.colorScheme.error else SuccessColor
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgCard,
        shape = RoundedCornerShape(InvoiceDimensions.dp14),
        border = BorderStroke(InvoiceDimensions.dp1, BorderColor),
    ) {
        Row(
            modifier = Modifier.padding(InvoiceDimensions.dp14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(InvoiceDimensions.dp42),
                color = if (isReversal) MaterialTheme.colorScheme.errorContainer else com.verto.app.ui.theme.SuccessContainer,
                shape = RoundedCornerShape(InvoiceDimensions.dp12),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        if (isReversal) Icons.Filled.Undo else Icons.Filled.Payments,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(InvoiceDimensions.dp22),
                    )
                }
            }
            Spacer(Modifier.width(InvoiceDimensions.dp12))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isReversal) {
                        androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_1709f4eeff0b)
                    } else {
                        when (payment.paymentMethod) {
                            com.verto.app.feature.invoice.application.InvoicePaymentMethod.CASH -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_7bdd72bb022e)
                            com.verto.app.feature.invoice.application.InvoicePaymentMethod.TRANSFER -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_5c2fb4140284)
                            com.verto.app.feature.invoice.application.InvoicePaymentMethod.CHECK -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_a0bddb1a997d)
                        }
                    },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = InvoiceTextScale.sp14,
                )
                Text(DateUtils.formatDateTime(payment.paidAt), color = TextMuted, fontSize = InvoiceTextScale.sp11)
                if (!isReversal && payment.note.isNotBlank()) {
                    Text(payment.note, color = TextSecondary, fontSize = InvoiceTextScale.sp11)
                }
            }
            Text(
                WhatsAppUtils.formatAmount(payment.amount),
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = InvoiceTextScale.sp14,
                textAlign = TextAlign.End,
            )
            if (canReverse) {
                VertoIconButton(onClick = onReverse, modifier = Modifier.size(InvoiceDimensions.dp48)) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Undo,
                        androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_30e483b4cc68),
                        tint = TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseInvoiceErrorDialog369(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v369_error_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentPrimary)
            }
        },
    )
}
