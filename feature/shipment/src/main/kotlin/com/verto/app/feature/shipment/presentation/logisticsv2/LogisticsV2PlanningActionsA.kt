package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import kotlinx.coroutines.*

internal fun LogisticsV2ViewModel.loadRouteTemplates() {
        viewModelScope.launch {
            runCatching {
                val organizationId = readWorkflow.organizationId()
                readWorkflow.routeTemplates(organizationId)
            }.onSuccess { state.routeTemplatesValue = it }
                .onFailure { operationError(ErrorHumanizer.humanize(it, "تحميل قوالب المسار")) }
        }
    }

internal fun LogisticsV2ViewModel.saveRouteTemplate(template: LogisticsRouteTemplate) {
        if (!state.accessValue.canManage) return permissionError()
        viewModelScope.launch {
            runCatching { planningWorkflow.saveRouteTemplate(template) }
                .onSuccess { saved ->
                    state.routeTemplatesValue = (state.routeTemplatesValue.filterNot { it.id == saved.id } + saved).sortedBy { it.name }
                    state.operationValue = LogisticsOperationUiState.Success("تم حفظ قالب المسار")
                }
                .onFailure { operationError(ErrorHumanizer.humanize(it, "حفظ قالب المسار")) }
        }
    }

internal fun LogisticsV2ViewModel.loadPlanning(shipmentId: String) {
        if (!state.accessValue.canManage) return setPlanningDenied()
        viewModelScope.launch {
            state.planningValue = LogisticsPlanningUiState.Loading
            runCatching { readWorkflow.planning(shipmentId) }
                .onSuccess { content ->
                    restorePlanningProgress(shipmentId)
                    ensureRouteWorkspace(content.initialDraft)
                    val workspace = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
                    val customs = content.customsPlan
                    if (workspace != null && customs != null &&
                        (workspace.customsCheckpointName.isBlank() || workspace.customsAfterStationId.isBlank() || workspace.customsExpectedDurationMinutes == null)
                    ) {
                        setRouteWorkspace(
                            workspace.copy(
                                customsCheckpointName = customs.checkpointName,
                                customsAfterStationId = customs.afterStationId,
                                customsExpectedDurationMinutes = customs.expectedDurationMinutes,
                            )
                        )
                    }
                    state.planningValue = content
                    val shipment = runCatching { readWorkflow.aggregate(shipmentId).shipment }.getOrNull()
                    val countrySuggestions = runCatching { readWorkflow.countrySuggestions() }.getOrDefault(emptyList())
                    state.draftValue = LogisticsDraftUiState.Content(
                        draft = shipment?.toHeaderDraft() ?: LogisticsShipmentHeaderDraft(
                            shipmentId = content.shipmentId,
                            shipmentNumber = content.shipmentNumber,
                            origin = LogisticsDefinitionLocationDraft(
                                city = content.initialDraft.sourceLocation.toLogisticsPlanningPlace().city,
                            ),
                            destination = LogisticsDefinitionLocationDraft(
                                city = content.initialDraft.destinationLocation.toLogisticsPlanningPlace().city,
                            ),
                            employeeId = content.initialDraft.assigneeId,
                            employeeName = content.initialDraft.assigneeName,
                        ),
                        employees = content.employees,
                        countrySuggestions = countrySuggestions,
                    )
                }
                .onFailure { state.planningValue = LogisticsPlanningUiState.Error(ErrorHumanizer.humanize(it, "تحميل بيانات التخطيط")) }
        }
    }
internal suspend fun LogisticsV2ViewModel.restorePlanningProgress(shipmentId: String) {
        val savedShipmentId = savedStateHandle.get<String>(LogisticsV2ViewModel.PLANNING_SHIPMENT_SAVED_STATE_KEY)
        val savedStep = if (savedShipmentId == shipmentId) restoredPlanningStepOrNull() else null
        val durable = runCatching { planningWorkflow.loadProgress() }.getOrNull()
            ?.takeIf { it.activeDraftShipmentId == shipmentId }
        durable?.let { observePersistenceTimestamp(it.updatedAt) }
        val resolved = savedStep ?: durable?.currentStep ?: LogisticsPlanningStep.BASICS
        state.planningStepValue = resolved
        savedStateHandle[LogisticsV2ViewModel.PLANNING_SHIPMENT_SAVED_STATE_KEY] = shipmentId
        savedStateHandle[LogisticsV2ViewModel.PLANNING_STEP_SAVED_STATE_KEY] = resolved.name
        state.routeWorkspaceValue = runCatching { planningWorkflow.loadRouteWorkspace(shipmentId) }.getOrNull()
        state.routeWorkspaceValue?.let { observePersistenceTimestamp(it.updatedAt) }
        runCatching {
            planningWorkflow.saveProgress(LogisticsDraftProgress(shipmentId, resolved, nextPersistenceTimestamp()))
        }
    }

