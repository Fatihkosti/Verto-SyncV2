package com.verto.app.feature.payment.presentation.invoiceeditor

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.payment.application.PurchaseInvoiceSettlementPolicy
import com.verto.app.feature.payment.application.PurchaseSettlementKind
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.feature.payment.application.model.InvoiceItemData
import com.verto.app.feature.payment.application.model.InventoryItemView
import com.verto.app.feature.payment.application.model.PaymentMode
import com.verto.app.feature.payment.application.model.PaymentDueInstallmentDraft
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import com.verto.app.money.Money
import com.verto.app.money.Quantity
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.util.Calendar

/** Dedicated local purchase editor. Only the committed line list scrolls. */
@Composable
internal fun NewPurchaseInvoiceEditor369(
    form: InvoiceEditorFormState,
    vm: InvoiceEditorViewModel,
    inventoryItems: List<InventoryItemView>,
    allClients: List<ClientItem>,
    isEditMode: Boolean,
    isInternational: Boolean = false,
    currencyLabel: String = "",
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (PaymentMode, Money) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSupplierPicker by rememberSaveable { mutableStateOf(false) }
    var showSettlement by rememberSaveable { mutableStateOf(false) }
    var settlementPaid by rememberSaveable { mutableStateOf("") }
    var installmentAmount by rememberSaveable { mutableStateOf("") }
    var installmentIntervalMonths by rememberSaveable { mutableStateOf("1") }
    var installmentFirstDueAt by rememberSaveable { mutableStateOf(0L) }
    var settlementError by rememberSaveable { mutableStateOf("") }
    var installmentScheduleTouched by rememberSaveable { mutableStateOf(false) }
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var searchFocusTrigger by rememberSaveable { mutableIntStateOf(0) }

    val validItems = form.invoiceItems.filter { it.name.isNotBlank() }
    val totalMoney = validItems.fold(Money.zero()) { total, item ->
        val quantity = Quantity.parseOrNull(item.quantity)
        val price = Money.parseOrNull(item.buyPrice)
        if (quantity == null || price == null) total else total + (price * quantity)
    }
    val selectedSupplier = allClients.firstOrNull {
        it.id == form.selectedClientId && it.id != CASH_SUPPLIER_ID
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
        val purchasePrice = Money.parseOrNull(form.draftItemBuyPrice)
        when {
            form.draftItemName.isBlank() -> form.itemError = context.getString(com.verto.feature.payment.R.string.payment_v369_item_required)
            quantity == null -> form.itemError = context.getString(com.verto.feature.payment.R.string.payment_v369_quantity_invalid)
            purchasePrice == null || !purchasePrice.isPositive() -> {
                form.itemError = context.getString(com.verto.feature.payment.R.string.payment_v369_invalid_purchase_price)
            }
            else -> {
                val salePrice = Money.parseOrNull(form.draftItemSellPrice)
                val line = InvoiceItemData(
                    name = form.draftItemName.trim(),
                    quantity = quantity.units.toString(),
                    // Sale price is retained for inventory/new-item semantics but deliberately not rendered in purchase lines.
                    sellPrice = salePrice?.takeIf { !it.isNegative() }?.toPlainString().orEmpty(),
                    buyPrice = purchasePrice.toPlainString(),
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
        if (form.draftItemName.isNotBlank() && !commitDraftLine()) return
        val lines = form.invoiceItems.filter { it.name.isNotBlank() }
        if (form.selectedClientId.isBlank() || form.selectedClientId == CASH_SUPPLIER_ID) {
            form.paymentMode = PaymentMode.CASH
        }
        if (!form.validate(isInternational = isInternational, validItems = lines)) return
        val total = lines.fold(Money.zero()) { running, item ->
            val qty = Quantity.parseOrNull(item.quantity)
            val price = Money.parseOrNull(item.buyPrice)
            if (qty == null || price == null) running else running + (price * qty)
        }
        if (isEditMode && form.paymentMode == PaymentMode.CASH) {
            form.dueInstallments = emptyList()
            form.pendingDue = 0L
            onSave(form.paymentMode, Money.zero())
            return
        }
        if (form.selectedClientId.isBlank() || form.selectedClientId == CASH_SUPPLIER_ID) {
            form.paymentMode = PaymentMode.CASH
            form.paidAmount = total.toPlainString()
            form.dueInstallments = emptyList()
            form.pendingDue = 0L
            onSave(PaymentMode.CASH, total)
        } else {
            settlementPaid = if (isEditMode) form.paidAmount.ifBlank { "0" } else total.toPlainString()
            installmentAmount = if (isEditMode) {
                form.dueInstallments.firstOrNull()?.let { Money.fromLegacyDouble(it.amount).toPlainString() }.orEmpty()
            } else ""
            installmentIntervalMonths = inferInstallmentIntervalMonths372(form.dueInstallments).toString()
            installmentScheduleTouched = false
            installmentFirstDueAt = form.dueInstallments.firstOrNull()?.dueDate
                ?: defaultFirstInstallmentDue370(form.selectedDateMillis ?: System.currentTimeMillis())
            settlementError = ""
            showSettlement = true
        }
    }

    if (showSettlement) {
        PurchaseSettlementDialog369(
            total = totalMoney,
            paidText = settlementPaid,
            paidEditable = !isEditMode,
            installmentAmountText = installmentAmount,
            installmentIntervalMonthsText = installmentIntervalMonths,
            firstDueDateMillis = installmentFirstDueAt.takeIf { it > 0L },
            error = settlementError,
            onPaidChange = { settlementPaid = it; settlementError = "" },
            onInstallmentAmountChange = { installmentAmount = it; installmentScheduleTouched = true; settlementError = "" },
            onInstallmentIntervalMonthsChange = { installmentIntervalMonths = it.filter(Char::isDigit); installmentScheduleTouched = true; settlementError = "" },
            onFirstDueDateClick = {
                val seed = Calendar.getInstance().apply {
                    timeInMillis = installmentFirstDueAt.takeIf { it > 0L }
                        ?: defaultFirstInstallmentDue370(form.selectedDateMillis ?: System.currentTimeMillis())
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        installmentFirstDueAt = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        installmentScheduleTouched = true
                        settlementError = ""
                    },
                    seed.get(Calendar.YEAR),
                    seed.get(Calendar.MONTH),
                    seed.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            onDismiss = { showSettlement = false },
            onConfirm = {
                val paid = Money.parseOrNull(settlementPaid)
                val decision = paid?.let {
                    runCatching { PurchaseInvoiceSettlementPolicy.evaluate(totalMoney, it) }.getOrNull()
                }
                if (decision == null) {
                    settlementError = context.getString(com.verto.feature.payment.R.string.payment_v349_invalid_paid)
                } else {
                    val schedule = when (decision.kind) {
                        PurchaseSettlementKind.FULLY_PAID -> emptyList()
                        PurchaseSettlementKind.CREDIT -> {
                            val targetInstallment = Money.parseOrNull(installmentAmount)
                            val intervalMonths = installmentIntervalMonths.toIntOrNull()
                            if (targetInstallment == null || !targetInstallment.isPositive() || targetInstallment > decision.remaining) {
                                settlementError = context.getString(com.verto.feature.payment.R.string.payment_v370_installment_amount_invalid)
                                return@PurchaseSettlementDialog369
                            }
                            if (intervalMonths == null || intervalMonths !in 1..120) {
                                settlementError = context.getString(com.verto.feature.payment.R.string.payment_v370_installment_interval_invalid)
                                return@PurchaseSettlementDialog369
                            }
                            val invoiceDate = form.selectedDateMillis ?: System.currentTimeMillis()
                            if (installmentFirstDueAt <= 0L || startOfDay370(installmentFirstDueAt) < startOfDay370(invoiceDate)) {
                                settlementError = context.getString(com.verto.feature.payment.R.string.payment_v370_first_due_invalid)
                                return@PurchaseSettlementDialog369
                            }
                            if (isEditMode && !installmentScheduleTouched && form.dueInstallments.isNotEmpty()) {
                                val preserved = form.dueInstallments.sumOf { Money.fromLegacyDouble(it.amount).amountMinor }
                                if (preserved != decision.remaining.amountMinor) {
                                    settlementError = context.getString(com.verto.feature.payment.R.string.payment_v372_schedule_changed)
                                    return@PurchaseSettlementDialog369
                                }
                                form.dueInstallments
                            } else {
                                buildPurchaseInstallments370(
                                    remaining = decision.remaining,
                                    targetInstallment = targetInstallment,
                                    intervalMonths = intervalMonths,
                                    firstDueDate = installmentFirstDueAt,
                                )
                            }
                        }
                    }
                    form.paidAmount = decision.paid.toPlainString()
                    form.dueInstallments = schedule
                    form.pendingDue = schedule.firstOrNull()?.dueDate ?: 0L
                    form.paymentMode = when (decision.kind) {
                        PurchaseSettlementKind.FULLY_PAID -> PaymentMode.CASH
                        PurchaseSettlementKind.CREDIT -> PaymentMode.CREDIT
                    }
                    showSettlement = false
                    onSave(form.paymentMode, decision.paid)
                }
            },
        )
    }

    if (showSupplierPicker) {
        PurchaseSupplierPickerDialog369(
            selectedSupplierId = form.selectedClientId,
            suppliers = allClients,
            globalSupplierOnly = isInternational,
            onDismiss = { showSupplierPicker = false },
            onWithoutSupplier = {
                form.selectedClientId = ""
                form.clientSearch = ""
                showSupplierPicker = false
            },
            onSelect = { id ->
                form.selectedClientId = id
                form.clientSearch = ""
                showSupplierPicker = false
            },
            onQuickAdd = { name ->
                vm.quickAddClient(
                    context, name, isSupplier = !isInternational, isGlobalSupplier = isInternational,
                ) { id ->
                    form.selectedClientId = id
                    form.clientSearch = ""
                    showSupplierPicker = false
                }
            },
        )
    }

    Scaffold(
        containerColor = BgDeep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            PurchaseInvoiceBottomBar369(
                itemCount = validItems.size,
                total = totalMoney,
                isSaving = isSaving,
                error = form.saveError,
                onSave = ::requestSave,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(
                    horizontal = PaymentDimensions.dp12,
                    vertical = PaymentDimensions.dp8,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
                }
                Text(
                    if (isInternational) {
                        buildString {
                            append(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v372_international_purchase_title))
                            if (currencyLabel.isNotBlank()) append(" · ").append(currencyLabel)
                        }
                    } else androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_purchase_invoice_title),
                    modifier = Modifier.weight(.75f),
                    color = TextPrimary,
                    fontSize = PaymentTextScale.sp18,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Surface(
                    shape = RoundedCornerShape(PaymentDimensions.dp12),
                    color = BgCard,
                    border = BorderStroke(PaymentDimensions.dp1, BorderColor),
                    modifier = Modifier.weight(1.15f).clickable(enabled = !isEditMode) { showSupplierPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = PaymentDimensions.dp8, vertical = PaymentDimensions.dp8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp5),
                    ) {
                        Icon(
                            Icons.Filled.LocalShipping,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(PaymentDimensions.dp17),
                        )
                        Text(
                            selectedSupplier?.name
                                ?: androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_no_supplier),
                            color = if (selectedSupplier == null) TextMuted else TextPrimary,
                            fontSize = PaymentTextScale.sp12,
                            fontWeight = if (selectedSupplier == null) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = { form.showDatePicker = true }) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v369_date),
                        tint = AccentPrimary,
                        modifier = Modifier.size(PaymentDimensions.dp22),
                    )
                }
            }

            PurchaseLineComposer369(
                form = form,
                inventoryItems = inventoryItems,
                editing = editingIndex >= 0,
                searchFocusTrigger = searchFocusTrigger,
                onCommit = { commitDraftLine() },
                onInventorySelected = { inventory ->
                    form.draftInventoryItemId = inventory.id
                    form.draftItemName = inventory.name
                    if (inventory.sellPrice > 0.0) {
                        form.draftItemSellPrice = Money.fromLegacyDouble(inventory.sellPrice).toPlainString()
                    }
                    if (inventory.buyPrice > 0.0) {
                        form.draftItemBuyPrice = Money.fromLegacyDouble(inventory.buyPrice).toPlainString()
                    }
                },
                onAutoAddForPurchase = { name, _, _ ->
                    // Exact-name selection reuses the current inventory prices. Truly new items stay
                    // unpersisted here and are created atomically by the existing invoice save boundary.
                    inventoryItems.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { inventory ->
                        form.draftInventoryItemId = inventory.id
                        form.draftItemName = inventory.name
                        if (inventory.sellPrice > 0.0) {
                            form.draftItemSellPrice = Money.fromLegacyDouble(inventory.sellPrice).toPlainString()
                        }
                        if (inventory.buyPrice > 0.0) {
                            form.draftItemBuyPrice = Money.fromLegacyDouble(inventory.buyPrice).toPlainString()
                        }
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

            PurchaseInvoiceLines369(
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

private fun buildPurchaseInstallments370(
    remaining: Money,
    targetInstallment: Money,
    intervalMonths: Int,
    firstDueDate: Long,
): List<PaymentDueInstallmentDraft> {
    require(remaining.isPositive())
    require(targetInstallment.isPositive())
    require(intervalMonths > 0)
    require(firstDueDate > 0L)
    val calendar = Calendar.getInstance().apply { timeInMillis = firstDueDate }
    val rows = mutableListOf<PaymentDueInstallmentDraft>()
    var balance = remaining
    var sequence = 0
    while (balance.isPositive()) {
        if (sequence > 0) calendar.add(Calendar.MONTH, intervalMonths)
        val amount = if (balance <= targetInstallment) balance else targetInstallment
        rows += PaymentDueInstallmentDraft(amount = amount.toLegacyDouble(), dueDate = calendar.timeInMillis)
        balance -= amount
        sequence++
    }
    return rows
}

private fun defaultFirstInstallmentDue370(baseDate: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = baseDate
        add(Calendar.MONTH, 1)
    }.timeInMillis

private fun startOfDay370(epochMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = epochMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun inferInstallmentIntervalMonths372(rows: List<PaymentDueInstallmentDraft>): Int {
    if (rows.size < 2) return 1
    val ordered = rows.sortedBy { it.dueDate }
    val first = Calendar.getInstance().apply { timeInMillis = ordered[0].dueDate }
    val second = Calendar.getInstance().apply { timeInMillis = ordered[1].dueDate }
    val months = (second.get(Calendar.YEAR) - first.get(Calendar.YEAR)) * 12 +
        second.get(Calendar.MONTH) - first.get(Calendar.MONTH)
    return months.coerceIn(1, 120)
}
