package com.verto.app.ui.screens.usersdashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.dashboard.application.BenzineFollowUpReason
import com.verto.app.feature.dashboard.application.BenzinePerformanceStatus
import com.verto.app.feature.dashboard.application.BenzineUserFilter
import com.verto.app.feature.dashboard.application.BenzineWeeklyPerformance
import com.verto.app.feature.dashboard.application.BenzineWeeklySummary
import com.verto.app.feature.dashboard.application.MarketerStatsItem
import com.verto.app.feature.dashboard.presentation.DashboardDimensions
import com.verto.app.feature.dashboard.presentation.DashboardTextScale
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.WarningColor
import com.verto.app.utils.CurrencyFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UsersDashboardScreen(
    onBack: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onCall: (String) -> Unit = {},
    onWhatsApp: (String) -> Unit = {},
    viewModel: UsersDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(role) {
        if (role != null && role != "admin") onBack()
    }

    LaunchedEffect(state.reminderMessage) {
        state.reminderMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeReminderMessage()
        }
    }

    UsersDashboardContent(
        state = state,
        snackbarHost = snackbarHost,
        events = UsersDashboardEvents(
            onBack = onBack,
            onOpenLeaderboard = onOpenLeaderboard,
            onRefresh = viewModel::refresh,
            onFilterSelect = viewModel::setFilter,
            contacts = UsersDashboardContactEvents(
                onOpenUser = onOpenUser,
                onCall = onCall,
                onWhatsApp = onWhatsApp,
            ),
        ),
    )
}
