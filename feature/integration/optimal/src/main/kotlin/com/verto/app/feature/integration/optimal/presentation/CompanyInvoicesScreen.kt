package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.ui.components.VertoOutlinedTextField

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilterOptions
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilters
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceLifecycle
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceListItem
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceSettlement
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceSyncStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoBottomSheet
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyInvoicesScreen(
    onBack: () -> Unit,
    onInvoiceClick: (CompanyInvoiceListItem) -> Unit,
    viewModel: CompanyInvoicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_4e093d635fc8), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
                    }
                },
                actions = {
                    VertoIconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = if (state.filters.hasActiveFilters()) {
                                androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_53b416726a2d)
                            } else {
                                androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_c7b3d17a0511)
                            },
                            tint = if (state.filters.hasActiveFilters()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            errorMessage != null -> CompanyInvoicesMessage(
                message = errorMessage,
                actionLabel = "إعادة المحاولة",
                onAction = viewModel::retry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            state.invoices.isEmpty() -> CompanyInvoicesMessage(
                message = if (state.filters.hasActiveFilters()) {
                    androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_8bbdcd4769f1)
                } else {
                    androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_b93dd2bcc8af)
                },
                actionLabel = if (state.filters.hasActiveFilters()) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_537651aaefc3) else null,
                onAction = viewModel::clearFilters,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = OptimalDimensions.dp16, vertical = OptimalDimensions.dp14),
                verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10),
            ) {
                items(
                    items = state.invoices,
                    key = { "${it.organizationId}:${it.invoiceId}" },
                ) { invoice ->
                    CompanyInvoiceCard(
                        invoice = invoice,
                        onClick = { onInvoiceClick(invoice) },
                    )
                }
            }
        }
    }

    if (showFilters) {
        CompanyInvoiceFiltersSheet(
            filters = state.filters,
            options = state.filterOptions,
            onCompanyChange = viewModel::updateCompany,
            onLifecycleChange = viewModel::updateLifecycle,
            onSettlementChange = viewModel::updateSettlement,
            onVehicleSearchChange = viewModel::updateVehicleSearch,
            onSyncStatusChange = viewModel::updateSyncStatus,
            onFromDateChange = viewModel::updateFromDate,
            onToDateExclusiveChange = viewModel::updateToDateExclusive,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun CompanyInvoiceCard(
    invoice: CompanyInvoiceListItem,
    onClick: () -> Unit,
) {
    val vehicle = listOf(invoice.vehicleName, invoice.vehicleType)
        .filter(String::isNotBlank)
        .joinToString(" • ")
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.companyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_315d9cef1ec5, invoice.invoiceNumber, formatDate(invoice.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatAmount(invoice.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8)) {
                InvoiceBadge(invoice.settlement.arabicLabel())
                InvoiceBadge(invoice.lifecycle.arabicLabel())
                InvoiceBadge(invoice.syncStatus.arabicLabel())
            }

            if (vehicle.isNotBlank() || invoice.plateNumber.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Text(
                        text = listOf(vehicle, invoice.plateNumber)
                            .filter(String::isNotBlank)
                            .joinToString(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_6353c35965cf)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (invoice.description.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null)
                    Text(
                        text = invoice.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceBadge(label: String) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = OptimalDimensions.dp8, vertical = OptimalDimensions.dp4),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyInvoiceFiltersSheet(
    filters: CompanyInvoiceFilters,
    options: CompanyInvoiceFilterOptions,
    onCompanyChange: (String?) -> Unit,
    onLifecycleChange: (CompanyInvoiceLifecycle?) -> Unit,
    onSettlementChange: (CompanyInvoiceSettlement?) -> Unit,
    onVehicleSearchChange: (String) -> Unit,
    onSyncStatusChange: (CompanyInvoiceSyncStatus?) -> Unit,
    onFromDateChange: (Long?) -> Unit,
    onToDateExclusiveChange: (Long?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var chooseFrom by rememberSaveable { mutableStateOf(false) }
    var chooseTo by rememberSaveable { mutableStateOf(false) }

    VertoBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = OptimalDimensions.dp20, vertical = OptimalDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp14),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_b06271c09698), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClear) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_clear)) }
                }
            }
            if (options.companies.isNotEmpty()) {
                item { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c1c6feb8a7a7), fontWeight = FontWeight.Bold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8)) {
                        item {
                            FilterChip(
                                selected = filters.companyId == null,
                                onClick = { onCompanyChange(null) },
                                label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_all)) },
                            )
                        }
                        items(options.companies, key = { it.companyId }) { company ->
                            FilterChip(
                                selected = filters.companyId == company.companyId,
                                onClick = { onCompanyChange(company.companyId) },
                                label = { Text(company.companyName) },
                            )
                        }
                    }
                }
            }
            item {
                VertoOutlinedTextField(
                    value = filters.vehicleSearch,
                    onValueChange = onVehicleSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_aae685886f0c)) },
                )
            }
            item { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_5ccae9b36e3f), fontWeight = FontWeight.Bold) }
            item {
                FilterRow(
                    selected = filters.lifecycle,
                    values = CompanyInvoiceLifecycle.entries,
                    label = CompanyInvoiceLifecycle::arabicLabel,
                    onChange = onLifecycleChange,
                )
            }
            item { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_5a39172b8820), fontWeight = FontWeight.Bold) }
            item {
                FilterRow(
                    selected = filters.settlement,
                    values = CompanyInvoiceSettlement.entries,
                    label = CompanyInvoiceSettlement::arabicLabel,
                    onChange = onSettlementChange,
                )
            }
            item { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sync_status), fontWeight = FontWeight.Bold) }
            item {
                FilterRow(
                    selected = filters.syncStatus,
                    values = CompanyInvoiceSyncStatus.entries,
                    label = CompanyInvoiceSyncStatus::arabicLabel,
                    onChange = onSyncStatusChange,
                )
            }
            if (options.hasDateValues) {
                item { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_d94d702d8343), fontWeight = FontWeight.Bold) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                    ) {
                        TextButton(onClick = { chooseFrom = true }, modifier = Modifier.weight(1f)) {
                            Text(filters.fromDateInclusive?.let(::formatDate) ?: androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_e5097a86be4f))
                        }
                        TextButton(onClick = { chooseTo = true }, modifier = Modifier.weight(1f)) {
                            Text(filters.toDateExclusive?.let { formatDate(it - 1L) } ?: androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_550302ee580d))
                        }
                    }
                }
            }
            item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_940efd24f1d8)) } }
        }
    }

    if (chooseFrom) {
        CompanyInvoiceDateDialog(
            initialDate = filters.fromDateInclusive,
            onDismiss = { chooseFrom = false },
            onConfirm = {
                onFromDateChange(it)
                chooseFrom = false
            },
        )
    }
    if (chooseTo) {
        CompanyInvoiceDateDialog(
            initialDate = filters.toDateExclusive?.minus(DAY_MILLIS),
            onDismiss = { chooseTo = false },
            onConfirm = {
                onToDateExclusiveChange(it?.plus(DAY_MILLIS))
                chooseTo = false
            },
        )
    }
}

