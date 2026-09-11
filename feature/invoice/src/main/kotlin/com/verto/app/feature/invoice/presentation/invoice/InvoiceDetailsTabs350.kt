package com.verto.app.feature.invoice.presentation.invoice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.feature.invoice.application.*
import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.feature.invoice.presentation.InvoiceTextScale
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoEmptyStateVariant
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils

internal enum class InvoiceDetailsTab350(val label: String) {
    ITEMS("البنود"),
    PAYMENTS("الدفعات"),
    COMMUNICATION("التواصل"),
}

@Composable
internal fun InvoiceOverview350(
    summary: InvoiceSummary,
    client: ClientItem?,
    commissionBeneficiary: ClientItem? = null,
    modifier: Modifier = Modifier,
) {
    val statusColor = when {
        summary.invoice.voided -> MaterialTheme.colorScheme.error
        summary.isPaid -> SuccessColor
        summary.isOverdue -> MaterialTheme.colorScheme.error
        else -> InfoColor
    }
    val statusText = when {
        summary.invoice.voided -> "ملغاة"
        summary.isPaid -> "مسددة"
        summary.isOverdue -> "متأخرة ${summary.financial.overdueDays} يوم"
        summary.totalPaid > 0.0 -> "جزئية"
        else -> "آجلة"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = InvoiceDimensions.dp20),
        color = BgCard,
        shape = RoundedCornerShape(InvoiceDimensions.dp16),
        border = androidx.compose.foundation.BorderStroke(InvoiceDimensions.dp1, BorderColor),
    ) {
        Column(
            Modifier.padding(InvoiceDimensions.dp14),
            verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp10),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        client?.name ?: "بيع نقدي",
                        color = TextPrimary,
                        fontSize = InvoiceTextScale.sp16,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        DateUtils.formatDate(summary.invoice.createdAt),
                        color = TextMuted,
                        fontSize = InvoiceTextScale.sp11,
                    )
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
                FinancialCell350("الإجمالي", summary.invoice.totalAmount, AccentPrimary, Modifier.weight(1f))
                FinancialCell350("المسدد", summary.totalPaid, SuccessColor, Modifier.weight(1f))
                FinancialCell350(
                    "المتبقي",
                    summary.remaining,
                    if (summary.remaining > 0.0) MaterialTheme.colorScheme.error else SuccessColor,
                    Modifier.weight(1f),
                )
            }

            if (summary.invoice.discount > 0.0) {
                Text(
                    "الخصم: ${WhatsAppUtils.formatAmount(summary.invoice.discount)}",
                    color = TextSecondary,
                    fontSize = InvoiceTextScale.sp11,
                )
            }
            if (summary.invoice.commission > 0.0 || summary.invoice.commissionBeneficiaryClientId != null) {
                val beneficiaryLabel = commissionBeneficiary?.name?.let { " — المستفيد: $it" }.orEmpty()
                Text(
                    "العمولة: ${WhatsAppUtils.formatAmount(summary.invoice.commission)}$beneficiaryLabel",
                    color = GoldPrimary,
                    fontSize = InvoiceTextScale.sp11,
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
private fun FinancialCell350(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            WhatsAppUtils.formatAmount(value),
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = InvoiceTextScale.sp15,
            maxLines = 1,
        )
        Text(label, color = TextMuted, fontSize = InvoiceTextScale.sp10)
    }
}

@Composable
internal fun InvoiceTabs350(
    selected: InvoiceDetailsTab350,
    onSelected: (InvoiceDetailsTab350) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.fillMaxWidth(),
        containerColor = BgDeep,
        contentColor = AccentPrimary,
        divider = { HorizontalDivider(color = BorderColor.copy(alpha = .5f)) },
    ) {
        InvoiceDetailsTab350.values().forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                text = {
                    Text(
                        tab.label,
                        fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Medium,
                        fontSize = InvoiceTextScale.sp13,
                    )
                },
            )
        }
    }
}

