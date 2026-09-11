package com.verto.app.feature.management.presentation

import com.verto.feature.management.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.ds_b95f941f9fe1),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        ManagementContent(
            state = state,
            onRetry = viewModel::loadIntegrations,
            onNavigate = onNavigate,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun ManagementContent(
    state: ManagementPresentationState,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.errorMessage != null -> ManagementMessage(
            message = state.errorMessage,
            actionLabel = "إعادة المحاولة",
            onAction = onRetry,
            modifier = modifier,
        )

        state.integrations.isEmpty() -> ManagementMessage(
            message = androidx.compose.ui.res.stringResource(R.string.ds_6c6f10844989),
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ManagementDimensions.dp16, vertical = ManagementDimensions.dp14),
            verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp12),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp4)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.ds_4e4171ec12a8),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.ds_40981bc8679a),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    )
                }
            }
            items(
                items = state.integrations,
                key = { it.id.value },
            ) { integration ->
                ManagementIntegrationCard(
                    integration = integration,
                    onClick = { onNavigate(integration.route) },
                    badges = state.badges[integration.id].orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun ManagementMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ManagementDimensions.dp24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        actionLabel?.let {
            TextButton(onClick = onAction) {
                Text(it)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementIntegrationPendingScreen(
    title: String,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VertoTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        ManagementMessage(
            message = androidx.compose.ui.res.stringResource(R.string.ds_860b0b533067),
            modifier = Modifier.padding(padding),
        )
    }
}
