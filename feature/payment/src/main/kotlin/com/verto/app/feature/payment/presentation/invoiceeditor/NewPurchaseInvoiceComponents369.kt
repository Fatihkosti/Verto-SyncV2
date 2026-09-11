package com.verto.app.feature.payment.presentation.invoiceeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.feature.payment.application.model.InvoiceItemData
import com.verto.app.feature.payment.application.model.InventoryItemView
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import com.verto.app.money.Money
import com.verto.app.money.Quantity
import com.verto.app.ui.components.VertoButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.WhatsAppUtils
import java.util.Date

@Composable
internal fun PurchaseLineComposer369(
    form: InvoiceEditorFormState,
    inventoryItems: List<InventoryItemView>,
    editing: Boolean,
    searchFocusTrigger: Int,
    onCommit: () -> Boolean,
    onInventorySelected: (InventoryItemView) -> Unit,
    onAutoAddForPurchase: (String, Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }

    LaunchedEffect(editing, searchFocusTrigger) {
        if (editing) quantityFocus.requestFocus() else nameFocus.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BgCard,
        shape = RoundedCornerShape(PaymentDimensions.dp16),
        border = BorderStroke(PaymentDimensions.dp1, BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(PaymentDimensions.dp10),
            verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
        ) {
            CompactGoodsNameField(
                item = InvoiceItemData(
                    name = form.draftItemName,
                    quantity = form.draftItemQuantity,
                    sellPrice = form.draftItemSellPrice,
                    buyPrice = form.draftItemBuyPrice,
                    inventoryItemId = form.draftInventoryItemId,
                ),
                inventoryItems = inventoryItems,
                isSale = false,
                onUpdate = {
                    form.draftItemName = it.name
                    form.draftItemQuantity = it.quantity
                    form.draftItemSellPrice = it.sellPrice
                    form.draftItemBuyPrice = it.buyPrice
                    form.draftInventoryItemId = it.inventoryItemId
                    form.itemError = ""
                },
                onQuickAddItem = { _, _, _, _ -> },
                onAutoAddForPurchase = onAutoAddForPurchase,
                onInventorySelected = onInventorySelected,
                focusRequester = nameFocus,
                onNext = { quantityFocus.requestFocus() },
                imeAction = ImeAction.Done,
                onDone = { onCommit() },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactTextField(
                    value = form.draftItemQuantity,
                    onValueChange = { form.draftItemQuantity = it; form.itemError = "" },
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a95134401afe),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onDone = { onCommit() },
                    focusRequester = quantityFocus,
                    modifier = Modifier.weight(.8f),
                )
                CompactTextField(
                    value = form.draftItemSellPrice,
                    onValueChange = { form.draftItemSellPrice = it; form.itemError = "" },
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_sale_price),
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    onDone = { onCommit() },
                    modifier = Modifier.weight(1.15f),
                )
                CompactTextField(
                    value = form.draftItemBuyPrice,
                    onValueChange = { form.draftItemBuyPrice = it; form.itemError = "" },
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_purchase_price),
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    onDone = { onCommit() },
                    modifier = Modifier.weight(1.15f),
                )
                if (editing) {
                    VertoButton(
                        onClick = { onCommit() },
                        modifier = Modifier.height(PaymentDimensions.dp42),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_update_line),
                            fontSize = PaymentTextScale.sp11,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PurchaseInvoiceLines369(
    items: List<InvoiceItemData>,
    onEdit: (Int, InvoiceItemData) -> Unit,
    onDelete: (Int, InvoiceItemData) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_empty_lines),
                color = TextMuted,
                fontSize = PaymentTextScale.sp13,
            )
        }
        return
    }

    // This is intentionally the only scrolling surface in the purchase editor.
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PaymentDimensions.dp12, vertical = PaymentDimensions.dp8),
        verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val quantity = Quantity.parseOrNull(item.quantity)
            val purchasePrice = Money.parseOrNull(item.buyPrice)
            val total = if (quantity != null && purchasePrice != null) purchasePrice * quantity else Money.zero()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BgCard,
                shape = RoundedCornerShape(PaymentDimensions.dp12),
                border = BorderStroke(PaymentDimensions.dp1, BorderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PaymentDimensions.dp10, vertical = PaymentDimensions.dp8),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            color = TextPrimary,
                            fontSize = PaymentTextScale.sp13,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(
                                com.verto.feature.payment.R.string.payment_v369_line_price,
                                item.quantity,
                                WhatsAppUtils.formatAmount(purchasePrice?.toLegacyDouble() ?: 0.0),
                            ),
                            color = TextMuted,
                            fontSize = PaymentTextScale.sp11,
                        )
                    }
                    Text(
                        WhatsAppUtils.formatAmount(total.toLegacyDouble()),
                        color = AccentPrimary,
                        fontSize = PaymentTextScale.sp13,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                    IconButton(onClick = { onEdit(index, item) }, modifier = Modifier.size(PaymentDimensions.dp36)) {
                        Icon(
                            Icons.Filled.Edit,
                            androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_edit_line),
                            tint = AccentPrimary,
                            modifier = Modifier.size(PaymentDimensions.dp17),
                        )
                    }
                    IconButton(onClick = { onDelete(index, item) }, modifier = Modifier.size(PaymentDimensions.dp36)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_delete_line),
                            tint = ErrorColor,
                            modifier = Modifier.size(PaymentDimensions.dp17),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PurchaseInvoiceBottomBar369(
    itemCount: Int,
    total: Money,
    isSaving: Boolean,
    error: String,
    onSave: () -> Unit,
) {
    Surface(color = BgCard, border = BorderStroke(PaymentDimensions.dp1, BorderColor)) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(PaymentDimensions.dp10),
            verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp4),
        ) {
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = PaymentTextScale.sp11)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp10),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_items_count, itemCount),
                        color = TextMuted,
                        fontSize = PaymentTextScale.sp11,
                    )
                    Text(
                        WhatsAppUtils.formatAmount(total.toLegacyDouble()),
                        color = TextPrimary,
                        fontSize = PaymentTextScale.sp18,
                        fontWeight = FontWeight.Bold,
                    )
                }
                VertoButton(
                    onClick = onSave,
                    enabled = !isSaving && itemCount > 0,
                    modifier = Modifier.weight(.9f).height(PaymentDimensions.dp44),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_save_invoice),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PurchaseSupplierPickerDialog369(
    selectedSupplierId: String,
    suppliers: List<ClientItem>,
    globalSupplierOnly: Boolean = false,
    onDismiss: () -> Unit,
    onWithoutSupplier: () -> Unit,
    onSelect: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_choose_supplier),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                DirectClientSearch(
                    selectedClientId = selectedSupplierId,
                    isSale = false,
                    isCashMode = true,
                    allClients = suppliers,
                    globalSupplierOnly = globalSupplierOnly,
                    searchQuery = query,
                    onSearchChange = { query = it },
                    onSelectClient = onSelect,
                    onQuickAdd = onQuickAdd,
                    showFieldLabel = false,
                )
                TextButton(onClick = onWithoutSupplier, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_remove_supplier),
                        color = TextSecondary,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel),
                    color = TextMuted,
                )
            }
        },
    )
}

