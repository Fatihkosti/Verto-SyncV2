package com.verto.app.feature.invoice.presentation.invoice

import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.feature.invoice.presentation.InvoiceTextScale

import com.verto.app.feature.invoice.application.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verto.app.feature.invoice.application.InvoiceSummary
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import com.verto.app.ui.components.VertoIconButton

// ─────────────────────────────────────────────────────
// زر الشريط السفلي
// ─────────────────────────────────────────────────────
@Composable
internal fun BottomActionBtn(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(InvoiceDimensions.dp54)
            .clip(RoundedCornerShape(InvoiceDimensions.dp14))
            .background(color.copy(alpha = .05f))
            .clickable(onClick = onClick)
            .padding(horizontal = InvoiceDimensions.dp2, vertical = InvoiceDimensions.dp5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(InvoiceDimensions.dp20))
        Spacer(Modifier.height(InvoiceDimensions.dp2))
        Text(label, color = color, fontSize = InvoiceTextScale.sp10, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────
// كارت الدفعة
// ─────────────────────────────────────────────────────
@Composable
internal fun PaymentItem(
    payment: PaymentItem,
    hasPhone: Boolean,
    isReversal: Boolean,
    canReverse: Boolean,
    onReverse: () -> Unit,
    onSendThankYou: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isReversal) MaterialTheme.colorScheme.error else SuccessColor
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(InvoiceDimensions.dp14))
            .background(BgCard).border(InvoiceDimensions.dp1, BorderColor, RoundedCornerShape(InvoiceDimensions.dp14)).padding(InvoiceDimensions.dp14)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(InvoiceDimensions.dp42).clip(RoundedCornerShape(InvoiceDimensions.dp12))
                    .background(if (isReversal) MaterialTheme.colorScheme.errorContainer else SuccessContainer),
                contentAlignment = Alignment.Center) {
                Icon(
                    if (isReversal) Icons.Filled.Undo else Icons.Filled.Payments,
                    null, tint = accent, modifier = Modifier.size(InvoiceDimensions.dp22)
                )
            }
            Spacer(Modifier.width(InvoiceDimensions.dp12))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isReversal) androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_1709f4eeff0b) else when (payment.paymentMethod) {
                        InvoicePaymentMethod.CASH -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_7bdd72bb022e)
                        InvoicePaymentMethod.TRANSFER -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_5c2fb4140284)
                        InvoicePaymentMethod.CHECK -> androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_a0bddb1a997d)
                    },
                    color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = InvoiceTextScale.sp14
                )
                Text(DateUtils.formatDateTime(payment.paidAt), color = TextMuted, fontSize = InvoiceTextScale.sp11)
                if (!isReversal && payment.paymentExchangeRate.isNotBlank() && payment.paymentCurrencyCode.isNotBlank()) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_f1159892a5cb, payment.paymentCurrencyCode, payment.paymentExchangeRate), color = TextMuted, fontSize = InvoiceTextScale.sp10)
                }
                if (!isReversal && payment.note.isNotBlank())
                    Text(payment.note, color = TextSecondary, fontSize = InvoiceTextScale.sp12)
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = payment.amount,
                    currencyLabel = androidx.compose.ui.res.stringResource(
                        com.verto.feature.invoice.R.string.legacy_ui_6bc5400734c2
                    ),
                    color = accent,
                )
                Spacer(Modifier.height(InvoiceDimensions.dp4))
                if (hasPhone && !isReversal) {
                    Surface(
                        shape    = RoundedCornerShape(InvoiceDimensions.dp6),
                        color    = AccentDim,
                        modifier = Modifier.clickable(onClick = onSendThankYou)
                    ) {
                        Row(Modifier.padding(horizontal = InvoiceDimensions.dp8, vertical = InvoiceDimensions.dp4),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp4)) {
                            Icon(Icons.Filled.Send, null, tint = AccentLight, modifier = Modifier.size(InvoiceDimensions.dp10))
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_cd565f0ee052), color = AccentLight, fontSize = InvoiceTextScale.sp10, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (canReverse) {
                Spacer(Modifier.width(InvoiceDimensions.dp4))
                VertoIconButton(onClick = onReverse, modifier = Modifier.size(InvoiceDimensions.dp48)) {
                    Icon(Icons.Filled.Undo, androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_30e483b4cc68), tint = TextMuted, modifier = Modifier.size(InvoiceDimensions.dp16))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// حوار إدخال العمولة
// ─────────────────────────────────────────────────────
@Composable
internal fun CommissionDialog(
    currentCommission: Double,
    invoiceTotal: Double,
    profit: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text  by remember { mutableStateOf(if (currentCommission > 0.0) currentCommission.toBigDecimal().toPlainString() else "") }
    var error by remember { mutableStateOf(false) }

    val commission     = text.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val adjProfit      = profit - commission
    val margin         = if (invoiceTotal > 0) profit / invoiceTotal * 100 else 0.0
    val adjMargin      = if (invoiceTotal > 0) adjProfit / invoiceTotal * 100 else 0.0

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(InvoiceDimensions.dp20))
                .background(BgCard)
                .border(InvoiceDimensions.dp1, GoldPrimary.copy(0.3f), RoundedCornerShape(InvoiceDimensions.dp20))
                .padding(InvoiceDimensions.dp20)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp14)) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_4652e511d2d9), color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = InvoiceTextScale.sp16)

                // معلومات الفاتورة
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(InvoiceDimensions.dp12))
                        .background(BgSurface).padding(InvoiceDimensions.dp12)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp6)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_c89ae688bf37), color = TextMuted, fontSize = InvoiceTextScale.sp12)
                            Text(WhatsAppUtils.formatAmount(invoiceTotal) + androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_c16704a4001c), color = TextPrimary, fontSize = InvoiceTextScale.sp12, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_f3fe66977ef0), color = TextMuted, fontSize = InvoiceTextScale.sp12)
                            Text(WhatsAppUtils.formatAmount(profit) + androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_696ac4a9186c), color = SuccessColor, fontSize = InvoiceTextScale.sp12, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_09c3eaebb559), color = TextMuted, fontSize = InvoiceTextScale.sp12)
                            Text(String.format(java.util.Locale.US, androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_d077a0ef40eb), margin), color = SuccessColor, fontSize = InvoiceTextScale.sp12, fontWeight = FontWeight.SemiBold)
                        }
                        if (commission > 0) {
                            HorizontalDivider(color = BorderColor.copy(0.5f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_9dbbc65d695e), color = TextMuted, fontSize = InvoiceTextScale.sp12)
                                Text(WhatsAppUtils.formatAmount(adjProfit) + androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_44a73d65c972),
                                    color = if (adjProfit >= 0) AccentPrimary else MaterialTheme.colorScheme.error,
                                    fontSize = InvoiceTextScale.sp12, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_20da09c47003), color = TextMuted, fontSize = InvoiceTextScale.sp12)
                                Text(String.format(java.util.Locale.US, androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_049f6726b3de), adjMargin),
                                    color = if (adjMargin >= 0) AccentPrimary else MaterialTheme.colorScheme.error,
                                    fontSize = InvoiceTextScale.sp12, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                VertoOutlinedTextField(
                    value         = text,
                    onValueChange = { text = it; error = false },
                    label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_cc6b2386a9b3), color = TextMuted) },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_22cf82b68b95), color = TextMuted) },
                    isError       = error,
                    singleLine    = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = GoldPrimary,
                        unfocusedBorderColor    = BorderColor,
                        focusedContainerColor   = BgSurface,
                        unfocusedContainerColor = BgSurface,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary
                    ),
                    shape = RoundedCornerShape(InvoiceDimensions.dp12)
                )
                if (error) Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_9973dc47c1c0), color = MaterialTheme.colorScheme.error, fontSize = InvoiceTextScale.sp12)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)) {
                    VertoOutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(InvoiceDimensions.dp12)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary) }
                    VertoButton(
                        onClick = {
                            val amount = text.trim().toDoubleOrNull()
                            if (amount == null || amount < 0) {
                                error = true
                            } else {
                                onSave(amount)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(InvoiceDimensions.dp12),
                        colors   = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save), color = TextPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
