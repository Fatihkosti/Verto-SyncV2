package com.verto.app.feature.party.presentation.supplier

import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

data class StatementRow(
    val date       : Long,
    val description: String,
    val debit      : Double,   // مدين  — مشتريات / ديون
    val credit     : Double,   // دائن  — مدفوعات / سداد
    val balance    : Double    // الرصيد بعد العملية (سالب = لصالح المورد)
)

// ─────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────
@HiltViewModel
class SupplierStatementViewModel @Inject constructor(
    private val partyService: PartyApplicationService
) : ViewModel() {

    private val _supplierId = MutableStateFlow("")
    private val _filterFrom = MutableStateFlow(run {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    })
    private val _filterTo = MutableStateFlow(run {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        cal.timeInMillis
    })

    val supplier    = _supplierId.flatMapLatest { partyService.getClientById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterFrom: StateFlow<Long> = _filterFrom
    val filterTo:   StateFlow<Long> = _filterTo

    fun setDateRange(from: Long, to: Long) { _filterFrom.value = from; _filterTo.value = to }

    // ── كل العمليات مرتبة زمنياً ─────────────────────
    private val _allData = combine(_supplierId, _filterFrom, _filterTo) { id, from, to -> Triple(id, from, to) }
        .flatMapLatest { (id, from, to) -> partyService.observeSupplierLedger(id, from, if (to == Long.MAX_VALUE) to else to + 1) }
        .map { ledger ->
            val opening = ledger.currencies.singleOrNull()?.opening?.toLegacyDouble() ?: 0.0
            val rows = ledger.currencies.flatMap { currency ->
                currency.rows.map { row ->
                    val amount = row.event.delta.toLegacyDouble()
                    StatementRow(
                        date = row.event.occurredAt,
                        description = "[${currency.currencyCode}] ${row.event.description.ifBlank { row.event.type.name }}",
                        debit = amount.coerceAtLeast(0.0),
                        credit = (-amount).coerceAtLeast(0.0),
                        balance = row.runningBalance.toLegacyDouble(),
                    )
                }
            }.sortedWith(compareBy<StatementRow> { it.date }.thenBy { it.description })
            Pair(opening, rows)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0.0, emptyList()))

    val openingBalance: StateFlow<Double> = _allData
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val statementRows: StateFlow<List<StatementRow>> = _allData
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(id: String) { _supplierId.value = id }

    // ── مقاصة المنافس: بنود + دفعيات (لي / علي) ──────
    private suspend fun buildCompetitorData(
        allSummaries: List<PartyInvoiceSummary>,
        from: Long,
        to: Long
    ): Pair<Double, List<StatementRow>> {
        var opening = 0.0
        for (s in allSummaries) {
            val isSale = s.invoice.category == PartyInvoiceCategory.SALE
            if (s.invoice.createdAt < from) {
                val total = partyService.getInvoiceItemsSync(s.invoice.id).sumOf { it.totalPrice }
                opening += if (isSale) total else -total
            }
            for (p in s.payments.filter { it.paidAt < from }) {
                opening += if (isSale) -p.amount else p.amount
            }
        }
        var balance = opening
        val rows = mutableListOf<StatementRow>()
        data class Ev(val date: Long, val isItem: Boolean, val isSale: Boolean, val amount: Double, val desc: String)
        val events = mutableListOf<Ev>()
        for (s in allSummaries) {
            val isSale = s.invoice.category == PartyInvoiceCategory.SALE
            if (s.invoice.createdAt in from..to) {
                for (item in partyService.getInvoiceItemsSync(s.invoice.id))
                    events.add(Ev(s.invoice.createdAt, true, isSale, item.totalPrice, "${item.itemName} ×${item.quantity}"))
            }
            for (p in s.payments.filter { it.paidAt in from..to })
                events.add(Ev(p.paidAt, false, isSale, p.amount, if (isSale) "سداد من المنافس" else "سداد للمنافس"))
        }
        events.sortBy { it.date }
        for (ev in events) {
            balance += if (ev.isItem) (if (ev.isSale) ev.amount else -ev.amount)
                       else          (if (ev.isSale) -ev.amount else ev.amount)
            rows.add(StatementRow(
                date        = ev.date,
                description = ev.desc,
                debit       = if (ev.isItem && ev.isSale || !ev.isItem && !ev.isSale) ev.amount else 0.0,
                credit      = if (ev.isItem && !ev.isSale || !ev.isItem && ev.isSale) ev.amount else 0.0,
                balance     = balance
            ))
        }
        return Pair(opening, rows)
    }

    // ── بناء السطور ───────────────────────────────────
    private fun buildStatementRows(
        // الحسابات تستخدم نماذج Party الخالصة.
        summaries: List<PartyInvoiceSummary>,
        openingBalance: Double
    ): List<StatementRow> {
        val rows = mutableListOf<StatementRow>()
        var runningBalance = openingBalance

        data class RawEntry(
            val date      : Long,
            val isInvoice : Boolean,
            val invoice   : PartyInvoice? = null,
            val payment   : PartyPayment? = null
        )

        val entries = mutableListOf<RawEntry>()
        for (s in summaries) {
            // ✅ كان: s.invoice — صح
            entries.add(RawEntry(s.invoice.createdAt, true, invoice = s.invoice))
            // ✅ كان: s.payments — صح (موجود في PartyInvoiceSummary)
            for (p in s.payments) {
                entries.add(RawEntry(p.paidAt, false, payment = p))
            }
        }
        entries.sortBy { it.date }

        for (entry in entries) {
            if (entry.isInvoice) {
                val inv    = checkNotNull(entry.invoice)
                val amount = inv.totalAmount
                when {
                    // ✅ كان: inv.paymentType == PaymentType.CASH — غير موجود
                    // الفاتورة النقدية = CLOSED_CASH
                    inv.status == PartyInvoiceStatus.CLOSED_CASH -> {
                        runningBalance += amount
                        rows.add(StatementRow(
                            date        = inv.createdAt,
                            // ✅ كان: inv.number → الاسم الصحيح: inv.invoiceNumber
                            description = "فاتورة نقدية #${inv.invoiceNumber}",
                            debit       = amount,
                            credit      = 0.0,
                            balance     = runningBalance
                        ))
                        runningBalance -= amount
                        rows.add(StatementRow(
                            date        = inv.createdAt,
                            description = "سداد نقدي #${inv.invoiceNumber}",
                            debit       = 0.0,
                            credit      = amount,
                            balance     = runningBalance
                        ))
                    }
                    // الفاتورة الآجلة = CLOSED_CREDIT
                    else -> {
                        runningBalance += amount
                        rows.add(StatementRow(
                            date        = inv.createdAt,
                            description = "فاتورة آجلة #${inv.invoiceNumber}",
                            debit       = amount,
                            credit      = 0.0,
                            balance     = runningBalance
                        ))
                    }
                }
            } else {
                val pay = checkNotNull(entry.payment)
                runningBalance -= pay.amount
                rows.add(StatementRow(
                    date        = pay.paidAt,
                    description = "سداد",
                    debit       = 0.0,
                    credit      = pay.amount,
                    balance     = runningBalance
                ))
            }
        }
        return rows
    }
}

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
