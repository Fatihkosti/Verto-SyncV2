package com.verto.app.ui.screens.leaderboard

import androidx.compose.ui.res.stringResource

import com.verto.feature.dashboard.R
import com.verto.app.feature.dashboard.presentation.DashboardDimensions
import com.verto.app.feature.dashboard.presentation.DashboardTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.CurrencyFormatter
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()

    // حارس الصلاحية: لوحة الصدارة للمدير (admin) حصراً (تعتمد get_marketer_stats المُحصَّن).
    LaunchedEffect(role) { if (role != null && role != "admin") onBack() }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_30b7ee6e9489), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.legacy_ui_ecdd43425fc2), tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                // اختيار الأسبوع (الحالي أولاً، ثم الأسابيع السابقة)
                if (state.weeks.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = DashboardDimensions.dp16, vertical = DashboardDimensions.dp8),
                        horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8)
                    ) {
                        state.weeks.forEachIndexed { idx, week ->
                            FilterChip(
                                selected = state.selectedWeek == week,
                                onClick = { viewModel.setWeek(week) },
                                label = {
                                    Text(
                                        if (idx == 0) stringResource(R.string.legacy_ui_a9e814af23a3) else stringResource(R.string.legacy_ui_83abfa4cbd09, state.weekLabel(week)),
                                        fontSize = DashboardTextScale.sp12
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                when {
                    state.isLoading && !state.hasLoadedOnce ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentPrimary)
                        }
                    state.error != null && !state.hasLoadedOnce ->
                        Box(Modifier.fillMaxSize().padding(DashboardDimensions.dp24), contentAlignment = Alignment.Center) {
                            Text(state.error ?: "", color = TextMuted)
                        }
                    state.ranked.isEmpty() ->
                        Box(Modifier.fillMaxSize().padding(DashboardDimensions.dp24), contentAlignment = Alignment.Center) {
                            Text(androidx.compose.ui.res.stringResource(R.string.ds_5b03d394e527), color = TextMuted)
                        }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(DashboardDimensions.dp16),
                        verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8)
                    ) {
                        itemsIndexed(state.ranked) { index, row ->
                            LeaderboardRow(rank = index + 1, row = row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, row: WeeklyLeaderRow) {
    val medal = when (rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null
    }
    val rankColor = when (rank) {
        1 -> GoldPrimary; 2 -> TextSecondary; 3 -> SuccessColor; else -> TextMuted
    }
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(DashboardDimensions.dp12)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(DashboardDimensions.dp12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(DashboardDimensions.dp36).clip(CircleShape).background(rankColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(medal ?: rank.toString(), color = rankColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(DashboardDimensions.dp12))
            Column(Modifier.weight(1f)) {
                Text(row.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_0fd53029cf2d, row.invoices), color = TextMuted, fontSize = DashboardTextScale.sp11)
            }
            Text(
                CurrencyFormatter.formatNoSymbol(row.commission),
                color = AccentPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = DashboardTextScale.sp15
            )
        }
    }
}
