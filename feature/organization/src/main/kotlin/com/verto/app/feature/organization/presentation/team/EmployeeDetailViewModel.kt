package com.verto.app.feature.organization.presentation.team

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.organization.domain.model.OrganizationEmployee
import com.verto.app.feature.organization.application.GetEmployeePerformanceUseCase
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EmployeeDetailEvent : UiEvent {
    data class Load(
        val userId: String,
        val employee: OrganizationEmployee?
    ) : EmployeeDetailEvent

    data class PeriodChanged(val from: Long, val to: Long) : EmployeeDetailEvent
}

@HiltViewModel
class EmployeeDetailViewModel @Inject constructor(
    private val getPerformance: GetEmployeePerformanceUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel(),
    UiStateHolder<EmployeeDetailUiState>,
    UiEventHandler<EmployeeDetailEvent> {
    private val _uiState = MutableStateFlow(EmployeeDetailUiState())
    override val uiState: StateFlow<EmployeeDetailUiState> = _uiState.asStateFlow()

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // الفترة الحالية لبطاقة الأداء (افتراضياً: الشهر الجاري حتى الآن)
    private var fromDate: Long = startOfCurrentMonth()
    private var toDate: Long = System.currentTimeMillis()
    private var currentEmployeeId: String = ""

    override fun onEvent(event: EmployeeDetailEvent) {
        when (event) {
            is EmployeeDetailEvent.Load -> loadInternal(event.userId, event.employee)
            is EmployeeDetailEvent.PeriodChanged -> setPeriodInternal(event.from, event.to)
        }
    }

    fun load(userId: String, employee: OrganizationEmployee?) =
        onEvent(EmployeeDetailEvent.Load(userId, employee))

    fun setPeriod(from: Long, to: Long) =
        onEvent(EmployeeDetailEvent.PeriodChanged(from, to))

    private fun loadInternal(userId: String, employee: OrganizationEmployee?) {
        currentEmployeeId = userId
        // البطاقات الأخرى (الملف/الحضور/الصلاحيات) تبقى كما هي؛ الأداء يُملأ بأرقام حقيقية.
        val base = buildFakeEmployeeDetail(userId = userId, employee = employee, resolveString = context::getString)
        _uiState.value = base.copy(
            performance = SalesPerformance(
                fromDate = dateFmt.format(fromDate),
                toDate = dateFmt.format(toDate)
            ),
            performanceLoading = true,
            performanceError = null,
            periodFrom = fromDate,
            periodTo = toDate
        )
        loadPerformance()
    }

    /**
     * تغيير الفترة الزمنية لبطاقة الأداء من الواجهة.
     * `from` يُضبط على بداية اليوم و`to` على نهايته لتغطية اليوم كاملاً،
     * وتُعكس الحدود تلقائياً إذا اختار المستخدم تاريخاً مقلوباً.
     */
    private fun setPeriodInternal(from: Long, to: Long) {
        val start = startOfDay(minOf(from, to))
        val end = endOfDay(maxOf(from, to))
        fromDate = start
        toDate = end
        _uiState.value = _uiState.value.copy(
            performance = _uiState.value.performance.copy(
                fromDate = dateFmt.format(fromDate),
                toDate = dateFmt.format(toDate)
            ),
            performanceLoading = true,
            performanceError = null,
            periodFrom = fromDate,
            periodTo = toDate
        )
        loadPerformance()
    }

    private fun loadPerformance() = viewModelScope.launch {
        val employeeId = currentEmployeeId
        if (employeeId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                performanceLoading = false,
                performanceError = "تعذّر تحديد الموظف"
            )
            return@launch
        }
        runCatching { getPerformance(employeeId, fromDate, toDate) }
            .onSuccess { perf ->
                _uiState.value = _uiState.value.copy(
                    performance = SalesPerformance(
                        fromDate = dateFmt.format(fromDate),
                        toDate = dateFmt.format(toDate),
                        invoiceCount = perf.invoiceCount,
                        cashInvoices = perf.cashInvoices,
                        creditInvoices = perf.creditInvoices,
                        totalSales = CurrencyFormatter.formatNoSymbol(perf.totalSales),
                        totalProfit = CurrencyFormatter.formatNoSymbol(perf.totalProfit),
                        debts = CurrencyFormatter.formatNoSymbol(perf.debts),
                        collections = CurrencyFormatter.formatNoSymbol(perf.collections),
                        addedClients = perf.addedClients
                    ),
                    performanceLoading = false,
                    performanceError = null
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    performanceLoading = false,
                    performanceError = ErrorHumanizer.humanize(e, "تحميل بيانات الأداء")
                )
            }
    }

    private fun startOfCurrentMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
