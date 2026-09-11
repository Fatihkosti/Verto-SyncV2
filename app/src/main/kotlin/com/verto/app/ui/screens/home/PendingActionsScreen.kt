package com.verto.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.R
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTabRow
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.PendingActionSection

private data class PendingTab(
    val label: String,
    val section: PendingActionSection,
)

/** Session 358: operational pending actions are intentionally split into three business tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingActionsScreen(
    onBack: () -> Unit,
    onOpenHomeDestination: (HomeDestination) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pendingActions by vm.pendingActions.collectAsStateWithLifecycle()
    val uiMessage by vm.uiMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var snoozeEventKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsEventKey by rememberSaveable { mutableStateOf<String?>(null) }

    val tabs = listOf(
        PendingTab(stringResource(R.string.home_pending_tab_customers), PendingActionSection.CUSTOMER),
        PendingTab(stringResource(R.string.home_pending_tab_suppliers), PendingActionSection.SUPPLIER),
        PendingTab(stringResource(R.string.home_pending_tab_inventory), PendingActionSection.INVENTORY),
    )
    val selectedTab = tabs[selectedTabIndex.coerceIn(tabs.indices)]
    val visibleActions = pendingActions.filter { action -> action.section == selectedTab.section }

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        vm.clearUiMessage()
    }

    LaunchedEffect(vm, context) {
        vm.pendingActionEffects.collect { effect ->
            when (effect) {
                is HomePendingActionEffect.Navigate -> onOpenHomeDestination(effect.destination)
                is HomePendingActionEffect.Dial -> runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", effect.phone, null)),
                    )
                }.onFailure {
                    snackbarHostState.showSnackbar(context.getString(R.string.home_open_call_failed))
                }
                is HomePendingActionEffect.WhatsApp -> runCatching {
                    val normalized = effect.phone.filter(Char::isDigit)
                    require(normalized.isNotBlank())
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized")),
                    )
                }.onFailure {
                    snackbarHostState.showSnackbar(context.getString(R.string.home_open_whatsapp_failed))
                }
                is HomePendingActionEffect.RequestSnooze -> snoozeEventKey = effect.eventKey
                is HomePendingActionEffect.ShowDetails -> detailsEventKey = effect.eventKey
            }
        }
    }

    val snoozeEvent = pendingActions.firstOrNull { it.eventKey == snoozeEventKey }
    val detailsEvent = pendingActions.firstOrNull {
        it.eventKey == detailsEventKey && it.details != null
    }

    LaunchedEffect(snoozeEventKey, snoozeEvent) {
        if (snoozeEventKey != null && snoozeEvent == null) snoozeEventKey = null
    }
    LaunchedEffect(detailsEventKey, detailsEvent) {
        if (detailsEventKey != null && detailsEvent == null) detailsEventKey = null
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

    detailsEvent?.details?.let { details ->
        PendingActionDetailsDialog(
            details = details,
            onDismiss = { detailsEventKey = null },
        )
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_pending_all_title),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.verto.core.designsystem.R.string.verto_navigate_back),
                            tint = TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            VertoTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BgCard,
                contentColor = AccentPrimary,
                modifier = Modifier
                    .padding(horizontal = HomeDesignTokens.screenHorizontalPadding, vertical = VertoSpacing.sm)
                    .clip(RoundedCornerShape(VertoRadius.md)),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val count = pendingActions.count { it.section == tab.section }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(stringResource(R.string.home_pending_tab_count, tab.label, count)) },
                    )
                }
            }

            if (visibleActions.isEmpty()) {
                VertoEmptyState(
                    title = stringResource(R.string.home_pending_all_empty),
                    message = stringResource(R.string.home_pending_tab_empty, selectedTab.label),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(VertoSpacing.lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = HomeDesignTokens.screenHorizontalPadding,
                        vertical = VertoSpacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
                ) {
                    items(
                        items = visibleActions,
                        key = { event -> event.eventKey },
                    ) { event ->
                        PendingActionCard(
                            event = event,
                            onOpen = {
                                vm.markPendingActionSeen(event.eventKey)
                                vm.openPendingAction(event.eventKey)
                            },
                            onAction = { action -> vm.executePendingAction(event.eventKey, action.id) },
                            onSnooze = { snoozeEventKey = event.eventKey },
                            onDismiss = { vm.dismissPendingAction(event.eventKey) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
