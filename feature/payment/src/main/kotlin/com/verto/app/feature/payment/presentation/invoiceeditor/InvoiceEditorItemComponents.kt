package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.ui.components.VertoOutlinedTextField

import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed

import com.verto.app.feature.payment.application.model.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InvoiceItemRow(
    item: InvoiceItemData,
    index: Int,
    isSale: Boolean,
    inventoryItems: List<InventoryItemView>,
    isLastItem: Boolean,
    onUpdate: (InvoiceItemData) -> Unit,
    onAddNext: () -> Unit,
    onDelete: () -> Unit,
    onQuickAddItem: (name: String, buyPrice: Double, sellPrice: Double, qty: Int) -> Unit,
    onAutoAddForPurchase: (String, Double, Double) -> Unit,
    isInternational: Boolean = false,
    onSave: (() -> Unit)? = null,
    currencyLabel: String = "",
    modifier: Modifier = Modifier
) {
    val nameFocus  = remember { FocusRequester() }
    val qtyFocus   = remember { FocusRequester() }
    val priceFocus = remember { FocusRequester() }

    LaunchedEffect(item.id) {
        if (item.name.isEmpty() && index > 0 && isLastItem) {
            nameFocus.requestFocus()
        }
    }

    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(PaymentDimensions.dp12))
            .combinedClickable(onLongClick = onDelete, onClick = {})
            .background(BgCard)
            .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp12))
            .padding(PaymentDimensions.dp10),
        verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6)) {
            CompactGoodsNameField(
                item                 = item,
                inventoryItems       = inventoryItems,
                isSale               = isSale,
                isInternational      = isInternational,
                onUpdate             = onUpdate,
                onQuickAddItem       = onQuickAddItem,
                onAutoAddForPurchase = onAutoAddForPurchase,
                focusRequester       = nameFocus,
                onNext               = { qtyFocus.requestFocus() },
                modifier             = Modifier.weight(2.5f)
            )

            CompactTextField(
                value         = item.quantity,
                onValueChange = { onUpdate(item.copy(quantity = it)) },
                placeholder   = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a95134401afe),
                keyboardType  = KeyboardType.Number,
                focusRequester = qtyFocus,
                onNext        = { priceFocus.requestFocus() },
                modifier      = Modifier.weight(0.8f)
            )

            if (isInternational) {
                CompactTextField(
                    value         = item.buyPrice,
                    onValueChange = { onUpdate(item.copy(buyPrice = it)) },
                    placeholder   = currencyLabel.ifBlank { androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_6d3b8a57de85) },
                    keyboardType  = KeyboardType.Decimal,
                    focusRequester = priceFocus,
                    onDone        = onSave,
                    imeAction     = ImeAction.Done,
                    modifier      = Modifier.weight(1.3f)
                )
            } else {
                CompactTextField(
                    value         = item.sellPrice,
                    onValueChange = { onUpdate(item.copy(sellPrice = it)) },
                    placeholder   = if (isSale) androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_b6aa0c7d7a21) else androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_56390caa2af4),
                    keyboardType  = KeyboardType.Decimal,
                    focusRequester = priceFocus,
                    onNext        = { onAddNext() },
                    imeAction     = ImeAction.Next,
                    modifier      = Modifier.weight(1.3f)
                )
            }
        }

        if (!isSale && !isInternational) {
            CompactTextField(
                value         = item.buyPrice,
                onValueChange = { onUpdate(item.copy(buyPrice = it)) },
                placeholder   = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_6d3b8a57de85),
                keyboardType  = KeyboardType.Decimal,
                imeAction     = ImeAction.Next,
                onNext        = { onAddNext() },
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────
// حقل اسم الصنف مع قائمة منسدلة
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactGoodsNameField(
    item: InvoiceItemData,
    inventoryItems: List<InventoryItemView>,
    isSale: Boolean,
    isInternational: Boolean = false,
    onUpdate: (InvoiceItemData) -> Unit,
    onQuickAddItem: (name: String, buyPrice: Double, sellPrice: Double, qty: Int) -> Unit,
    onAutoAddForPurchase: (String, Double, Double) -> Unit,
    onInventorySelected: (InventoryItemView) -> Unit = {},
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogBuyPrice by remember { mutableStateOf("") }
    var dialogSellPrice by remember { mutableStateOf("") }
    var dialogQty by remember { mutableStateOf("") }

    val filtered = if (item.name.length >= 1)
        inventoryItems.filter {
            !it.isUnitItem && it.name.isNotBlank() &&
            it.name.contains(item.name, ignoreCase = true) && it.name != item.name
        }.take(6)
    else emptyList()
    val showQuickAdd = item.name.length >= 1 && item.inventoryItemId.isBlank()

    ExposedDropdownMenuBox(
        expanded         = expanded && (filtered.isNotEmpty() || showQuickAdd),
        onExpandedChange = { expanded = it },
        modifier         = modifier
    ) {
        BasicTextField(
            value         = item.name,
            onValueChange = { onUpdate(item.copy(name = it, inventoryItemId = "")); expanded = true },
            textStyle     = androidx.compose.material3.LocalTextStyle.current.copy(
                color = TextPrimary, fontSize = PaymentTextScale.sp14),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
                onDone = { onDone?.invoke() },
            ),
            modifier        = Modifier.menuAnchor().focusRequester(focusRequester)
                .fillMaxWidth().height(PaymentDimensions.dp42)
                .clip(RoundedCornerShape(PaymentDimensions.dp10)).background(BgCard)
                .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp10))
                .padding(horizontal = PaymentDimensions.dp14),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp10),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_7fbf2c353e51),
                        tint = TextMuted,
                        modifier = Modifier.size(PaymentDimensions.dp24),
                    )
                    Box(Modifier.weight(1f)) {
                        if (item.name.isEmpty()) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_181a5cb5d713), color = TextMuted, fontSize = PaymentTextScale.sp12)
                        }
                        innerTextField()
                    }
                }
            }
        )
        ExposedDropdownMenu(
            expanded         = expanded && (filtered.isNotEmpty() || showQuickAdd),
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(BgCard)
        ) {
            filtered.forEach { inv ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = PaymentDimensions.dp48),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(inv.name, color = TextPrimary, fontSize = PaymentTextScale.sp14, fontWeight = FontWeight.SemiBold)
                            Text(
                                androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_edf2477f8e2f, inv.quantity),
                                color = if (inv.quantity > 0) SuccessColor else WarningColor,
                                fontSize = PaymentTextScale.sp12,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    onClick = {
                        onUpdate(
                            if (isInternational) {
                                item.copy(name = inv.name, inventoryItemId = inv.id)
                            } else {
                                item.copy(
                                    name            = inv.name,
                                    inventoryItemId = inv.id,
                                    sellPrice       = if (inv.sellPrice > 0)
                                        WhatsAppUtils.formatAmount(inv.sellPrice) else item.sellPrice,
                                    buyPrice        = if (inv.buyPrice > 0)
                                        WhatsAppUtils.formatAmount(inv.buyPrice) else ""
                                )
                            }
                        )
                        onInventorySelected(inv)
                        expanded = false
                        onNext()
                    }
                )
            }
            if (showQuickAdd) {
                HorizontalDivider(color = BorderColor, thickness = PaymentDimensions.dp0_5)
                if (isSale) {
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AddCircleOutline, null,
                                    tint = AccentPrimary, modifier = Modifier.size(PaymentDimensions.dp16))
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_c2fc4667bcf9), color = AccentPrimary, fontSize = PaymentTextScale.sp13)
                            }
                        },
                        onClick = {
                            dialogSellPrice = item.sellPrice
                            dialogBuyPrice  = item.buyPrice
                            dialogQty       = ""
                            expanded        = false
                            showAddDialog   = true
                        }
                    )
                } else if (!isInternational) {
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AddCircleOutline, null,
                                    tint = SuccessColor, modifier = Modifier.size(PaymentDimensions.dp16))
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_23fbaecfd69d), color = SuccessColor, fontSize = PaymentTextScale.sp13)
                            }
                        },
                        onClick = {
                            onAutoAddForPurchase(
                                item.name,
                                item.buyPrice.toAmountDouble(),
                                item.sellPrice.toAmountDouble()
                            )
                            expanded = false
                            onNext()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PaymentDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_c2fc4667bcf9), color = AccentPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp10)) {
                    Text(item.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = PaymentTextScale.sp14)
                    VertoOutlinedTextField(
                        value         = dialogSellPrice,
                        onValueChange = { dialogSellPrice = it },
                        label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_56390caa2af4), color = TextMuted, fontSize = PaymentTextScale.sp12) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary, unfocusedBorderColor = BorderColor,
                            focusedContainerColor = BgSurface, unfocusedContainerColor = BgSurface,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(PaymentDimensions.dp10), modifier = Modifier.fillMaxWidth()
                    )
                    VertoOutlinedTextField(
                        value         = dialogBuyPrice,
                        onValueChange = { dialogBuyPrice = it },
                        label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_6d3b8a57de85), color = TextMuted, fontSize = PaymentTextScale.sp12) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary, unfocusedBorderColor = BorderColor,
                            focusedContainerColor = BgSurface, unfocusedContainerColor = BgSurface,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(PaymentDimensions.dp10), modifier = Modifier.fillMaxWidth()
                    )
                    VertoOutlinedTextField(
                        value         = dialogQty,
                        onValueChange = { dialogQty = it },
                        label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a95134401afe), color = TextMuted, fontSize = PaymentTextScale.sp12) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary, unfocusedBorderColor = BorderColor,
                            focusedContainerColor = BgSurface, unfocusedContainerColor = BgSurface,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(PaymentDimensions.dp10), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onQuickAddItem(item.name, dialogBuyPrice.toAmountDouble(),
                        dialogSellPrice.toAmountDouble(), dialogQty.toIntOrNull() ?: 0)
                    showAddDialog = false
                    onNext()
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add), color = AccentPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────
// حقل نص مدمج
// ─────────────────────────────────────────────────────

/**
 * The focused add-item form used by the invoice composer. It deliberately
 * keeps the draft outside the saved line list, so pressing Done either adds a
 * complete line or leaves the user on the form with a useful validation hint.
 */
