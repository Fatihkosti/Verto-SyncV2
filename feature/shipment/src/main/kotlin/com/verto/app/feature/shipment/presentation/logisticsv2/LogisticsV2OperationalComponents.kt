package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.utils.ErrorHumanizer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.model.LogisticsRouteAnalyticsReadModel
import kotlinx.coroutines.launch

internal val LogisticsOperationUiState.isWorking: Boolean
    get() = this is LogisticsOperationUiState.Working

@Composable
internal fun LogisticsOperationFeedback(
    state: LogisticsOperationUiState,
    viewModel: LogisticsV2ViewModel = hiltViewModel(),
) {
    when (state) {
        LogisticsOperationUiState.Idle -> Unit
        is LogisticsOperationUiState.Working -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is LogisticsOperationUiState.Success -> Text(
            text = state.message,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        is LogisticsOperationUiState.Error -> Text(
            text = state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        is LogisticsOperationUiState.AwaitingHandoff -> CustodyHandoffConfirmationDialog(
            initial = state.draft,
            onDismiss = { viewModel.clearOperationState() },
            onRepack = { viewModel.requestCargoRepack(state.draft.shipmentId) },
            onConfirm = { viewModel.confirmHandoff(it) },
        )
        is LogisticsOperationUiState.AwaitingCustoms -> CustomsPickupDialog(
            initial = state.draft,
            onDismiss = { viewModel.clearOperationState() },
            onRepack = { viewModel.requestCargoRepack(state.draft.shipmentId) },
            onConfirm = { viewModel.confirmCustomsPickup(it) },
        )
        is LogisticsOperationUiState.AwaitingRepack -> CargoRepackDialog(
            initial = state.draft,
            onDismiss = { viewModel.clearOperationState() },
            onConfirm = { viewModel.confirmCargoRepack(it) },
        )
    }
}

internal fun LogisticsV2ViewModel.handoffSourceToFirstCarrier(shipmentId: String, sourceId: String) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    loadHandoffDraft { executionWorkflow.sourceHandoffDraft(shipmentId, sourceId) }
}

internal fun LogisticsV2ViewModel.recordOperationalHandoff(shipmentId: String) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    loadHandoffDraft { executionWorkflow.operationalHandoffDraft(shipmentId) }
}

internal fun LogisticsV2ViewModel.confirmHandoff(draft: LogisticsCustodyHandoffDraft) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    if (!draft.isValid) return operationError("أكمل بيانات التسليم والاستلام المطلوبة")
    val key = "handoff-confirm:${draft.shipmentId}:${draft.sourceId ?: "shipment"}"
    launchOperation(key, "تم تأكيد التسليم والاستلام", { selectShipment(draft.shipmentId) }) { requestId ->
        val sourceId = draft.sourceId
        if (sourceId != null) executionWorkflow.handoffSourceToFirstCarrier(draft.shipmentId, sourceId, draft, requestId)
        else executionWorkflow.recordOperationalHandoff(draft.shipmentId, draft, requestId)
    }
}

internal fun LogisticsV2ViewModel.requestCustomsPickup(shipmentId: String) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    if (operationBusy()) return
    viewModelScope.launch {
        runCatching { executionWorkflow.customsPickupDraft(shipmentId) }
            .onSuccess { operationAwaitingCustoms(it) }
            .onFailure { operationError(ErrorHumanizer.humanize(it, "تجهيز استلام المخلص")) }
    }
}

internal fun LogisticsV2ViewModel.confirmCustomsPickup(draft: LogisticsCustomsPickupDraft) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    if (!draft.isValid) return operationError("أكمل بيانات المخلص والاستلام")
    launchOperation("customs-start:${draft.shipmentId}", "بدأت إجراءات الجمارك", { selectShipment(draft.shipmentId) }) { requestId ->
        executionWorkflow.startCustoms(draft.shipmentId, draft, requestId)
    }
}

internal fun LogisticsV2ViewModel.completeCustoms(shipmentId: String) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    launchOperation("customs-complete:$shipmentId", "اكتملت إجراءات الجمارك", { selectShipment(shipmentId) }) { requestId ->
        executionWorkflow.completeCustoms(shipmentId, requestId)
    }
}

internal fun LogisticsV2ViewModel.requestCargoRepack(shipmentId: String) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    loadRepackDraft { executionWorkflow.cargoRepackDraft(shipmentId) }
}

internal fun LogisticsV2ViewModel.confirmCargoRepack(draft: LogisticsCargoRepackDraft) {
    if (!currentAccess().canManage) return operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    if (!draft.isValid) return operationError("أكمل بيانات إعادة التعبئة المطلوبة")
    launchOperation("cargo-repack:${draft.shipmentId}", "تم تسجيل إعادة التعبئة", { selectShipment(draft.shipmentId) }) { requestId ->
        executionWorkflow.recordCargoRepack(draft.shipmentId, draft, requestId)
    }
}

private fun LogisticsV2ViewModel.loadHandoffDraft(block: suspend () -> LogisticsCustodyHandoffDraft) {
    if (operationBusy()) return
    viewModelScope.launch {
        runCatching { block() }
            .onSuccess { operationAwaitingHandoff(it) }
            .onFailure { operationError(ErrorHumanizer.humanize(it, "تجهيز التأكيد")) }
    }
}

private fun LogisticsV2ViewModel.loadRepackDraft(block: suspend () -> LogisticsCargoRepackDraft) {
    if (operationBusy()) return
    viewModelScope.launch {
        runCatching { block() }
            .onSuccess { operationAwaitingRepack(it) }
            .onFailure { operationError(ErrorHumanizer.humanize(it, "تجهيز إعادة التعبئة")) }
    }
}

@Composable
internal fun LogisticsOperationalAnalyticsSection(analytics: LogisticsRouteAnalyticsReadModel) {
    if (analytics.carriers.isEmpty() && analytics.routes.isEmpty()) return
    LogisticsSection("تحليلات التشغيل") {
        analytics.carriers.take(3).forEach { carrier ->
            LogisticsLabeledValue(
                carrier.carrierName ?: carrier.carrierId,
                "مراحل ${carrier.handledLegCount} • متوسط ${logisticsDurationLabel(carrier.averageActualTransitMillis)} • طبيعي/متأخر/شديد ${carrier.normalCount}/${carrier.lateCount}/${carrier.veryLateCount} • تكلفة ${carrier.scopedCost.toPlainString()}",
            )
        }
        analytics.routes.take(3).forEach { route ->
            LogisticsLabeledValue(
                route.shipmentNumber,
                "محطات مخططة/فعلية ${route.plannedStopCount}/${route.actualStopCount} • غير مخططة ${route.unplannedStopCount} • زمن ${logisticsDurationLabel(route.totalElapsedMillis)} • تكلفة ${route.totalCost.toPlainString()} • عابر للحدود ${route.crossBorderCost.toPlainString()}",
            )
        }
    }
}
