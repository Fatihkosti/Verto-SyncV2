package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class LogisticsRouteKind { LIST, CREATE, DETAIL, PLAN, RECEIVE, COSTS, PARTNERS }

sealed interface LogisticsRouteTarget {
    data object Create : LogisticsRouteTarget
    data class Detail(val shipmentId: String, val replaceCurrent: Boolean = false) : LogisticsRouteTarget
    data class Plan(val shipmentId: String, val replaceCurrent: Boolean = false) : LogisticsRouteTarget
    data class Receive(val shipmentId: String, val replaceCurrent: Boolean = false) : LogisticsRouteTarget
    data class Costs(val shipmentId: String, val replaceCurrent: Boolean = false) : LogisticsRouteTarget
    data class Partners(val shipmentId: String, val replaceCurrent: Boolean = false) : LogisticsRouteTarget
}

@Composable
fun LogisticsRouteEntry(
    route: LogisticsRouteKind,
    shipmentId: String?,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: LogisticsV2ViewModel = hiltViewModel()
    LaunchedEffect(access) { viewModel.updateAccess(access) }
    when (route) {
        LogisticsRouteKind.LIST -> LogisticsListRoute(viewModel, access, onNavigate)
        LogisticsRouteKind.CREATE -> LogisticsCreateRoute(viewModel, access, onNavigate, onBack)
        LogisticsRouteKind.DETAIL -> LogisticsDetailRoute(viewModel, requireShipmentId(shipmentId), access, onNavigate, onBack)
        LogisticsRouteKind.PLAN -> LogisticsPlanRoute(viewModel, requireShipmentId(shipmentId), access, onNavigate, onBack)
        LogisticsRouteKind.RECEIVE -> LogisticsReceiveRoute(viewModel, requireShipmentId(shipmentId), access, onNavigate, onBack)
        LogisticsRouteKind.COSTS -> LogisticsCostsRoute(viewModel, requireShipmentId(shipmentId), access, onNavigate, onBack)
        LogisticsRouteKind.PARTNERS -> LogisticsPartnersRoute(viewModel, requireShipmentId(shipmentId), access, onBack)
    }
}

@Composable
private fun LogisticsListRoute(
    viewModel: LogisticsV2ViewModel,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
) {
    val state by viewModel.centerState.collectAsStateWithLifecycle()
    LogisticsCenterScreen(
        state = state,
        access = access,
        onNewShipment = { onNavigate(LogisticsRouteTarget.Create) },
        onOpenShipment = { onNavigate(LogisticsRouteTarget.Detail(it.id)) },
        onAction = { shipmentId, action -> routeLogisticsAction(viewModel, shipmentId, action, onNavigate) },
        onRetry = viewModel::retryCenter,
    )
}

@Composable
private fun LogisticsCreateRoute(
    viewModel: LogisticsV2ViewModel,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
    onBack: () -> Unit,
) {
    CreateLogisticsShipmentScreen(
        access = access,
        onBack = onBack,
        onResolved = { onNavigate(LogisticsRouteTarget.Plan(it, replaceCurrent = true)) },
    )
}

@Composable
private fun LogisticsDetailRoute(
    viewModel: LogisticsV2ViewModel,
    shipmentId: String,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val operation by viewModel.operationState.collectAsStateWithLifecycle()
    LaunchedEffect(access, shipmentId) {
        viewModel.updateAccess(access)
        if (access.canView) viewModel.selectShipment(shipmentId)
    }
    LogisticsShipmentDetailScreen(
        state = state,
        access = access,
        operationState = operation,
        onBack = onBack,
        onAction = { routeLogisticsAction(viewModel, shipmentId, it, onNavigate) },
        onOpenPartners = { onNavigate(LogisticsRouteTarget.Partners(shipmentId)) },
        onSaveDocument = { uri, name, mime -> viewModel.saveDocument(shipmentId, uri, name, mime) },
        onDeleteDocument = { viewModel.deleteDocument(shipmentId, it) },
    )
}

