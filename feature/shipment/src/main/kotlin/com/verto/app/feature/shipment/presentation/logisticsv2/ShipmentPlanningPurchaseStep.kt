package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke

internal data class LogisticsV236PurchaseStepState(
    val canManage: Boolean,
    val saving: Boolean,
    val saveError: String?,
    val invoiceLoading: Boolean,
    val invoiceLoadError: String?,
    val validationAttempt: Int,
)

internal data class LogisticsV236PurchaseStepActions(
    val onDraftChange: (LogisticsPlanningDraft) -> Unit,
    val onRetryInvoices: () -> Unit,
)

private data class SupplierPlanningActions(
    val onAddInvoice: () -> Unit,
    val onRemoveSupplier: () -> Unit,
    val onRemoveInvoice: (String) -> Unit,
    val onPackageCountChange: (String, String) -> Unit,
    val onWeightChange: (String, String) -> Unit,
    val onReadyDateClick: (String) -> Unit,
)

private data class SupplierActionTargets(
    val onAddInvoice: (String) -> Unit,
    val onRemoveSupplier: (String) -> Unit,
    val onRemoveInvoice: (String) -> Unit,
    val onReadyDate: (String) -> Unit,
)

private data class PurchaseSupplierSelection(
    val selectedIds: List<String>,
    val visibleIds: List<String>,
    val pendingId: String,
    val showValidation: Boolean,
)

private data class PurchaseSectionCallbacks(
    val onSupplierSelected: (String) -> Unit,
    val onAddSupplier: () -> Unit,
    val supplierActions: (String) -> SupplierPlanningActions,
    val onRetryInvoices: () -> Unit,
)

private data class PurchaseOverlayState(
    val sheetSupplierId: String?,
    val readyDateInvoiceId: String?,
    val invoiceRemovalId: String?,
    val supplierRemovalId: String?,
)

private data class PurchaseOverlayContext(
    val draft: LogisticsPlanningDraft,
    val invoices: List<LogisticsPurchaseInvoiceOptionUi>,
    val suppliers: List<LogisticsPurchaseSupplierOptionUi>,
    val state: LogisticsV236PurchaseStepState,
)

private data class PurchaseOverlayCallbacks(
    val onDraftChange: (LogisticsPlanningDraft) -> Unit,
    val onRetryInvoices: () -> Unit,
    val onSheetDismiss: () -> Unit,
    val onSheetAdded: (String) -> Unit,
    val onReadyDateDismiss: () -> Unit,
    val onInvoiceRemovalDismiss: () -> Unit,
)

private data class PurchaseRemovalCallbacks(
    val onSupplierRemovalDismiss: () -> Unit,
    val onSupplierRemoved: (String) -> Unit,
)

/** v236 — dedicated suppliers/invoices step. Invoice lines stay deliberately hidden. */
@Composable
internal fun ShipmentV236PurchaseStep(
    draft: LogisticsPlanningDraft,
    invoices: List<LogisticsPurchaseInvoiceOptionUi>,
    state: LogisticsV236PurchaseStepState,
    actions: LogisticsV236PurchaseStepActions,
) {
    val suppliers = invoices.groupedBySupplier()
    var pendingSupplierId by rememberSaveable(draft.shipmentId) { mutableStateOf("") }
    var sheetSupplierId by rememberSaveable(draft.shipmentId) { mutableStateOf<String?>(null) }
    var invoiceRemovalId by rememberSaveable(draft.shipmentId) { mutableStateOf<String?>(null) }
    var supplierRemovalId by rememberSaveable(draft.shipmentId) { mutableStateOf<String?>(null) }
    var readyDateInvoiceId by rememberSaveable(draft.shipmentId) { mutableStateOf<String?>(null) }

    val targets = SupplierActionTargets(
        onAddInvoice = { sheetSupplierId = it },
        onRemoveSupplier = { supplierRemovalId = it },
        onRemoveInvoice = { invoiceRemovalId = it },
        onReadyDate = { readyDateInvoiceId = it },
    )
    PurchaseStepSection(
        draft, suppliers, state, pendingSupplierId,
        PurchaseSectionCallbacks(
            onSupplierSelected = { id ->
                pendingSupplierId = id
                if (draft.sources.none { it.supplierId == id }) sheetSupplierId = id
            },
            onAddSupplier = { pendingSupplierId = "" },
            supplierActions = supplierActions(draft, actions.onDraftChange, targets),
            onRetryInvoices = actions.onRetryInvoices,
        ),
    )
    PurchaseStepOverlays(
        context = PurchaseOverlayContext(draft, invoices, suppliers, state),
        overlay = PurchaseOverlayState(sheetSupplierId, readyDateInvoiceId, invoiceRemovalId, supplierRemovalId),
        callbacks = PurchaseOverlayCallbacks(
            actions.onDraftChange, actions.onRetryInvoices, { sheetSupplierId = null },
            { supplierId -> pendingSupplierId = supplierId; sheetSupplierId = null },
            { readyDateInvoiceId = null }, { invoiceRemovalId = null },
        ),
        removals = PurchaseRemovalCallbacks(
            onSupplierRemovalDismiss = { supplierRemovalId = null },
            onSupplierRemoved = { supplierId ->
                if (pendingSupplierId == supplierId) pendingSupplierId = ""
                supplierRemovalId = null
            },
        ),
    )
}

