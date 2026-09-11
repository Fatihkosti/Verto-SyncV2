package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.ResolveLogisticsInventoryIdentityUseCase
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import com.verto.app.utils.ErrorHumanizer

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class LogisticsV2ViewModel @Inject constructor(
    internal val savedStateHandle: SavedStateHandle,
    internal val planningWorkflow: LogisticsPlanningWorkflow,
    internal val executionWorkflow: LogisticsExecutionWorkflow,
    internal val receivingWorkflow: LogisticsReceivingSettlementWorkflow,
    internal val readWorkflow: LogisticsReadWorkflow,
) : ViewModel() {
    internal val state = LogisticsV2StateStore(restoredPlanningStep())
    internal val draftState: StateFlow<LogisticsDraftUiState> = state.draftState
    internal val planningStep: StateFlow<LogisticsPlanningStep> = state.planningStep
    internal val routeWorkspace: StateFlow<LogisticsRouteWorkspaceSnapshot?> = state.routeWorkspace
    internal val routeTemplates: StateFlow<List<LogisticsRouteTemplate>> = state.routeTemplates
    internal var draftOpenJob: Job? = null
    internal val draftAutosave = LogisticsDraftAutosaveCoordinator(
        scope = viewModelScope,
        save = planningWorkflow::saveHeader,
        onResult = ::onDraftSaveResult,
    )
    internal val purchaseAutosave = LogisticsPurchasePlanAutosaveCoordinator(
        scope = viewModelScope,
        save = planningWorkflow::savePurchasePlan,
        onResult = ::onPurchaseSaveResult,
    )
    internal val routeWorkspaceAutosave = LogisticsRouteWorkspaceAutosaveCoordinator(
        scope = viewModelScope,
        save = planningWorkflow::saveRouteWorkspace,
        onFailure = { operationError(ErrorHumanizer.humanize(it, "حفظ مسودة المسار محليًا")) },
    )
    internal val operationState: StateFlow<LogisticsOperationUiState> = state.operationState
    internal val detailState: StateFlow<LogisticsDetailUiState> = state.detailState
    internal val planningState: StateFlow<LogisticsPlanningUiState> = state.planningState
    internal val receivingState: StateFlow<LogisticsReceivingUiState> = state.receivingState
    internal var receivingDraftSaveJob: Job? = null
    internal var latestReceivingDraft: LogisticsFinalReceivingDraftSnapshot? = null
    internal val costsState: StateFlow<LogisticsCostsUiState> = state.costsState
    internal val partnersState: StateFlow<LogisticsPartnersUiState> = state.partnersState
    internal val centerState: StateFlow<LogisticsCenterUiState> = combine(state.access, state.centerRefresh) { currentAccess, _ -> currentAccess }
        .flatMapLatest { currentAccess ->
            if (!currentAccess.canView) return@flatMapLatest flowOf(LogisticsCenterUiState.PermissionDenied)
            readWorkflow.observeOrganizationId().distinctUntilChanged().flatMapLatest { organizationId ->
                if (organizationId.isBlank()) flowOf(LogisticsCenterUiState.Loading)
                else readWorkflow.observeUnified(organizationId)
                    .map { records ->
                        val aggregates = records.mapNotNull { record ->
                            record.v2Shipment?.let { shipment ->
                                readWorkflow.aggregate(organizationId, shipment.id)?.let { shipment.id to it }
                            }
                        }.toMap()
                        unifiedCenterState(currentAccess, records, System.currentTimeMillis(), aggregates).withClosedShortageLabels(aggregates)
                    }
                    .onStart { emit(LogisticsCenterUiState.Loading) }
                    .catch { emit(LogisticsCenterUiState.Error(ErrorHumanizer.humanize(it, "تحميل الشحنات"))) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogisticsCenterUiState.Loading)
    internal fun currentAccess(): LogisticsV2Access = state.accessValue
    internal companion object {
        const val DRAFT_SAVED_STATE_KEY = "logisticsDraftShipmentId"
        const val PLANNING_SHIPMENT_SAVED_STATE_KEY = "logisticsPlanningShipmentId"
        const val PLANNING_STEP_SAVED_STATE_KEY = "logisticsPlanningStep"
    }
    private var lastPersistenceTimestamp = 0L
    @Synchronized
    internal fun observePersistenceTimestamp(value: Long) {
        if (value > lastPersistenceTimestamp) lastPersistenceTimestamp = value
    }
    @Synchronized
    internal fun nextPersistenceTimestamp(): Long {
        val now = System.currentTimeMillis()
        lastPersistenceTimestamp = maxOf(now, lastPersistenceTimestamp + 1L)
        return lastPersistenceTimestamp
    }
    internal fun restoredPlanningStep(): LogisticsPlanningStep = restoredPlanningStepOrNull() ?: LogisticsPlanningStep.BASICS
    internal fun restoredPlanningStepOrNull(): LogisticsPlanningStep? = savedStateHandle.get<String>(PLANNING_STEP_SAVED_STATE_KEY)
        ?.let { runCatching { LogisticsPlanningStep.valueOf(it) }.getOrNull() }
    internal fun clearDurableDraft(shipmentId: String) {
        if (savedStateHandle.get<String>(DRAFT_SAVED_STATE_KEY) == shipmentId) savedStateHandle[DRAFT_SAVED_STATE_KEY] = null
        if (savedStateHandle.get<String>(PLANNING_SHIPMENT_SAVED_STATE_KEY) == shipmentId) {
            savedStateHandle[PLANNING_SHIPMENT_SAVED_STATE_KEY] = null
            savedStateHandle[PLANNING_STEP_SAVED_STATE_KEY] = null
        }
        val stagedUris = state.routeWorkspaceValue
            ?.takeIf { it.shipmentId == shipmentId }
            ?.pendingDocuments
            ?.mapNotNull { it.privateUri }
            ?.distinct()
            .orEmpty()
        state.routeWorkspaceValue = null
        viewModelScope.launch {
            stagedUris.forEach { privateUri ->
                runCatching { executionWorkflow.removeStagedDocument(shipmentId, privateUri) }
            }
            runCatching { planningWorkflow.clearShipment(shipmentId) }
        }
    }
    internal fun refreshCenter() { state.centerRefreshValue += 1L }
    internal fun permissionError() = operationError("لا تملك صلاحية تنفيذ هذا الإجراء")
    internal fun operationError(message: String) { state.operationValue = LogisticsOperationUiState.Error(message) }
    internal fun operationBusy(): Boolean = state.operationValue is LogisticsOperationUiState.Working
    internal fun operationAwaitingHandoff(draft: LogisticsCustodyHandoffDraft) { state.operationValue = LogisticsOperationUiState.AwaitingHandoff(draft) }
    internal fun operationAwaitingCustoms(draft: LogisticsCustomsPickupDraft) { state.operationValue = LogisticsOperationUiState.AwaitingCustoms(draft) }
    internal fun operationAwaitingRepack(draft: LogisticsCargoRepackDraft) { state.operationValue = LogisticsOperationUiState.AwaitingRepack(draft) }
    internal fun setDetailDenied() { state.detailValue = LogisticsDetailUiState.PermissionDenied }
    internal fun setPlanningDenied() { state.planningValue = LogisticsPlanningUiState.PermissionDenied }
    internal fun setReceivingDenied() { state.receivingValue = LogisticsReceivingUiState.PermissionDenied }
    internal fun setCostsDenied() { state.costsValue = LogisticsCostsUiState.PermissionDenied }
    internal fun setPartnersDenied() { state.partnersValue = LogisticsPartnersUiState.PermissionDenied }
    internal fun requestIdFor(key: String): String = state.requestIdFor(key)
    internal fun <T> launchOperation(key: String, successMessage: String, onSuccess: (T) -> Unit = {}, block: suspend (String) -> T) {
        if (state.operationValue is LogisticsOperationUiState.Working) return
        val requestId = requestIdFor(key)
        viewModelScope.launch {
            state.operationValue = LogisticsOperationUiState.Working(key)
            runCatching { block(requestId) }
                .onSuccess { result ->
                    state.removeRequestId(key)
                    state.operationValue = LogisticsOperationUiState.Success(successMessage)
                    onSuccess(result)
                }
                .onFailure { state.operationValue = LogisticsOperationUiState.Error(ErrorHumanizer.humanize(it, "تنفيذ العملية")) }
        }
    }

internal fun openShipmentDraft() {
        if (!state.accessValue.canManage) {
            state.draftValue = LogisticsDraftUiState.PermissionDenied
            return
        }
        if (state.draftValue is LogisticsDraftUiState.Content ||
            state.draftValue is LogisticsDraftUiState.ResumeAvailable ||
            draftOpenJob?.isActive == true
        ) return
        val navigationDraftId = savedStateHandle.get<String>("shipmentId")?.trim()?.takeIf(String::isNotBlank)
            ?: savedStateHandle.get<String>(LogisticsV2ViewModel.DRAFT_SAVED_STATE_KEY)?.trim()?.takeIf(String::isNotBlank)
        draftOpenJob = viewModelScope.launch {
            state.draftValue = LogisticsDraftUiState.Loading
            if (navigationDraftId != null) {
                openDraftIntoContent(navigationDraftId, "open-draft-explicit")
                return@launch
            }
            val progress = runCatching { planningWorkflow.loadProgress() }.getOrNull()
            progress?.let { observePersistenceTimestamp(it.updatedAt) }
            if (progress != null) {
                val aggregate = runCatching { readWorkflow.aggregate(progress.activeDraftShipmentId) }.getOrNull()
                if (aggregate?.shipment?.state == LogisticsShipmentState.DRAFT) {
                    state.draftValue = LogisticsDraftUiState.ResumeAvailable(
                        shipmentId = aggregate.shipment.id,
                        shipmentNumber = aggregate.shipment.shipmentNumber,
                        currentStep = progress.currentStep,
                    )
                    return@launch
                }
                runCatching { planningWorkflow.clearShipment(progress.activeDraftShipmentId) }
            }
            createFreshDraft()
        }
    }

internal fun onDraftSaveResult(revision: Long, result: Result<com.verto.app.feature.shipment.domain.model.LogisticsShipment>) {
        if (revision != draftAutosave.revision) return
        val current = state.draftValue as? LogisticsDraftUiState.Content ?: return
        result.onSuccess { saved ->
            val persisted = current.draft.copy(
                origin = LogisticsDefinitionLocationDraft(
                    countryName = saved.originLocationDetails?.countryNameSnapshot.orEmpty(),
                    city = saved.originLocationDetails?.city.orEmpty().ifBlank { saved.sourceLocation.toLogisticsPlanningPlace().city },
                ),
                destination = LogisticsDefinitionLocationDraft(
                    countryName = saved.destinationLocationDetails?.countryNameSnapshot.orEmpty(),
                    city = saved.destinationLocationDetails?.city.orEmpty().ifBlank { saved.destinationLocation.toLogisticsPlanningPlace().city },
                ),
                employeeId = saved.assignee?.employeeId.orEmpty(), employeeName = saved.assignee?.employeeName.orEmpty(),
            )
            state.draftValue = current.copy(draft = persisted, saving = false, saveError = null)
            val planning = state.planningValue as? LogisticsPlanningUiState.Content
            if (planning != null && planning.shipmentId == saved.id) {
                val syncedMilestones = planning.initialDraft.milestones.map { milestone ->
                    when (milestone.type) {
                        com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.ORIGIN -> milestone.copy(
                            location = saved.sourceLocation,
                            placeName = saved.sourceLocation,
                        )
                        com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.DESTINATION -> milestone.copy(
                            location = saved.destinationLocation,
                            placeName = saved.destinationLocation,
                        )
                        else -> milestone
                    }
                }
                state.planningValue = planning.copy(
                    initialDraft = planning.initialDraft.copy(
                        sourceLocation = saved.sourceLocation,
                        destinationLocation = saved.destinationLocation,
                        assigneeId = saved.assignee?.employeeId.orEmpty(),
                        assigneeName = saved.assignee?.employeeName.orEmpty(),
                        milestones = syncedMilestones,
                    ),
                )
            }
        }.onFailure { error ->
            state.draftValue = current.copy(saving = false, saveError = ErrorHumanizer.humanize(error, "حفظ المسودة"))
        }
    }

internal fun ensureRouteWorkspace(draft: LogisticsPlanningDraft) {
        if (draft.shipmentId.isBlank()) return
        val existing = state.routeWorkspaceValue?.takeIf { it.shipmentId == draft.shipmentId }
        val persistedMilestones = existing?.milestones?.takeIf { it.size >= 2 } ?: draft.milestones
        val seedMilestones = if (persistedMilestones.size >= 2) persistedMilestones else listOf(
            com.verto.app.feature.shipment.domain.model.LogisticsMilestone(
                id = "ui-stop:${UUID.randomUUID()}", shipmentId = draft.shipmentId,
                type = com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.ORIGIN, order = 0,
                location = draft.sourceLocation, placeName = draft.sourceLocation,
            ),
            com.verto.app.feature.shipment.domain.model.LogisticsMilestone(
                id = "ui-stop:${UUID.randomUUID()}", shipmentId = draft.shipmentId,
                type = com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.DESTINATION, order = 1,
                location = draft.destinationLocation, placeName = draft.destinationLocation,
            ),
        )
        val baseMilestones = seedMilestones
            .filter { it.type != com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.CUSTOMS }
            .sortedBy { it.order }
            .mapIndexed { index, milestone ->
                val anchored = when (milestone.type) {
                    com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.ORIGIN ->
                        milestone.withDefinitionLocation(draft.sourceLocation)
                    com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.DESTINATION ->
                        milestone.withDefinitionLocation(draft.destinationLocation)
                    else -> milestone
                }
                anchored.copy(order = index)
            }
        val previousLegs = existing?.legs?.takeIf { it.isNotEmpty() } ?: draft.legs
        val normalizedLegs = reconcileLegs(draft, baseMilestones, previousLegs)
        val candidate = (existing ?: LogisticsRouteWorkspaceSnapshot(shipmentId = draft.shipmentId)).copy(
            incoterm = "",
            volumeM3 = "",
            palletCount = "",
            insuranceReference = "",
            milestones = baseMilestones,
            legs = normalizedLegs.map { it.asV230PlanningLeg(it.mode) },
            expandedSection = existing?.expandedSection?.takeIf { it.startsWith("V237_") } ?: V237_CURSOR_TRIP_TYPE,
            pendingCosts = emptyList(),
            pendingDocuments = existing?.pendingDocuments.orEmpty(),
        ).withV237CargoDefaults(draft)
        if (candidate != existing) setRouteWorkspace(candidate)
    }

internal fun nextFromCustoms(shipmentId: String) {
        if (!state.accessValue.canManage) return permissionError()
        val workspace = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
            ?: return operationError("تعذر العثور على مسودة التخطيط")
        if (!workspace.v238CustomsValidation().isValid) return operationError("راجع بيانات الجمارك قبل المتابعة")
        launchOperation(
            key = "customs-plan:$shipmentId",
            successMessage = "تم حفظ تخطيط الجمارك",
            onSuccess = { refreshed: LogisticsPlanningUiState.Content ->
                val currentWorkspace = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
                if (currentWorkspace != null) {
                    setRouteWorkspace(
                        currentWorkspace.copy(
                            pendingDocuments = currentWorkspace.pendingDocuments.filterNot { it.milestoneId == V238_CUSTOMS_DOCUMENT_TARGET },
                            returnToReviewAfterEdit = false,
                        )
                    )
                }
                val current = state.planningValue as? LogisticsPlanningUiState.Content
                if (current != null && current.shipmentId == shipmentId) {
                    state.planningValue = current.copy(
                        customsPlan = refreshed.customsPlan,
                        customsPlanDocuments = refreshed.customsPlanDocuments,
                        customsCheckpointSuggestions = refreshed.customsCheckpointSuggestions,
                    )
                }
                setPlanningStep(shipmentId, LogisticsPlanningStep.REVIEW)
            },
        ) {
            val flushed = routeWorkspaceAutosave.flush { state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId } }
            if (!flushed) error("تعذر حفظ مسودة الجمارك محليًا")
            val latest = state.routeWorkspaceValue?.takeIf { it.shipmentId == shipmentId }
                ?: error("تعذر العثور على مسودة التخطيط")
            planningWorkflow.saveCustomsPlan(shipmentId, latest)
            readWorkflow.planning(shipmentId)
        }
    }

internal fun savePlanning(shipmentId: String, draft: LogisticsPlanningDraft, onSaved: () -> Unit) {
        if (!state.accessValue.canManage || !draft.isValid) return operationError("بيانات التخطيط غير مكتملة")
        if (draft.pendingDocuments.any { !it.isReady }) {
            return operationError("أكمل تجهيز المستندات أو أزل المستند الذي تعذر تجهيزه")
        }
        val ready = draft.submitAction == LogisticsPlanningSubmitAction.MARK_READY
        val key = if (ready) "ready-plan:$shipmentId" else "draft-plan:$shipmentId"
        val message = if (ready) "تم اعتماد الخطة" else "تم حفظ المسودة"
        launchOperation(key, message, {
            refreshCenter()
            if (ready) {
                clearDurableDraft(shipmentId)
                selectShipment(shipmentId)
                onSaved()
            } else {
                clearCommittedPendingDocumentsAndReload(shipmentId)
            }
        }) { requestId ->
            if (ready) planningWorkflow.approvePlanning(shipmentId, requestId)
            else planningWorkflow.savePlanning(shipmentId, draft, requestId)
        }
    }

internal fun saveDocument(
        shipmentId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String,
        type: LogisticsDocumentType = LogisticsDocumentType.OTHER,
        milestoneId: String? = null
    ) {
        if (!state.accessValue.canManage) return permissionError()
        launchOperation("document-save:$shipmentId:${milestoneId ?: "shipment"}:$sourceUri", "تم حفظ المستند", { selectShipment(shipmentId) }) { requestId ->
            executionWorkflow.saveDocument(
                LogisticsDocumentWriteRequest(
                    shipmentId = shipmentId,
                    source = LogisticsDocumentWriteSource(sourceUri, displayName, mimeType, type),
                    scope = LogisticsDocumentWriteScope(milestoneId = milestoneId),
                    requestId = requestId,
                ),
            )
        }
    }

}