@Composable
private fun LogisticsPlanRoute(
    viewModel: LogisticsV2ViewModel,
    shipmentId: String,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.planningState.collectAsStateWithLifecycle()
    val operation by viewModel.operationState.collectAsStateWithLifecycle()
    LaunchedEffect(access, shipmentId) {
        viewModel.updateAccess(access)
        if (access.canManage) viewModel.loadPlanning(shipmentId)
    }
    when (val current = state) {
        LogisticsPlanningUiState.Idle,
        LogisticsPlanningUiState.Loading -> LogisticsV2Screen(title = "تخطيط الشحنة", onBack = onBack) { LogisticsLoading() }
        LogisticsPlanningUiState.PermissionDenied -> LogisticsV2Screen(title = "تخطيط الشحنة", onBack = onBack) { LogisticsPermissionDenied() }
        is LogisticsPlanningUiState.Error -> LogisticsV2Screen(title = "تخطيط الشحنة", onBack = onBack) {
            LogisticsError(current.message) { viewModel.loadPlanning(shipmentId) }
        }
        is LogisticsPlanningUiState.Content -> ShipmentPlanningScreen(
            shipmentNumber = current.shipmentNumber,
            initialDraft = current.initialDraft,
            employees = current.employees,
            purchaseInvoices = current.purchaseInvoices,
            access = access,
            operationState = operation,
            onBack = onBack,
            onSavePlan = { draft ->
                viewModel.savePlanning(shipmentId, draft) {
                    onNavigate(LogisticsRouteTarget.Detail(shipmentId, replaceCurrent = true))
                }
            },
        )
    }
}

@Composable
private fun LogisticsReceiveRoute(
    viewModel: LogisticsV2ViewModel,
    shipmentId: String,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.receivingState.collectAsStateWithLifecycle()
    val operation by viewModel.operationState.collectAsStateWithLifecycle()
    LaunchedEffect(access, shipmentId) {
        viewModel.updateAccess(access)
        if (access.canConfirm) viewModel.startReceivingAndOpen(shipmentId) { viewModel.loadReceiving(shipmentId) }
    }
    when (val current = state) {
        LogisticsReceivingUiState.Idle,
        LogisticsReceivingUiState.Loading -> LogisticsV2Screen(title = "استلام الشحنة", onBack = onBack) { LogisticsLoading() }
        LogisticsReceivingUiState.PermissionDenied -> LogisticsV2Screen(title = "استلام الشحنة", onBack = onBack) { LogisticsPermissionDenied() }
        is LogisticsReceivingUiState.Error -> LogisticsV2Screen(title = "استلام الشحنة", onBack = onBack) {
            LogisticsError(current.message) { viewModel.loadReceiving(shipmentId) }
        }
        is LogisticsReceivingUiState.Content -> ShipmentReceivingScreen(
            shipmentId = current.shipmentId,
            shipmentNumber = current.shipmentNumber,
            initialLines = current.lines,
            savedDraft = current.savedDraft,
            access = access,
            operationState = operation,
            onBack = onBack,
            onSubmit = { draft ->
                viewModel.submitReceiving(shipmentId, draft) {
                    onNavigate(LogisticsRouteTarget.Detail(shipmentId, replaceCurrent = true))
                }
            },
        )
    }
}

@Composable
private fun LogisticsCostsRoute(
    viewModel: LogisticsV2ViewModel,
    shipmentId: String,
    access: LogisticsV2Access,
    onNavigate: (LogisticsRouteTarget) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.costsState.collectAsStateWithLifecycle()
    val operation by viewModel.operationState.collectAsStateWithLifecycle()
    LaunchedEffect(access, shipmentId) {
        viewModel.updateAccess(access)
        if (access.canView) viewModel.loadCosts(shipmentId)
    }
    when (val current = state) {
        LogisticsCostsUiState.Idle,
        LogisticsCostsUiState.Loading -> LogisticsV2Screen(title = "تكلفة الشحنة", onBack = onBack) { LogisticsLoading() }
        LogisticsCostsUiState.PermissionDenied -> LogisticsV2Screen(title = "تكلفة الشحنة", onBack = onBack) { LogisticsPermissionDenied() }
        is LogisticsCostsUiState.Error -> LogisticsV2Screen(title = "تكلفة الشحنة", onBack = onBack) {
            LogisticsError(current.message) { viewModel.loadCosts(shipmentId) }
        }
        is LogisticsCostsUiState.Content -> ShipmentCostSettlementScreen(
            shipmentNumber = current.shipmentNumber,
            state = current.settlement,
            access = access,
            operationState = operation,
            onBack = onBack,
            onAddCost = { viewModel.addCost(shipmentId, it) },
            onSettle = { viewModel.settleCosts(shipmentId) },
            onCloseWithoutAdditionalCosts = {
                viewModel.closeShipment(shipmentId) {
                    onNavigate(LogisticsRouteTarget.Detail(shipmentId, replaceCurrent = true))
                }
            },
        )
    }
}

