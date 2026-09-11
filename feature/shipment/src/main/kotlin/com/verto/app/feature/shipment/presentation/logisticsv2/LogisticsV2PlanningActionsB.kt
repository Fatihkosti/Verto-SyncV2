package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.AppFailure
import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import kotlinx.coroutines.*

internal fun LogisticsV2ViewModel.nextFromPlanningBasics(onNext: () -> Unit) {
        val current = state.draftValue as? LogisticsDraftUiState.Content ?: return
        if (!current.draft.canContinue) return operationError("حدد محطة المغادرة والوصول والموظف المسؤول")
        if (state.operationValue is LogisticsOperationUiState.Working) return
        viewModelScope.launch {
            state.operationValue = LogisticsOperationUiState.Working("basics-next:${current.draft.shipmentId}")
            if (flushPendingSave()) {
                state.operationValue = LogisticsOperationUiState.Success("تم حفظ الأساسيات")
                onNext()
            } else {
                state.operationValue = LogisticsOperationUiState.Error("تعذر حفظ الأساسيات؛ بقيت الشاشة مفتوحة")
            }
        }
    }
internal fun LogisticsV2ViewModel.backFromPlanningBasics(onBack: () -> Unit) {
        viewModelScope.launch { if (flushPendingSave()) onBack() }
    }
internal fun LogisticsV2ViewModel.updatePurchasePlanDraft(updated: LogisticsPlanningDraft) {
        val current = state.planningValue as? LogisticsPlanningUiState.Content ?: return
        if (updated.shipmentId != current.shipmentId || updated == current.initialDraft) return
        state.planningValue = current.copy(initialDraft = updated, purchaseSaving = true, purchaseSaveError = null)
        purchaseAutosave.schedule(updated)
    }
internal fun LogisticsV2ViewModel.retryPurchaseInvoices(shipmentId: String) {
        val current = state.planningValue as? LogisticsPlanningUiState.Content ?: return
        if (current.shipmentId != shipmentId || current.purchaseInvoiceLoading) return
        state.planningValue = current.copy(purchaseInvoiceLoading = true, purchaseInvoiceLoadError = null)
        viewModelScope.launch {
            runCatching { readWorkflow.planning(shipmentId) }
                .onSuccess { refreshed ->
                    val latest = state.planningValue as? LogisticsPlanningUiState.Content ?: return@onSuccess
                    if (latest.shipmentId != shipmentId) return@onSuccess
                    state.planningValue = latest.copy(
                        purchaseInvoices = refreshed.purchaseInvoices,
                        inventoryCatalog = refreshed.inventoryCatalog,
                        purchaseInvoiceLoading = false,
                        purchaseInvoiceLoadError = refreshed.purchaseInvoiceLoadError,
                    )
                }
                .onFailure { error ->
                    val latest = state.planningValue as? LogisticsPlanningUiState.Content ?: return@onFailure
                    if (latest.shipmentId != shipmentId) return@onFailure
                    state.planningValue = latest.copy(
                        purchaseInvoiceLoading = false,
                        purchaseInvoiceLoadError = ErrorHumanizer.humanize(error, "تحميل الفواتير الدولية"),
                    )
                }
        }
    }
internal fun LogisticsV2ViewModel.bindPurchaseLineInventoryItem(invoiceItemId: String, inventoryItemId: String) {
        if (!state.accessValue.canManage || state.operationValue is LogisticsOperationUiState.Working) return
        viewModelScope.launch {
            state.operationValue = LogisticsOperationUiState.Working("inventory-bind:$invoiceItemId")
            runCatching { planningWorkflow.bindExisting(invoiceItemId, inventoryItemId) }
                .onSuccess { resolvedId ->
                    applyResolvedPurchaseLineIdentity(invoiceItemId, resolvedId)
                    state.operationValue = LogisticsOperationUiState.Success("تم ربط الصنف بالمخزون")
                }
                .onFailure { error -> operationError(ErrorHumanizer.humanize(error, "ربط الصنف بالمخزون")) }
        }
    }

internal fun LogisticsV2ViewModel.createPurchaseLineInventoryItem(invoiceItemId: String) {
        if (!state.accessValue.canManage || state.operationValue is LogisticsOperationUiState.Working) return
        viewModelScope.launch {
            state.operationValue = LogisticsOperationUiState.Working("inventory-create:$invoiceItemId")
            runCatching { planningWorkflow.createNew(invoiceItemId) }
                .onSuccess { resolvedId ->
                    applyResolvedPurchaseLineIdentity(invoiceItemId, resolvedId)
                    state.operationValue = LogisticsOperationUiState.Success("تم إنشاء الصنف وربطه بكمية صفر")
                }
                .onFailure { error -> operationError(ErrorHumanizer.humanize(error, "إنشاء صنف المخزون")) }
        }
    }