@Composable
internal fun PurchaseSettlementDialog369(
    total: Money,
    paidText: String,
    paidEditable: Boolean = true,
    installmentAmountText: String = "",
    installmentIntervalMonthsText: String = "1",
    firstDueDateMillis: Long? = null,
    error: String,
    onPaidChange: (String) -> Unit,
    onInstallmentAmountChange: (String) -> Unit = {},
    onInstallmentIntervalMonthsChange: (String) -> Unit = {},
    onFirstDueDateClick: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val paid = Money.parseOrNull(paidText)
    val remaining = if (paid != null && !paid.isNegative() && paid <= total) total - paid else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_settlement_title),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp12)) {
                SettlementRow349(
                    androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_invoice_value),
                    total,
                    TextPrimary,
                )
                if (paidEditable) {
                    CompactTextField(
                        value = paidText,
                        onValueChange = onPaidChange,
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_paid),
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                        onDone = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (paid != null) {
                    SettlementRow349(
                        androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_paid),
                        paid,
                        AccentPrimary,
                    )
                }
                if (remaining != null) {
                    SettlementRow349(
                        androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_remaining),
                        remaining,
                        if (remaining.isZero()) AccentPrimary else ErrorColor,
                    )
                    if (remaining.isPositive()) {
                        TextButton(
                            onClick = onFirstDueDateClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val formatted = firstDueDateMillis?.let {
                                android.text.format.DateFormat.getDateFormat(context).format(Date(it))
                            } ?: "—"
                            Text(
                                androidx.compose.ui.res.stringResource(
                                    com.verto.feature.payment.R.string.payment_v370_first_due_date,
                                    formatted,
                                ),
                                color = AccentPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        CompactTextField(
                            value = installmentAmountText,
                            onValueChange = onInstallmentAmountChange,
                            placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v370_installment_amount),
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                            onDone = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CompactTextField(
                            value = installmentIntervalMonthsText,
                            onValueChange = onInstallmentIntervalMonthsChange,
                            placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v370_installment_every_months),
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                            onDone = onConfirm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val target = Money.parseOrNull(installmentAmountText)
                        if (target != null && target.isPositive()) {
                            val count = ((remaining.amountMinor + target.amountMinor - 1L) / target.amountMinor).coerceAtLeast(1L)
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v370_installment_count, count),
                                color = TextMuted,
                                fontSize = PaymentTextScale.sp11,
                            )
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, color = ErrorColor, fontSize = PaymentTextScale.sp11)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_continue),
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel),
                    color = TextMuted,
                )
            }
        },
    )
}