internal fun LogisticsV2ViewModel.setPlanningStep(shipmentId: String, step: LogisticsPlanningStep) {
        if (state.planningStepValue == step &&
            savedStateHandle.get<String>(LogisticsV2ViewModel.PLANNING_SHIPMENT_SAVED_STATE_KEY) == shipmentId
        ) return
        state.planningStepValue = step
        savedStateHandle[LogisticsV2ViewModel.PLANNING_SHIPMENT_SAVED_STATE_KEY] = shipmentId
        savedStateHandle[LogisticsV2ViewModel.PLANNING_STEP_SAVED_STATE_KEY] = step.name
        viewModelScope.launch {
            runCatching { planningWorkflow.saveProgress(LogisticsDraftProgress(shipmentId, step, nextPersistenceTimestamp())) }
                .onFailure { operationError(ErrorHumanizer.humanize(it, "حفظ تقدم التخطيط محليًا")) }
        }
    }

internal fun LogisticsV2ViewModel.updateRouteWorkspace(
        shipmentId: String,
        transform: (LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot,
    ) {
        val current = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId } ?: return
        val transformed = transform(current)
        val updated = transformed.copy(
            shipmentId = shipmentId,
            legs = transformed.legs.map { it.asV230PlanningLeg(it.mode) },
            incoterm = "",
            volumeM3 = "",
            palletCount = "",
            insuranceReference = "",
            pendingCosts = emptyList(),
            pendingDocuments = transformed.pendingDocuments,
        )
        if (updated != current) setRouteWorkspace(updated)
    }

internal fun LogisticsV2ViewModel.setRouteWorkspace(snapshot: LogisticsRouteWorkspaceSnapshot) {
        val stamped = snapshot.copy(updatedAt = nextPersistenceTimestamp())
        state.routeWorkspaceValue = stamped
        routeWorkspaceAutosave.schedule(stamped)
    }

internal fun LogisticsV2ViewModel.flushRouteWorkspaceBestEffort() = routeWorkspaceAutosave.flushBestEffort {
        state.routeWorkspaceValue
    }

internal fun LogisticsV2ViewModel.nextFromRoute(shipmentId: String, draft: LogisticsPlanningDraft) {
        if (!state.accessValue.canManage) return permissionError()
        val workspace = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            ?: return operationError("تعذر العثور على مسودة المسار")
        if (!workspace.v237TripValidation().isValid || !draft.isV237PersistableRoute()) {
            return operationError("راجع بيانات المسار قبل المتابعة")
        }
        launchOperation(
            key = "route-next:$shipmentId",
            successMessage = "تم حفظ المسار",
            onSuccess = {
                val current = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
                if (current?.returnToReviewAfterEdit == true) {
                    setRouteWorkspace(current.copy(returnToReviewAfterEdit = false))
                    setPlanningStep(shipmentId, LogisticsPlanningStep.REVIEW)
                } else {
                    setPlanningStep(shipmentId, LogisticsPlanningStep.CUSTOMS)
                }
            },
        ) { requestId ->
            val flushed = routeWorkspaceAutosave.flush {
                state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            }
            if (!flushed) error("تعذر حفظ مسودة المسار محليًا")
            planningWorkflow.saveRouteDraft(
                shipmentId = shipmentId,
                milestones = draft.milestones,
                legs = draft.legs.map { it.asV230PlanningLeg(it.mode) },
                transportIntent = com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportIntent(
                    kind = workspace.routeTransportPlanKind,
                    unifiedMode = workspace.unifiedTransportMode.takeIf {
                        workspace.routeTransportPlanKind == com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind.UNIFIED
                    },
                ),
                requestId = requestId,
            )
        }
    }
