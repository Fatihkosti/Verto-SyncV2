package com.verto.app.feature.party.presentation.supplier

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.party.application.model.PartyPaymentAllocationResult
import com.verto.app.feature.party.application.model.PartyPaymentTarget
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────
@HiltViewModel
class SupplierPaymentViewModel @Inject constructor(
    private val partyService: PartyApplicationService,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private companion object {
        const val PENDING_BULK_PAYMENT_REQUEST_ID = "pending_bulk_payment_request_id"
    }

    private fun pendingBulkPaymentRequestId(): String =
        savedStateHandle.get<String>(PENDING_BULK_PAYMENT_REQUEST_ID)
            ?: java.util.UUID.randomUUID().toString().also {
                savedStateHandle[PENDING_BULK_PAYMENT_REQUEST_ID] = it
            }

    private val _supplierId = MutableStateFlow("")

    val supplier = _supplierId
        .flatMapLatest { partyService.getClientById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── إجمالي الديون للمورد ──────────────────────────
    val totalDebt = _supplierId.flatMapLatest { id ->
        partyService.getInvoiceSummariesForClient(id).map { summaries ->
            summaries
                .filter { it.invoice.status == PartyInvoiceStatus.CLOSED_CREDIT }
                .sumOf { maxOf(it.invoice.totalAmount - it.totalPaid, 0.0) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ── رصيد لصالحنا عند المورد — من سجل الرصيد المقدَّم الفعلي (الجزء الموجب) ──
    val supplierCredit = _supplierId.flatMapLatest { id ->
        partyService.getNetCreditForClient(id).map { maxOf(it, 0.0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ── رصيد الصندوق الحالي ───────────────────────────
    val cashBoxBalance = partyService.cashBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _paymentResult = MutableStateFlow<PaymentResult?>(null)
    val paymentResult: StateFlow<PaymentResult?> = _paymentResult

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun clearError() { _error.value = null }

    // منع الضغط المزدوج أثناء تنفيذ السداد
    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    val isCompetitor = _supplierId
        .flatMapLatest { id -> partyService.observeIsCompetitor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGlobalSupplier = _supplierId
        .flatMapLatest { id -> partyService.observeSupplierScope(id).map { it == com.verto.app.feature.party.domain.model.SupplierScope.INTERNATIONAL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun init(id: String) { _supplierId.value = id }

    private suspend fun creditTargets(supplierId: String, predicate: (PartyInvoiceSummary) -> Boolean) =
        try {
            partyService.getInvoiceSummariesForClient(supplierId).first()
                .filter { it.invoice.status == PartyInvoiceStatus.CLOSED_CREDIT && it.remaining > 0.01 && predicate(it) }
                .sortedBy { it.invoice.createdAt }
                .map { it.toTarget() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) { emptyList() }

    // ── سداد المنافس باتجاه محدد ──────────────────────
    fun payCompetitor(amount: Double, fromCompetitor: Boolean) = runAllocation { debt, requestId ->
        val supplierId   = _supplierId.value
        val supplierName = supplier.value?.name ?: ""
        // fromCompetitor = المنافس دفع لنا (تحصيل على المبيعات) = نقد يدخل.
        val targets = creditTargets(supplierId) {
            if (fromCompetitor) it.invoice.category == PartyInvoiceCategory.SALE
            else                it.invoice.category == PartyInvoiceCategory.PURCHASE
        }
        partyService.allocatePayment(
            clientId   = supplierId,
            clientName = supplierName,
            amount     = amount,
            targets    = targets,
            note       = if (fromCompetitor) "سداد من المنافس" else "سداد للمنافس",
            moneyIn    = fromCompetitor,
            requestId  = requestId
        )
    }

    /**
     * Session 4: السداد الجماعي عبر [PartyApplicationService.allocatePayment] — Transaction واحدة لكل
     * الدفعات + الصندوق + الفائض (أو لا شيء). الفائض يُسجَّل رصيداً مقدَّماً لصالحنا.
     *
     * المورد العالمي: [rate] = localAmount / amount → كل حركة صندوق بالعملة المحلية.
     */
    fun pay(amount: Double, localAmount: Double = amount) = runAllocation { debt, requestId ->
        val supplierId   = _supplierId.value
        val supplierName = supplier.value?.name ?: ""
        val rate         = if (amount > 0.0) localAmount / amount else 1.0
        val targets      = creditTargets(supplierId) { it.invoice.category == PartyInvoiceCategory.PURCHASE }
        partyService.allocatePayment(
            clientId   = supplierId,
            clientName = supplierName,
            amount     = amount,
            targets    = targets,
            note       = "سداد مورد",
            moneyIn    = false,          // سداد للمورد = نقد يخرج من الصندوق
            rate       = rate,
            requireSupplierPermission = true,
            requestId = requestId
        )
    }

    fun clearResult() { _paymentResult.value = null }

    /** يلفّ التوزيع بحماية الضغط المزدوج ويبني نتيجة العرض. */
    private fun runAllocation(block: suspend (debt: Double, requestId: String) -> PartyPaymentAllocationResult) {
        if (_processing.value) return
        _processing.value = true
        viewModelScope.launch {
            try {
                val debt = totalDebt.value
                val requestId = pendingBulkPaymentRequestId()
                when (val res = block(debt, requestId)) {
                    is PartyPaymentAllocationResult.Success -> {
                        savedStateHandle.remove<String>(PENDING_BULK_PAYMENT_REQUEST_ID)
                        val cashAfter = try {
                            partyService.cashBalance.first()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            0.0
                        }
                        _paymentResult.value = PaymentResult(
                            paidAmount    = res.applied + res.surplusCredit,
                            debtBefore    = debt,
                            debtAfter     = maxOf(debt - res.applied, 0.0),
                            surplusCredit = res.surplusCredit,
                            cashBoxAfter  = cashAfter
                        )
                    }
                    is PartyPaymentAllocationResult.Error -> _error.value = res.message
                }
            } finally {
                _processing.value = false
            }
        }
    }
}

data class PaymentResult(
    val paidAmount   : Double,
    val debtBefore   : Double,
    val debtAfter    : Double,
    val surplusCredit: Double,
    val cashBoxAfter : Double
)

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierPaymentScreen(
    supplierId: String,
    onBack: () -> Unit,
    vm: SupplierPaymentViewModel = hiltViewModel()
) {
    LaunchedEffect(supplierId) { vm.init(supplierId) }

    val supplier            by vm.supplier.collectAsStateWithLifecycle()
    val totalDebt           by vm.totalDebt.collectAsStateWithLifecycle()
    val supplierCredit      by vm.supplierCredit.collectAsStateWithLifecycle()
    val cashBoxBalance      by vm.cashBoxBalance.collectAsStateWithLifecycle()
    val paymentResult       by vm.paymentResult.collectAsStateWithLifecycle()
    val isCompetitor        by vm.isCompetitor.collectAsStateWithLifecycle()
    val isGlobalSupplier    by vm.isGlobalSupplier.collectAsStateWithLifecycle()
    val processing          by vm.processing.collectAsStateWithLifecycle()
    val error               by vm.error.collectAsStateWithLifecycle()

    var amountText              by remember { mutableStateOf("") }
    var exchangeRateText        by remember { mutableStateOf("") }
    var showConfirmDialog        by remember { mutableStateOf(false) }
    var competitorDirection     by remember { mutableStateOf<Boolean?>(null) }

    val amount       = amountText.toDoubleOrNull() ?: 0.0
    val exchangeRate = exchangeRateText.toDoubleOrNull() ?: 0.0
    val localAmount  = if (isGlobalSupplier && exchangeRate > 0) amount * exchangeRate else amount

    // معاينة حسابية قبل التأكيد
    val previewDebtAfter    = maxOf(totalDebt - amount, 0.0)
    val previewSurplus      = if (amount > totalDebt) amount - totalDebt else 0.0
    val previewCashAfter    = cashBoxBalance - localAmount

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title  = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_supplier_payment_title, supplier?.name ?: androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_loading_name)),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(PartyDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp14)
        ) {
            // ── ملخص الحالة الحالية ────────────────────
            SummaryCard(
                totalDebt      = totalDebt,
                supplierCredit = supplierCredit,
                cashBoxBalance = cashBoxBalance
            )

            // ── حقل إدخال المبلغ ──────────────────────
            VertoOutlinedTextField(
                value         = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label         = { Text(if (isGlobalSupplier) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_d965ac63be2e) else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_6bde36b19201)) },
                leadingIcon   = { Icon(Icons.Filled.Payments, null, tint = AccentBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier      = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentBlue,
                    unfocusedBorderColor = BorderColor
                )
            )

            // ── حقول العملة الأجنبية (للمورد العالمي فقط) ──
            if (isGlobalSupplier) {
                VertoOutlinedTextField(
                    value         = exchangeRateText,
                    onValueChange = { exchangeRateText = it.filter { c -> c.isDigit() || c == '.' } },
                    label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_fd829449a723)) },
                    leadingIcon   = { Icon(Icons.Filled.CurrencyExchange, null, tint = AccentBlue) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentBlue,
                        unfocusedBorderColor = BorderColor
                    )
                )
                if (amount > 0.01 && exchangeRate > 0) {
                    VertoOutlinedTextField(
                        value         = WhatsAppUtils.formatAmount(localAmount),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_3e9efeb9fe17)) },
                        leadingIcon   = { Icon(Icons.Filled.AccountBalance, null, tint = SuccessColor) },
                        modifier      = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = SuccessColor,
                            unfocusedBorderColor = SuccessColor,
                            disabledBorderColor  = SuccessColor
                        )
                    )
                }
            }

            // ── معاينة النتيجة ────────────────────────
            if (amount > 0.01) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PartyDimensions.dp12))
                        .background(BgCard)
                        .border(PartyDimensions.dp1, BorderColor, RoundedCornerShape(PartyDimensions.dp12))
                        .padding(PartyDimensions.dp14),
                    verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_147f4dd714bd), color = AccentBlue,
                        fontSize = PartyTextScale.sp13, fontWeight = FontWeight.Bold)
                    PreviewRow("الديون بعد السداد",
                        WhatsAppUtils.formatAmount(previewDebtAfter),
                        if (previewDebtAfter > 0.01) ErrorColor else SuccessColor)
                    PreviewRow("رصيد الصندوق بعد السداد",
                        WhatsAppUtils.formatAmount(previewCashAfter),
                        if (previewCashAfter >= 0) TextSecondary else ErrorColor)
                    if (previewSurplus > 0.01) {
                        PreviewRow(
                            "رصيد لصالحنا عند المورد",
                            WhatsAppUtils.formatAmount(previewSurplus),
                            SuccessColor
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── زر تأكيد السداد ───────────────────────
            VertoButton(
                onClick  = { if (amount > 0.01) showConfirmDialog = true },
                enabled  = amount > 0.01 && (!isCompetitor || competitorDirection != null) && (!isGlobalSupplier || exchangeRate > 0),
                modifier = Modifier.fillMaxWidth().height(PartyDimensions.dp52),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(PartyDimensions.dp8))
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_11b0cc7ecdb7), fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ── dialog اتجاه السداد (للمنافس فقط) ──────────────
    if (isCompetitor && competitorDirection == null) {
        AlertDialog(
            onDismissRequest = { onBack() },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PartyDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_53383c97802a), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_34c281e54733), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { competitorDirection = true }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_9fccc80f9668), color = SuccessColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { competitorDirection = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_348ae40b33e4), color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── ديالوج تأكيد السداد ──────────────────────────
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_11b0cc7ecdb7)) },
            text    = {
                if (isGlobalSupplier)
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_0a92bc139f64, WhatsAppUtils.formatAmount(amount), WhatsAppUtils.formatAmount(localAmount)))
                else
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_45b9464a6aff, WhatsAppUtils.formatAmount(amount)))
            },
            confirmButton = {
                TextButton(
                    enabled = !processing,
                    onClick = {
                        if (isCompetitor) vm.payCompetitor(amount, competitorDirection == true)
                        else              vm.pay(amount, localAmount)
                        showConfirmDialog = false
                    }
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }

    // ── نتيجة السداد ─────────────────────────────────
    paymentResult?.let { result ->
        AlertDialog(
            onDismissRequest = { vm.clearResult(); onBack() },
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_f984bcbf2b80), color = SuccessColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)) {
                    ResultRow(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_6bde36b19201),   WhatsAppUtils.formatAmount(result.paidAmount), AccentBlue)
                    ResultRow(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_c7acfb3fe862),  WhatsAppUtils.formatAmount(result.debtAfter),
                        if (result.debtAfter > 0.01) ErrorColor else SuccessColor)
                    ResultRow(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_2867ca0edb8e),     WhatsAppUtils.formatAmount(result.cashBoxAfter),
                        if (result.cashBoxAfter >= 0) TextSecondary else ErrorColor)
                    if (result.surplusCredit > 0.01) {
                        ResultRow(
                            androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_6452be84295b),
                            WhatsAppUtils.formatAmount(result.surplusCredit),
                            SuccessColor
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_87555b034843),
                            color = SuccessColor, fontSize = PartyTextScale.sp11
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearResult(); onBack() }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentBlue)
                }
            }
        )
    }

    // ── خطأ السداد ───────────────────────────────────
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PartyDimensions.dp20),
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_78698483b2d9), color = ErrorColor, fontWeight = FontWeight.Bold) },
            text  = { Text(msg, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_ok), color = AccentBlue) }
            }
        )
    }
}

