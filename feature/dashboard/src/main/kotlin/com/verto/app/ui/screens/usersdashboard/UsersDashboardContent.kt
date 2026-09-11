package com.verto.app.ui.screens.usersdashboard

import androidx.compose.ui.res.stringResource

import com.verto.feature.dashboard.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.dashboard.application.BenzineUserFilter
import com.verto.app.feature.dashboard.presentation.DashboardDimensions
import com.verto.app.feature.dashboard.presentation.DashboardTextScale
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

internal data class UsersDashboardContactEvents(
    val onOpenUser: (String) -> Unit,
    val onCall: (String) -> Unit,
    val onWhatsApp: (String) -> Unit,
)

internal data class UsersDashboardEvents(
    val onBack: () -> Unit,
    val onOpenLeaderboard: () -> Unit,
    val onRefresh: () -> Unit,
    val onFilterSelect: (BenzineUserFilter) -> Unit,
    val contacts: UsersDashboardContactEvents,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun UsersDashboardContent(
    state: UsersDashboardUiState,
    snackbarHost: SnackbarHostState,
    events: UsersDashboardEvents,
) {
    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_7f080fc4f5c1), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = events.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.legacy_ui_ecdd43425fc2), tint = TextPrimary)
                    }
                },
                actions = {
                    VertoIconButton(onClick = events.onOpenLeaderboard) {
                        Icon(Icons.Filled.EmojiEvents, stringResource(R.string.legacy_ui_cbb5cb25be2a), tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = events.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            UsersDashboardBody(state = state, events = events)
        }
    }
}

@Composable
private fun UsersDashboardBody(
    state: UsersDashboardUiState,
    events: UsersDashboardEvents,
) {
    when {
        state.isLoading && !state.hasLoadedOnce -> LoadingState()
        state.error != null && !state.hasLoadedOnce -> ErrorState(state.error.orEmpty())
        else -> UsersDashboardList(state = state, events = events)
    }
}

@Composable
private fun UsersDashboardList(
    state: UsersDashboardUiState,
    events: UsersDashboardEvents,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(DashboardDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12),
    ) {
        if (state.isStale) {
            item {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_9b9154145494),
                    color = WarningColor,
                    fontSize = DashboardTextScale.sp12,
                )
            }
        }
        if (state.isWeeklyDataLoading && !state.hasWeeklyData) item { WeeklyDataLoadingNotice() }
        state.weeklyDataError?.let { message ->
            item { WeeklyDataNotice(message = message, onRetry = events.onRefresh) }
        }
        if (state.hasWeeklyData) {
            weeklyDashboardItems(state = state, events = events)
        } else {
            basicDashboardItems(state = state, events = events)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.weeklyDashboardItems(
    state: UsersDashboardUiState,
    events: UsersDashboardEvents,
) {
    state.weeklySummary?.let { summary ->
        item {
            WeeklySummarySection(
                summary = summary,
                activeComparison = state.activeComparisonText,
                salesComparison = state.salesComparisonText,
                commissionsComparison = state.commissionsComparisonText,
                followUpComparison = state.followUpComparisonText,
            )
        }
    }
    if (state.followUpPreview.isNotEmpty()) {
        item {
            FollowUpSection(
                rows = state.followUpPreview,
                onOpen = events.contacts.onOpenUser,
                onShowAll = { events.onFilterSelect(BenzineUserFilter.NEEDS_FOLLOW_UP) },
            )
        }
    }
    item {
        UserFilters(
            selected = state.selectedFilter,
            onSelect = events.onFilterSelect,
            weeklyAvailable = true,
        )
    }
    items(state.filteredPerformanceRows, key = { it.clientId }) { row ->
        BenzineUserRow(
            row = row,
            onCall = { events.contacts.onCall(row.phone) },
            onWhatsApp = { events.contacts.onWhatsApp(row.phone) },
            onOpen = { events.contacts.onOpenUser(row.clientId) },
        )
    }
    if (state.performanceRows.isEmpty()) {
        item { EmptyState("لا يوجد مسوقون أو ورش مرتبطون بعد") }
    } else if (state.filteredPerformanceRows.isEmpty()) {
        item { EmptyState("لا توجد نتائج ضمن هذا الفلتر") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.basicDashboardItems(
    state: UsersDashboardUiState,
    events: UsersDashboardEvents,
) {
    item {
        UserFilters(
            selected = state.selectedFilter,
            onSelect = events.onFilterSelect,
            weeklyAvailable = false,
        )
    }
    items(state.basicFilteredStats, key = { it.clientId }) { row ->
        BasicUserRow(
            row = row,
            onCall = { events.contacts.onCall(row.phone) },
            onWhatsApp = { events.contacts.onWhatsApp(row.phone) },
            onOpen = { events.contacts.onOpenUser(row.clientId) },
        )
    }
    if (state.basicFilteredStats.isEmpty()) {
        item { EmptyState("لا يوجد مسوقون أو ورش مرتبطون بعد") }
    }
}
