package com.verto.app.feature.payment.presentation.invoiceeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.money.Money
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.WhatsAppUtils

@Composable
internal fun SaleDiscountDialog351(
    grossTotal: Money,
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(current) { mutableStateOf(current) }
    val parsed = if (value.isBlank()) Money.zero() else Money.parseOrNull(value)
    val valid = parsed != null && !parsed.isNegative() && parsed < grossTotal
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("خصم الفاتورة", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                Text("إجمالي البنود: ${WhatsAppUtils.formatAmount(grossTotal.toLegacyDouble())}", color = TextSecondary)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("قيمة الخصم") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (!valid) Text("الخصم يجب أن يكون من صفر وأقل من قيمة الفاتورة", color = ErrorColor, fontSize = PaymentTextScale.sp11)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(parsed!!.toPlainString()) }, enabled = valid) {
                Text("اعتماد", color = AccentPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) } },
    )
}

@Composable
internal fun SaleReferralChip351(
    referrerName: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(PaymentDimensions.dp12),
        color = BgCard,
        border = BorderStroke(PaymentDimensions.dp1, BorderColor),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaymentDimensions.dp10, vertical = PaymentDimensions.dp8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (referrerName.isNullOrBlank()) "+ إضافة محيل" else "المحيل: $referrerName",
                color = if (referrerName.isNullOrBlank()) AccentPrimary else TextPrimary,
                fontSize = PaymentTextScale.sp12,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun SaleReferrerPicker351(
    eligible: List<ClientItem>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("المحيل", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("مسوق أو ورشة أحالت هذا العميل", color = TextMuted, fontSize = PaymentTextScale.sp11)
                eligible.take(12).forEach { client ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(client.id) }.padding(vertical = PaymentDimensions.dp10),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(client.name, color = TextPrimary)
                        if (client.id == selectedId) Text("محدد", color = AccentPrimary)
                    }
                }
                if (selectedId.isNotBlank()) {
                    TextButton(onClick = { onSelect("") }) { Text("إزالة المحيل", color = ErrorColor) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق", color = AccentPrimary) } },
    )
}
