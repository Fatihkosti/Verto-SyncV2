package com.verto.app.feature.invoice.presentation.invoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.verto.app.feature.invoice.application.InvoiceLineView
import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary

@Composable
internal fun InvoiceReturnDialog351(
    lines: List<InvoiceLineView>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>, String, Boolean) -> Unit,
) {
    val quantities = remember { mutableStateMapOf<String, String>() }
    var reason by remember { mutableStateOf("") }
    var cashRefund by remember { mutableStateOf(false) }
    val parsed = lines.associate { line -> line.id to (quantities[line.id]?.toIntOrNull() ?: 0) }
    val quantityValid = lines.all { line -> (parsed[line.id] ?: 0) in 0..line.quantity }
    val canConfirm = quantityValid && parsed.values.any { it > 0 } && reason.trim().length >= 3
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("مرتجع من الفاتورة", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)) {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp6)) {
                    items(lines, key = { it.id }) { line ->
                        OutlinedTextField(
                            value = quantities[line.id].orEmpty(),
                            onValueChange = { quantities[line.id] = it },
                            label = { Text("${line.itemName} — حتى ${line.quantity}") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("سبب المرتجع") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.Row {
                    Checkbox(checked = cashRefund, onCheckedChange = { cashRefund = it })
                    Text("استرداد نقدي", color = TextSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (canConfirm) onConfirm(parsed, reason.trim(), cashRefund) }, enabled = canConfirm) { Text("تسجيل المرتجع", color = AccentPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) } },
    )
}