@Composable
private fun LogisticsPartnersRoute(
    viewModel: LogisticsV2ViewModel,
    shipmentId: String,
    access: LogisticsV2Access,
    onBack: () -> Unit,
) {
    val state by viewModel.partnersState.collectAsStateWithLifecycle()
    val operation by viewModel.operationState.collectAsStateWithLifecycle()
    LaunchedEffect(access, shipmentId) {
        viewModel.updateAccess(access)
        if (access.canView) viewModel.loadPartners(shipmentId)
    }
    when (val current = state) {
        LogisticsPartnersUiState.Idle,
        LogisticsPartnersUiState.Loading -> LogisticsV2Screen(title = "الجهات اللوجستية", onBack = onBack) { LogisticsLoading() }
        LogisticsPartnersUiState.PermissionDenied -> LogisticsV2Screen(title = "الجهات اللوجستية", onBack = onBack) { LogisticsPermissionDenied() }
        is LogisticsPartnersUiState.Error -> LogisticsV2Screen(title = "الجهات اللوجستية", onBack = onBack) {
            LogisticsError(current.message) { viewModel.loadPartners(shipmentId) }
        }
        is LogisticsPartnersUiState.Content -> LogisticsPartnerDirectoryScreen(
            state = current.state,
            operationState = operation,
            onBack = onBack,
            onSave = { viewModel.saveAndLinkPartner(shipmentId, it) },
        )
    }
}

private fun routeLogisticsAction(
    viewModel: LogisticsV2ViewModel,
    shipmentId: String,
    action: LogisticsNextAction,
    onNavigate: (LogisticsRouteTarget) -> Unit,
) {
    when (action) {
        LogisticsNextAction.PREPARE -> onNavigate(LogisticsRouteTarget.Plan(shipmentId))
        LogisticsNextAction.PREPARE_MOVEMENT -> onNavigate(LogisticsRouteTarget.Detail(shipmentId))
        LogisticsNextAction.START_TRANSPORT -> viewModel.startTransport(shipmentId)
        LogisticsNextAction.START_MOVEMENT,
        LogisticsNextAction.RECORD_CUSTOMS_ARRIVAL,
        LogisticsNextAction.RECORD_CUSTOMS_DEPARTURE,
        LogisticsNextAction.RECORD_MILESTONE_ARRIVAL,
        LogisticsNextAction.RECORD_MILESTONE_DEPARTURE,
        LogisticsNextAction.RECORD_DESTINATION_ARRIVAL -> viewModel.recordJourneyAction(shipmentId, action)
        LogisticsNextAction.START_RECEIVING,
        LogisticsNextAction.RECEIVE_ANOTHER_BATCH -> viewModel.startReceivingAndOpen(shipmentId) {
            onNavigate(LogisticsRouteTarget.Receive(shipmentId))
        }
        LogisticsNextAction.SETTLE_COSTS -> onNavigate(LogisticsRouteTarget.Costs(shipmentId))
        LogisticsNextAction.CLOSE -> viewModel.closeShipment(shipmentId)
        LogisticsNextAction.NONE -> Unit
    }
}

private fun requireShipmentId(value: String?): String = value?.takeIf(String::isNotBlank)
    ?: error("shipmentId is required for this logistics route")
