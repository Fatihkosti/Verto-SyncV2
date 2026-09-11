package com.verto.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.R
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.PendingAction
import kotlinx.coroutines.delay

/** Session 358: Home rotates one actionable event every five minutes. */
@Composable
internal fun HomePendingActionsSection(
    pendingActions: List<PendingAction>,
    onOpenEvent: (String) -> Unit,
    onExecuteAction: (String, String) -> Unit,
    onRequestSnooze: (String) -> Unit,
    onDismissEvent: (String) -> Unit,
    onEventSeen: (String) -> Unit,
    onReadMoreEvents: () -> Unit,
) {
    val sectionTitle = stringResource(R.string.home_pending_title)
    if (pendingActions.isEmpty()) {
        HomePendingActionsEmptyState(sectionTitle)
        return
    }

    var visibleIndex by rememberSaveable { mutableIntStateOf(0) }
    val stableKeys = pendingActions.map(PendingAction::eventKey)
    LaunchedEffect(stableKeys) {
        visibleIndex = visibleIndex.coerceIn(0, pendingActions.lastIndex)
        if (pendingActions.size > 1) {
            while (true) {
                delay(PENDING_ROTATION_INTERVAL_MILLIS)
                visibleIndex = (visibleIndex + 1) % pendingActions.size
            }
        }
    }
    val event = pendingActions[visibleIndex.coerceIn(0, pendingActions.lastIndex)]

    LaunchedEffect(event.eventKey) {
        onEventSeen(event.eventKey)
    }

    Column(
        modifier = Modifier.fillMaxWidth().semantics { paneTitle = sectionTitle },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sectionTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                    text = stringResource(R.string.home_pending_read_more),
                    color = AccentPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(VertoRadius.sm))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.home_pending_read_more_description),
                            onClick = onReadMoreEvents,
                        )
                        .padding(horizontal = VertoSpacing.xs, vertical = VertoSpacing.xxs),
                )
        }
        Spacer(Modifier.height(VertoSpacing.sm))
        PendingActionCard(
            event = event,
            onOpen = { onOpenEvent(event.eventKey) },
            onAction = { action -> onExecuteAction(event.eventKey, action.id) },
            onSnooze = { onRequestSnooze(event.eventKey) },
            onDismiss = { onDismissEvent(event.eventKey) },
            onInteractionChanged = { },
        )
    }
}

private const val PENDING_ROTATION_INTERVAL_MILLIS: Long = 5L * 60L * 1_000L
