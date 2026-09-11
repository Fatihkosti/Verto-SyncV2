package com.verto.app.ui.screens.expenses

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import com.verto.app.ui.components.VertoIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddExpenseDialog(
    vm: ExpensesViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, String, String?) -> Unit
) {
    var item by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedInvoiceId by remember { mutableStateOf<String?>(null) }
    val purchaseInvoices by vm.recentPurchaseInvoices.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(ExpensesDimensions.dp20)).background(BgCard).padding(ExpensesDimensions.dp20),
            verticalArrangement = Arrangement.spacedBy(ExpensesDimensions.dp12)
        ) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_45fb4b937312), color = TextPrimary, fontSize = ExpensesTextScale.sp16, fontWeight = FontWeight.Bold)

            VertoTextField(
                value = item,
                onValueChange = { item = it },
                label = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_0380431d559d),
                modifier = Modifier.fillMaxWidth(),
            )

            VertoTextField(
                value = amount,
                onValueChange = { amount = it },
                label = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_f58002349bfd),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )

            var expanded by remember { mutableStateOf(false) }
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val filteredInvoices = if (searchQuery.isEmpty() || searchQuery.startsWith("فاتورة #")) {
                purchaseInvoices
            } else {
                purchaseInvoices.filter { it.invoiceNumber.toString().contains(searchQuery) }
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                VertoOutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        expanded = true
                        selectedInvoiceId = null
                    },
                    readOnly = false,
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_8823f3af2012)) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_852612b65a24)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            VertoIconButton(onClick = {
                                searchQuery = ""
                                selectedInvoiceId = null
                                expanded = false
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_clear))
                            }
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = dialogFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (filteredInvoices.isNotEmpty() && expanded) {
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = BgCard
                    ) {
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_c21a0b0d354d), color = TextPrimary) },
                            onClick = {
                                selectedInvoiceId = null
                                searchQuery = ""
                                expanded = false
                            }
                        )
                        filteredInvoices.forEach { invoice ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_17f76fe31e9c, invoice.invoiceNumber) +
                                            androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_1f1eda8fe97b, WhatsAppUtils.formatAmount(invoice.totalAmount)),
                                        color = TextPrimary
                                    )
                                },
                                onClick = {
                                    selectedInvoiceId = invoice.id
                                    searchQuery = "فاتورة #${invoice.invoiceNumber}"
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            VertoTextField(
                value = note,
                onValueChange = { note = it },
                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_note),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(ExpensesDimensions.dp8)) {
                VertoOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(ExpensesDimensions.dp1, BorderColor)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                }
                VertoButton(
                    onClick = {
                        amount.toDoubleOrNull()?.let { parsedAmount ->
                            if (item.isNotBlank()) {
                                onConfirm("مصروف عام", item, parsedAmount, note, selectedInvoiceId)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save))
                }
            }
        }
    }
}

@Composable
internal fun AdjustCashDialog(
    isAdd: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val color = if (isAdd) SuccessColor else MaterialTheme.colorScheme.error

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(ExpensesDimensions.dp20)).background(BgCard).padding(ExpensesDimensions.dp20),
            verticalArrangement = Arrangement.spacedBy(ExpensesDimensions.dp12)
        ) {
            Text(
                if (isAdd) androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_2dfd1e87b2d8_2) else androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_2dfd1e87b2d8),
                color = color,
                fontSize = ExpensesTextScale.sp16,
                fontWeight = FontWeight.Bold
            )
            VertoTextField(
                value = amount,
                onValueChange = { amount = it },
                label = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_f58002349bfd),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )
            VertoTextField(
                value = note,
                onValueChange = { note = it },
                label = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_d63732cb3135),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ExpensesDimensions.dp8)) {
                VertoOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(ExpensesDimensions.dp1, BorderColor)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                }
                VertoButton(
                    onClick = {
                        amount.toDoubleOrNull()?.let { parsedAmount ->
                            onConfirm(parsedAmount, note)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Text(if (isAdd) androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add) else androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_c78b4cc71d0d))
                }
            }
        }
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentPrimary,
    unfocusedBorderColor = BorderColor,
    focusedContainerColor = BgDeep,
    unfocusedContainerColor = BgDeep,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = AccentPrimary,
    unfocusedLabelColor = TextMuted
)
