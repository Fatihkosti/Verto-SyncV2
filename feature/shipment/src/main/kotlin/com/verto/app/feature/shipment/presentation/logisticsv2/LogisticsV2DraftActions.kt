package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import kotlinx.coroutines.*

internal fun LogisticsV2ViewModel.updateAccess(newAccess: LogisticsV2Access) {
        state.accessValue = newAccess
        if (!newAccess.canView) state.detailValue = LogisticsDetailUiState.PermissionDenied
    }
internal fun LogisticsV2ViewModel.retryCenter() { state.centerRefreshValue += 1L }
internal fun LogisticsV2ViewModel.clearOperationState() { state.operationValue = LogisticsOperationUiState.Idle }
internal fun LogisticsV2ViewModel.clearSelection() { state.detailValue = LogisticsDetailUiState.Idle }
internal fun LogisticsV2ViewModel.selectShipment(shipmentId: String) {
        if (!state.accessValue.canView) return setDetailDenied()
        viewModelScope.launch {
            state.detailValue = LogisticsDetailUiState.Loading
            runCatching { readWorkflow.detail(shipmentId) }
                .onSuccess { state.detailValue = LogisticsDetailUiState.Content(it) }
                .onFailure { state.detailValue = LogisticsDetailUiState.Error(ErrorHumanizer.humanize(it, "تحميل تفاصيل الشحنة")) }
        }
    }
internal fun LogisticsV2ViewModel.resumeShipmentDraft() {
        val candidate = state.draftValue as? LogisticsDraftUiState.ResumeAvailable ?: return
        if (draftOpenJob?.isActive == true) return
        draftOpenJob = viewModelScope.launch {
            state.draftValue = LogisticsDraftUiState.Loading
            openDraftIntoContent(candidate.shipmentId, "resume-draft")
        }
    }

internal fun LogisticsV2ViewModel.startNewShipmentDraft() {
        if (!state.accessValue.canManage || draftOpenJob?.isActive == true) return
        draftOpenJob = viewModelScope.launch {
            state.draftValue = LogisticsDraftUiState.Loading
            createFreshDraft()
        }
    }

internal suspend fun LogisticsV2ViewModel.createFreshDraft() {
        val requestId = requestIdFor("new-draft")
        runCatching { planningWorkflow.openDraft(null, requestId) }.onSuccess { opened ->
            state.removeRequestId("new-draft")
            savedStateHandle[LogisticsV2ViewModel.DRAFT_SAVED_STATE_KEY] = opened.shipmentId
            val progress = LogisticsDraftProgress(
                activeDraftShipmentId = opened.shipmentId,
                currentStep = LogisticsPlanningStep.BASICS,
                updatedAt = nextPersistenceTimestamp(),
            )
            runCatching { planningWorkflow.saveProgress(progress) }
                .onFailure { error ->
                    state.draftValue = LogisticsDraftUiState.Error(ErrorHumanizer.humanize(error, "حفظ تقدم المسودة محليًا"))
                    return
                }
            loadOpenedDraftContent(opened.shipment)
        }.onFailure { error ->
            state.draftValue = LogisticsDraftUiState.Error(ErrorHumanizer.humanize(error, "إنشاء مسودة الشحنة"))
        }
    }

internal suspend fun LogisticsV2ViewModel.openDraftIntoContent(shipmentId: String, requestKey: String) {
        val requestId = requestIdFor(requestKey)
        runCatching { planningWorkflow.openDraft(shipmentId, requestId) }.onSuccess { opened ->
            state.removeRequestId(requestKey)
            savedStateHandle[LogisticsV2ViewModel.DRAFT_SAVED_STATE_KEY] = opened.shipmentId
            val existing = runCatching { planningWorkflow.loadProgress() }.getOrNull()
            existing?.let { observePersistenceTimestamp(it.updatedAt) }
            val step = existing?.takeIf { it.activeDraftShipmentId == opened.shipmentId }?.currentStep
                ?: LogisticsPlanningStep.BASICS
            runCatching {
                planningWorkflow.saveProgress(
                    LogisticsDraftProgress(opened.shipmentId, step, nextPersistenceTimestamp()),
                )
            }.onFailure { error ->
                state.draftValue = LogisticsDraftUiState.Error(ErrorHumanizer.humanize(error, "حفظ تقدم المسودة محليًا"))
                return
            }
            loadOpenedDraftContent(opened.shipment)
        }.onFailure { error ->
            state.draftValue = LogisticsDraftUiState.Error(ErrorHumanizer.humanize(error, "فتح مسودة الشحنة"))
        }
    }

