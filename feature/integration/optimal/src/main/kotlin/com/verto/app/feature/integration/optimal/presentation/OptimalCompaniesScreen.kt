package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.ui.components.VertoOutlinedTextField

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkFilter
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkStatus
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimalCompaniesScreen(
    onBack: () -> Unit,
    onCompanyClick: (String) -> Unit,
    viewModel: OptimalCompaniesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage

    LaunchedEffect(viewModel) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_fa397324da26), fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
        ) {
            VertoOutlinedTextField(
                value = state.searchTerm,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_1f1803f93c58)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
            ) {
                CompanyFilterChip("الكل", OptimalCompanyLinkFilter.ALL, state.linkFilter, viewModel::updateFilter)
                CompanyFilterChip("مرتبطة", OptimalCompanyLinkFilter.LINKED, state.linkFilter, viewModel::updateFilter)
                CompanyFilterChip("غير مرتبطة", OptimalCompanyLinkFilter.UNLINKED, state.linkFilter, viewModel::updateFilter)
            }

            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                errorMessage != null -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = OptimalDimensions.dp24),
                )
                state.companies.isEmpty() -> Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_0431055571b0),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = OptimalDimensions.dp24),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = OptimalDimensions.dp20),
                    verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10),
                ) {
                    items(state.companies, key = OptimalCompany::clientId) { company ->
                        OptimalCompanyCard(company = company, onClick = { onCompanyClick(company.clientId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyFilterChip(
    label: String,
    filter: OptimalCompanyLinkFilter,
    selected: OptimalCompanyLinkFilter,
    onClick: (OptimalCompanyLinkFilter) -> Unit,
) {
    FilterChip(
        selected = filter == selected,
        onClick = { onClick(filter) },
        label = { Text(label) },
    )
}

@Composable
private fun OptimalCompanyCard(
    company: OptimalCompany,
    onClick: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OptimalDimensions.dp16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
        ) {
            Icon(Icons.Default.Business, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp4)) {
                Text(company.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (company.linkStatus == OptimalCompanyLinkStatus.LINKED) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_3987c8d4b60c_2) else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_3987c8d4b60c),
                    color = if (company.linkStatus == OptimalCompanyLinkStatus.LINKED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_0fd53029cf2d, company.invoiceCount), style = MaterialTheme.typography.labelLarge)
        }
    }
}
