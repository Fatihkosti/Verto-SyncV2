package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import java.math.BigDecimal
import com.verto.app.ui.components.VertoBottomSheet

internal data class LogisticsV236InvoiceSheetState(
    val supplierName: String,
    val invoices: List<LogisticsPurchaseInvoiceOptionUi>,
    val alreadySelected: Set<String>,
    val loading: Boolean,
    val loadError: String?,
)

internal data class LogisticsV236InvoiceSheetActions(
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
    val onAdd: (Set<String>) -> Unit,
)

private data class InvoiceSheetUiState(
    val query: String,
    val filtered: List<LogisticsPurchaseInvoiceOptionUi>,
    val pendingSelection: Set<String>,
    val newSelectionCount: Int,
)

private data class InvoiceSheetUiActions(
    val onQueryChange: (String) -> Unit,
    val onToggle: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvoiceMultiSelectSheet(
    state: LogisticsV236InvoiceSheetState,
    actions: LogisticsV236InvoiceSheetActions,
) {
    var query by rememberSaveable(state.supplierName) { mutableStateOf("") }
    var pendingSelection by remember(state.supplierName, state.alreadySelected) { mutableStateOf(state.alreadySelected) }
    val ui = InvoiceSheetUiState(
        query = query,
        filtered = remember(query, state.invoices) { state.invoices.filterV236Invoices(query) },
        pendingSelection = pendingSelection,
        newSelectionCount = (pendingSelection - state.alreadySelected).size,
    )
    VertoBottomSheet(onDismissRequest = actions.onDismiss, containerColor = BgCard) {
        InvoiceSheetContent(
            state = state,
            actions = actions,
            ui = ui,
            uiActions = InvoiceSheetUiActions(
                onQueryChange = { query = it },
                onToggle = { id -> pendingSelection = if (id in pendingSelection) pendingSelection - id else pendingSelection + id },
            ),
        )
    }
}

@Composable
private fun InvoiceSheetContent(
    state: LogisticsV236InvoiceSheetState,
    actions: LogisticsV236InvoiceSheetActions,
    ui: InvoiceSheetUiState,
    uiActions: InvoiceSheetUiActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .padding(horizontal = VertoSize.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
    ) {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7d492bbf6f42), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(state.supplierName, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        LogisticsTextField(value = ui.query, onValueChange = uiActions.onQueryChange, label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_search), imeAction = ImeAction.Done)
        InvoiceSheetResults(state, actions, ui, uiActions)
        VertoPrimaryButton(
            text = "إضافة المحدد (${ui.newSelectionCount})",
            onClick = { actions.onAdd(ui.pendingSelection - state.alreadySelected) },
            enabled = ui.newSelectionCount > 0 && !state.loading && state.loadError.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(VertoSpacing.xs))
    }
}

@Composable
private fun InvoiceSheetResults(
    state: LogisticsV236InvoiceSheetState,
    actions: LogisticsV236InvoiceSheetActions,
    ui: InvoiceSheetUiState,
    uiActions: InvoiceSheetUiActions,
) {
    if (state.loading) {
        LogisticsLoading()
        return
    }
    if (!state.loadError.isNullOrBlank()) {
        LogisticsError(state.loadError.orEmpty(), actions.onRetry)
        return
    }
    if (ui.filtered.isEmpty()) {
        LogisticsEmpty("لا توجد فواتير دولية متاحة لهذا المورد")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = LogisticsV2Tokens.invoicePickerMaxHeight),
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        items(ui.filtered, key = { it.id }) { option ->
            val already = option.id in state.alreadySelected
            InvoicePickerRow(
                option = option,
                checked = option.id in ui.pendingSelection,
                enabled = !already && option.selectable,
                onToggle = { uiActions.onToggle(option.id) },
            )
        }
    }
}

@Composable
private fun InvoicePickerRow(
    option: LogisticsPurchaseInvoiceOptionUi,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .semantics {
                contentDescription = buildString {
                    append("فاتورة ${option.source.invoiceNumberSnapshot}. ")
                    append(option.readinessLabel)
                }
            },
        color = BgCard,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(VertoStroke.thin, if (checked) AccentPrimary else BorderColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(VertoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
        ) {
            Checkbox(checked = checked, onCheckedChange = { if (enabled) onToggle() }, enabled = enabled)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a8d9bb06f339, option.source.invoiceNumberSnapshot), color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_424406d06bce_2, option.invoiceDate.takeIf { it > 0L }?.let(::formatLogisticsDate) ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_424406d06bce), formatPurchaseTotal(option.totalAmount)),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    option.readinessLabel,
                    color = if (option.selectable) SuccessColor else ErrorColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun InvoicePlanningMetricDialog(
    title: String,
    initialValue: String,
    decimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    val normalized = value.replace(',', '.').trim()
    val valid = if (decimal) normalized.toBigDecimalOrNull()?.signum() == 1 else normalized.toIntOrNull()?.let { it > 0 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
                LogisticsTextField(
                    value = value,
                    onValueChange = { raw ->
                        value = if (decimal) raw.replace(',', '.').filter { it.isDigit() || it == '.' }
                        else raw.filter(Char::isDigit)
                    },
                    label = title,
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    isError = value.isNotBlank() && !valid,
                    errorText = if (decimal) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_a3e9281b95c2_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_a3e9281b95c2),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(normalized) }, enabled = valid) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadyDatePickerDialog(
    initialDate: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate ?: System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onConfirm) }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
internal fun ConfirmPlanningRemovalDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_remove), color = ErrorColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    )
}

private fun formatPurchaseTotal(total: BigDecimal): String =
    total.stripTrailingZeros().toPlainString().ifBlank { "0" }
