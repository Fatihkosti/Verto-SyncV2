package com.verto.app.ui.screens.usersdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.dashboard.application.BenzineFollowUpReason
import com.verto.app.feature.dashboard.application.BenzinePerformanceCalculator
import com.verto.app.feature.dashboard.application.BenzinePerformanceStatus
import com.verto.app.feature.dashboard.application.BenzineUserFilter
import com.verto.app.feature.dashboard.application.BenzineWeeklyPerformance
import com.verto.app.feature.dashboard.application.BenzineWeeklySummary
import com.verto.app.feature.dashboard.application.BenzineWeekPolicy
import com.verto.app.feature.dashboard.application.CommissionEligibilityItem
import com.verto.app.feature.dashboard.application.DashboardAdminGateway
import com.verto.app.feature.dashboard.application.MarketerStatsItem
import com.verto.app.feature.dashboard.application.withdrawableByClient
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class UsersDashboardUiState(
    val stats: List<MarketerStatsItem> = emptyList(),
    val sort: MarketerSort = MarketerSort.PURCHASES,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isStale: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val reminderMessage: String? = null,
    val withdrawableByClient: Map<String, Double> = emptyMap(),
    val eligibilityRows: List<CommissionEligibilityItem> = emptyList(),
    val performanceRows: List<BenzineWeeklyPerformance> = emptyList(),
    val weeklySummary: BenzineWeeklySummary? = null,
    val selectedFilter: BenzineUserFilter = BenzineUserFilter.ALL,
    val isWeeklyDataLoading: Boolean = false,
    val weeklyDataError: String? = null,
    val isWeeklyDataStale: Boolean = false,
) {
    val sorted: List<MarketerStatsItem> get() = MarketerStatsSorter.sort(stats, sort)

    val hasWeeklyData: Boolean get() = weeklySummary != null

    val basicFilteredStats: List<MarketerStatsItem>
        get() = when (selectedFilter) {
            BenzineUserFilter.MARKETERS -> stats.filter { it.accountType == "MARKETER" }
            BenzineUserFilter.WORKSHOPS -> stats.filter { it.accountType == "WORKSHOP_OWNER" }
            else -> stats
        }

    val filteredPerformanceRows: List<BenzineWeeklyPerformance>
        get() = when (selectedFilter) {
            BenzineUserFilter.ALL -> performanceRows
            BenzineUserFilter.MARKETERS -> performanceRows.filter { it.accountType == "MARKETER" }
            BenzineUserFilter.WORKSHOPS -> performanceRows.filter { it.accountType == "WORKSHOP_OWNER" }
            BenzineUserFilter.ACTIVE_THIS_WEEK -> performanceRows.filter { it.currentWeekInvoices > 0 }
            BenzineUserFilter.NEW -> performanceRows.filter { it.status == BenzinePerformanceStatus.NEW }
            BenzineUserFilter.NEEDS_FOLLOW_UP -> performanceRows.filter { it.followUpReason != null }
        }

    val followUpPreview: List<BenzineWeeklyPerformance>
        get() {
            val followUps = performanceRows.filter { it.followUpReason != null }
            val inactive = followUps
                .filter { it.followUpReason == BenzineFollowUpReason.INACTIVE }
                .sortedBy { it.lastPurchaseAt ?: Long.MIN_VALUE }
            val declining = followUps
                .filter { it.followUpReason == BenzineFollowUpReason.DECLINING }
                .sortedBy { it.salesDeltaPercent ?: 0.0 }
            val newNotStarted = followUps
                .filter { it.followUpReason == BenzineFollowUpReason.NEW_NOT_STARTED }
                .sortedBy { BenzineWeekPolicy.parseIsoMillis(it.joinedAt) ?: Long.MIN_VALUE }
            return (inactive + declining + newNotStarted).take(3)
        }

    val activeComparisonText: String
        get() = weeklySummary?.let { signedDifference(it.activeCurrent, it.activePrevious) } ?: "—"

    val salesComparisonText: String
        get() = weeklySummary?.let { BenzinePerformanceCalculator.trendLabel(it.salesCurrent, it.salesPrevious) } ?: "—"

    val commissionsComparisonText: String
        get() = weeklySummary?.let { BenzinePerformanceCalculator.trendLabel(it.commissionsCurrent, it.commissionsPrevious) } ?: "—"

    val followUpComparisonText: String
        get() = weeklySummary?.let { signedDifference(it.followUpCurrent, it.followUpPrevious) } ?: "—"

    private fun signedDifference(current: Int, previous: Int): String {
        val difference = current - previous
        return when {
            difference > 0 -> "+$difference عن السابق"
            difference < 0 -> "$difference عن السابق"
            else -> "دون تغيير"
        }
    }
}

