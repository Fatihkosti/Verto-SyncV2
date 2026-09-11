package com.verto.app.feature.integration.optimal.presentation

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
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.CompanyEvent
import com.verto.app.feature.integration.optimal.domain.model.CompanyEventType
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimalCompanyDetailsScreen(
    onBack: () -> Unit,
    viewModel: OptimalCompanyDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            VertoTopAppBar(
                title = { Text(state.company?.name ?: androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_2a4cd71cb43d), fontWeight = FontWeight.Bold) },
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
        when {
            state.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            state.accessDenied -> CompanyDetailsMessage("لا تملك صلاحية عرض سجل الشركة", padding)
            state.company == null -> CompanyDetailsMessage("الشركة غير موجودة", padding)
            else -> CompanyDetailsContent(
                state = state,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun CompanyDetailsContent(
    state: OptimalCompanyDetailsUiState,
    modifier: Modifier = Modifier,
) {
    val company = requireNotNull(state.company)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(OptimalDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
    ) {
        item {
            VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(OptimalDimensions.dp16),
                    verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                ) {
                    Text(company.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (company.linkStatus == OptimalCompanyLinkStatus.LINKED) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_4a680b5c99a9_2) else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_4a680b5c99a9))
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_a224df30e2d8, company.invoiceCount))
                }
            }
        }
        item {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_d8857a571008), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.events.isEmpty()) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_f75baa39fe1a),
                    modifier = Modifier.fillMaxWidth().padding(vertical = OptimalDimensions.dp24),
                )
            }
        } else {
            items(state.events, key = { "${it.type}:${it.eventId}" }) { event ->
                CompanyEventCard(event)
            }
        }
    }
}

@Composable
private fun CompanyEventCard(event: CompanyEvent) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(OptimalDimensions.dp14),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
        ) {
            Icon(
                imageVector = when (event.type) {
                    CompanyEventType.INVOICE -> Icons.Default.ReceiptLong
                    CompanyEventType.PAYMENT -> Icons.Default.Payments
                    CompanyEventType.MESSAGE -> Icons.Default.Message
                },
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp3)) {
                Text(event.title, fontWeight = FontWeight.Bold)
                if (event.description.isNotBlank()) Text(event.description, style = MaterialTheme.typography.bodyMedium)
                Text(formatDate(event.occurredAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            event.amount?.let { Text(formatAmount(it), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CompanyDetailsMessage(message: String, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(OptimalDimensions.dp24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(message) }
}

private fun formatDate(epochMillis: Long): String = if (epochMillis <= 0L) {
    "وقت غير متاح"
} else {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar", "SD")).format(Date(epochMillis))
}

private fun formatAmount(amount: Double): String = NumberFormat
    .getNumberInstance(Locale("ar", "SD"))
    .format(amount)