private fun supplierActions(
    draft: LogisticsPlanningDraft,
    onDraftChange: (LogisticsPlanningDraft) -> Unit,
    targets: SupplierActionTargets,
): (String) -> SupplierPlanningActions = { supplierId ->
    SupplierPlanningActions(
        onAddInvoice = { targets.onAddInvoice(supplierId) },
        onRemoveSupplier = { targets.onRemoveSupplier(supplierId) },
        onRemoveInvoice = targets.onRemoveInvoice,
        onPackageCountChange = { invoiceId, raw ->
            onDraftChange(draft.updateInvoicePlanningMetadata(invoiceId, packageCount = raw.filter(Char::isDigit).toIntOrNull()))
        },
        onWeightChange = { invoiceId, raw ->
            val normalized = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
            onDraftChange(draft.updateInvoicePlanningMetadata(invoiceId, weightKg = normalized.toBigDecimalOrNull()))
        },
        onReadyDateClick = targets.onReadyDate,
    )
}

@Composable
private fun PurchaseStepSection(
    draft: LogisticsPlanningDraft,
    suppliers: List<LogisticsPurchaseSupplierOptionUi>,
    state: LogisticsV236PurchaseStepState,
    pendingSupplierId: String,
    callbacks: PurchaseSectionCallbacks,
) {
    val selection = PurchaseSupplierSelection(
        selectedIds = draft.sources.map { it.supplierId }.distinct(),
        visibleIds = (draft.sources.map { it.supplierId }.distinct() + listOfNotNull(pendingSupplierId.takeIf(String::isNotBlank))).distinct(),
        pendingId = pendingSupplierId,
        showValidation = state.validationAttempt > 0,
    )
    LogisticsSection(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_1df31d7f7c8c),
        trailing = if (draft.sources.isEmpty()) null else "${selection.selectedIds.size} مورد • ${draft.sources.size} فاتورة",
    ) {
        PurchaseSupplierPicker(draft, suppliers, selection, callbacks.onSupplierSelected)
        PurchaseInvoiceLoadState(state, callbacks.onRetryInvoices)
        SupplierPlanningGroups(draft, suppliers, selection, state, callbacks.supplierActions)
        if (selection.selectedIds.isNotEmpty()) AddAnotherSupplierButton(state.canManage, callbacks.onAddSupplier)
        PurchaseSaveState(state, draft.sources.isEmpty(), selection.showValidation)
    }
}

@Composable
private fun PurchaseSupplierPicker(
    draft: LogisticsPlanningDraft,
    suppliers: List<LogisticsPurchaseSupplierOptionUi>,
    selection: PurchaseSupplierSelection,
    onSupplierSelected: (String) -> Unit,
) {
    LogisticsSearchPicker(
        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_70ff5f7b99e0),
        selectedLabel = supplierName(selection.pendingId, suppliers, draft.sources),
        options = suppliers
            .filter { it.supplierId !in selection.selectedIds || it.supplierId == selection.pendingId }
            .map { LogisticsPickerOption(it.supplierId, it.supplierName) },
        onSelect = { onSupplierSelected(it.id) },
        isError = selection.showValidation && selection.visibleIds.isEmpty(),
        errorText = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e13063f4777f),
        emptyMessage = "لا يوجد مورد لديه فواتير شراء دولية متاحة",
    )
}

