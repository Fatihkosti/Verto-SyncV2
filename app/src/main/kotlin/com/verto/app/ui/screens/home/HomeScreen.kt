package com.verto.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.R
import com.verto.app.ui.components.UiMessage
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.components.VertoStatusBanner
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.PendingActionSection
import java.util.Calendar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNewInvoice: () -> Unit,
    onNewPurchase: () -> Unit,
    onNewInternationalInvoice: (String, Double) -> Unit,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenPendingActions: () -> Unit,
    onOpenHomeDestination: (HomeDestination) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOpenHomeDestination by rememberUpdatedState(onOpenHomeDestination)

    val userName by vm.userName.collectAsStateWithLifecycle()
    val unreadNotificationsCount by vm.unreadNotificationsCount.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val uiMessage by vm.uiMessage.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val quickActions by vm.quickActions.collectAsStateWithLifecycle()
    val busyQuickActionIds by vm.busyQuickActionIds.collectAsStateWithLifecycle()
    val isHomeExpenseSaving by vm.isHomeExpenseSaving.collectAsStateWithLifecycle()
    val educationalContent by vm.educationalContent.collectAsStateWithLifecycle()
    val pendingActions by vm.pendingActions.collectAsStateWithLifecycle()
    val primaryPendingActions = remember(pendingActions) {
        pendingActions.filter { action -> action.section != PendingActionSection.OTHER }
    }
    val activityEvents by vm.activityEvents.collectAsStateWithLifecycle()
    val isObservationSubmitting by vm.isObservationSubmitting.collectAsStateWithLifecycle()
    val observationSubmittedToken by vm.observationSubmittedToken.collectAsStateWithLifecycle()
    val quickActionScrollKey by vm.quickActionScrollKey.collectAsStateWithLifecycle()
    val quickActionScrollOffset by vm.quickActionScrollOffset.collectAsStateWithLifecycle()
    val activityScrollKey by vm.activityScrollKey.collectAsStateWithLifecycle()
    val activityScrollOffset by vm.activityScrollOffset.collectAsStateWithLifecycle()

    val quickActionListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    var quickActionRestorationApplied by remember { mutableStateOf(false) }
    var activityRestorationApplied by remember { mutableStateOf(false) }

    val fabPolicy = resolveHomeFabPolicy(
        canCreateSales = permissions?.salesCreate == true,
        canCreatePurchases = permissions?.purchasesCreate == true,
    )

    var snoozeEventKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDetailsEventKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showCurrencyDialog by rememberSaveable { mutableStateOf(false) }
    var currencyNameInput by rememberSaveable { mutableStateOf("") }
    var exchangeRateInput by rememberSaveable { mutableStateOf("") }
    var showEducationalDialog by rememberSaveable(educationalContent?.id) { mutableStateOf(false) }
    var showExpenseDialog by rememberSaveable { mutableStateOf(false) }
    var ideaText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(observationSubmittedToken) {
        if (observationSubmittedToken > 0L) ideaText = ""
    }

    LaunchedEffect(quickActions, quickActionScrollKey, quickActionScrollOffset) {
        val restoredIndex = resolveRestoredItemIndex(
            stableKeys = quickActions.map { action -> action.id },
            restoredKey = quickActionScrollKey,
            fallbackIndex = quickActionListState.firstVisibleItemIndex,
        )
        if (quickActions.isNotEmpty()) {
            val restoredPosition = sanitizeHomeScrollPosition(
                index = restoredIndex,
                // A carousel always restores on an item boundary so a stale saved offset
                // cannot leave half of a tile visible on the first frame.
                offset = 0,
                itemCount = quickActions.size,
            )
            quickActionListState.scrollToItem(restoredPosition.index, restoredPosition.offset)
            quickActionRestorationApplied = true
        }
    }

    LaunchedEffect(quickActionListState, quickActions) {
        snapshotFlow {
            if (!quickActionRestorationApplied || quickActionListState.isScrollInProgress) {
                null
            } else {
                quickActionListState.firstVisibleItemIndex to
                    quickActionListState.firstVisibleItemScrollOffset
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (index, offset) ->
                quickActions.getOrNull(index)?.let { action ->
                    vm.saveQuickActionScroll(action.id, offset)
                }
            }
    }

    LaunchedEffect(activityEvents, activityScrollKey, activityScrollOffset) {
        val restoredIndex = resolveRestoredItemIndex(
            stableKeys = activityEvents.map { event -> event.eventKey },
            restoredKey = activityScrollKey,
            fallbackIndex = activityListState.firstVisibleItemIndex,
        )
        if (activityEvents.isNotEmpty()) {
            val restoredPosition = sanitizeHomeScrollPosition(
                index = restoredIndex,
                offset = activityScrollOffset,
                itemCount = activityEvents.size,
            )
            activityListState.scrollToItem(restoredPosition.index, restoredPosition.offset)
            activityRestorationApplied = true
        }
    }

    LaunchedEffect(activityListState, activityEvents) {
        snapshotFlow {
            if (!activityRestorationApplied || activityListState.isScrollInProgress) {
                null
            } else {
                activityListState.firstVisibleItemIndex to
                    activityListState.firstVisibleItemScrollOffset
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (index, offset) ->
                activityEvents.getOrNull(index)?.let { event ->
                    vm.saveActivityScroll(event.eventKey, offset)
                }
            }
    }

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        vm.clearUiMessage()
    }

    LaunchedEffect(vm) {
        vm.activityEventDestinations.collect { destination ->
            currentOpenHomeDestination(destination)
        }
    }

    LaunchedEffect(vm, context) {
        vm.quickActionEffects.collect { effect ->
            when (effect) {
                is HomeQuickActionEffect.Navigate -> currentOpenHomeDestination(effect.destination)
                HomeQuickActionEffect.ShowExpenseDialog -> showExpenseDialog = true
                HomeQuickActionEffect.ShowInternationalPurchaseDialog -> showCurrencyDialog = true
                HomeQuickActionEffect.ExpenseSaved -> showExpenseDialog = false
            }
        }
    }

    LaunchedEffect(vm, context) {
        vm.pendingActionEffects.collect { effect ->
            when (effect) {
                is HomePendingActionEffect.Navigate -> currentOpenHomeDestination(effect.destination)
                is HomePendingActionEffect.Dial -> runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", effect.phone, null)),
                    )
                }.onFailure { snackbarHostState.showSnackbar(context.getString(R.string.home_open_call_failed)) }
                is HomePendingActionEffect.WhatsApp -> runCatching {
                    val normalized = effect.phone.filter(Char::isDigit)
                    require(normalized.isNotBlank())
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized")),
                    )
                }.onFailure { snackbarHostState.showSnackbar(context.getString(R.string.home_open_whatsapp_failed)) }
                is HomePendingActionEffect.RequestSnooze -> snoozeEventKey = effect.eventKey
                is HomePendingActionEffect.ShowDetails -> pendingDetailsEventKey = effect.eventKey
            }
        }
    }

    val snoozeEvent = pendingActions.firstOrNull { it.eventKey == snoozeEventKey }
    val pendingDetailsEvent = pendingActions.firstOrNull {
        it.eventKey == pendingDetailsEventKey && it.details != null
    }
    LaunchedEffect(snoozeEventKey, snoozeEvent) {
        if (snoozeEventKey != null && snoozeEvent == null) snoozeEventKey = null
    }
    LaunchedEffect(pendingDetailsEventKey, pendingDetailsEvent) {
        if (pendingDetailsEventKey != null && pendingDetailsEvent == null) {
            pendingDetailsEventKey = null
        }
    }
    if (snoozeEvent != null) {
        PendingActionSnoozeDialog(
            eventTitle = snoozeEvent.title,
            nowEpochMillis = System.currentTimeMillis(),
            onSelect = { until ->
                vm.snoozePendingAction(snoozeEvent.eventKey, until)
                snoozeEventKey = null
            },
            onDismiss = { snoozeEventKey = null },
        )
    }

    pendingDetailsEvent?.details?.let { details ->
        PendingActionDetailsDialog(
            details = details,
            onDismiss = { pendingDetailsEventKey = null },
        )
    }

    if (showEducationalDialog) {
        educationalContent?.let { content ->
            EducationalContentDialog(
                content = content,
                onDismiss = { showEducationalDialog = false },
            )
        }
    }

    if (showExpenseDialog) {
        HomeExpenseDialog(
            isSaving = isHomeExpenseSaving,
            onSave = vm::addExpenseFromHome,
            onDismiss = { showExpenseDialog = false },
        )
    }

    if (showCurrencyDialog) {
        InternationalPurchaseDialog(
            currencyName = currencyNameInput,
            exchangeRate = exchangeRateInput,
            onCurrencyNameChange = { currencyNameInput = it },
            onExchangeRateChange = { exchangeRateInput = it },
            onConfirm = {
                val rate = exchangeRateInput.toDoubleOrNull()
                if (currencyNameInput.isNotBlank() && rate != null && rate > 0) {
                    showCurrencyDialog = false
                    onNewInternationalInvoice(currencyNameInput.trim().uppercase(), rate)
                    currencyNameInput = ""
                    exchangeRateInput = ""
                }
            },
            onDismiss = {
                showCurrencyDialog = false
                currencyNameInput = ""
                exchangeRateInput = ""
            },
        )
    }

    HomeContent(
        state = HomeContentState(
            isRefreshing = isRefreshing,
            uiMessage = uiMessage,
            header = HomeHeaderContentState(
                userName = userName,
                unreadNotificationsCount = unreadNotificationsCount,
            ),
            quickActions = HomeQuickActionsContentState(
                actions = quickActions,
                busyActionIds = busyQuickActionIds,
            ),
            pending = HomePendingContentState(
                actions = primaryPendingActions,
                educationalContent = educationalContent,
            ),
            activity = HomeActivityContentState(
                events = activityEvents,
                fabPolicy = fabPolicy,
            ),
            idea = HomeIdeaContentState(
                text = ideaText,
                isSubmitting = isObservationSubmitting,
            ),
        ),
        events = HomeContentEvents(
            onRefresh = vm::onRefresh,
            header = HomeHeaderEvents(
                onSearch = onSearch,
                onOpenDrawer = onOpenDrawer,
                onNotifications = onNotifications,
            ),
            quickActions = HomeQuickActionEvents(
                onActionClick = vm::onQuickActionClicked,
            ),
            pending = HomePendingEvents(
                onOpenEvent = vm::openPendingAction,
                onExecuteAction = vm::executePendingAction,
                onRequestSnooze = { eventKey -> snoozeEventKey = eventKey },
                onDismissEvent = vm::dismissPendingAction,
                onEventSeen = vm::markPendingActionSeen,
                onReadMoreEvents = onOpenPendingActions,
                onReadEducation = { showEducationalDialog = true },
            ),
            idea = HomeIdeaEvents(
                onTextChange = { ideaText = it },
                onSubmit = { category -> vm.submitTeamObservation(ideaText, category) },
            ),
            activity = HomeActivityEvents(
                onOpenEvent = vm::openActivityEvent,
                onNewInvoice = onNewInvoice,
                onNewPurchase = onNewPurchase,
                onNewInternationalPurchase = { showCurrencyDialog = true },
            ),
        ),
        snackbarHostState = snackbarHostState,
        quickActionListState = quickActionListState,
        activityListState = activityListState,
    )
}
