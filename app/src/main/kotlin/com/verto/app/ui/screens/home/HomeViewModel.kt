package com.verto.app.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.application.presentationboundary.DataSyncAccess
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.feature.dashboard.application.HomeNotificationBadgeQuery
import com.verto.app.feature.dashboard.application.activityevent.ObserveActivityEventsUseCase
import com.verto.app.feature.dashboard.application.education.SelectEducationalContentUseCase
import com.verto.app.feature.dashboard.application.pendingaction.DispatchPendingActionUseCase
import com.verto.app.feature.dashboard.application.pendingaction.ManagePendingActionStateUseCase
import com.verto.app.feature.dashboard.application.pendingaction.ObservePendingActionsUseCase
import com.verto.app.feature.dashboard.application.pendingaction.PendingActionDispatchResult
import com.verto.app.feature.dashboard.application.pendingaction.SystemPendingActionClock
import com.verto.app.feature.dashboard.application.quickaction.ObserveQuickActionsUseCase
import com.verto.app.feature.expenses.application.AddExpenseCommand
import com.verto.app.feature.expenses.application.ExpensesGateway
import com.verto.app.ui.components.UiMessage
import com.verto.app.utils.ErrorHumanizer
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.EducationalContent
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.TeamObservationRepository
import com.verto.feature.dashboard.api.TeamObservationCategory
import com.verto.feature.dashboard.api.QuickActionDestinationIds
import com.verto.feature.dashboard.api.QuickActionIds
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val syncManager: DataSyncAccess,
    private val sessionReader: SessionReader,
    notificationBadgeQuery: HomeNotificationBadgeQuery,
    private val observeQuickActions: ObserveQuickActionsUseCase,
    private val observePendingActions: ObservePendingActionsUseCase,
    private val observeActivityEvents: ObserveActivityEventsUseCase,
    private val managePendingActionState: ManagePendingActionStateUseCase,
    private val dispatchPendingAction: DispatchPendingActionUseCase,
    private val pendingActionClock: SystemPendingActionClock,
    private val expensesGateway: ExpensesGateway,
    selectEducationalContent: SelectEducationalContentUseCase,
    private val teamObservationRepository: TeamObservationRepository,
    savedStateHandle: SavedStateHandle,
    permissionProvider: PermissionProvider,
) : ViewModel() {

    val educationalContent: StateFlow<EducationalContent?> = selectEducationalContent(
        rotationDay = LocalDate.now().toEpochDay(),
        restoredContentId = savedStateHandle[EDUCATIONAL_CONTENT_ID_KEY],
    ).map { selected ->
        savedStateHandle[EDUCATIONAL_CONTENT_ID_KEY] = selected?.id
        selected
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val homeClock = pendingActionClock.observeNowEpochMillis()
        .distinctUntilChanged()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )

    private val activityWindowClock = homeClock
        .map { nowEpochMillis -> nowEpochMillis / ACTIVITY_WINDOW_BOUNDARY_MILLIS }
        .distinctUntilChanged()
        .map { pendingActionClock.nowEpochMillis() }

    val userName: StateFlow<String> = sessionReader.userName
        .map { local -> local.ifBlank { "Verto" } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Verto")

    val unreadNotificationsCount: StateFlow<Int> = notificationBadgeQuery.observeUnreadCount()
        .map { count -> count.coerceAtLeast(0) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val permissions: StateFlow<EmployeePermissions?> = permissionProvider.permissions

    private val homePermissionContext: StateFlow<HomePermissionContext?> =
        observeHomePermissionContext(sessionReader, permissionProvider)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val quickActions: StateFlow<List<QuickAction>> = homePermissionContext
        .flatMapLatest { context ->
            if (context == null) flowOf(emptyList()) else observeQuickActions(context)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingActions: StateFlow<List<PendingAction>> = homePermissionContext
        .flatMapLatest { context ->
            if (context == null) {
                flowOf(emptyList())
            } else {
                observePendingActions.observe(context, homeClock)
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activityEvents: StateFlow<List<ActivityEvent>> = homePermissionContext
        .flatMapLatest { context ->
            if (context == null) {
                flowOf(emptyList())
            } else {
                activityWindowClock.flatMapLatest { nowEpochMillis ->
                    observeActivityEvents(context, nowEpochMillis)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickActionScrollKey: StateFlow<String?> = savedStateHandle.getStateFlow(
        QUICK_ACTION_SCROLL_KEY,
        null,
    )
    val quickActionScrollOffset: StateFlow<Int> = savedStateHandle.getStateFlow(
        QUICK_ACTION_SCROLL_OFFSET_KEY,
        0,
    )
    val activityScrollKey: StateFlow<String?> = savedStateHandle.getStateFlow(
        ACTIVITY_SCROLL_KEY,
        null,
    )
    val activityScrollOffset: StateFlow<Int> = savedStateHandle.getStateFlow(
        ACTIVITY_SCROLL_OFFSET_KEY,
        0,
    )

    private val restorationState = savedStateHandle

    private val _activityEventDestinations = MutableSharedFlow<HomeDestination>(extraBufferCapacity = 1)
    val activityEventDestinations = _activityEventDestinations.asSharedFlow()

    private val _pendingActionEffects = MutableSharedFlow<HomePendingActionEffect>(extraBufferCapacity = 1)
    val pendingActionEffects = _pendingActionEffects.asSharedFlow()

    private val _quickActionEffects = createQuickActionEffectChannel()
    val quickActionEffects = _quickActionEffects.receiveAsFlow()

    private val _busyQuickActionIds = MutableStateFlow<Set<String>>(emptySet())
    val busyQuickActionIds: StateFlow<Set<String>> = _busyQuickActionIds.asStateFlow()

    val isHomeExpenseSaving: StateFlow<Boolean> = busyQuickActionIds
        .map { QuickActionIds.RECORD_EXPENSE in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isObservationSubmitting = MutableStateFlow(false)
    val isObservationSubmitting: StateFlow<Boolean> = _isObservationSubmitting.asStateFlow()

    private val _observationSubmittedToken = MutableStateFlow(0L)
    val observationSubmittedToken: StateFlow<Long> = _observationSubmittedToken.asStateFlow()

    init {
        viewModelScope.launch {
            // getMyProfile refreshes the canonical SessionReader name from the server profile.
            runCatching { authRepo.getMyProfile() }
        }
        viewModelScope.launch {
            syncManager.request(SyncRequestReason.FOREGROUND).onFailure { error ->
                _uiMessage.value = UiMessage.Error(ErrorHumanizer.humanize(error, "تحميل البيانات"))
            }
        }
    }

    fun onRefresh() = viewModelScope.launch {
        _isRefreshing.value = true
        syncManager.request(SyncRequestReason.MANUAL).onFailure {
            _uiMessage.value = UiMessage.Info(
                "تعذّرت المزامنة الآن، ستُعاد المحاولة تلقائياً عند توفر الإنترنت",
            )
        }
        _isRefreshing.value = false
    }

    fun submitTeamObservation(text: String, category: TeamObservationCategory) {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            _uiMessage.value = UiMessage.Warning("اكتب ما لاحظته أولاً")
            return
        }
        if (_isObservationSubmitting.value) return
        viewModelScope.launch {
            _isObservationSubmitting.value = true
            try {
                teamObservationRepository.submit(normalized, category)
                _observationSubmittedToken.value = _observationSubmittedToken.value + 1L
                _uiMessage.value = UiMessage.Success("شكراً، وصلت المعلومة")
                // Durable sync request; local capture succeeds even when the network is unavailable.
                syncManager.request(SyncRequestReason.OUTBOX_WRITE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiMessage.value = UiMessage.Error(ErrorHumanizer.humanize(error, "حفظ الملاحظة"))
            } finally {
                _isObservationSubmitting.value = false
            }
        }
    }

    fun onQuickActionClicked(action: QuickAction) {
        // The UI object is never authoritative. Only its stable ID crosses the execution boundary.
        onQuickActionClicked(action.id)
    }

    internal fun onQuickActionClicked(actionId: String) {
        val action = resolveCurrentAuthorizedQuickAction(
            actionId = actionId,
            actions = quickActions.value,
            context = homePermissionContext.value,
        ) ?: return rejectQuickActionExecution()

        when (action.destination.id) {
            QuickActionDestinationIds.EXPENSE_ENTRY -> emitQuickActionEffect(HomeQuickActionEffect.ShowExpenseDialog)
            QuickActionDestinationIds.INTERNATIONAL_PURCHASE_SETUP ->
                emitQuickActionEffect(HomeQuickActionEffect.ShowInternationalPurchaseDialog)
            else -> emitQuickActionEffect(HomeQuickActionEffect.Navigate(action.destination))
        }
    }

    fun addExpenseFromHome(statement: String, amount: Double, date: Long) {
        val normalizedStatement = statement.trim()
        if (normalizedStatement.isBlank()) {
            _uiMessage.value = UiMessage.Warning("أدخل بيان المصروف")
            return
        }
        if (!amount.isFinite() || amount <= 0.0) {
            _uiMessage.value = UiMessage.Warning("أدخل قيمة أكبر من صفر")
            return
        }
        if (!isCurrentQuickActionAuthorized(QuickActionIds.RECORD_EXPENSE, QuickActionDestinationIds.EXPENSE_ENTRY)) {
            rejectQuickActionExecution()
            return
        }
        if (!tryAcquireQuickActionBusy(QuickActionIds.RECORD_EXPENSE)) return

        viewModelScope.launch {
            try {
                // Re-check after launch: permission can be revoked while the expense dialog is open
                // or between submission and coroutine execution.
                if (!isCurrentQuickActionAuthorized(QuickActionIds.RECORD_EXPENSE, QuickActionDestinationIds.EXPENSE_ENTRY)) {
                    rejectQuickActionExecution()
                    return@launch
                }
                expensesGateway.addExpense(
                    AddExpenseCommand(
                        category = "عام",
                        item = normalizedStatement,
                        amount = amount,
                        note = "",
                        date = date,
                    ),
                )
                _uiMessage.value = UiMessage.Success("تم حفظ المصروف")
                _quickActionEffects.send(HomeQuickActionEffect.ExpenseSaved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiMessage.value = UiMessage.Error(ErrorHumanizer.humanize(error, "حفظ المصروف"))
            } finally {
                releaseQuickActionBusy(QuickActionIds.RECORD_EXPENSE)
            }
        }
    }


    private fun isCurrentQuickActionAuthorized(actionId: String, expectedDestinationId: String): Boolean {
        val action = resolveCurrentAuthorizedQuickAction(
            actionId = actionId,
            actions = quickActions.value,
            context = homePermissionContext.value,
        ) ?: return false
        return action.destination.id == expectedDestinationId
    }

    private fun emitQuickActionEffect(effect: HomeQuickActionEffect) {
        viewModelScope.launch { _quickActionEffects.send(effect) }
    }

    private fun rejectQuickActionExecution() {
        _uiMessage.value = UiMessage.Warning("لم يعد هذا الإجراء متاحًا بالصلاحيات الحالية")
    }

    private fun tryAcquireQuickActionBusy(actionId: String): Boolean =
        tryAcquireQuickActionBusy(_busyQuickActionIds, actionId)

    private fun releaseQuickActionBusy(actionId: String) {
        releaseQuickActionBusy(_busyQuickActionIds, actionId)
    }

    fun openActivityEvent(eventKey: String) {
        val context = homePermissionContext.value ?: return rejectActivityEvent()
        val destination = resolveVisibleActivityEventDestination(activityEvents.value, eventKey, context)
            ?: return rejectActivityEvent()
        if (!_activityEventDestinations.tryEmit(destination)) rejectActivityEvent()
    }

    fun openPendingAction(eventKey: String) {
        val context = homePermissionContext.value ?: return rejectPendingAction()
        val visibleActions = pendingActions.value
        val result = dispatchPendingAction.openEvent(context, visibleActions, eventKey)
        val event = visibleActions.firstOrNull { it.eventKey == eventKey.trim() }
        if (result is PendingActionDispatchResult.Authorized && event?.shouldOpenDetails() == true) {
            showPendingActionDetails(event.eventKey)
        } else {
            handleDispatch(result)
        }
    }

    fun executePendingAction(eventKey: String, actionId: String) {
        val context = homePermissionContext.value ?: return rejectPendingAction()
        val visibleActions = pendingActions.value
        val result = dispatchPendingAction.dispatchAction(
            context = context,
            visiblePendingActions = visibleActions,
            eventKey = eventKey,
            actionId = actionId,
        )
        val event = visibleActions.firstOrNull { it.eventKey == eventKey.trim() }
        val action = event?.actions?.firstOrNull { it.id == actionId.trim() }
        if (
            result is PendingActionDispatchResult.Authorized &&
            event?.shouldOpenDetails(action?.id) == true
        ) {
            showPendingActionDetails(event.eventKey)
        } else {
            handleDispatch(result)
        }
    }

    fun markPendingActionSeen(eventKey: String) = viewModelScope.launch {
        val context = homePermissionContext.value ?: return@launch
        if (!dispatchPendingAction.canManageEvent(context, pendingActions.value, eventKey)) return@launch
        runCatching {
            managePendingActionState.markSeen(context, eventKey, pendingActionClock.nowEpochMillis())
        }
    }

    fun snoozePendingAction(eventKey: String, snoozedUntilEpochMillis: Long) = viewModelScope.launch {
        val context = homePermissionContext.value ?: return@launch rejectPendingAction()
        if (!dispatchPendingAction.canManageEvent(context, pendingActions.value, eventKey)) {
            return@launch rejectPendingAction()
        }
        val now = pendingActionClock.nowEpochMillis()
        runCatching {
            managePendingActionState.snooze(context, eventKey, now, snoozedUntilEpochMillis)
        }.onFailure { error ->
            _uiMessage.value = UiMessage.Error(ErrorHumanizer.humanize(error, "تأجيل الحدث"))
        }
    }

    fun dismissPendingAction(eventKey: String) = viewModelScope.launch {
        val context = homePermissionContext.value ?: return@launch rejectPendingAction()
        if (!dispatchPendingAction.canManageEvent(context, pendingActions.value, eventKey)) {
            return@launch rejectPendingAction()
        }
        runCatching {
            managePendingActionState.dismiss(context, eventKey, pendingActionClock.nowEpochMillis())
        }.onFailure { error ->
            _uiMessage.value = UiMessage.Error(ErrorHumanizer.humanize(error, "إخفاء الحدث"))
        }
    }

    fun saveQuickActionScroll(stableKey: String, offset: Int) {
        restorationState[QUICK_ACTION_SCROLL_KEY] = stableKey
        restorationState[QUICK_ACTION_SCROLL_OFFSET_KEY] = offset.coerceAtLeast(0)
    }

    fun saveActivityScroll(stableKey: String, offset: Int) {
        restorationState[ACTIVITY_SCROLL_KEY] = stableKey
        restorationState[ACTIVITY_SCROLL_OFFSET_KEY] = offset.coerceAtLeast(0)
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    private fun handleDispatch(result: PendingActionDispatchResult) {
        when (result) {
            is PendingActionDispatchResult.Authorized -> dispatchDestination(result.destination)
            is PendingActionDispatchResult.Rejected -> rejectPendingAction()
        }
    }

    private fun showPendingActionDetails(eventKey: String) {
        if (!_pendingActionEffects.tryEmit(HomePendingActionEffect.ShowDetails(eventKey))) {
            rejectPendingAction()
        }
    }

    private fun dispatchDestination(destination: HomeDestination) {
        val effect = when (destination.id) {
            HomeDestinationIds.PARTY_CALL -> destination.arguments[DispatchPendingActionUseCase.PHONE_ARGUMENT]
                ?.let(HomePendingActionEffect::Dial)
            HomeDestinationIds.PARTY_WHATSAPP -> destination.arguments[DispatchPendingActionUseCase.PHONE_ARGUMENT]
                ?.let(HomePendingActionEffect::WhatsApp)
            HomeDestinationIds.PENDING_ACTION_REMIND -> destination.arguments[DispatchPendingActionUseCase.EVENT_KEY_ARGUMENT]
                ?.let(HomePendingActionEffect::RequestSnooze)
            else -> HomePendingActionEffect.Navigate(destination)
        }
        if (effect == null || !_pendingActionEffects.tryEmit(effect)) rejectPendingAction()
    }

    private fun rejectActivityEvent() {
        _uiMessage.value = UiMessage.Info("لم يعد هذا الحدث متاحًا أو لا تملك صلاحيته")
    }

    private fun rejectPendingAction() {
        _uiMessage.value = UiMessage.Info("لم يعد هذا الإجراء متاحًا أو لا تملك صلاحيته")
    }

    private companion object {
        const val EDUCATIONAL_CONTENT_ID_KEY = "home.educational_content_id"
        const val QUICK_ACTION_SCROLL_KEY = "home.quick_actions.scroll_key"
        const val QUICK_ACTION_SCROLL_OFFSET_KEY = "home.quick_actions.scroll_offset"
        const val ACTIVITY_SCROLL_KEY = "home.activity.scroll_key"
        const val ACTIVITY_SCROLL_OFFSET_KEY = "home.activity.scroll_offset"
        const val ACTIVITY_WINDOW_BOUNDARY_MILLIS: Long = 60L * 60L * 1_000L
    }
}


internal fun resolveCurrentAuthorizedQuickAction(
    actionId: String,
    actions: List<QuickAction>,
    context: HomePermissionContext?,
): QuickAction? {
    if (actionId.isBlank() || context == null) return null
    val canonical = actions.firstOrNull { action -> action.id == actionId } ?: return null
    return canonical.takeIf { action -> action.isAllowedBy(context) }
}


internal fun tryAcquireQuickActionBusy(
    state: MutableStateFlow<Set<String>>,
    actionId: String,
): Boolean {
    while (true) {
        val current = state.value
        if (actionId in current) return false
        if (state.compareAndSet(current, current + actionId)) return true
    }
}

internal fun releaseQuickActionBusy(
    state: MutableStateFlow<Set<String>>,
    actionId: String,
) {
    state.update { busyIds -> busyIds - actionId }
}


internal fun createQuickActionEffectChannel(): Channel<HomeQuickActionEffect> =
    Channel(Channel.BUFFERED)
