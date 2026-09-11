package com.verto.app.feature.payment.presentation.payment

import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.payment.application.model.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.feature.payment.domain.model.PaymentMethod
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import com.verto.feature.payment.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.verto.app.feature.payment.application.AddPaymentUseCase
import com.verto.app.feature.payment.application.ObservePaymentInvoiceSummaryUseCase

@HiltViewModel
class AddPaymentViewModel @Inject constructor(
    private val observeInvoiceSummary: ObservePaymentInvoiceSummaryUseCase,
    private val addPayment: AddPaymentUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private companion object { const val PENDING_PAYMENT_REQUEST_ID = "pending_payment_request_id" }

    private fun pendingRequestId(): String =
        savedStateHandle.get<String>(PENDING_PAYMENT_REQUEST_ID)
            ?: java.util.UUID.randomUUID().toString().also { savedStateHandle[PENDING_PAYMENT_REQUEST_ID] = it }

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private val _error = MutableStateFlow<UserErrorPresentation?>(null)
    val error = _error.asStateFlow()

    private val _invoiceSummary = MutableStateFlow<PaymentInvoiceSummaryViewData?>(null)
    val invoiceSummary = _invoiceSummary.asStateFlow()

    fun loadInvoice(id: String) = viewModelScope.launch {
        observeInvoiceSummary(id).collect { _invoiceSummary.value = it }
    }

    fun savePayment(
        invoiceId: String,
        clientId: String,
        amount: Double,
        method: PaymentMethod,
        note: String,
        paidAt: Long,
        paymentExchangeRate: Double? = null,
    ) = viewModelScope.launch {
        val s = _invoiceSummary.value
        val result = addPayment(
            invoiceId = invoiceId,
            clientId = clientId,
            amount = amount,
            paymentMethod = method,
            note = note,
            remainingAmount = s?.remaining,
            clientName = s?.invoice?.description ?: "",
            invoiceNumber = s?.invoice?.invoiceNumber ?: 0,
            paidAt = paidAt,
            paymentCurrencyCode = s?.invoice?.transactionCurrencyCode.orEmpty(),
            paymentExchangeRate = paymentExchangeRate,
            exchangeRateTimestamp = paidAt,
            exchangeRateSource = "USER_INPUT",
            requestId = pendingRequestId(),
        )
        when (result) {
            is AddPaymentUseCase.Result.Success -> {
                savedStateHandle.remove<String>(PENDING_PAYMENT_REQUEST_ID)
                _error.value = null
                _saved.value = true
            }
            is AddPaymentUseCase.Result.Error -> _error.value = result.presentation
        }
    }

    fun clearError() { _error.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentScreen(
    invoiceId: String,
    clientId: String,
    prefillFullPayment: Boolean = false,
    onBack: () -> Unit,
    vm: AddPaymentViewModel = hiltViewModel()
) {
    LaunchedEffect(invoiceId) { vm.loadInvoice(invoiceId) }

    val summary by vm.invoiceSummary.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var amount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var paymentRate by remember { mutableStateOf("") }
    var paymentRateError by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    var paidAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var fullPaymentPrefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(prefillFullPayment, summary?.remaining) {
        val remaining = summary?.remaining ?: return@LaunchedEffect
        if (prefillFullPayment && !fullPaymentPrefilled && remaining > 0.0) {
            amount = remaining.toBigDecimal().stripTrailingZeros().toPlainString()
            fullPaymentPrefilled = true
        }
    }

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    Scaffold(containerColor = BgDeep) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            VertoTopBar(
                title = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a8436ce4b250),
                onBack = onBack,
            )

            Column(
                Modifier.padding(horizontal = PaymentDimensions.dp20),
                verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp18),
            ) {
                error?.takeIf { it.target != "amount" && it.target != "exchangeRate" }?.let {
                    VertoUserError(
                        error = it,
                        modifier = Modifier.fillMaxWidth(),
                        onEdit = vm::clearError,
                    )
                }

                summary?.let { s ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PaymentDimensions.dp16))
                            .background(BgCard)
                            .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp16))
                            .padding(PaymentDimensions.dp16)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                            Text(
                                stringResource(R.string.payment_invoice_title, s.invoice.invoiceNumber),
                                color = TextMuted,
                                fontSize = PaymentTextScale.sp12,
                            )
                            Text(
                                s.invoice.description,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = PaymentTextScale.sp16,
                            )
                            Divider(color = BorderColor)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_total), color = TextMuted, fontSize = PaymentTextScale.sp11)
                                    Text(
                                        stringResource(
                                            R.string.payment_amount_with_currency,
                                            WhatsAppUtils.formatAmount(s.invoice.totalAmount),
                                            s.invoice.transactionCurrencyCode.ifBlank {
                                                stringResource(R.string.payment_currency_short_default)
                                            },
                                        ),
                                        color = GoldPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_85c5c53e9b00), color = TextMuted, fontSize = PaymentTextScale.sp11)
                                    Text(
                                        stringResource(
                                            R.string.payment_amount_with_currency,
                                            WhatsAppUtils.formatAmount(s.totalPaid),
                                            s.invoice.transactionCurrencyCode.ifBlank {
                                                stringResource(R.string.payment_currency_short_default)
                                            },
                                        ),
                                        color = SuccessColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_557f737dff23), color = TextMuted, fontSize = PaymentTextScale.sp11)
                                    Text(
                                        stringResource(
                                            R.string.payment_amount_with_currency,
                                            WhatsAppUtils.formatAmount(s.remaining),
                                            s.invoice.transactionCurrencyCode.ifBlank {
                                                stringResource(R.string.payment_currency_short_default)
                                            },
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            val paymentProgress = if (s.invoice.totalAmount > 0.0) {
                                (s.totalPaid / s.invoice.totalAmount).toFloat()
                            } else {
                                0f
                            }
                            VertoLinearValueProgress(progress = paymentProgress)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_6cf184df8a3d), color = TextSecondary, fontSize = PaymentTextScale.sp13, fontWeight = FontWeight.SemiBold)
                    VertoOutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().height(PaymentDimensions.dp56),
                        shape = RoundedCornerShape(PaymentDimensions.dp12),
                        border = BorderStroke(PaymentDimensions.dp1, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BgCard),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(DateUtils.formatDate(paidAt), color = TextPrimary, fontSize = PaymentTextScale.sp14)
                            Icon(Icons.Filled.CalendarToday, null, tint = AccentPrimary)
                        }
                    }
                }

                VertoTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountError = false
                        if (error?.target == "amount") vm.clearError()
                    },
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_6bde36b19201),
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_22cf82b68b95),
                    keyboardType = KeyboardType.Decimal,
                    trailingText = summary?.invoice?.transactionCurrencyCode?.ifBlank { "جنيه" } ?: "جنيه",
                    isRequired = true,
                    isError = amountError || error?.target == "amount",
                    leadingIcon = { Icon(Icons.Filled.Payments, null, tint = TextMuted) },
                )
                error?.takeIf { it.target == "amount" }?.let {
                    VertoUserError(
                        error = it,
                        modifier = Modifier.fillMaxWidth(),
                        onEdit = vm::clearError,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                    Text("طريقة الدفع", color = TextSecondary, fontSize = PaymentTextScale.sp13, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                        listOf(
                            PaymentMethod.CASH to "نقد",
                            PaymentMethod.TRANSFER to "تحويل",
                            PaymentMethod.CHECK to "شيك",
                        ).forEach { (method, label) ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                summary?.let { s ->
                    if (s.remaining > 0) {
                        TextButton(
                            onClick = { amount = WhatsAppUtils.formatAmount(s.remaining) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.payment_pay_full_amount,
                                    WhatsAppUtils.formatAmount(s.remaining),
                                    s.invoice.transactionCurrencyCode.ifBlank {
                                        stringResource(R.string.payment_currency_default)
                                    },
                                ),
                                color = AccentPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                val currencyInvoice = summary?.invoice
                val isKnownForeign = currencyInvoice != null &&
                    currencyInvoice.legacyCurrencyStatus == "KNOWN" &&
                    currencyInvoice.transactionCurrencyCode.isNotBlank() &&
                    currencyInvoice.functionalCurrencyCode.isNotBlank() &&
                    currencyInvoice.transactionCurrencyCode != currencyInvoice.functionalCurrencyCode
                if (isKnownForeign) {
                    VertoTextField(
                        value = paymentRate,
                        onValueChange = {
                            paymentRate = it
                            paymentRateError = false
                            if (error?.target == "exchangeRate") vm.clearError()
                        },
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_b4c88c744018),
                        placeholder = currencyInvoice?.invoiceExchangeRateSnapshot.orEmpty(),
                        keyboardType = KeyboardType.Decimal,
                        trailingText = "${currencyInvoice?.functionalCurrencyCode} / ${currencyInvoice?.transactionCurrencyCode}",
                        isRequired = true,
                        isError = paymentRateError || error?.target == "exchangeRate",
                    )
                    error?.takeIf { it.target == "exchangeRate" }?.let {
                        VertoUserError(
                            error = it,
                            modifier = Modifier.fillMaxWidth(),
                            onEdit = vm::clearError,
                        )
                    }
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.verto.feature.payment.R.string.payment_ds_5e1bf3bddf74,
                            currencyInvoice?.invoiceExchangeRateSnapshot.orEmpty(),
                            currencyInvoice?.functionalCurrencyCode.orEmpty(),
                            currencyInvoice?.transactionCurrencyCode.orEmpty(),
                        ),
                        color = TextMuted,
                        fontSize = PaymentTextScale.sp11,
                    )
                }

                VertoTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_note),
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_aebb4ccfcadd),
                )

                Spacer(Modifier.height(PaymentDimensions.dp8))
                VertoPrimaryButton(
                    text = "تسجيل السداد",
                    onClick = {
                        val amt = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val rate = paymentRate.replace(",", ".").toDoubleOrNull()
                        amountError = amt <= 0
                        paymentRateError = isKnownForeign && (rate == null || rate <= 0.0)
                        if (!amountError && !paymentRateError) {
                            vm.savePayment(invoiceId, clientId, amt, paymentMethod, note, paidAt, rate)
                        }
                    },
                )
                Spacer(Modifier.height(PaymentDimensions.dp32))
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = paidAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { paidAt = it }
                    showDatePicker = false
                }) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm),
                        color = AccentPrimary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel),
                        color = TextSecondary,
                    )
                }
            },
        ) { DatePicker(state = dateState) }
    }
}