@Composable
private fun PurchaseInvoiceLoadState(state: LogisticsV236PurchaseStepState, onRetry: () -> Unit) {
    when {
        state.invoiceLoading -> LogisticsLoading()
        !state.invoiceLoadError.isNullOrBlank() -> LogisticsError(state.invoiceLoadError, onRetry)
    }
}

@Composable
private fun SupplierPlanningGroups(
    draft: LogisticsPlanningDraft,
    suppliers: List<LogisticsPurchaseSupplierOptionUi>,
    selection: PurchaseSupplierSelection,
    state: LogisticsV236PurchaseStepState,
    actions: (String) -> SupplierPlanningActions,
) {
    if (selection.visibleIds.isEmpty()) {
        LogisticsEmpty("اختر موردًا ثم أضف فاتورة دولية واحدة على الأقل.")
        return
    }
    selection.visibleIds.forEach { supplierId ->
        val selectedSources = draft.sources.filter { it.supplierId == supplierId }
        val supplierName = suppliers.firstOrNull { it.supplierId == supplierId }?.supplierName
            ?: selectedSources.firstOrNull()?.supplierNameSnapshot.orEmpty().ifBlank { "المورد" }
        SupplierPlanningGroup(supplierName, selectedSources, state.canManage, selection.showValidation, actions(supplierId))
    }
}

@Composable
private fun AddAnotherSupplierButton(canManage: Boolean, onClick: () -> Unit) {
    VertoOutlinedButton(
        onClick = onClick,
        enabled = canManage,
        modifier = Modifier.fillMaxWidth().heightIn(min = VertoSize.minTouchTarget),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(VertoSize.iconMedium))
        Spacer(Modifier.width(VertoSpacing.xs))
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_6e77e390de58))
    }
}

@Composable
private fun PurchaseSaveState(state: LogisticsV236PurchaseStepState, noInvoices: Boolean, showValidation: Boolean) {
    if (state.saving) {
        val savingDescription = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_76ecc72fdfeb)
        Row(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = savingDescription },
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(VertoSize.iconSmall), strokeWidth = VertoStroke.progress)
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_79efbefd8692), style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
    if (!state.saveError.isNullOrBlank()) LogisticsError(state.saveError)
    if (showValidation && noInvoices) {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5014ba36d0c1), color = ErrorColor, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PurchaseStepOverlays(
    context: PurchaseOverlayContext,
    overlay: PurchaseOverlayState,
    callbacks: PurchaseOverlayCallbacks,
    removals: PurchaseRemovalCallbacks,
) {
    PurchaseInvoiceSheetOverlay(context, overlay.sheetSupplierId, callbacks)
    ReadyDateOverlay(context.draft, overlay.readyDateInvoiceId, callbacks.onDraftChange, callbacks.onReadyDateDismiss)
    InvoiceRemovalOverlay(context.draft, overlay.invoiceRemovalId, callbacks.onDraftChange, callbacks.onInvoiceRemovalDismiss)
    SupplierRemovalOverlay(
        context.draft, overlay.supplierRemovalId, callbacks.onDraftChange,
        removals.onSupplierRemovalDismiss, removals.onSupplierRemoved,
    )
}

@Composable
private fun PurchaseInvoiceSheetOverlay(
    context: PurchaseOverlayContext,
    supplierId: String?,
    callbacks: PurchaseOverlayCallbacks,
) {
    supplierId ?: return
    val supplier = context.suppliers.firstOrNull { it.supplierId == supplierId }
    InvoiceMultiSelectSheet(
        state = LogisticsV236InvoiceSheetState(
            supplierName = supplier?.supplierName
                ?: context.draft.sources.firstOrNull { it.supplierId == supplierId }?.supplierNameSnapshot.orEmpty(),
            invoices = supplier?.invoices.orEmpty(),
            alreadySelected = context.draft.sources.filter { it.supplierId == supplierId }.map { it.invoiceId }.toSet(),
            loading = context.state.invoiceLoading,
            loadError = context.state.invoiceLoadError,
        ),
        actions = LogisticsV236InvoiceSheetActions(callbacks.onRetryInvoices, callbacks.onSheetDismiss) { ids ->
            val byId = context.invoices.associateBy { it.id }
            val updated = ids.fold(context.draft) { current, id ->
                byId[id]?.takeIf { it.selectable }?.let(current::addWholeInvoice) ?: current
            }
            if (updated != context.draft) callbacks.onDraftChange(updated)
            callbacks.onSheetAdded(supplierId)
        },
    )
}

