package com.verto.app.ui.screens.home

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.R
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.QuickAction

@Composable
internal fun HomeQuickActionsSection(
    actions: List<QuickAction>,
    listState: LazyListState,
    busyActionIds: Set<String>,
    onActionClick: (QuickAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.home_quick_actions_title),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (actions.isEmpty()) {
            VertoEmptyState(
                title = stringResource(R.string.home_quick_actions_empty_title),
                message = stringResource(R.string.home_quick_actions_empty_message),
                icon = Icons.Filled.Tune,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val visibleCount = quickActionVisibleCount(
                    availableWidthDp = maxWidth.value.toInt(),
                    fontScale = fontScale,
                ).let { policyCount ->
                    if (fontScale < 1.3f && maxWidth >= HomeDesignTokens.quickActionMinWidth * 4) {
                        4
                    } else {
                        policyCount
                    }
                }
                val gap = VertoSpacing.xs
                val calculatedActionWidth = (maxWidth - gap * (visibleCount - 1)) / visibleCount.toFloat()
                val actionWidth = calculatedActionWidth
                    .coerceAtLeast(HomeDesignTokens.quickActionMinWidth)
                    .let { if (visibleCount >= 4) calculatedActionWidth else it }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(VertoSpacing.none),
                        flingBehavior = rememberSnapFlingBehavior(listState),
                    ) {
                        items(actions, key = QuickAction::id) { action ->
                            QuickActionTile(
                                action = action,
                                width = actionWidth,
                                isBusy = action.id in busyActionIds,
                                onClick = { onActionClick(action) },
                            )
                        }
                    }
                    QuickActionPageIndicators(
                        itemCount = actions.size,
                        visibleCount = visibleCount,
                        listState = listState,
                    )
                }
            }
        }
    }

}
