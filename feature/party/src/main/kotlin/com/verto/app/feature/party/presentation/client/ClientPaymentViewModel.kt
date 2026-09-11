package com.verto.app.feature.party.presentation.client

import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClientPaymentViewModel @Inject constructor(
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

    private val _clientId = MutableStateFlow("")

    val client = _clientId
        .flatMapLatest { partyService.getClientById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isCompetitor = _clientId
        .flatMapLatest { id -> partyService.observeIsCompetitor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val creditInvoices = _clientId.flatMapLatest { id ->
        partyService.getInvoiceSummariesForClient(id).map { summaries ->
            summaries
                .filter { it.invoice.category == PartyInvoiceCategory.SALE && it.invoice.status == PartyInvoiceStatus.CLOSED_CREDIT && it.remaining > 0.01 }
                .sortedBy { it.invoice.createdAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // صافي الرصيد المقدَّم: موجب = لصالحنا، سالب = نحن مدينون للعميل (رصيد العميل).
    private val netCredit = _clientId
        .flatMapLatest { id -> partyService.getNetCreditForClient(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // رصيد العميل لدينا (نحن مدينون له) = الجزء السالب من صافي الرصيد.
    val clientCredit = netCredit
        .map { maxOf(-it, 0.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val rawDebt = creditInvoices.map { list -> list.sumOf { it.remaining } }

    // الدين المعروض بعد طرح رصيد العميل المقدَّم.
    val totalDebt = combine(rawDebt, netCredit) { debt, credit -> maxOf(debt + credit, 0.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _result = MutableStateFlow<ClientPayResult?>(null)
    val result: StateFlow<ClientPayResult?> = _result

    // منع الضغط المزدوج أثناء تنفيذ السداد
    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    fun init(id: String) { _clientId.value = id }

    /**
     * Session 4: السداد الجماعي يمر عبر [PartyApplicationService.allocatePayment] — Transaction واحدة
     * (كل الدفعات + الصندوق + الفائض ذرّياً، أو لا شيء). الفائض يُسجَّل رصيداً مقدَّماً.
     */
    fun pay(amount: Double, selectedInvoiceId: String?) = runAllocation { requestId ->
        val clientId   = _clientId.value
        val clientName = client.value?.name ?: ""
        val invoices   = creditInvoices.value
        val selected   = if (selectedInvoiceId != null)
            invoices.filter { it.invoice.id == selectedInvoiceId } else invoices

        partyService.allocatePayment(
            clientId   = clientId,
            clientName = clientName,
            amount     = amount,
            targets    = selected.map { it.toTarget() },
            note       = "سداد عميل",
            moneyIn    = true,           // تحصيل من العميل = نقد يدخل الصندوق
            requestId  = requestId
        )
    }

    // ── سداد المنافس باتجاه محدد ──────────────────────
    fun payCompetitor(amount: Double, fromCompetitor: Boolean) = runAllocation { requestId ->
        val clientId   = _clientId.value
        val clientName = client.value?.name ?: ""
        // fromCompetitor = المنافس دفع لنا (تحصيل على فواتير المبيعات) = نقد يدخل.
        val invoices = creditInvoices.value.filter { s ->
            if (fromCompetitor) s.invoice.category == PartyInvoiceCategory.SALE
            else                s.invoice.category == PartyInvoiceCategory.PURCHASE
        }
        partyService.allocatePayment(
            clientId   = clientId,
            clientName = clientName,
            amount     = amount,
            targets    = invoices.map { it.toTarget() },
            note       = if (fromCompetitor) "سداد من المنافس" else "سداد للمنافس",
            moneyIn    = fromCompetitor,
            requestId  = requestId
        )
    }

    fun clearResult() { _result.value = null }

    /** يلفّ التوزيع بحماية الضغط المزدوج ويبني نتيجة العرض. */
    private fun runAllocation(block: suspend (requestId: String) -> PartyPaymentAllocationResult) {
        if (_processing.value) return
        _processing.value = true
        viewModelScope.launch {
            try {
                val debtBefore = totalDebt.value
                val requestId = pendingBulkPaymentRequestId()
                when (val res = block(requestId)) {
                    is PartyPaymentAllocationResult.Success -> {
                        savedStateHandle.remove<String>(PENDING_BULK_PAYMENT_REQUEST_ID)
                        _result.value = ClientPayResult(
                            paidAmount = res.applied + res.surplusCredit,
                            debtBefore = debtBefore,
                            debtAfter  = maxOf(debtBefore - res.applied, 0.0),
                            surplusCredit = res.surplusCredit
                        )
                    }
                    is PartyPaymentAllocationResult.Error ->
                        _error.value = res.message
                }
            } finally {
                _processing.value = false
            }
        }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun clearError() { _error.value = null }
}

private fun PartyInvoiceSummary.toTarget() = PartyPaymentTarget(
    invoiceId     = invoice.id,
    invoiceNumber = invoice.invoiceNumber,
    remaining     = remaining
)

data class ClientPayResult(
    val paidAmount: Double,
    val debtBefore: Double,
    val debtAfter : Double,
    val surplusCredit: Double = 0.0
)

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