@Composable
private fun ReadyDateOverlay(
    draft: LogisticsPlanningDraft,
    invoiceId: String?,
    onDraftChange: (LogisticsPlanningDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    invoiceId ?: return
    val source = draft.sources.firstOrNull { it.invoiceId == invoiceId }
    ReadyDatePickerDialog(
        initialDate = source?.expectedReadyAt,
        onDismiss = onDismiss,
        onConfirm = { selected ->
            onDraftChange(draft.updateInvoicePlanningMetadata(invoiceId, expectedReadyAt = selected))
            onDismiss()
        },
    )
}

@Composable
private fun InvoiceRemovalOverlay(
    draft: LogisticsPlanningDraft,
    invoiceId: String?,
    onDraftChange: (LogisticsPlanningDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    invoiceId ?: return
    ConfirmPlanningRemovalDialog(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_adfd3b712643),
        message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_525f2b8f5eca),
        onDismiss = onDismiss,
        onConfirm = { onDraftChange(draft.removeInvoice(invoiceId)); onDismiss() },
    )
}

@Composable
private fun SupplierRemovalOverlay(
    draft: LogisticsPlanningDraft,
    supplierId: String?,
    onDraftChange: (LogisticsPlanningDraft) -> Unit,
    onDismiss: () -> Unit,
    onRemoved: (String) -> Unit,
) {
    supplierId ?: return
    ConfirmPlanningRemovalDialog(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_3f0911893f60),
        message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_dbe41b824e07),
        onDismiss = onDismiss,
        onConfirm = { onDraftChange(draft.removeSupplier(supplierId)); onRemoved(supplierId) },
    )
}

@Composable
private fun SupplierPlanningGroup(
    supplierName: String,
    selectedSources: List<LogisticsShipmentSource>,
    canManage: Boolean,
    showValidation: Boolean,
    actions: SupplierPlanningActions,
) {
    val groupDescription = androidx.compose.ui.res.stringResource(
        com.verto.app.feature.shipment.R.string.shipment_ds_d5a0baea91ff,
        supplierName,
    )
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = groupDescription },
        color = BgCard,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(VertoStroke.thin, BorderColor),
    ) {
        Column(Modifier.padding(VertoSpacing.sm), verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm)) {
            SupplierPlanningHeader(supplierName)
            Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_38bd1a4075c9), style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            SelectedInvoiceList(selectedSources, canManage, showValidation, actions)
            VertoSecondaryButton(
                text = "إضافة فاتورة",
                onClick = actions.onAddInvoice,
                enabled = canManage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (selectedSources.isNotEmpty()) {
                TextButton(onClick = actions.onRemoveSupplier, enabled = canManage, modifier = Modifier.align(Alignment.Start)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0e94e9e0a5ad), color = ErrorColor)
                }
            }
        }
    }
}

@Composable
private fun SupplierPlanningHeader(supplierName: String) {
    Surface(color = AccentPrimary.copy(alpha = 0.06f), shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(VertoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
        ) {
            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = AccentPrimary)
            Text(supplierName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectedInvoiceList(
    sources: List<LogisticsShipmentSource>,
    canManage: Boolean,
    showValidation: Boolean,
    actions: SupplierPlanningActions,
) {
    if (sources.isEmpty()) {
        Text(
            if (showValidation) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5014ba36d0c1) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_12f546366adb),
            color = if (showValidation) ErrorColor else TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    sources.forEach { source ->
        key(source.invoiceId) {
            SelectedInvoicePlanningCard(
                source = source,
                canManage = canManage,
                showValidation = showValidation,
                actions = InvoicePlanningCardActions(
                    onRemove = { actions.onRemoveInvoice(source.invoiceId) },
                    onPackageCountChange = { actions.onPackageCountChange(source.invoiceId, it) },
                    onWeightChange = { actions.onWeightChange(source.invoiceId, it) },
                    onReadyDateClick = { actions.onReadyDateClick(source.invoiceId) },
                ),
            )
        }
    }
}

private fun supplierName(
    supplierId: String,
    suppliers: List<LogisticsPurchaseSupplierOptionUi>,
    selectedSources: List<LogisticsShipmentSource>,
): String? = supplierId.takeIf(String::isNotBlank)?.let { id ->
    suppliers.firstOrNull { it.supplierId == id }?.supplierName
        ?: selectedSources.firstOrNull { it.supplierId == id }?.supplierNameSnapshot
}
