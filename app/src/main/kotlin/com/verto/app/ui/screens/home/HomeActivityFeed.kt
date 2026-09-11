package com.verto.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MiscellaneousServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.verto.app.R
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.utils.DateUtils
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun HomeActivityFeedSection(
    events: List<ActivityEvent>,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val sectionTitle = stringResource(R.string.home_activity_title)
    val relativeTimeTickEpochMillis = rememberActivityRelativeTimeTick()
    val visibleRowCount = events.size.coerceIn(1, HomeDesignTokens.activityMaxVisibleRows)
    val visibleDividerCount = if (events.size > visibleRowCount) {
        visibleRowCount
    } else {
        (visibleRowCount - 1).coerceAtLeast(0)
    }
    val activityCardHeight =
        HomeDesignTokens.activityRowMinHeight * visibleRowCount +
            VertoStroke.thin * visibleDividerCount +
            VertoSpacing.xxs * 2
    Column(modifier = modifier.semantics { paneTitle = sectionTitle }) {
        Text(
            text = sectionTitle,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = HomeDesignTokens.screenHorizontalPadding),
        )
        Spacer(Modifier.height(VertoSpacing.sm))

        if (events.isEmpty()) {
            HomeActivityEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeDesignTokens.activityEmptyStateHeight)
                    .padding(horizontal = HomeDesignTokens.screenHorizontalPadding),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HomeDesignTokens.screenHorizontalPadding),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(activityCardHeight),
                    color = BgCard,
                    shape = RoundedCornerShape(VertoRadius.lg),
                    border = BorderStroke(VertoStroke.thin, BorderColor),
                    tonalElevation = VertoElevation.card,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = VertoSpacing.md,
                            vertical = VertoSpacing.xxs,
                        ),
                    ) {
                        itemsIndexed(
                            items = events,
                            key = { _, event -> event.eventKey },
                            contentType = { _, event -> event.kind },
                        ) { index, event ->
                            HomeActivityEventRow(
                                event = event,
                                onOpen = { onOpenEvent(event.eventKey) },
                                showDivider = index < events.lastIndex,
                                relativeTimeTickEpochMillis = relativeTimeTickEpochMillis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberActivityRelativeTimeTick(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                nowEpochMillis = System.currentTimeMillis()
                delay(RELATIVE_TIME_REFRESH_MILLIS)
            }
        }
    }
    return nowEpochMillis
}

private const val RELATIVE_TIME_REFRESH_MILLIS = 60_000L

@Composable
private fun HomeActivityEventRow(
    event: ActivityEvent,
    onOpen: () -> Unit,
    showDivider: Boolean,
    relativeTimeTickEpochMillis: Long,
) {
    val configuration = LocalConfiguration.current
    val formattedTime = remember(event.occurredAtEpochMillis, configuration, relativeTimeTickEpochMillis) {
        DateUtils.formatTimeAgo(event.occurredAtEpochMillis)
    }
    val detailText = remember(event) {
        buildList {
            add(event.description)
            event.subject?.label?.let(::add)
            event.value?.let { value ->
                add(listOfNotNull(value.label, value.text).joinToString(" "))
            }
        }.joinToString(" • ")
    }
    val statusLabel = event.status.label()
    val statusColor = event.status.color()
    val accessibilityLabel = remember(event, formattedTime) {
        activityEventAccessibilityLabel(event, formattedTime)
    }

    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .height(
                HomeDesignTokens.activityRowMinHeight +
                    if (showDivider) VertoStroke.thin else VertoSpacing.none,
            )
            .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = accessibilityLabel
        },
        color = BgCard,
        shape = RoundedCornerShape(VertoRadius.xs),
        tonalElevation = VertoElevation.none,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeDesignTokens.activityRowMinHeight),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(VertoSize.iconSmall),
                )
                Surface(
                    shape = CircleShape,
                    color = event.kind.tint().copy(alpha = VertoAlpha.soft),
                ) {
                    Icon(
                        imageVector = event.kind.icon(),
                        contentDescription = null,
                        tint = event.kind.tint(),
                        modifier = Modifier.padding(VertoSpacing.xs).size(HomeDesignTokens.activityIcon),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = event.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = detailText,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = formattedTime,
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            if (showDivider) {
                androidx.compose.material3.HorizontalDivider(color = BorderColor)
            }
        }
    }
}

@Composable
private fun ActivityEventStatus.label(): String = when (this) {
    ActivityEventStatus.CREATED -> "جديد"
    ActivityEventStatus.UPDATED -> "محدث"
    ActivityEventStatus.COMPLETED -> "مكتمل"
    ActivityEventStatus.CANCELLED -> "ملغي"
    ActivityEventStatus.REVERSED -> "معكوس"
    ActivityEventStatus.OTHER -> "اليوم"
}

@Composable
private fun ActivityEventStatus.color() = when (this) {
    ActivityEventStatus.CREATED -> MaterialTheme.colorScheme.error
    ActivityEventStatus.UPDATED -> AccentBlue
    ActivityEventStatus.COMPLETED -> com.verto.app.ui.theme.SuccessColor
    ActivityEventStatus.CANCELLED,
    ActivityEventStatus.REVERSED -> MaterialTheme.colorScheme.error
    ActivityEventStatus.OTHER -> TextMuted
}

@Composable
private fun HomeActivityEmptyState(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.home_activity_empty_title)
    Box(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = title },
        contentAlignment = Alignment.TopCenter,
    ) {
        VertoEmptyState(
            title = title,
            message = stringResource(R.string.home_activity_empty_message),
            icon = Icons.Filled.History,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ActivityEventKind.icon(): ImageVector = when (this) {
    ActivityEventKind.INVOICE -> Icons.Filled.ReceiptLong
    ActivityEventKind.PAYMENT -> Icons.Filled.Payments
    ActivityEventKind.INVENTORY -> Icons.Filled.Inventory2
    ActivityEventKind.PURCHASE -> Icons.Filled.ShoppingCart
    ActivityEventKind.PARTY -> Icons.Filled.People
    ActivityEventKind.PRICE_CHANGE -> Icons.Filled.Edit
    ActivityEventKind.CANCELLATION -> Icons.Filled.Cancel
    ActivityEventKind.MAINTENANCE -> Icons.Filled.MiscellaneousServices
    ActivityEventKind.SHIPMENT -> Icons.Filled.LocalShipping
    ActivityEventKind.OTHER -> Icons.Filled.History
}

@Composable
private fun ActivityEventKind.tint() = when (this) {
    ActivityEventKind.CANCELLATION -> MaterialTheme.colorScheme.error
    ActivityEventKind.PAYMENT -> AccentPrimary
    else -> AccentBlue
}
