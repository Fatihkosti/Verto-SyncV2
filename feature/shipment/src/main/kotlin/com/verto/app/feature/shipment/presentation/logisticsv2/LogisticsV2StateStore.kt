package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Owns mutable presentation state so the ViewModel only exposes read-only flows. */
internal class LogisticsV2StateStore(initialPlanningStep: LogisticsPlanningStep) {
    private val accessMutable = MutableStateFlow(LogisticsV2Access.ViewOnly)
    private val centerRefreshMutable = MutableStateFlow(0L)
    private val draftMutable = MutableStateFlow<LogisticsDraftUiState>(LogisticsDraftUiState.Idle)
    private val planningStepMutable = MutableStateFlow(initialPlanningStep)
    private val routeWorkspaceMutable = MutableStateFlow<LogisticsRouteWorkspaceSnapshot?>(null)
    private val routeTemplatesMutable = MutableStateFlow<List<LogisticsRouteTemplate>>(emptyList())
    private val operationMutable = MutableStateFlow<LogisticsOperationUiState>(LogisticsOperationUiState.Idle)
    private val detailMutable = MutableStateFlow<LogisticsDetailUiState>(LogisticsDetailUiState.Idle)
    private val planningMutable = MutableStateFlow<LogisticsPlanningUiState>(LogisticsPlanningUiState.Idle)
    private val receivingMutable = MutableStateFlow<LogisticsReceivingUiState>(LogisticsReceivingUiState.Idle)
    private val costsMutable = MutableStateFlow<LogisticsCostsUiState>(LogisticsCostsUiState.Idle)
    private val partnersMutable = MutableStateFlow<LogisticsPartnersUiState>(LogisticsPartnersUiState.Idle)
    private val requestIds = mutableMapOf<String, String>()

    val access: StateFlow<LogisticsV2Access> = accessMutable
    val centerRefresh: StateFlow<Long> = centerRefreshMutable
    val draftState: StateFlow<LogisticsDraftUiState> = draftMutable
    val planningStep: StateFlow<LogisticsPlanningStep> = planningStepMutable
    val routeWorkspace: StateFlow<LogisticsRouteWorkspaceSnapshot?> = routeWorkspaceMutable
    val routeTemplates: StateFlow<List<LogisticsRouteTemplate>> = routeTemplatesMutable
    val operationState: StateFlow<LogisticsOperationUiState> = operationMutable
    val detailState: StateFlow<LogisticsDetailUiState> = detailMutable
    val planningState: StateFlow<LogisticsPlanningUiState> = planningMutable
    val receivingState: StateFlow<LogisticsReceivingUiState> = receivingMutable
    val costsState: StateFlow<LogisticsCostsUiState> = costsMutable
    val partnersState: StateFlow<LogisticsPartnersUiState> = partnersMutable

    var accessValue: LogisticsV2Access
        get() = accessMutable.value
        set(value) { accessMutable.value = value }
    var centerRefreshValue: Long
        get() = centerRefreshMutable.value
        set(value) { centerRefreshMutable.value = value }
    var draftValue: LogisticsDraftUiState
        get() = draftMutable.value
        set(value) { draftMutable.value = value }
    var planningStepValue: LogisticsPlanningStep
        get() = planningStepMutable.value
        set(value) { planningStepMutable.value = value }
    var routeWorkspaceValue: LogisticsRouteWorkspaceSnapshot?
        get() = routeWorkspaceMutable.value
        set(value) { routeWorkspaceMutable.value = value }
    var routeTemplatesValue: List<LogisticsRouteTemplate>
        get() = routeTemplatesMutable.value
        set(value) { routeTemplatesMutable.value = value }
    var operationValue: LogisticsOperationUiState
        get() = operationMutable.value
        set(value) { operationMutable.value = value }
    var detailValue: LogisticsDetailUiState
        get() = detailMutable.value
        set(value) { detailMutable.value = value }
    var planningValue: LogisticsPlanningUiState
        get() = planningMutable.value
        set(value) { planningMutable.value = value }
    var receivingValue: LogisticsReceivingUiState
        get() = receivingMutable.value
        set(value) { receivingMutable.value = value }
    var costsValue: LogisticsCostsUiState
        get() = costsMutable.value
        set(value) { costsMutable.value = value }
    var partnersValue: LogisticsPartnersUiState
        get() = partnersMutable.value
        set(value) { partnersMutable.value = value }

    fun requestIdFor(key: String): String = requestIds.getOrPut(key) { UUID.randomUUID().toString() }
    fun removeRequestId(key: String) { requestIds.remove(key) }
}
