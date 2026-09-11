package com.verto.app.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.dashboard.application.CommissionEligibilityItem
import com.verto.app.feature.dashboard.application.DashboardAdminGateway
import com.verto.app.feature.dashboard.application.BenzineWeekPolicy
import com.verto.app.utils.ErrorHumanizer
import com.verto.app.utils.MoneyMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** صفّ مسوّق ضمن لوحة الصدارة الأسبوعية. */
data class WeeklyLeaderRow(
    val clientId: String,
    val name: String,
    val commission: Double,
    val invoices: Int
)

data class LeaderboardUiState(
    val weeks: List<Long> = emptyList(),        // بدايات الأسابيع (millis) تنازلياً — الحالي أولاً
    val selectedWeek: Long? = null,
    val rows: List<WeeklyLeaderRow> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val error: String? = null
) {
    // بند 7: الترتيب حسب العمولة الأسبوعية — نفس معيار المسابقة الأسبوعية في أوتودرايف
    // دون الاعتماد على endpoint أسبوعي عام.
    val ranked: List<WeeklyLeaderRow> get() = rows.sortedByDescending { it.commission }
    fun weekLabel(week: Long?): String =
        week?.let {
            SimpleDateFormat("dd/MM", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Riyadh") }
                .format(java.util.Date(it))
        } ?: "—"
}

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val dashboardAdminGateway: DashboardAdminGateway
) : ViewModel() {

    val role: StateFlow<String?> = dashboardAdminGateway.role

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private var allRows: List<CommissionEligibilityItem> = emptyList()
    private var nameByClient: Map<String, String> = emptyMap()

    init { load() }

    fun setWeek(week: Long) = _uiState.update { it.copy(selectedWeek = week, rows = buildRows(week)) }

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = !it.hasLoadedOnce, isRefreshing = it.hasLoadedOnce) }
        fetch()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetch()
    }

    private fun fetch() = viewModelScope.launch {
        // أسماء المسوّقين من get_marketer_stats (مُحصَّن admin) — clientId → الاسم
        dashboardAdminGateway.getMarketerStats().onSuccess { stats ->
            nameByClient = stats.associate { it.clientId to it.fullName }
        }
        dashboardAdminGateway.getCommissionEligibility()
            .onSuccess { rows ->
                allRows = rows
                // بند 7: التجميع حسب أسبوع الفاتورة الفعلي (من created_at) لا حسب
                //   week_start (وهو ثابت = الأسبوع الحالي لكل الصفوف، فيُظهر كل الفترات).
                //   نضمّن الأسبوع الحالي دائماً وإن لم تكن له فواتير بعد.
                val current = BenzineWeekPolicy.currentWeekStart(System.currentTimeMillis())
                val weeks = (rows
                    .filter { it.invoiceStatus == "CLOSED_CASH" || it.invoiceStatus == "CLOSED_CREDIT" }
                    .mapNotNull { BenzineWeekPolicy.parseIsoMillis(it.createdAt)?.let(BenzineWeekPolicy::weekStartOf) } + current)
                    .distinct().sortedDescending()
                _uiState.update {
                    it.copy(
                        weeks = weeks,
                        selectedWeek = current,
                        rows = buildRows(current),
                        isLoading = false, isRefreshing = false, hasLoadedOnce = true, error = null
                    )
                }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false, isRefreshing = false,
                        error = if (it.hasLoadedOnce) null else ErrorHumanizer.humanize(e, "تحميل البيانات")
                    )
                }
            }
    }

    private fun buildRows(week: Long?): List<WeeklyLeaderRow> {
        if (week == null) return emptyList()
        return allRows
            // مطابقة منطق المسابقة: الفواتير المغلقة فقط (CLOSED_CASH/CLOSED_CREDIT)
            .filter { it.invoiceStatus == "CLOSED_CASH" || it.invoiceStatus == "CLOSED_CREDIT" }
            .filter { BenzineWeekPolicy.parseIsoMillis(it.createdAt)?.let(BenzineWeekPolicy::weekStartOf) == week }
            .groupBy { it.clientId }
            .map { (clientId, rws) ->
                WeeklyLeaderRow(
                    clientId   = clientId,
                    name       = nameByClient[clientId] ?: "مسوّق",
                    commission = with(MoneyMath) { rws.map { it.commission }.moneySum() },
                    invoices   = rws.size
                )
            }
    }

}
