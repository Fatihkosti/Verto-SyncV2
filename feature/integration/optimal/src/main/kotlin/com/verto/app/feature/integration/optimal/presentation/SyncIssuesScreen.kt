package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssue
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssueState
import java.text.DateFormat
import java.util.Date
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIssuesScreen(
    onBack: () -> Unit,
    viewModel: SyncIssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_ff57a1126112)) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            errorMessage != null -> SyncIssuesMessage(
                message = errorMessage,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            state.issues.isEmpty() -> SyncIssuesMessage(
                message = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_2a4870412f6a),
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = OptimalDimensions.dp16, vertical = OptimalDimensions.dp12),
                verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
            ) {
                items(
                    items = state.issues,
                    key = { "${it.organizationId}:${it.eventId}" },
                ) { issue ->
                    SyncIssueCard(
                        issue = issue,
                        isRetrying = issue.eventId in state.retryingEventIds,
                        onRetry = { viewModel.retry(issue.eventId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncIssueCard(
    issue: OptimalSyncIssue,
    isRetrying: Boolean,
    onRetry: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp9),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = issue.operationLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c75f125c6fd3, issue.dataTypeLabel, issue.state.label()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            (issue.companyName ?: issue.clientId)?.let {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_82947d4cda79, it.take(40)), style = MaterialTheme.typography.bodyMedium)
            }
            issue.invoiceId?.let {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c507b0a279f8, it.take(16)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = issue.reason,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_e11d1da2c545, issue.attemptCount))
                    issue.lastAttemptAt?.let { append(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_a8b457832563, formatSyncIssueTime(it))) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (issue.canRetry) {
                VertoButton(
                    onClick = onRetry,
                    enabled = !isRetrying,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = OptimalDimensions.dp8),
                            strokeWidth = OptimalDimensions.dp2,
                        )
                    }
                    Text(if (isRetrying) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_a4a3c104c832) else androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry))
                }
            }
        }
    }
}

@Composable
private fun SyncIssuesMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            modifier = Modifier.padding(OptimalDimensions.dp24),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun OptimalSyncIssueState.label(): String = when (this) {
    OptimalSyncIssueState.FAILED -> "فشلت"
    OptimalSyncIssueState.BLOCKED -> "متوقفة وتحتاج مراجعة"
    OptimalSyncIssueState.RETRYING -> "بانتظار إعادة المحاولة"
}

private fun formatSyncIssueTime(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
