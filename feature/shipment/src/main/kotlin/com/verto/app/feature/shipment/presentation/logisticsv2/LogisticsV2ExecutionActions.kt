package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.AppFailure
import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.*

internal fun LogisticsV2ViewModel.startTransport(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("start:$shipmentId", "بدأ نقل الشحنة", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> planningWorkflow.start(shipmentId, requestId) }
    }
internal fun LogisticsV2ViewModel.prepareMovement(shipmentId: String, draft: LogisticsMovementPreparationDraft) {
        if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات تجهيز الحركة غير مكتملة")
        launchOperation("movement-prepare:$shipmentId:${draft.legId}", "تم تجهيز الحركة دون بدء النقل", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> planningWorkflow.prepareMovement(shipmentId, draft, requestId) }
    }
internal fun LogisticsV2ViewModel.startPreparedMovement(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("movement-start:$shipmentId", "بدأت الحركة", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.journey(shipmentId, LogisticsNextAction.START_MOVEMENT, requestId) }
    }
internal fun LogisticsV2ViewModel.recordOperationalArrival(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("arrival:$shipmentId", "تم تسجيل الوصول", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.recordOperationalArrival(shipmentId, requestId) }
    }
internal fun LogisticsV2ViewModel.confirmUnload(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("unload:$shipmentId", "تم تأكيد التفريغ", {
            selectShipment(shipmentId)
        }) { requestId ->
            executionWorkflow.updateMilestoneHandling(shipmentId, LogisticsMilestoneHandlingStatus.UNLOADED, requestId)
        }
    }
internal fun LogisticsV2ViewModel.confirmLoad(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("load:$shipmentId", "تم تأكيد التحميل", {
            selectShipment(shipmentId)
        }) { requestId ->
            executionWorkflow.updateMilestoneHandling(shipmentId, LogisticsMilestoneHandlingStatus.LOADED, requestId)
        }
    }
internal fun LogisticsV2ViewModel.recordOperationalDeparture(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("departure:$shipmentId", "تم تسجيل المغادرة", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.recordOperationalDeparture(shipmentId, requestId) }
    }
internal fun LogisticsV2ViewModel.updateEta(shipmentId: String, legId: String, plannedArrivalAt: Long?) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("eta:$shipmentId:$legId", "تم تحديث موعد الوصول", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.updateEta(shipmentId, legId, plannedArrivalAt, requestId) }
    }