@HiltViewModel
class UsersDashboardViewModel @Inject constructor(
    private val dashboardAdminGateway: DashboardAdminGateway,
) : ViewModel() {

    val role: StateFlow<String?> = dashboardAdminGateway.role

    private val _uiState = MutableStateFlow(UsersDashboardUiState())
    val uiState: StateFlow<UsersDashboardUiState> = _uiState.asStateFlow()

    init { load() }

    fun setSort(sort: MarketerSort) = _uiState.update { it.copy(sort = sort) }

    fun setFilter(filter: BenzineUserFilter) = _uiState.update { it.copy(selectedFilter = filter) }

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(
                isLoading = !it.hasLoadedOnce,
                isRefreshing = it.hasLoadedOnce,
                error = if (it.hasLoadedOnce) it.error else null,
            )
        }
        fetch()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing || _uiState.value.isLoading) return
        _uiState.update { it.copy(isRefreshing = true) }
        fetch()
    }

    /**
     * Marketer stats are the primary source for this screen. Weekly commission eligibility is a
     * secondary enrichment source: its failure must never blank the marketer/workshop list.
     */
    private fun fetch() {
        viewModelScope.launch {
            val statsResult = dashboardAdminGateway.getMarketerStats()
            if (statsResult.isFailure) {
                handlePrimaryLoadFailure(statsResult.exceptionOrNull())
                return@launch
            }

            val stats = statsResult.getOrThrow()

            // Publish the primary list immediately. This keeps the screen usable while the weekly
            // enrichment is loading and also prevents a secondary server/read failure from
            // replacing the whole screen with an error state.
            _uiState.update {
                it.copy(
                    stats = stats,
                    isLoading = false,
                    error = null,
                    isStale = false,
                    hasLoadedOnce = true,
                    isWeeklyDataLoading = true,
                    weeklyDataError = null,
                )
            }

            dashboardAdminGateway.getCommissionEligibility()
                .onSuccess { eligibility ->
                    val performance = BenzinePerformanceCalculator.calculate(
                        stats = stats,
                        eligibility = eligibility,
                        nowMillis = System.currentTimeMillis(),
                    )
                    _uiState.update {
                        it.copy(
                            stats = stats,
                            eligibilityRows = eligibility,
                            withdrawableByClient = eligibility.withdrawableByClient(),
                            performanceRows = performance.rows,
                            weeklySummary = performance.summary,
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            isStale = false,
                            hasLoadedOnce = true,
                            isWeeklyDataLoading = false,
                            weeklyDataError = null,
                            isWeeklyDataStale = false,
                        )
                    }
                }
                .onFailure {
                    handleWeeklyDataFailure()
                }
        }
    }

    private fun handlePrimaryLoadFailure(error: Throwable?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                isWeeklyDataLoading = false,
                isStale = it.hasLoadedOnce,
                error = if (it.hasLoadedOnce) null else ErrorHumanizer.humanize(
                    error ?: IllegalStateException("Unknown dashboard load failure"),
                    "تحميل بيانات المسوقين والورش",
                ),
            )
        }
    }

    private fun handleWeeklyDataFailure() {
        _uiState.update {
            val hasPreviousWeeklyData = it.weeklySummary != null
            it.copy(
                isLoading = false,
                isRefreshing = false,
                isWeeklyDataLoading = false,
                error = null,
                weeklyDataError = if (hasPreviousWeeklyData) {
                    "تعذّر تحديث الأداء الأسبوعي — تُعرض آخر بيانات أسبوعية ناجحة"
                } else {
                    "تعذّر تحميل الأداء الأسبوعي — تُعرض بيانات المسوقين الأساسية"
                },
                isWeeklyDataStale = hasPreviousWeeklyData,
                eligibilityRows = if (hasPreviousWeeklyData) it.eligibilityRows else emptyList(),
                withdrawableByClient = if (hasPreviousWeeklyData) it.withdrawableByClient else emptyMap(),
                performanceRows = if (hasPreviousWeeklyData) it.performanceRows else emptyList(),
                weeklySummary = if (hasPreviousWeeklyData) it.weeklySummary else null,
            )
        }
    }

    fun sendReminder(stat: MarketerStatsItem) {
        viewModelScope.launch {
            dashboardAdminGateway.sendInactivityReminder(stat.clientId, stat.fullName)
                .onSuccess { _uiState.update { s -> s.copy(reminderMessage = "تم إرسال تذكير إلى ${stat.fullName}") } }
                .onFailure { _ -> _uiState.update { s -> s.copy(reminderMessage = "تعذّر إرسال التذكير، حاول مرة أخرى") } }
        }
    }

    fun consumeReminderMessage() = _uiState.update { it.copy(reminderMessage = null) }

    fun startConversation(clientId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val subject = "محادثة " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US)
                .format(java.util.Date())
            dashboardAdminGateway.createNewConversation(clientId, subject)
                .onSuccess(onCreated)
                .onFailure { _ ->
                    _uiState.update { it.copy(reminderMessage = "تعذّر فتح المحادثة، حاول مرة أخرى") }
                }
        }
    }

    fun sendAdminReminder(stat: MarketerStatsItem, message: String, navRoute: String) {
        viewModelScope.launch {
            dashboardAdminGateway.sendAdminReminder(stat.clientId, message, navRoute)
                .onSuccess { _uiState.update { s -> s.copy(reminderMessage = "تم إرسال التذكير إلى ${stat.fullName}") } }
                .onFailure { _ -> _uiState.update { s -> s.copy(reminderMessage = "تعذّر إرسال التذكير، حاول مرة أخرى") } }
        }
    }

    fun findByClientId(clientId: String): MarketerStatsItem? =
        _uiState.value.stats.firstOrNull { it.clientId == clientId }
}