internal suspend fun LogisticsV2ViewModel.loadOpenedDraftContent(shipment: com.verto.app.feature.shipment.domain.model.LogisticsShipment) {
        runCatching {
            val employees = readWorkflow.activeEmployees().map { LogisticsEmployeeOption(it.id, it.name) }
            val countrySuggestions = readWorkflow.countrySuggestions()
            employees to countrySuggestions
        }
            .onSuccess { (employees, countrySuggestions) ->
                state.draftValue = LogisticsDraftUiState.Content(
                    draft = shipment.toHeaderDraft(),
                    employees = employees,
                    countrySuggestions = countrySuggestions,
                )
            }.onFailure { error ->
                state.draftValue = LogisticsDraftUiState.Error(ErrorHumanizer.humanize(error, "تحميل الموظفين"))
            }
    }
internal fun LogisticsV2ViewModel.updateDraftOriginCountry(value: String) = updateDraft { it.copy(origin = it.origin.copy(countryName = value)) }
internal fun LogisticsV2ViewModel.updateDraftOriginCity(value: String) = updateDraft { it.copy(origin = it.origin.copy(city = value)) }
internal fun LogisticsV2ViewModel.updateDraftDestinationCountry(value: String) = updateDraft { it.copy(destination = it.destination.copy(countryName = value)) }
internal fun LogisticsV2ViewModel.updateDraftDestinationCity(value: String) = updateDraft { it.copy(destination = it.destination.copy(city = value)) }
internal fun LogisticsV2ViewModel.updateDraftDepartureStation(value: String) = updateDraft {
        val place = value.toLogisticsPlanningPlace()
        it.copy(
            origin = it.origin.copy(
                countryName = place.country.ifBlank { it.originCountryName },
                city = place.city,
            ),
        )
    }
internal fun LogisticsV2ViewModel.updateDraftFinalArrivalStation(value: String) = updateDraft {
        val place = value.toLogisticsPlanningPlace()
        it.copy(
            destination = it.destination.copy(
                countryName = place.country.ifBlank { it.destinationCountryName },
                city = place.city,
            ),
        )
    }
internal fun LogisticsV2ViewModel.updateDraftEmployee(employeeId: String) {
        val current = state.draftValue as? LogisticsDraftUiState.Content ?: return
        val employee = current.employees.firstOrNull { it.id == employeeId } ?: return
        updateDraft { it.copy(employeeId = employee.id, employeeName = employee.name) }
    }
internal fun LogisticsV2ViewModel.createShipment(draft: CreateLogisticsShipmentDraft, onCreated: (String) -> Unit) {
        if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات المسودة غير مكتملة أو الصلاحية مفقودة")
        val content = state.draftValue as? LogisticsDraftUiState.Content
            ?: return operationError("مسودة الشحنة غير جاهزة")
        if (!content.draft.canContinue) return operationError("حدد المحطات والموظف المسؤول")
        if (state.operationValue is LogisticsOperationUiState.Working) return
        viewModelScope.launch {
            state.operationValue = LogisticsOperationUiState.Working("draft-next:${content.draft.shipmentId}")
            if (flushPendingSave()) {
                state.operationValue = LogisticsOperationUiState.Success("تم حفظ المسودة")
                refreshCenter()
                onCreated(content.draft.shipmentId)
            } else {
                state.operationValue = LogisticsOperationUiState.Error("تعذر حفظ المسودة؛ بقيت الشاشة مفتوحة")
            }
        }
    }
internal fun LogisticsV2ViewModel.backFromDraft(onBack: () -> Unit) {
        viewModelScope.launch {
            draftOpenJob?.join()
            if (flushPendingSave()) onBack()
        }
    }
internal fun LogisticsV2ViewModel.flushDraftBestEffort() = draftAutosave.flushBestEffort {
        (state.draftValue as? LogisticsDraftUiState.Content)?.draft
    }
internal fun LogisticsV2ViewModel.updateDraft(transform: (LogisticsShipmentHeaderDraft) -> LogisticsShipmentHeaderDraft) {
        val current = state.draftValue as? LogisticsDraftUiState.Content ?: return
        val updated = transform(current.draft)
        if (updated == current.draft) return
        state.draftValue = current.copy(draft = updated, saving = true, saveError = null)
        draftAutosave.schedule(updated)
    }
internal suspend fun LogisticsV2ViewModel.flushPendingSave(): Boolean =
        draftAutosave.flush { (state.draftValue as? LogisticsDraftUiState.Content)?.draft }
