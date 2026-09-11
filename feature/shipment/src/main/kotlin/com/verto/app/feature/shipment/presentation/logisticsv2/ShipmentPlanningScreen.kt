package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.shipment.R
import com.verto.app.ui.components.VertoConfirmationDialog

/** v236 planning wizard. Purchase selection is isolated from route planning; execution facts stay absent. */
@Composable
internal fun ShipmentPlanningScreen(
    shipmentNumber: String,
    viewModel: LogisticsV2ViewModel = hiltViewModel(),
    initialDraft: LogisticsPlanningDraft,
    employees: List<LogisticsEmployeeOption>,
    purchaseInvoices: List<LogisticsPurchaseInvoiceOptionUi>,
    access: LogisticsV2Access,
    operationState: LogisticsOperationUiState = LogisticsOperationUiState.Idle,
    onBack: () -> Unit,
    onSavePlan: (LogisticsPlanningDraft) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val livePlanning by viewModel.planningState.collectAsStateWithLifecycle()
    val draftState by viewModel.draftState.collectAsStateWithLifecycle()
    val currentStep by viewModel.planningStep.collectAsStateWithLifecycle()
    val workspaceState by viewModel.routeWorkspace.collectAsStateWithLifecycle()
    val routeTemplates by viewModel.routeTemplates.collectAsStateWithLifecycle()
    val liveContent = livePlanning as? LogisticsPlanningUiState.Content
    val purchaseDraft = liveContent?.initialDraft ?: initialDraft
    val liveInvoices = liveContent?.purchaseInvoices ?: purchaseInvoices
    val basicsState = draftState as? LogisticsDraftUiState.Content
    val header = basicsState?.draft
    val sourceCity = header?.originCity?.ifBlank { header.departureStation }
    val destinationCity = header?.destinationCity?.ifBlank { header.finalArrivalStation }
    val sourceLocation = header?.let { definition ->
        if (definition.originCountryName.isBlank()) sourceCity.orEmpty()
        else logisticsPlanningPlace(definition.originCountryName, sourceCity.orEmpty())
    } ?: purchaseDraft.sourceLocation
    val destinationLocation = header?.let { definition ->
        if (definition.destinationCountryName.isBlank()) destinationCity.orEmpty()
        else logisticsPlanningPlace(definition.destinationCountryName, destinationCity.orEmpty())
    } ?: purchaseDraft.destinationLocation
    val assigneeId = header?.employeeId ?: purchaseDraft.assigneeId
    val assigneeName = header?.employeeName ?: purchaseDraft.assigneeName
    val routeSeed = purchaseDraft.copy(
        sourceLocation = sourceLocation,
        destinationLocation = destinationLocation,
        assigneeId = assigneeId,
        assigneeName = assigneeName,
        transportDetails = null,
        expectedDepartureAt = null,
        expectedArrivalAt = null,
        pendingCosts = emptyList(),
        pendingDocuments = emptyList(),
    )

    LaunchedEffect(initialDraft.shipmentId, sourceLocation, destinationLocation) {
        viewModel.ensureRouteWorkspace(routeSeed)
        viewModel.loadRouteTemplates()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushDraftBestEffort()
                viewModel.flushPurchasePlanBestEffort()
                viewModel.flushRouteWorkspaceBestEffort()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushDraftBestEffort()
            viewModel.flushPurchasePlanBestEffort()
            viewModel.flushRouteWorkspaceBestEffort()
        }
    }

    val workspace = workspaceState?.takeIf { it.shipmentId == initialDraft.shipmentId }
        ?: LogisticsRouteWorkspaceSnapshot(shipmentId = initialDraft.shipmentId)
    val effectiveLegs = workspace.v237LegsWithReadyProofs()
    val planDraft = routeSeed.copy(
        transportMode = deriveShipmentMode(effectiveLegs),
        milestones = workspace.milestones,
        legs = effectiveLegs,
    )
    val purchaseReady = planDraft.isPurchaseStepReady(liveInvoices)
    val routeReady = planDraft.isV237RouteReady(workspace)
    val routeScreen = workspace.v237Screen()
    val reviewIssues = planDraft.v238ReviewIssues(workspace, liveInvoices)
    val routeDetailsIndex = when (routeScreen) {
        LogisticsV237RouteScreen.TRIP_TYPE -> 2
        LogisticsV237RouteScreen.ROUTE_BUILDER -> 3
        LogisticsV237RouteScreen.STATION_DETAILS -> 4
    }
    val working = operationState is LogisticsOperationUiState.Working
    val scrollState = rememberScrollState()
    var definitionValidationAttempt by remember(initialDraft.shipmentId) { mutableIntStateOf(0) }
    var purchaseValidationAttempt by remember(initialDraft.shipmentId) { mutableIntStateOf(0) }
    var routeValidationAttempt by remember(initialDraft.shipmentId) { mutableIntStateOf(0) }
    var customsValidationAttempt by remember(initialDraft.shipmentId) { mutableIntStateOf(0) }
    var pendingDuplicateMilestoneId by remember(initialDraft.shipmentId) { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember(initialDraft.shipmentId) { mutableStateOf(false) }
    var showApproveConfirmation by remember(initialDraft.shipmentId) { mutableStateOf(false) }

    fun mutateWorkspace(transform: (LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) {
        viewModel.updateRouteWorkspace(initialDraft.shipmentId, transform)
    }

    fun editFromReview(target: LogisticsV238ReviewTarget) {
        mutateWorkspace { current ->
            val cursor = when (target) {
                LogisticsV238ReviewTarget.TRIP_TYPE -> V237_CURSOR_TRIP_TYPE
                LogisticsV238ReviewTarget.ROUTE -> V237_CURSOR_ROUTE_BUILDER
                LogisticsV238ReviewTarget.STATION -> current.v237FirstIncompleteLeg()?.let { V237_CURSOR_STATION_FLOW_PREFIX + it.toMilestoneId }
                    ?: V237_CURSOR_ROUTE_BUILDER
                else -> current.expandedSection
            }
            current.copy(returnToReviewAfterEdit = true, expandedSection = cursor)
        }
        val step = when (target) {
            LogisticsV238ReviewTarget.BASICS -> LogisticsPlanningStep.BASICS
            LogisticsV238ReviewTarget.PURCHASE -> LogisticsPlanningStep.PURCHASE
            LogisticsV238ReviewTarget.TRIP_TYPE, LogisticsV238ReviewTarget.ROUTE, LogisticsV238ReviewTarget.STATION -> LogisticsPlanningStep.ROUTE
            LogisticsV238ReviewTarget.CUSTOMS -> LogisticsPlanningStep.CUSTOMS
        }
        viewModel.setPlanningStep(initialDraft.shipmentId, step)
    }

    fun moveBack() {
        if (working) return
        when (currentStep) {
            LogisticsPlanningStep.BASICS -> showExitConfirmation = true
            LogisticsPlanningStep.PURCHASE -> viewModel.backFromPurchasePlan {
                viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.BASICS)
            }
            LogisticsPlanningStep.ROUTE -> when (routeScreen) {
                LogisticsV237RouteScreen.TRIP_TYPE -> {
                    viewModel.flushRouteWorkspaceBestEffort()
                    viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.PURCHASE)
                }
                LogisticsV237RouteScreen.ROUTE_BUILDER -> mutateWorkspace { it.copy(expandedSection = V237_CURSOR_TRIP_TYPE) }
                LogisticsV237RouteScreen.STATION_DETAILS -> {
                    val leg = workspace.v237StationId()?.let(workspace::v237LegForTarget)
                    when {
                        workspace.v237IsStationCreate() && workspace.v237StationId() != null -> {
                            val id = requireNotNull(workspace.v237StationId())
                            mutateWorkspace { it.withV237RemovedIntermediate(planDraft, id).copy(expandedSection = V237_CURSOR_ROUTE_BUILDER) }
                        }
                        workspace.v237IsStationFlow() && leg != null -> {
                            val previous = workspace.v237PreviousLeg(leg.sequence)
                            mutateWorkspace { current ->
                                current.copy(
                                    expandedSection = previous?.let { V237_CURSOR_STATION_FLOW_PREFIX + it.toMilestoneId }
                                        ?: V237_CURSOR_ROUTE_BUILDER,
                                )
                            }
                        }
                        else -> mutateWorkspace { it.copy(expandedSection = V237_CURSOR_ROUTE_BUILDER) }
                    }
                }
            }
            LogisticsPlanningStep.CUSTOMS -> {
                mutateWorkspace { it.copy(expandedSection = V237_CURSOR_ROUTE_BUILDER) }
                viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.ROUTE)
            }
            LogisticsPlanningStep.REVIEW -> viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.CUSTOMS)
        }
    }

    fun moveNext() {
        when (currentStep) {
            LogisticsPlanningStep.BASICS -> {
                if (header?.validation?.isValid != true) {
                    definitionValidationAttempt += 1
                    return
                }
                viewModel.nextFromPlanningBasics {
                    if (workspace.returnToReviewAfterEdit) {
                        mutateWorkspace { it.copy(returnToReviewAfterEdit = false) }
                        viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.REVIEW)
                    } else {
                        viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.PURCHASE)
                    }
                }
            }
            LogisticsPlanningStep.PURCHASE -> {
                if (!purchaseReady) {
                    purchaseValidationAttempt += 1
                    return
                }
                viewModel.nextFromPurchasePlan {
                    if (workspace.returnToReviewAfterEdit) {
                        mutateWorkspace { it.copy(returnToReviewAfterEdit = false) }
                        viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.REVIEW)
                    } else {
                        viewModel.setPlanningStep(initialDraft.shipmentId, LogisticsPlanningStep.ROUTE)
                    }
                }
            }
            LogisticsPlanningStep.ROUTE -> {
                val currentScreenInvalid = when (routeScreen) {
                    LogisticsV237RouteScreen.TRIP_TYPE -> !workspace.v237TripValidation().isValid
                    LogisticsV237RouteScreen.ROUTE_BUILDER -> false
                    LogisticsV237RouteScreen.STATION_DETAILS -> {
                        val targetId = workspace.v237StationId()
                        val leg = targetId?.let(workspace::v237LegForTarget)
                        val target = leg?.let(workspace::v237TargetForLeg)
                        leg == null || target == null || !leg.v237Validation(target, workspace.routeTransportPlanKind).isValid
                    }
                }
                routeValidationAttempt = if (currentScreenInvalid) routeValidationAttempt + 1 else 0
                if (workspace.returnToReviewAfterEdit &&
                    routeScreen == LogisticsV237RouteScreen.TRIP_TYPE &&
                    workspace.v237TripValidation().isValid && routeReady
                ) {
                    viewModel.nextFromRoute(initialDraft.shipmentId, planDraft)
                } else {
                    handleV237RouteNext(
                        screen = routeScreen,
                        workspace = workspace,
                        routeReady = routeReady,
                        actions = V237RouteNextActions(
                            mutate = ::mutateWorkspace,
                            requestDuplicateConfirmation = { pendingDuplicateMilestoneId = it },
                            persistRoute = { viewModel.nextFromRoute(initialDraft.shipmentId, planDraft) },
                        ),
                    )
                }
            }
            LogisticsPlanningStep.CUSTOMS -> {
                if (!workspace.v238CustomsValidation().isValid) {
                    customsValidationAttempt += 1
                    return
                }
                viewModel.nextFromCustoms(initialDraft.shipmentId)
            }
            LogisticsPlanningStep.REVIEW -> {
                if (reviewIssues.isNotEmpty()) {
                    editFromReview(reviewIssues.first().target)
                } else {
                    showApproveConfirmation = true
                }
            }
        }
    }

    BackHandler(enabled = !working) { moveBack() }

    LogisticsPlanningScaffold(
        shipmentNumber = shipmentNumber,
        step = currentStep,
        onBack = ::moveBack,
        onNext = ::moveNext,
        nextLabel = if (currentStep == LogisticsPlanningStep.REVIEW) stringResource(R.string.logistics_v230_approve) else stringResource(R.string.logistics_v230_next),
        backEnabled = !working,
        detailsIndexOverride = if (currentStep == LogisticsPlanningStep.ROUTE) routeDetailsIndex else null,
        nextEnabled = !working && when (currentStep) {
            // Keep Next actionable on the definition screen; invalid input reveals inline errors.
            LogisticsPlanningStep.BASICS -> true
            // Keep v236 Next actionable so validation errors appear inline instead of silently disabling progress.
            LogisticsPlanningStep.PURCHASE -> true
            // v237 keeps Next actionable so field-level validation is never hidden behind a disabled button.
            LogisticsPlanningStep.ROUTE -> true
            LogisticsPlanningStep.CUSTOMS -> true
            LogisticsPlanningStep.REVIEW -> true
        },
        contentScrollState = scrollState,
    ) {
        when (currentStep) {
            LogisticsPlanningStep.BASICS -> ShipmentV230Basics(
                state = basicsState,
                fallbackEmployees = employees,
                viewModel = viewModel,
                validationAttempt = definitionValidationAttempt,
            )
            LogisticsPlanningStep.PURCHASE -> ShipmentV236PurchaseStep(
                draft = planDraft,
                invoices = liveInvoices,
                state = LogisticsV236PurchaseStepState(
                    canManage = access.canManage,
                    saving = liveContent?.purchaseSaving == true,
                    saveError = liveContent?.purchaseSaveError,
                    invoiceLoading = liveContent?.purchaseInvoiceLoading == true,
                    invoiceLoadError = liveContent?.purchaseInvoiceLoadError,
                    validationAttempt = purchaseValidationAttempt,
                ),
                actions = LogisticsV236PurchaseStepActions(
                    onDraftChange = viewModel::updatePurchasePlanDraft,
                    onRetryInvoices = { viewModel.retryPurchaseInvoices(initialDraft.shipmentId) },
                ),
            )
            LogisticsPlanningStep.ROUTE -> when (routeScreen) {
                LogisticsV237RouteScreen.TRIP_TYPE -> ShipmentV237TripType(
                    workspace = workspace,
                    validationAttempt = routeValidationAttempt,
                    canManage = access.canManage,
                    onWorkspaceChange = ::mutateWorkspace,
                )
                LogisticsV237RouteScreen.ROUTE_BUILDER -> ShipmentV237RouteBuilder(
                    draft = planDraft,
                    workspace = workspace,
                    canManage = access.canManage,
                    validationAttempt = routeValidationAttempt,
                    onWorkspaceChange = ::mutateWorkspace,
                    onEditStation = { id -> mutateWorkspace { it.copy(expandedSection = V237_CURSOR_STATION_EDIT_PREFIX + id) } },
                    onAddStation = {
                        mutateWorkspace { current ->
                            val added = current.withIntermediateMilestone(planDraft, "").withV237CargoDefaults(planDraft)
                            val created = added.milestones.sortedBy { it.order }.dropLast(1).lastOrNull()
                            added.copy(expandedSection = created?.let { V237_CURSOR_STATION_CREATE_PREFIX + it.id } ?: V237_CURSOR_ROUTE_BUILDER)
                        }
                    },
                    onCustomsRequested = {
                        routeValidationAttempt += 1
                        val incomplete = workspace.v237FirstIncompleteLeg()
                        val duplicate = workspace.v237UnconfirmedImmediateDuplicateId()
                        when {
                            incomplete != null -> mutateWorkspace { it.copy(expandedSection = V237_CURSOR_STATION_FLOW_PREFIX + incomplete.toMilestoneId) }
                            duplicate != null -> mutateWorkspace { it.copy(expandedSection = V237_CURSOR_STATION_FLOW_PREFIX + duplicate) }
                            routeReady -> viewModel.nextFromRoute(initialDraft.shipmentId, planDraft)
                        }
                    },
                )
                LogisticsV237RouteScreen.STATION_DETAILS -> workspace.v237StationId()?.let { targetId ->
                    ShipmentV237StationDetails(
                        draft = planDraft,
                        workspace = workspace,
                        milestoneId = targetId,
                        canManage = access.canManage,
                        validationAttempt = routeValidationAttempt,
                        stationNameSuggestions = routeTemplates.flatMap { template -> template.stops.map { it.city } }.filter(String::isNotBlank).distinct(),
                        documentActions = LogisticsV237StationDocumentActions(
                            onStage = { uri, name, mime, target -> viewModel.stagePlanningDocument(initialDraft.shipmentId, uri, name, mime, target) },
                            onRetry = { viewModel.retryPlanningDocument(initialDraft.shipmentId, it) },
                            onRemove = { viewModel.removePlanningDocument(initialDraft.shipmentId, it) },
                            onOpen = { viewModel.openPlanningDocument(initialDraft.shipmentId, it) },
                        ),
                        onWorkspaceChange = ::mutateWorkspace,
                    )
                }
            }
            LogisticsPlanningStep.CUSTOMS -> ShipmentV238CustomsStep(
                workspace = workspace,
                canManage = access.canManage,
                validationAttempt = customsValidationAttempt,
                suggestions = liveContent?.customsCheckpointSuggestions.orEmpty(),
                savedDocuments = liveContent?.customsPlanDocuments.orEmpty(),
                documentActions = LogisticsV238CustomsDocumentActions(
                    onStage = { uri, name, mime, target -> viewModel.stagePlanningDocument(initialDraft.shipmentId, uri, name, mime, target) },
                    onRetry = { viewModel.retryPlanningDocument(initialDraft.shipmentId, it) },
                    onRemove = { viewModel.removePlanningDocument(initialDraft.shipmentId, it) },
                    onOpen = { viewModel.openPlanningDocument(initialDraft.shipmentId, it) },
                    onOpenSaved = { viewModel.openCustomsPlanDocument(initialDraft.shipmentId, it) },
                    onDeleteSaved = { viewModel.deleteCustomsPlanDocument(initialDraft.shipmentId, it) },
                ),
                onWorkspaceChange = ::mutateWorkspace,
            )
            LogisticsPlanningStep.REVIEW -> ShipmentV238Review(
                shipmentNumber = shipmentNumber,
                draft = planDraft,
                workspace = workspace,
                invoiceOptions = liveInvoices,
                issues = reviewIssues,
                onEdit = ::editFromReview,
            )
        }
        LogisticsOperationFeedback(operationState)
    }

    val duplicateId = pendingDuplicateMilestoneId
    if (duplicateId != null) {
        VertoConfirmationDialog(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d47e7bd6fb64),
            message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_3b21889bf821),
            confirmLabel = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fa480611e83e),
            onConfirm = {
                mutateWorkspace { it.withV237DuplicateConfirmed(duplicateId) }
                pendingDuplicateMilestoneId = null
            },
            onDismiss = { pendingDuplicateMilestoneId = null },
            dismissLabel = "تعديل الاسم",
        )
    }

    if (showApproveConfirmation) {
        VertoConfirmationDialog(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_c9e978b9a8bd),
            message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_defdd0902183),
            confirmLabel = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_f8ad8d2321d3),
            onConfirm = {
                showApproveConfirmation = false
                onSavePlan(planDraft.copy(submitAction = LogisticsPlanningSubmitAction.MARK_READY))
            },
            onDismiss = { showApproveConfirmation = false },
        )
    }

    if (showExitConfirmation) {
        VertoConfirmationDialog(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_3cf4bf454788),
            message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d766a1345ad3),
            confirmLabel = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b5530de57c4a),
            onConfirm = {
                showExitConfirmation = false
                viewModel.backFromPlanningBasics(onBack)
            },
            onDismiss = { showExitConfirmation = false },
            dismissLabel = "متابعة التخطيط",
        )
    }
}

