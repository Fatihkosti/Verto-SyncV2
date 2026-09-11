package com.verto.app.feature.party.presentation.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.application.PartyDashboardMetricsCalculator
import com.verto.app.feature.party.application.intelligence.CustomerDecisionSnapshot
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

/**
 * Dashboard orchestration only. Monetary/business calculations live in
 * [PartyDashboardMetricsCalculator], so this ViewModel never adds currencies or performs profit math.
 */
@HiltViewModel
class ClientDashboardViewModel @Inject constructor(
    private val partyService: PartyApplicationService,
) : ViewModel() {

    private val _clientId = MutableStateFlow("")
    private val _filterFrom = MutableStateFlow(todayRange().first)
    private val _filterTo = MutableStateFlow(todayRange().second)

    val permissions = partyService.permissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val client = _clientId
        .flatMapLatest { partyService.getClientById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customerProfile = _clientId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(null) else partyService.observeCustomerProfile(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal val decision: StateFlow<CustomerDecisionSnapshot?> = _clientId
        .flatMapLatest { id ->
            if (id.isBlank()) flowOf(null)
            else partyService.observeCustomerDecision(id).map<CustomerDecisionSnapshot, CustomerDecisionSnapshot?> { it }
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

    val allInvoiceSummaries = _clientId.flatMapLatest { id ->
        partyService.getInvoiceSummariesForClient(id).map { rows ->
            rows.filter { it.invoice.category == PartyInvoiceCategory.SALE }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoiceSummaries = combine(_clientId, _filterFrom, _filterTo) { id, from, to -> Triple(id, from, to) }
        .flatMapLatest { (id, from, to) ->
            partyService.getInvoiceSummariesForClientInRange(id, from, to).map { rows ->
                rows.filter { it.invoice.category == PartyInvoiceCategory.SALE }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoiceSummariesForDisplay = combine(allInvoiceSummaries, _filterFrom, _filterTo) { all, from, to ->
        all.filter { summary -> summary.remaining > 0 || summary.invoice.createdAt in from..to }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allItems = _clientId.flatMapLatest { id -> partyService.getItemsForClient(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val completeLedger = _clientId.flatMapLatest { id -> partyService.observeCustomerLedger(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val periodLedger = combine(_clientId, _filterFrom, _filterTo) { id, from, to -> Triple(id, from, to) }
        .flatMapLatest { (id, from, to) ->
            partyService.observeCustomerLedger(id, from, if (to == Long.MAX_VALUE) to else to + 1)
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

    private val allCommercialMetrics = combine(allInvoiceSummaries, allItems) { summaries, items ->
        PartyDashboardMetricsCalculator.customerCommercial(summaries, items)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PartyDashboardMetricsCalculator.customerCommercial(emptyList(), emptyList()),
    )

    private val periodCommercialMetrics = combine(invoiceSummaries, allItems) { summaries, items ->
        PartyDashboardMetricsCalculator.customerCommercial(summaries, items)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PartyDashboardMetricsCalculator.customerCommercial(emptyList(), emptyList()),
    )

    internal val totalCommissionsByCurrency = allCommercialMetrics.map { it.commissionsByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val totalSalesByCurrency = periodCommercialMetrics.map { it.turnoverByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val totalProfitByCurrency = periodCommercialMetrics.map { it.profitByCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profitComplete = periodCommercialMetrics.map { it.profitComplete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    internal val commissionsInPeriodByCurrency = periodCommercialMetrics.map { it.commissionsByCurrency }
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
        _clientId.value = id
    }
}