internal fun LogisticsV2ViewModel.updateFutureLeg(shipmentId: String, leg: LogisticsShipmentLeg, reason: String) {
        if (!state.accessValue.canManage) return permissionError()
        if (reason.isBlank()) return operationError("سبب تعديل المرحلة المستقبلية مطلوب")
        launchOperation("future-leg:$shipmentId:${leg.id}", "تم تحديث المرحلة المستقبلية مع حفظ سجل التغييرات", {
            selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.updateFutureLeg(shipmentId, leg, reason, requestId) }
    }
internal fun LogisticsV2ViewModel.insertUnplannedStation(shipmentId: String, draft: LogisticsUnplannedStationDraft) {
        if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات المحطة غير المخططة غير صحيحة")
        launchOperation("unplanned:$shipmentId", "تمت إضافة المحطة غير المخططة", { selectShipment(shipmentId) }) { requestId ->
            executionWorkflow.insertUnplannedStation(shipmentId, draft, requestId)
        }
    }
internal fun LogisticsV2ViewModel.correctActualTime(shipmentId: String, draft: LogisticsActualTimeCorrectionDraft) {
        if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات تصحيح الوقت غير صحيحة")
        launchOperation("actual-time-correction:$shipmentId:${draft.milestoneId}:${draft.target}", "تم تصحيح الوقت مع حفظ سجل التدقيق", { selectShipment(shipmentId) }) { requestId ->
            executionWorkflow.correctActualTime(shipmentId, draft, requestId)
        }
    }
internal fun LogisticsV2ViewModel.recordFollowUp(shipmentId: String, note: String) {
        if (!state.accessValue.canManage || note.isBlank()) return operationError("اكتب نتيجة المتابعة")
        launchOperation("follow-up:$shipmentId", "تم تسجيل المتابعة", {
            selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.followUp(shipmentId, note, requestId) }
    }
internal fun LogisticsV2ViewModel.cancelShipment(shipmentId: String, reason: String) {
        if (!state.accessValue.canManage || reason.isBlank()) return operationError("سبب الإلغاء مطلوب")
        launchOperation("cancel:$shipmentId", "تم إلغاء الشحنة", {
            clearDurableDraft(shipmentId); refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.cancel(shipmentId, reason, requestId) }
    }
internal fun LogisticsV2ViewModel.openDocument(shipmentId: String, documentId: String) {
        if (!state.accessValue.canView) return permissionError()
        viewModelScope.launch {
            runCatching { executionWorkflow.openDocument(shipmentId, documentId) }
                .onSuccess { opened ->
                    if (!opened) state.operationValue = LogisticsOperationUiState.Error("لا يوجد تطبيق محلي لفتح هذا المستند")
                }
                .onFailure { state.operationValue = LogisticsOperationUiState.Error(ErrorHumanizer.humanize(it, "فتح المستند")) }
        }
    }

internal fun LogisticsV2ViewModel.stagePlanningDocument(
        shipmentId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String,
        target: PlanningDocumentTarget,
    ) {
        if (!state.accessValue.canManage) return permissionError()
        val draftId = UUID.randomUUID().toString()
        val pending = LogisticsPendingDocumentDraft(
            draftId = draftId,
            sourceUri = sourceUri,
            displayName = displayName,
            mimeType = mimeType,
            sourceInvoiceId = target.sourceInvoiceId,
            milestoneId = target.milestoneId,
            legId = target.legId,
            isStaging = true,
        )
        updateRouteWorkspace(shipmentId) { current ->
            current.copy(pendingDocuments = current.pendingDocuments + pending)
        }
        launchPlanningDocumentStage(shipmentId, draftId)
    }

internal fun LogisticsV2ViewModel.retryPlanningDocument(shipmentId: String, draftId: String) {
        if (!state.accessValue.canManage) return permissionError()
        val pending = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            ?.pendingDocuments?.firstOrNull { it.draftId == draftId } ?: return
        if (pending.sourceUri.isBlank()) return operationError("أعد اختيار الملف لإضافته من جديد")
        replacePendingDocument(shipmentId, draftId) { it.copy(isStaging = true, errorMessage = null) }
        launchPlanningDocumentStage(shipmentId, draftId)
    }

internal fun LogisticsV2ViewModel.removePlanningDocument(shipmentId: String, draftId: String) {
        if (!state.accessValue.canManage) return permissionError()
        val pending = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            ?.pendingDocuments?.firstOrNull { it.draftId == draftId } ?: return
        if (pending.isStaging) return
        val privateUri = pending.privateUri
        if (privateUri.isNullOrBlank()) {
            updateRouteWorkspace(shipmentId) { current ->
                current.copy(pendingDocuments = current.pendingDocuments.filterNot { it.draftId == draftId })
            }
            return
        }
        viewModelScope.launch {
            runCatching { executionWorkflow.removeStagedDocument(shipmentId, privateUri) }
                .onSuccess {
                    updateRouteWorkspace(shipmentId) { current ->
                        current.copy(pendingDocuments = current.pendingDocuments.filterNot { it.draftId == draftId })
                    }
                }
                .onFailure { error ->
                    replacePendingDocument(shipmentId, draftId) {
                        it.copy(errorMessage = ErrorHumanizer.humanize(error, "إزالة المستند"))
                    }
                }
        }
    }

internal fun LogisticsV2ViewModel.openPlanningDocument(shipmentId: String, draftId: String) {
        if (!state.accessValue.canView) return permissionError()
        val pending = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            ?.pendingDocuments?.firstOrNull { it.draftId == draftId } ?: return
        val privateUri = pending.privateUri ?: return operationError("المستند غير جاهز للفتح")
        viewModelScope.launch {
            runCatching { executionWorkflow.openStagedDocument(shipmentId, privateUri, pending.mimeType) }
                .onSuccess { opened ->
                    if (!opened) state.operationValue = LogisticsOperationUiState.Error("لا يوجد تطبيق محلي لفتح هذا المستند")
                }
                .onFailure { state.operationValue = LogisticsOperationUiState.Error(ErrorHumanizer.humanize(it, "فتح المستند")) }
        }
    }

internal fun LogisticsV2ViewModel.deleteCustomsPlanDocument(shipmentId: String, document: LogisticsCustomsPlanDocument) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation(
            key = "customs-plan-document-delete:$shipmentId:${document.id}",
            successMessage = "تم حذف مستند التخطيط",
            onSuccess = { refreshed: LogisticsPlanningUiState.Content ->
                val current = state.planningValue as? LogisticsPlanningUiState.Content
                if (current != null && current.shipmentId == shipmentId) {
                    state.planningValue = current.copy(
                        customsPlan = refreshed.customsPlan,
                        customsPlanDocuments = refreshed.customsPlanDocuments,
                    )
                }
            },
        ) {
            planningWorkflow.deleteCustomsPlanDocument(shipmentId, document)
            readWorkflow.planning(shipmentId)
        }
    }

internal fun LogisticsV2ViewModel.openCustomsPlanDocument(shipmentId: String, document: LogisticsCustomsPlanDocument) {
        if (!state.accessValue.canView) return permissionError()
        if (document.shipmentId != shipmentId) return operationError("المستند لا يتبع هذه الشحنة")
        viewModelScope.launch {
            runCatching { executionWorkflow.openStagedDocument(shipmentId, document.privateUri, document.mimeType) }
                .onSuccess { opened ->
                    if (!opened) state.operationValue = LogisticsOperationUiState.Error("لا يوجد تطبيق محلي لفتح هذا المستند")
                }
                .onFailure { state.operationValue = LogisticsOperationUiState.Error(ErrorHumanizer.humanize(it, "فتح المستند")) }
        }
    }

internal fun LogisticsV2ViewModel.deletePlanningSavedDocument(shipmentId: String, documentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("planning-document-delete:$shipmentId:$documentId", "تم حذف المستند", {
            loadPlanning(shipmentId)
        }) { executionWorkflow.deleteDocument(shipmentId, documentId) }
    }

internal fun LogisticsV2ViewModel.launchPlanningDocumentStage(shipmentId: String, draftId: String) {
        val pending = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            ?.pendingDocuments?.firstOrNull { it.draftId == draftId } ?: return
        viewModelScope.launch {
            runCatching {
                executionWorkflow.stageDocument(
                    shipmentId = shipmentId,
                    draftId = draftId,
                    sourceUri = pending.sourceUri,
                    displayName = pending.displayName,
                    mimeType = pending.mimeType,
                )
            }.onSuccess { stored ->
                replacePendingDocument(shipmentId, draftId) {
                    it.copy(
                        sourceUri = "",
                        privateUri = stored.privateUri,
                        displayName = stored.displayName,
                        mimeType = stored.mimeType,
                        sizeBytes = stored.sizeBytes,
                        sha256 = stored.sha256,
                        isStaging = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                replacePendingDocument(shipmentId, draftId) {
                    it.copy(isStaging = false, errorMessage = planningDocumentErrorMessage(error))
                }
            }
        }
    }

internal fun LogisticsV2ViewModel.replacePendingDocument(
        shipmentId: String,
        draftId: String,
        transform: (LogisticsPendingDocumentDraft) -> LogisticsPendingDocumentDraft,
    ) {
        updateRouteWorkspace(shipmentId) { current ->
            current.copy(pendingDocuments = current.pendingDocuments.map { document ->
                if (document.draftId == draftId) transform(document) else document
            })
        }
    }

internal fun LogisticsV2ViewModel.planningDocumentErrorMessage(error: Throwable): String =
    when (val failure = ErrorClassifier.classify(error)) {
        is AppFailure.BusinessRule -> when (failure.code) {
            "LOGISTICS_DOCUMENT_TOO_LARGE" -> "حجم المستند أكبر من الحد المسموح."
            "LOGISTICS_DOCUMENT_UNSUPPORTED" -> "نوع المستند غير مدعوم. استخدم PDF أو صورة JPG/PNG/WebP."
            else -> ErrorHumanizer.humanize(error, "تجهيز المستند للحفظ")
        }
        is AppFailure.PermissionDenied -> "تعذر الاحتفاظ بصلاحية قراءة المستند. أعد اختياره."
        else -> ErrorHumanizer.humanize(error, "تجهيز المستند للحفظ")
    }
internal fun LogisticsV2ViewModel.recordJourneyAction(shipmentId: String, action: LogisticsNextAction) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("journey:$shipmentId:${action.name}", "تم تحديث رحلة الشحنة", {
            refreshCenter(); selectShipment(shipmentId)
        }) { requestId -> executionWorkflow.journey(shipmentId, action, requestId) }
    }
