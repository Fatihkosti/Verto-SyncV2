package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkStatus
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationCode
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimalCodesScreen(
    onBack: () -> Unit,
    viewModel: OptimalCodesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    state.registrationCode?.let { registrationCode ->
        OptimalCompanyJoinCodeDialog(
            registrationCode = registrationCode,
            onDismiss = viewModel::dismissRegistrationCode,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_e3660ae25952), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = OptimalDimensions.dp16, vertical = OptimalDimensions.dp12),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_67abb8eabfb2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VertoOutlinedTextField(
                value = state.searchTerm,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_1f1803f93c58)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )

            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.companies.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            modifier = Modifier.size(OptimalDimensions.dp48),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(OptimalDimensions.dp12))
                        Text(
                            text = if (state.searchTerm.isBlank()) {
                                androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_f53778497d91)
                            } else {
                                androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_1b8e0dfe6074)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                    ) {
                        items(state.companies, key = OptimalCompany::clientId) { company ->
                            OptimalCompanySelectionCard(
                                company = company,
                                selected = state.selectedClientId == company.clientId,
                                enabled = !state.isIssuing,
                                onSelect = { viewModel.selectCompany(company.clientId) },
                            )
                        }
                    }
                }
            }

            VertoButton(
                onClick = viewModel::issueCode,
                enabled = state.canIssue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isIssuing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OptimalDimensions.dp18),
                        strokeWidth = OptimalDimensions.dp2,
                    )
                } else {
                    Icon(Icons.Default.Key, contentDescription = null)
                }
                Spacer(Modifier.width(OptimalDimensions.dp8))
                Text(if (state.isIssuing) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_0810f8d3cda1_2) else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_0810f8d3cda1))
            }
        }
    }
}

@Composable
private fun OptimalCompanySelectionCard(
    company: OptimalCompany,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val linked = company.linkStatus == OptimalCompanyLinkStatus.LINKED
    val selectable = enabled && !linked
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OptimalDimensions.dp14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = selectable,
            )
            Spacer(Modifier.width(OptimalDimensions.dp8))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (linked) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_6b60a98dd565_2) else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_6b60a98dd565),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (linked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun OptimalCompanyJoinCodeDialog(
    registrationCode: OptimalRegistrationCode,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_e3660ae25952)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10)) {
                Text(
                    text = registrationCode.companyName,
                    fontWeight = FontWeight.Bold,
                )
                SelectionContainer {
                    Text(
                        registrationCode.code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_2d6d4e97be91))
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_3ba6b3be53ef, registrationCode.expiresAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(registrationCode.code))
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(OptimalDimensions.dp6))
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_46e6841e2136))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close)) }
        },
    )
}
