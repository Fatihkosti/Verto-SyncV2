package com.verto.app.feature.party.presentation.supplier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.application.PartyDashboardMetricsCalculator
import com.verto.app.feature.party.application.intelligence.SupplierPerformanceSnapshot
import com.verto.app.feature.party.domain.ledger.LedgerEventType
import com.verto.app.feature.party.domain.model.PartyInvoiceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal fun todayRange(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    return start to (start + 86_400_000L - 1L)
}

/** Presentation orchestration only; Party dashboard money math is application-owned. */
@HiltViewModel
class SupplierDashboardViewModel @Inject constructor(
    private val partyService: PartyApplicationService,
) : ViewModel() {

    private val _supplierId = MutableStateFlow("")
    private val _filterFrom = MutableStateFlow(todayRange().first)
    private val _filterTo = MutableStateFlow(todayRange().second)

    val permissions = partyService.permissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val supplier = _supplierId
        .flatMapLatest { partyService.getClientById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal val intelligence: StateFlow<SupplierPerformanceSnapshot?> = _supplierId
        .flatMapLatest { id ->
            if (id.isBlank()) flowOf(null)
            else partyService.observeSupplierPerformance(id).map<SupplierPerformanceSnapshot, SupplierPerformanceSnapshot?> { it }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val filterFrom: StateFlow<Long> = _filterFrom
    val filterTo: StateFlow<Long> = _filterTo

    fun setDateRange(from: Long, to: Long) {
        _filterFrom.value = from
        _filterTo.value = to
    }

    fun clearDateRange() {
        val (from, to) = todayRange()
        _filterFrom.value = from
        _filterTo.value = to
    }

    val isDateFiltered: StateFlow<Boolean> = _filterFrom
        .map { it > 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val invoiceSummaries = combine(_supplierId, _filterFrom, _filterTo) { id, from, to -> Triple(id, from, to) }
        .flatMapLatest { (id, from, to) ->
            partyService.getInvoiceSummariesForClientInRange(id, from, to).map { rows ->
                rows.filter { it.invoice.category == PartyInvoiceCategory.PURCHASE }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoiceSummaries = _supplierId.flatMapLatest { id ->
        partyService.getInvoiceSummariesForClient(id).map { rows ->
            rows.filter { it.invoice.category == PartyInvoiceCategory.PURCHASE }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val completeLedger = _supplierId.flatMapLatest { id -> partyService.observeSupplierLedger(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val periodLedger = combine(_supplierId, _filterFrom, _filterTo) { id, from, to -> Triple(id, from, to) }
        .flatMapLatest { (id, from, to) ->
            partyService.observeSupplierLedger(id, from, if (to == Long.MAX_VALUE) to else to + 1)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val completeLedgerMetrics = completeLedger
        .map(PartyDashboardMetricsCalculator::ledger)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PartyDashboardMetricsCalculator.ledger(null),
        )

    private val periodLedgerMetrics = periodLedger
        .map(PartyDashboardMetricsCalculator::ledger)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PartyDashboardMetricsCalculator.ledger(null),
        )

    internal val balanceAmounts = completeLedgerMetrics.map { it.closingByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val balanceDirection = completeLedgerMetrics.map { it.direction }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PartyDashboardMetricsCalculator.ledger(null).direction)

    internal val newDebtInPeriodByCurrency = periodLedgerMetrics.map { it.invoicedByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val collectedInPeriodByCurrency = periodLedgerMetrics.map { it.paidByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val periodCommercialMetrics = invoiceSummaries
        .map(PartyDashboardMetricsCalculator::supplierCommercial)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PartyDashboardMetricsCalculator.supplierCommercial(emptyList()),
        )

    internal val totalPurchasesByCurrency = periodCommercialMetrics.map { it.turnoverByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoiceCount = periodLedger
        .map { ledger ->
            ledger?.currencies?.sumOf { currency ->
                currency.rows.count { it.event.type == LedgerEventType.INVOICE }
            } ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastActivity = completeLedger
        .map { ledger -> ledger?.currencies?.flatMap { it.rows }?.maxOfOrNull { it.event.occurredAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun init(id: String) {
        _supplierId.value = id
    }
}
