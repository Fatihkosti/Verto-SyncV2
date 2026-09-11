package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordFilterOptions
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordListItem
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordQuery
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceSyncStatus
import java.text.DateFormat
import java.util.Date
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoBottomSheet
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceRecordsScreen(
    onBack: () -> Unit,
    onRecordClick: (String) -> Unit,
    viewModel: MaintenanceRecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_48e7374cc78d), fontWeight = FontWeight.Bold) },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VertoOutlinedTextField(
                    value = state.query.freeText,
                    onValueChange = viewModel::updateFreeText,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_e9f10a5f924c)) },
                )
                VertoOutlinedButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                    Text(
                        text = if (state.query.hasActiveFilters()) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_40b1451f2f20_2) else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_40b1451f2f20),
                        modifier = Modifier.padding(start = OptimalDimensions.dp6),
                    )
                }
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                errorMessage != null -> MaintenanceRecordsMessage(
                    message = errorMessage,
                    modifier = Modifier.weight(1f),
                )

                state.records.isEmpty() -> MaintenanceRecordsMessage(
                    message = if (state.query == MaintenanceRecordQuery()) {
                        androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_fd7787d78574)
                    } else {
                        androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_c240b67b54f4)
                    },
                    modifier = Modifier.weight(1f),
                    actionLabel = if (state.query == MaintenanceRecordQuery()) null else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_03fb35bb323f),
                    onAction = viewModel::clearAll,
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = OptimalDimensions.dp20),
                    verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10),
                ) {
                    items(
                        items = state.records,
                        key = { "${it.organizationId}:${it.recordId}" },
                    ) { record ->
                        MaintenanceRecordCard(record, onClick = { onRecordClick(record.recordId) })
                    }
                }
            }
        }
    }

    if (showFilters) {
        MaintenanceFiltersSheet(
            query = state.query,
            options = state.filterOptions,
            onCompanySearchChange = viewModel::updateCompanySearch,
            onVehicleSearchChange = viewModel::updateVehicleSearch,
            onDriverChange = viewModel::updateDriver,
            onSyncStatusChange = viewModel::updateSyncStatus,
            onFromDateChange = viewModel::updateFromDate,
            onToDateExclusiveChange = viewModel::updateToDateExclusive,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun MaintenanceRecordCard(
    record: MaintenanceRecordListItem,
    onClick: () -> Unit,
) {
    val vehicleTitle = listOf(
        record.vehicleSnapshot.name,
        record.vehicleSnapshot.vehicleType,
    ).filter(String::isNotBlank).joinToString(" • ").ifBlank { "سيارة غير مسماة" }
    val invoiceLabel = record.invoiceNumber?.let { "فاتورة #$it" }
        ?: "فاتورة ${record.invoiceId.take(8)}"

    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.companyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDate(record.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(vehicleTitle, style = MaterialTheme.typography.bodyLarge)
                    if (record.vehicleSnapshot.plateNumber.isNotBlank()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_64d753fae6de, record.vehicleSnapshot.plateNumber),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (record.vehicleReference == null) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_faf440447b46),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                Text(invoiceLabel, style = MaterialTheme.typography.bodyMedium)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_5ff08eaeb1dc, record.syncStatus.arabicLabel()), style = MaterialTheme.typography.labelMedium)
            }

            if (record.driverOrDelegate.isNotBlank()) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_3db2703543fe, record.driverOrDelegate),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = record.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceFiltersSheet(
    query: MaintenanceRecordQuery,
    options: MaintenanceRecordFilterOptions,
    onCompanySearchChange: (String) -> Unit,
    onVehicleSearchChange: (String) -> Unit,
    onDriverChange: (String?) -> Unit,
    onSyncStatusChange: (MaintenanceSyncStatus?) -> Unit,
    onFromDateChange: (Long?) -> Unit,
    onToDateExclusiveChange: (Long?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectFromDate by rememberSaveable { mutableStateOf(false) }
    var selectToDate by rememberSaveable { mutableStateOf(false) }

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
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_db29f09b178c), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClear) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_clear)) }
                }
            }
            item {
                VertoOutlinedTextField(
                    value = query.companySearch,
                    onValueChange = onCompanySearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_02c2c39dd5d7)) },
                )
            }
            item {
                VertoOutlinedTextField(
                    value = query.vehicleSearch,
                    onValueChange = onVehicleSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_aae685886f0c)) },
                )
            }
            if (options.drivers.isNotEmpty()) {
                item { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_e2672b124068), fontWeight = FontWeight.Bold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8)) {
                        item {
                            FilterChip(
                                selected = query.driver == null,
                                onClick = { onDriverChange(null) },
                                label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_all)) },
                            )
                        }
                        items(options.drivers, key = { it }) { driver ->
                            FilterChip(
                                selected = query.driver == driver,
                                onClick = { onDriverChange(driver) },
                                label = { Text(driver) },
                            )
                        }
                    }
                }
            }
            if (options.syncStatuses.isNotEmpty()) {
                item { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sync_status), fontWeight = FontWeight.Bold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8)) {
                        item {
                            FilterChip(
                                selected = query.syncStatus == null,
                                onClick = { onSyncStatusChange(null) },
                                label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_all)) },
                            )
                        }
                        items(options.syncStatuses, key = { it.name }) { status ->
                            FilterChip(
                                selected = query.syncStatus == status,
                                onClick = { onSyncStatusChange(status) },
                                label = { Text(status.arabicLabel()) },
                            )
                        }
                    }
                }
            }
            if (options.hasDateValues) {
                item { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_d94d702d8343), fontWeight = FontWeight.Bold) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                    ) {
                        FilterChip(
                            selected = query.fromDateInclusive != null,
                            onClick = { selectFromDate = true },
                            label = {
                                Text(query.fromDateInclusive?.let { androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_aa819c6cd4ea_2, formatDate(it)) } ?: androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_aa819c6cd4ea))
                            },
                        )
                        FilterChip(
                            selected = query.toDateExclusive != null,
                            onClick = { selectToDate = true },
                            label = {
                                Text(
                                    query.toDateExclusive
                                        ?.minus(1L)
                                        ?.let { androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_cdd98d09807c, formatDate(it)) }
                                        ?: androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_9a8dd53cd07e),
                                )
                            },
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(OptimalDimensions.dp12))
                VertoOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_0f4d51166aad))
                }
                Spacer(Modifier.height(OptimalDimensions.dp12))
            }
        }
    }

    if (selectFromDate) {
        MaintenanceDatePickerDialog(
            initialDate = query.fromDateInclusive,
            onDismiss = { selectFromDate = false },
            onSelected = { selected ->
                onFromDateChange(selected)
                selectFromDate = false
            },
        )
    }
    if (selectToDate) {
        MaintenanceDatePickerDialog(
            initialDate = query.toDateExclusive?.minus(1L),
            onDismiss = { selectToDate = false },
            onSelected = { selected ->
                onToDateExclusiveChange(selected?.plus(ONE_DAY_MILLIS))
                selectToDate = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceDatePickerDialog(
    initialDate: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long?) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSelected(datePickerState.selectedDateMillis) }) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun MaintenanceRecordsMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(OptimalDimensions.dp24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        actionLabel?.let { label ->
            TextButton(onClick = onAction) { Text(label) }
        }
    }
}

internal fun MaintenanceSyncStatus.arabicLabel(): String = when (this) {
    MaintenanceSyncStatus.LOCAL_ONLY -> "محلي"
    MaintenanceSyncStatus.PENDING -> "بانتظار المزامنة"
    MaintenanceSyncStatus.SYNCING -> "جارٍ المزامنة"
    MaintenanceSyncStatus.SYNCED -> "متزامن"
    MaintenanceSyncStatus.FAILED -> "فشلت المزامنة"
    MaintenanceSyncStatus.BLOCKED -> "المزامنة محظورة"
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