private fun PartyInvoiceSummary.toTarget() = PartyPaymentTarget(
    invoiceId     = invoice.id,
    invoiceNumber = invoice.invoiceNumber,
    remaining     = remaining
)

// ─────────────────────────────────────────────────────
// Composables مساعدة
// ─────────────────────────────────────────────────────
@Composable
private fun SummaryCard(
    totalDebt: Double,
    supplierCredit: Double,
    cashBoxBalance: Double
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp12))
            .background(BgCard)
            .border(PartyDimensions.dp1, BorderColor, RoundedCornerShape(PartyDimensions.dp12))
            .padding(PartyDimensions.dp14),
        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
    ) {
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_d977a7bb758d), color = TextMuted, fontSize = PartyTextScale.sp12)
        if (totalDebt > 0.01) {
            PreviewRow("إجمالي الديون للمورد",
                WhatsAppUtils.formatAmount(totalDebt), ErrorColor)
        }
        if (supplierCredit > 0.01) {
            PreviewRow("رصيد لصالحنا عند المورد",
                WhatsAppUtils.formatAmount(supplierCredit), SuccessColor)
        }
        PreviewRow("رصيد الصندوق",
            WhatsAppUtils.formatAmount(cashBoxBalance),
            if (cashBoxBalance >= 0) TextSecondary else ErrorColor)
    }
}

@Composable
private fun PreviewRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = PartyTextScale.sp12)
        Text(value, color = color, fontSize = PartyTextScale.sp12, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = PartyTextScale.sp13)
        Text(value, color = color, fontSize = PartyTextScale.sp13, fontWeight = FontWeight.Bold)
    }
}
