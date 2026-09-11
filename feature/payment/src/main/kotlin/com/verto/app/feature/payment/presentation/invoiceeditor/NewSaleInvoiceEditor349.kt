package com.verto.app.feature.payment.presentation.invoiceeditor

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.feature.payment.application.SaleInvoiceSettlementPolicy
import com.verto.app.feature.payment.application.SaleSettlementKind
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.feature.payment.application.model.ClientType
import com.verto.app.feature.payment.application.model.InvoiceItemData
import com.verto.app.feature.payment.application.model.InventoryItemView
import com.verto.app.feature.payment.application.model.PaymentMode
import com.verto.app.feature.payment.application.model.PaymentDueInstallmentDraft
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import com.verto.app.money.Money
import com.verto.app.money.Quantity
import com.verto.app.ui.components.VertoButton
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Session 349 sale-only editor. The only scrolling surface is the line list.
 * Search/composer stays above it and the item-count/total/save bar stays below it.
 */
@Composable
internal fun NewSaleInvoiceEditor349(
    form: InvoiceEditorFormState,
    vm: InvoiceEditorViewModel,
    inventoryItems: List<InventoryItemView>,
    allClients: List<ClientItem>,
    isEditMode: Boolean,
    canManageCommission: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (PaymentMode, Money) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCustomerPicker by rememberSaveable { mutableStateOf(false) }
    var showDiscount by rememberSaveable { mutableStateOf(false) }
    var showReferrerPicker by rememberSaveable { mutableStateOf(false) }
    var showSettlement by rememberSaveable { mutableStateOf(false) }
    var settlementPaid by rememberSaveable { mutableStateOf("") }
    var settlementError by rememberSaveable { mutableStateOf("") }
    var settlementDueAt by rememberSaveable { mutableStateOf(0L) }
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var searchFocusTrigger by rememberSaveable { mutableIntStateOf(0) }

    val validItems = form.invoiceItems.filter { it.name.isNotBlank() }
    val grossTotal = validItems.fold(Money.zero()) { total, item ->
        val quantity = Quantity.parseOrNull(item.quantity)
        val price = Money.parseOrNull(item.sellPrice)
        if (quantity == null || price == null) total else total + (price * quantity)
    }
    val discount = Money.parseOrNull(form.invoiceDiscount) ?: Money.zero()
    val totalMoney = if (!discount.isNegative() && discount < grossTotal) grossTotal - discount else grossTotal
    val selectedCustomer = allClients.firstOrNull { it.id == form.selectedClientId && it.id != CASH_CLIENT_ID }
    val buyerEarnsCommission = selectedCustomer?.customerSegment in setOf(ClientType.MARKETER.name, ClientType.WORKSHOP_OWNER.name)
    val eligibleReferrers = allClients.filter { client ->
        client.id != selectedCustomer?.id && client.customerSegment in setOf(ClientType.MARKETER.name, ClientType.WORKSHOP_OWNER.name)
    }
    val selectedReferrer = eligibleReferrers.firstOrNull { it.id == form.referrerClientId }

    LaunchedEffect(selectedCustomer?.id, buyerEarnsCommission) {
        if (selectedCustomer == null || buyerEarnsCommission) form.referrerClientId = ""
    }

    fun clearDraftLine() {
        form.draftItemName = ""
        form.draftItemQuantity = "1"
        form.draftItemSellPrice = ""
        form.draftItemBuyPrice = ""
        form.draftInventoryItemId = ""
        form.itemError = ""
        editingIndex = -1
        searchFocusTrigger++
    }

    fun commitDraftLine(): Boolean {
        val quantity = Quantity.parseOrNull(form.draftItemQuantity)
        val price = Money.parseOrNull(form.draftItemSellPrice)
        when {
            form.draftItemName.isBlank() -> form.itemError = "اكتب اسم الصنف أو اختره من القائمة"
            quantity == null -> form.itemError = "أدخل كمية صحيحة أكبر من صفر"
            price == null || !price.isPositive() -> form.itemError = "سعر البيع غير صالح"
            else -> {
                val line = InvoiceItemData(
                    name = form.draftItemName.trim(),
                    quantity = quantity.units.toString(),
                    sellPrice = price.toPlainString(),
                    buyPrice = form.draftItemBuyPrice,
                    inventoryItemId = form.draftInventoryItemId,
                )
                val current = form.invoiceItems.filter { it.name.isNotBlank() }.toMutableList()
                if (editingIndex in current.indices) current[editingIndex] = line else current += line
                form.invoiceItems = current
                clearDraftLine()
                return true
            }
        }
        return false
    }

    fun requestSave() {
        if (form.draftItemName.isNotBlank()) {
            if (!commitDraftLine()) return
        }
        val lines = form.invoiceItems.filter { it.name.isNotBlank() }
        if (form.selectedClientId.isBlank() || form.selectedClientId == CASH_CLIENT_ID) {
            // A cash walk-in must never inherit a restored CREDIT draft mode.
            form.paymentMode = PaymentMode.CASH
        }
        if (!form.validate(isInternational = false, validItems = lines)) return
        val recomputedGross = lines.fold(Money.zero()) { total, item ->
            val qty = Quantity.parseOrNull(item.quantity)
            val price = Money.parseOrNull(item.sellPrice)
            if (qty == null || price == null) total else total + (price * qty)
        }
        val appliedDiscount = Money.parseOrNull(form.invoiceDiscount) ?: Money.zero()
        if (appliedDiscount.isNegative() || appliedDiscount >= recomputedGross) {
            form.saveError = "الخصم يجب أن يكون أقل من إجمالي البنود"
            return
        }
        val recomputedTotal = recomputedGross - appliedDiscount
        if (isEditMode) {
            onSave(form.paymentMode, Money.zero())
            return
        }
        if (form.selectedClientId.isBlank() || form.selectedClientId == CASH_CLIENT_ID) {
            form.paymentMode = PaymentMode.CASH
            form.paidAmount = recomputedTotal.toPlainString()
            form.pendingDue = 0L
            onSave(PaymentMode.CASH, recomputedTotal)
        } else {
            settlementPaid = recomputedTotal.toPlainString()
            settlementDueAt = form.dueInstallments.firstOrNull()?.dueDate ?: 0L
            settlementError = ""
            showSettlement = true
        }
    }

    if (showSettlement) {
        SaleSettlementDialog349(
            total = totalMoney,
            paidText = settlementPaid,
            error = settlementError,
            dueDateMillis = settlementDueAt.takeIf { it > 0L },
            onPaidChange = { settlementPaid = it; settlementError = "" },
            onDueDateClick = {
                val invoiceDate = form.selectedDateMillis ?: System.currentTimeMillis()
                val seed = Calendar.getInstance().apply {
                    timeInMillis = settlementDueAt.takeIf { it > 0L } ?: invoiceDate
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        settlementDueAt = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        settlementError = ""
                    },
                    seed.get(Calendar.YEAR), seed.get(Calendar.MONTH), seed.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            onDismiss = { showSettlement = false },
            onConfirm = {
                val paid = Money.parseOrNull(settlementPaid)
                val result = paid?.let { runCatching { SaleInvoiceSettlementPolicy.evaluate(totalMoney, it) }.getOrNull() }
                if (result == null) {
                    settlementError = context.getString(com.verto.feature.payment.R.string.payment_v349_invalid_paid)
                } else {
                    form.paidAmount = result.paid.toPlainString()
                    when (result.kind) {
                        SaleSettlementKind.FULLY_PAID -> {
                            form.paymentMode = PaymentMode.CASH
                            form.dueInstallments = emptyList()
                            form.pendingDue = 0L
                            showSettlement = false
                            onSave(PaymentMode.CASH, result.paid)
                        }
                        SaleSettlementKind.CREDIT -> {
                            val invoiceDate = form.selectedDateMillis ?: System.currentTimeMillis()
                            if (settlementDueAt <= 0L || startOfDay372(settlementDueAt) < startOfDay372(invoiceDate)) {
                                settlementError = context.getString(com.verto.feature.payment.R.string.payment_v372_sale_due_required)
                                return@SaleSettlementDialog349
                            }
                            form.paymentMode = PaymentMode.CREDIT
                            form.dueInstallments = listOf(
                                PaymentDueInstallmentDraft(result.remaining.toLegacyDouble(), settlementDueAt)
                            )
                            form.pendingDue = settlementDueAt
                            showSettlement = false
                            onSave(PaymentMode.CREDIT, result.paid)
                        }
                    }
                }
            },
        )
    }

    if (showDiscount) {
        SaleDiscountDialog351(
            grossTotal = grossTotal,
            current = form.invoiceDiscount,
            onDismiss = { showDiscount = false },
            onConfirm = { form.invoiceDiscount = it; showDiscount = false },
        )
    }
    if (showReferrerPicker) {
        SaleReferrerPicker351(
            eligible = eligibleReferrers,
            selectedId = form.referrerClientId,
            onDismiss = { showReferrerPicker = false },
            onSelect = { form.referrerClientId = it; showReferrerPicker = false },
        )
    }

    if (showCustomerPicker) {
        SaleCustomerPickerDialog349(
            selectedClientId = form.selectedClientId,
            clients = allClients,
            onDismiss = { showCustomerPicker = false },
            onWithoutCustomer = {
                form.selectedClientId = ""
                form.clientSearch = ""
                showCustomerPicker = false
            },
            onSelect = { id ->
                form.selectedClientId = id
                form.clientSearch = ""
                showCustomerPicker = false
            },
            onQuickAdd = { name ->
                vm.quickAddClient(context, name, isSupplier = false) { id ->
                    form.selectedClientId = id
                    form.clientSearch = ""
                    showCustomerPicker = false
                }
            },
        )
    }

    Scaffold(
        containerColor = BgDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            SaleInvoiceBottomBar349(
                itemCount = validItems.size,
                grossTotal = grossTotal,
                discount = discount,
                total = totalMoney,
                isSaving = isSaving,
                error = form.saveError,
                onDiscount = { showDiscount = true },
                onSave = ::requestSave,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(
                    horizontal = PaymentDimensions.dp12,
                    vertical = PaymentDimensions.dp8,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
                }
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_sale_invoice_title),
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = PaymentTextScale.sp18,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(PaymentDimensions.dp12),
                    color = BgCard,
                    border = BorderStroke(PaymentDimensions.dp1, BorderColor),
                    modifier = Modifier.clickable(enabled = !isEditMode) { showCustomerPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = PaymentDimensions.dp10, vertical = PaymentDimensions.dp8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6),
                    ) {
                        Icon(Icons.Filled.PersonOutline, null, tint = AccentPrimary, modifier = Modifier.size(PaymentDimensions.dp17))
                        Text(
                            selectedCustomer?.name ?: androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v349_no_customer),
                            color = if (selectedCustomer == null) TextMuted else TextPrimary,
                            fontSize = PaymentTextScale.sp12,
                            fontWeight = if (selectedCustomer == null) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(PaymentDimensions.dp12),
                color = BgCard,
                border = BorderStroke(PaymentDimensions.dp1, BorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaymentDimensions.dp12, vertical = PaymentDimensions.dp2)
                    .clickable { form.showDatePicker = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PaymentDimensions.dp12, vertical = PaymentDimensions.dp9),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(PaymentDimensions.dp18),
                    )
                    Text(
                        "التاريخ: ${DateUtils.formatDate(form.selectedDateMillis ?: System.currentTimeMillis())}",
                        color = TextPrimary,
                        fontSize = PaymentTextScale.sp12,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (selectedCustomer != null && !buyerEarnsCommission) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaymentDimensions.dp12, vertical = PaymentDimensions.dp4),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SaleReferralChip351(
                        referrerName = selectedReferrer?.name,
                        enabled = !isEditMode || canManageCommission,
                        onClick = { showReferrerPicker = true },
                    )
                }
            }

            SaleLineComposer349(
                form = form,
                inventoryItems = inventoryItems,
                editing = editingIndex >= 0,
                searchFocusTrigger = searchFocusTrigger,
                onCommit = { commitDraftLine() },
                onInventorySelected = { inventory ->
                    form.draftInventoryItemId = inventory.id
                    if (inventory.sellPrice > 0.0) form.draftItemSellPrice = Money.fromLegacyDouble(inventory.sellPrice).toPlainString()
                    if (inventory.buyPrice > 0.0) form.draftItemBuyPrice = Money.fromLegacyDouble(inventory.buyPrice).toPlainString()
                },
                onQuickAdd = { name, buyPrice, sellPrice, quantity ->
                    vm.quickAddInventoryItem(context, name, buyPrice, sellPrice, quantity) { inventory ->
                        form.draftInventoryItemId = inventory.id
                        form.draftItemName = inventory.name
                        if (inventory.sellPrice > 0.0) form.draftItemSellPrice = Money.fromLegacyDouble(inventory.sellPrice).toPlainString()
                        if (inventory.buyPrice > 0.0) form.draftItemBuyPrice = Money.fromLegacyDouble(inventory.buyPrice).toPlainString()
                    }
                },
                modifier = Modifier.padding(horizontal = PaymentDimensions.dp12),
            )

            if (form.itemError.isNotBlank()) {
                Text(
                    form.itemError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = PaymentTextScale.sp11,
                    modifier = Modifier.padding(horizontal = PaymentDimensions.dp16, vertical = PaymentDimensions.dp4),
                )
            }

            SaleInvoiceLines349(
                items = validItems,
                onEdit = { index, item ->
                    editingIndex = index
                    form.draftItemName = item.name
                    form.draftItemQuantity = item.quantity
                    form.draftItemSellPrice = item.sellPrice
                    form.draftItemBuyPrice = item.buyPrice
                    form.draftInventoryItemId = item.inventoryItemId
                    form.itemError = ""
                },
                onDelete = { index, item ->
                    val current = form.invoiceItems.filter { it.name.isNotBlank() }.toMutableList()
                    if (index in current.indices) {
                        current.removeAt(index)
                        form.invoiceItems = current
                        if (editingIndex == index) clearDraftLine()
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(com.verto.feature.payment.R.string.payment_v349_deleted_line, item.name),
                                actionLabel = context.getString(com.verto.feature.payment.R.string.payment_v349_undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                val restored = form.invoiceItems.filter { it.name.isNotBlank() }.toMutableList()
                                restored.add(index.coerceIn(0, restored.size), item)
                                form.invoiceItems = restored
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun startOfDay372(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = value
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
