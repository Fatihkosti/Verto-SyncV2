package com.verto.app.feature.dashboard.presentation.observation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.R
import com.verto.feature.dashboard.api.TeamObservation
import com.verto.feature.dashboard.api.TeamObservationCategory
import com.verto.feature.dashboard.api.TeamObservationStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamObservationsRoute(
    onBack: () -> Unit,
    vm: TeamObservationsViewModel = hiltViewModel(),
) {
    val observations by vm.observations.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.team_observations_title)) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(R.string.team_observations_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !isAdmin -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(VertoSpacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.team_observations_admin_only))
            }
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (observations.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(VertoSpacing.lg),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.team_observations_empty), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(VertoSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
                    ) {
                        items(observations, key = TeamObservation::id) { observation ->
                            TeamObservationCard(
                                observation = observation,
                                onImportantChange = { vm.setImportant(observation.id, it) },
                                onStatusChange = { vm.setStatus(observation.id, it) },
                            )
                        }
                        item { Spacer(Modifier.height(VertoSpacing.xl)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamObservationCard(
    observation: TeamObservation,
    onImportantChange: (Boolean) -> Unit,
    onStatusChange: (TeamObservationStatus) -> Unit,
) {
    val formatted = remember(observation.createdAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(observation.createdAtEpochMillis))
    }
    VertoCard(
        contentPadding = PaddingValues(VertoSpacing.none),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(VertoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
        ) {
            Text(
                text = observationCategoryLabel(observation.category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = observation.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${observation.authorName} • $formatted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = observation.isImportant,
                    onClick = { onImportantChange(!observation.isImportant) },
                    label = {
                        Text(
                            if (observation.isImportant) {
                                androidx.compose.ui.res.stringResource(R.string.team_observation_important)
                            } else {
                                androidx.compose.ui.res.stringResource(R.string.team_observation_normal)
                            },
                        )
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
            ) {
                StatusChip(
                    selected = observation.status == TeamObservationStatus.NEW,
                    label = androidx.compose.ui.res.stringResource(R.string.team_observation_status_new),
                    onClick = { onStatusChange(TeamObservationStatus.NEW) },
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    selected = observation.status == TeamObservationStatus.REVIEWED,
                    label = androidx.compose.ui.res.stringResource(R.string.team_observation_status_reviewed),
                    onClick = { onStatusChange(TeamObservationStatus.REVIEWED) },
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    selected = observation.status == TeamObservationStatus.CLOSED,
                    label = androidx.compose.ui.res.stringResource(R.string.team_observation_status_closed),
                    onClick = { onStatusChange(TeamObservationStatus.CLOSED) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
    )
}

private fun observationCategoryLabel(category: TeamObservationCategory): String = when (category) {
    TeamObservationCategory.IDEA -> "فكرة"
    TeamObservationCategory.MARKET_INFO -> "معلومة سوق"
    TeamObservationCategory.COMPLAINT -> "شكوى"
}