@Composable
internal fun InvoiceItemsTab350(
    lines: List<InvoiceLineView>,
    isSale: Boolean,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VertoEmptyState(message = "لا توجد بنود", iconText = "🧾", variant = VertoEmptyStateVariant.Plain)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = InvoiceDimensions.dp20, vertical = InvoiceDimensions.dp12),
        verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8),
    ) {
        items(lines, key = { it.id }) { line ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BgCard,
                shape = RoundedCornerShape(InvoiceDimensions.dp12),
                border = androidx.compose.foundation.BorderStroke(InvoiceDimensions.dp1, BorderColor),
            ) {
                Row(
                    Modifier.padding(horizontal = InvoiceDimensions.dp14, vertical = InvoiceDimensions.dp12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.itemName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = InvoiceTextScale.sp13, maxLines = 2)
                        Text(
                            "${line.quantity} × ${WhatsAppUtils.formatAmount(if (isSale) line.sellPrice else line.buyPrice)}",
                            color = TextMuted,
                            fontSize = InvoiceTextScale.sp11,
                        )
                    }
                    Text(
                        WhatsAppUtils.formatAmount(line.totalPrice),
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = InvoiceTextScale.sp13,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InvoicePaymentsTab350(
    summary: InvoiceSummary,
    client: ClientItem?,
    canReverse: Boolean,
    onAddPayment: () -> Unit,
    onPayFull: () -> Unit,
    onReverse: (PaymentItem) -> Unit,
    onThankYou: (PaymentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (!summary.isPaid && !summary.invoice.voided) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = InvoiceDimensions.dp20, vertical = InvoiceDimensions.dp10),
                horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8),
            ) {
                OutlinedButton(onClick = onAddPayment, modifier = Modifier.weight(1f)) {
                    Text("دفعة جزئية")
                }
                Button(onClick = onPayFull, modifier = Modifier.weight(1f)) {
                    Text("سداد كامل")
                }
            }
        }

        if (summary.payments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VertoEmptyState(message = "لا توجد دفعات مسجلة", iconText = "💳", variant = VertoEmptyStateVariant.Plain)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = InvoiceDimensions.dp20, vertical = InvoiceDimensions.dp8),
                verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8),
            ) {
                items(summary.payments.sortedByDescending { it.paidAt }, key = { it.id }) { payment ->
                    val isReversal = payment.reversedPaymentId != null || payment.amount < 0.0
                    PaymentItem(
                        payment = payment,
                        hasPhone = client?.phone?.isNotBlank() == true,
                        isReversal = isReversal,
                        canReverse = canReverse && !isReversal,
                        onReverse = { onReverse(payment) },
                        onSendThankYou = { onThankYou(payment) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun InvoiceCommunicationTab350(
    summary: InvoiceSummary,
    client: ClientItem?,
    history: List<InvoiceCommunicationEvent>,
    canCommunicate: Boolean,
    canThank: Boolean,
    onReminder: () -> Unit,
    onThankYou: () -> Unit,
    onShareInvoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = InvoiceDimensions.dp20, vertical = InvoiceDimensions.dp12),
        verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp10),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BgCard,
                shape = RoundedCornerShape(InvoiceDimensions.dp14),
                border = androidx.compose.foundation.BorderStroke(InvoiceDimensions.dp1, BorderColor),
            ) {
                Column(Modifier.padding(InvoiceDimensions.dp14), verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)) {
                    Text("التواصل مع العميل", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InvoiceTextScale.sp14)
                    Text(client?.phone?.takeIf { it.isNotBlank() } ?: "لا يوجد رقم هاتف", color = TextMuted, fontSize = InvoiceTextScale.sp11)
                    if (canCommunicate) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)) {
                            if (summary.remaining > 0.0) {
                                Button(onClick = onReminder, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Notifications, null, modifier = Modifier.size(InvoiceDimensions.dp20))
                                    Spacer(Modifier.width(InvoiceDimensions.dp6))
                                    Text("إرسال تذكير")
                                }
                            }
                            OutlinedButton(onClick = onShareInvoice, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Send, null, modifier = Modifier.size(InvoiceDimensions.dp20))
                                Spacer(Modifier.width(InvoiceDimensions.dp6))
                                Text("إرسال الفاتورة")
                            }
                        }
                        if (canThank) {
                            OutlinedButton(onClick = onThankYou, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Send, null, modifier = Modifier.size(InvoiceDimensions.dp20))
                                Spacer(Modifier.width(InvoiceDimensions.dp6))
                                Text("رسالة شكر لآخر دفعة")
                            }
                        }
                    }
                    Text(
                        "فتح واتساب يُسجل كفتح رسالة فقط، وليس كإثبات إرسال.",
                        color = TextMuted,
                        fontSize = InvoiceTextScale.sp10,
                    )
                }
            }
        }

        item {
            Text("سجل التواصل", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = InvoiceTextScale.sp14)
        }

        if (history.isEmpty()) {
            item {
                Text("لا يوجد سجل تواصل لهذه الفاتورة.", color = TextMuted, fontSize = InvoiceTextScale.sp12)
            }
        } else {
            items(history, key = { it.id }) { event ->
                val label = when (event.kind) {
                    InvoiceCommunicationKind.INVOICE_OPENED -> "فُتحت الفاتورة في واتساب"
                    InvoiceCommunicationKind.REMINDER_OPENED -> "فُتح تذكير في واتساب"
                    InvoiceCommunicationKind.THANK_YOU_OPENED -> "فُتحت رسالة شكر في واتساب"
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BgCard,
                    shape = RoundedCornerShape(InvoiceDimensions.dp12),
                    border = androidx.compose.foundation.BorderStroke(InvoiceDimensions.dp1, BorderColor),
                ) {
                    Row(Modifier.padding(InvoiceDimensions.dp12), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (event.kind == InvoiceCommunicationKind.REMINDER_OPENED) Icons.Filled.Notifications else Icons.Filled.Send,
                            null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(InvoiceDimensions.dp20),
                        )
                        Spacer(Modifier.width(InvoiceDimensions.dp10))
                        Column(Modifier.weight(1f)) {
                            Text(label, color = TextPrimary, fontSize = InvoiceTextScale.sp12, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    DateUtils.formatDateTime(event.createdAt),
                                    event.employeeName.takeIf { it.isNotBlank() },
                                ).joinToString(" • "),
                                color = TextMuted,
                                fontSize = InvoiceTextScale.sp10,
                            )
                        }
                    }
                }
            }
        }
    }
}