@Composable
private fun <T> FilterRow(
    selected: T?,
    values: List<T>,
    label: (T) -> String,
    onChange: (T?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onChange(null) },
                label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_all)) },
            )
        }
        items(values) { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onChange(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyInvoiceDateDialog(
    initialDate: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.selectedDateMillis) }) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_e08ca265fdea)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun CompanyInvoicesMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(OptimalDimensions.dp24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

private fun CompanyInvoiceLifecycle.arabicLabel(): String = when (this) {
    CompanyInvoiceLifecycle.ACTIVE -> "نشطة"
    CompanyInvoiceLifecycle.VOIDED -> "ملغاة"
}

private fun CompanyInvoiceSettlement.arabicLabel(): String = when (this) {
    CompanyInvoiceSettlement.CASH -> "كاش"
    CompanyInvoiceSettlement.CREDIT -> "آجل"
}

private fun CompanyInvoiceSyncStatus.arabicLabel(): String = when (this) {
    CompanyInvoiceSyncStatus.PENDING -> "بانتظار المزامنة"
    CompanyInvoiceSyncStatus.SYNCED -> "متزامنة"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))

private fun formatAmount(amount: Double): String = NumberFormat.getNumberInstance().format(amount)

private const val DAY_MILLIS: Long = 86_400_000L