internal suspend fun LogisticsV2ViewModel.applyResolvedPurchaseLineIdentity(invoiceItemId: String, inventoryItemId: String) {
        val current = state.planningValue as? LogisticsPlanningUiState.Content ?: return
        val updatedDraft = current.initialDraft.copy(
            lines = current.initialDraft.lines.map { line ->
                if (line.sourceInvoiceItemId == invoiceItemId) line.copy(inventoryItemId = inventoryItemId) else line
            },
        )
        val updatedInvoices = current.purchaseInvoices.map { invoice ->
            invoice.copy(
                lines = invoice.lines.map { line ->
                    if (line.sourceInvoiceItemId == invoiceItemId) line.copy(inventoryItemId = inventoryItemId) else line
                },
            )
        }
        val catalog = runCatching { planningWorkflow.catalog() }.getOrDefault(current.inventoryCatalog)
        state.planningValue = current.copy(
            initialDraft = updatedDraft,
            purchaseInvoices = updatedInvoices,
            inventoryCatalog = catalog,
            purchaseSaving = updatedDraft != current.initialDraft,
            purchaseSaveError = null,
        )
        if (updatedDraft != current.initialDraft) purchaseAutosave.schedule(updatedDraft)
    }
internal fun LogisticsV2ViewModel.nextFromPurchasePlan(onNext: () -> Unit) {
        val current = state.planningValue as? LogisticsPlanningUiState.Content ?: return
        if (!current.initialDraft.isPurchaseStepReady(current.purchaseInvoices)) return operationError("أكمل الموردين والفواتير والحمولة قبل المتابعة")
        if (state.operationValue is LogisticsOperationUiState.Working) return
        viewModelScope.launch {
            state.operationValue = LogisticsOperationUiState.Working("purchase-next:${current.shipmentId}")
            if (flushPurchasePlan()) {
                state.operationValue = LogisticsOperationUiState.Success("تم حفظ حمولة الشراء")
                onNext()
            } else state.operationValue = LogisticsOperationUiState.Error("تعذر حفظ حمولة الشراء؛ بقيت الشاشة مفتوحة")
        }
    }
internal fun LogisticsV2ViewModel.backFromPurchasePlan(onBack: () -> Unit) {
        viewModelScope.launch { if (flushPurchasePlan()) onBack() }
    }
internal fun LogisticsV2ViewModel.flushPurchasePlanBestEffort() = purchaseAutosave.flushBestEffort { (state.planningValue as? LogisticsPlanningUiState.Content)?.initialDraft }
internal suspend fun LogisticsV2ViewModel.flushPurchasePlan(): Boolean =
        purchaseAutosave.flush { (state.planningValue as? LogisticsPlanningUiState.Content)?.initialDraft }
internal fun LogisticsV2ViewModel.onPurchaseSaveResult(revision: Long, result: Result<com.verto.app.feature.shipment.domain.model.LogisticsShipment>) {
        if (revision != purchaseAutosave.revision) return
        val current = state.planningValue as? LogisticsPlanningUiState.Content ?: return
        result.onSuccess { saved ->
            val persisted = current.initialDraft.copy(
                expectedDepartureAt = saved.expectedDepartureAt, expectedArrivalAt = saved.expectedArrivalAt,
                transportDetails = saved.transportDetails,
            )
            state.planningValue = current.copy(initialDraft = persisted, purchaseSaving = false, purchaseSaveError = null)
        }.onFailure { error ->
            state.planningValue = current.copy(
                purchaseSaving = false,
                purchaseSaveError = purchasePlanErrorMessage(error),
            )
        }
    }

internal fun LogisticsV2ViewModel.purchasePlanErrorMessage(error: Throwable): String =
    when (val failure = ErrorClassifier.classify(error)) {
        is AppFailure.BusinessRule -> when (failure.code) {
            "SHIPMENT_INVENTORY_LINK_REQUIRED" ->
                "يوجد صنف في فاتورة الشراء غير مرتبط بالمخزون. اربطه بالمخزون ثم أعد المحاولة."
            "SHIPMENT_PURCHASE_INVOICE_UNAVAILABLE",
            "SHIPMENT_PURCHASE_INVOICE_CONFLICT" ->
                "إحدى الفواتير لم تعد متاحة لأنها مرتبطة بشحنة أخرى. حدّث الاختيار ثم أعد المحاولة."
            else -> ErrorHumanizer.humanize(error, "حفظ حمولة الشراء")
        }
        else -> ErrorHumanizer.humanize(error, "حفظ حمولة الشراء")
    }
internal fun LogisticsV2ViewModel.clearCommittedPendingDocumentsAndReload(shipmentId: String) {
        viewModelScope.launch {
            val current = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            if (current != null && current.pendingDocuments.isNotEmpty()) {
                val cleaned = current.copy(
                    pendingDocuments = emptyList(),
                    updatedAt = nextPersistenceTimestamp(),
                )
                state.routeWorkspaceValue = cleaned
                runCatching { planningWorkflow.saveRouteWorkspace(cleaned) }
                    .onFailure { operationError(ErrorHumanizer.humanize(it, "تنظيف سجل المستندات المؤقتة")) }
            }
            loadPlanning(shipmentId)
        }
    }
