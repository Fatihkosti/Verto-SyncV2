package com.verto.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.verto.app.R
import com.verto.app.ui.components.UiMessage
import com.verto.app.ui.components.VertoStatusBanner
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.EducationalContent
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.TeamObservationCategory

internal data class HomeHeaderContentState(
    val userName: String,
    val unreadNotificationsCount: Int,
)

internal data class HomeQuickActionsContentState(
    val actions: List<QuickAction>,
    val busyActionIds: Set<String>,
)

internal data class HomePendingContentState(
    val actions: List<PendingAction>,
    val educationalContent: EducationalContent?,
)

internal data class HomeIdeaContentState(
    val text: String,
    val isSubmitting: Boolean,
)

internal data class HomeActivityContentState(
    val events: List<ActivityEvent>,
    val fabPolicy: HomeFabPolicy,
)

internal data class HomeContentState(
    val isRefreshing: Boolean,
    val uiMessage: UiMessage?,
    val header: HomeHeaderContentState,
    val quickActions: HomeQuickActionsContentState,
    val pending: HomePendingContentState,
    val activity: HomeActivityContentState,
    val idea: HomeIdeaContentState,
)

internal data class HomeHeaderEvents(
    val onSearch: () -> Unit,
    val onOpenDrawer: () -> Unit,
    val onNotifications: () -> Unit,
)

internal data class HomeQuickActionEvents(
    val onActionClick: (QuickAction) -> Unit,
)

internal typealias HomePendingActionExecutor = (String, String) -> Unit

internal data class HomePendingEvents(
    val onOpenEvent: (String) -> Unit,
    val onExecuteAction: HomePendingActionExecutor,
    val onRequestSnooze: (String) -> Unit,
    val onDismissEvent: (String) -> Unit,
    val onEventSeen: (String) -> Unit,
    val onReadMoreEvents: () -> Unit,
    val onReadEducation: () -> Unit,
)

internal data class HomeIdeaEvents(
    val onTextChange: (String) -> Unit,
    val onSubmit: (TeamObservationCategory) -> Unit,
)

internal data class HomeActivityEvents(
    val onOpenEvent: (String) -> Unit,
    val onNewInvoice: () -> Unit,
    val onNewPurchase: () -> Unit,
    val onNewInternationalPurchase: () -> Unit,
)

internal data class HomeContentEvents(
    val onRefresh: () -> Unit,
    val header: HomeHeaderEvents,
    val quickActions: HomeQuickActionEvents,
    val pending: HomePendingEvents,
    val idea: HomeIdeaEvents,
    val activity: HomeActivityEvents,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun HomeContent(
    state: HomeContentState,
    events: HomeContentEvents,
    snackbarHostState: SnackbarHostState,
    quickActionListState: LazyListState,
    activityListState: LazyListState,
) {
    Scaffold(
        containerColor = BgDeep,
        floatingActionButton = {
            if (state.activity.fabPolicy.isVisible) {
                HomeFab(state.activity.fabPolicy, events.activity)
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val message = state.uiMessage
                VertoStatusBanner(
                    title = when (message) {
                        is UiMessage.Success -> stringResource(R.string.home_status_success_title)
                        is UiMessage.Warning -> stringResource(R.string.home_status_warning_title)
                        is UiMessage.Error -> stringResource(R.string.home_status_error_title)
                        else -> stringResource(R.string.home_status_info_title)
                    },
                    message = data.visuals.message,
                    tone = when (message) {
                        is UiMessage.Success -> VertoStatusTone.Success
                        is UiMessage.Warning -> VertoStatusTone.Warning
                        is UiMessage.Error -> VertoStatusTone.Error
                        else -> VertoStatusTone.Info
                    },
                    modifier = Modifier.padding(horizontal = VertoSize.screenHorizontalPadding),
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = events.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    HomeHeader(
                        userName = state.header.userName,
                        unreadNotificationsCount = state.header.unreadNotificationsCount,
                        onSearch = events.header.onSearch,
                        onOpenDrawer = events.header.onOpenDrawer,
                        onNotifications = events.header.onNotifications,
                    )
                }

                item(key = "quick_actions") {
                    Column(modifier = Modifier.padding(horizontal = HomeDesignTokens.screenHorizontalPadding)) {
                        HomeQuickActionsSection(
                            actions = state.quickActions.actions,
                            listState = quickActionListState,
                            busyActionIds = state.quickActions.busyActionIds,
                            onActionClick = events.quickActions.onActionClick,
                        )
                        Spacer(Modifier.height(VertoSpacing.md))
                    }
                }

                item(key = "pending") {
                    Column(modifier = Modifier.padding(horizontal = HomeDesignTokens.screenHorizontalPadding)) {
                        HomePendingActionsSection(
                            pendingActions = state.pending.actions,
                            onOpenEvent = events.pending.onOpenEvent,
                            onExecuteAction = events.pending.onExecuteAction,
                            onRequestSnooze = events.pending.onRequestSnooze,
                            onDismissEvent = events.pending.onDismissEvent,
                            onEventSeen = events.pending.onEventSeen,
                            onReadMoreEvents = events.pending.onReadMoreEvents,
                        )
                        Spacer(Modifier.height(VertoSpacing.md))
                    }
                }

                item(key = "idea_capture") {
                    Column(modifier = Modifier.padding(horizontal = HomeDesignTokens.screenHorizontalPadding)) {
                        HomeIdeaCaptureCard(
                            text = state.idea.text,
                            isSubmitting = state.idea.isSubmitting,
                            onTextChange = events.idea.onTextChange,
                            onSubmit = events.idea.onSubmit,
                        )
                        Spacer(Modifier.height(VertoSpacing.md))
                    }
                }

                state.pending.educationalContent?.let { content ->
                    item(key = "education:${content.id}") {
                        Column(modifier = Modifier.padding(horizontal = HomeDesignTokens.screenHorizontalPadding)) {
                            HomeEducationalContentCard(
                                content = content,
                                onReadMore = events.pending.onReadEducation,
                            )
                            Spacer(Modifier.height(VertoSpacing.xl))
                        }
                    }
                }

                item(key = "activity") {
                    HomeActivityFeedSection(
                        events = state.activity.events,
                        onOpenEvent = events.activity.onOpenEvent,
                        listState = activityListState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(HomeDesignTokens.activityBottomPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeFab(
    policy: HomeFabPolicy,
    events: HomeActivityEvents,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(VertoSize.fab)
            .shadow(VertoElevation.floating, RoundedCornerShape(VertoRadius.md))
            .clip(RoundedCornerShape(VertoRadius.md))
            .background(AccentPrimary)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = policy.contentDescription,
                onLongClickLabel = if (policy.supportsInternationalPurchase) {
                    stringResource(R.string.home_international_purchase_action)
                } else {
                    null
                },
                onClick = {
                    when (policy.primaryAction) {
                        HomeFabPrimaryAction.SALES -> events.onNewInvoice()
                        HomeFabPrimaryAction.PURCHASES -> events.onNewPurchase()
                        null -> Unit
                    }
                },
                onLongClick = if (policy.supportsInternationalPurchase) {
                    events.onNewInternationalPurchase
                } else {
                    null
                },
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = TextOnAccent,
        )
    }
}