private data class V237RouteNextActions(
    val mutate: (((LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) -> Unit),
    val requestDuplicateConfirmation: (String) -> Unit,
    val persistRoute: () -> Unit,
)

private fun handleV237RouteNext(
    screen: LogisticsV237RouteScreen,
    workspace: LogisticsRouteWorkspaceSnapshot,
    routeReady: Boolean,
    actions: V237RouteNextActions,
) {
    when (screen) {
        LogisticsV237RouteScreen.TRIP_TYPE -> {
            if (workspace.v237TripValidation().isValid) {
                actions.mutate { it.copy(expandedSection = V237_CURSOR_ROUTE_BUILDER) }
            }
        }
        LogisticsV237RouteScreen.ROUTE_BUILDER -> {
            val incomplete = workspace.v237FirstIncompleteLeg()
            val duplicate = workspace.v237UnconfirmedImmediateDuplicateId()
            when {
                incomplete != null -> actions.mutate { it.copy(expandedSection = V237_CURSOR_STATION_FLOW_PREFIX + incomplete.toMilestoneId) }
                duplicate != null -> actions.mutate { it.copy(expandedSection = V237_CURSOR_STATION_FLOW_PREFIX + duplicate) }
                routeReady -> actions.persistRoute()
            }
        }
        LogisticsV237RouteScreen.STATION_DETAILS -> {
            val targetId = workspace.v237StationId() ?: return
            val leg = workspace.v237LegForTarget(targetId) ?: return
            val target = workspace.v237TargetForLeg(leg) ?: return
            if (!leg.v237Validation(target, workspace.routeTransportPlanKind).isValid) return
            if (workspace.v237UnconfirmedImmediateDuplicateId() == targetId) {
                actions.requestDuplicateConfirmation(targetId)
                return
            }
            if (!workspace.v237IsStationFlow()) {
                actions.mutate { it.copy(expandedSection = V237_CURSOR_ROUTE_BUILDER) }
                return
            }
            val next = workspace.v237NextIncompleteLeg(leg.sequence)
            val duplicate = workspace.v237UnconfirmedImmediateDuplicateId()
            when {
                next != null -> actions.mutate { it.copy(expandedSection = V237_CURSOR_STATION_FLOW_PREFIX + next.toMilestoneId) }
                duplicate != null -> actions.mutate { it.copy(expandedSection = V237_CURSOR_STATION_FLOW_PREFIX + duplicate) }
                routeReady -> actions.persistRoute()
            }
        }
    }
}
