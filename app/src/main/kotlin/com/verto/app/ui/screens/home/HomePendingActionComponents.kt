package com.verto.app.ui.screens.home

import com.verto.app.ui.components.VertoButton

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.verto.app.R
import com.verto.app.feature.dashboard.application.pendingaction.pendingActionSnoozeOptions
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.AccentLight
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionPriority
import java.text.DateFormat
import java.util.Date

@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var isTouchExplorationEnabled by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager.isTouchExplorationEnabled)
    }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            isTouchExplorationEnabled = enabled
        }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        onDispose {
            accessibilityManager.removeTouchExplorationStateChangeListener(listener)
        }
    }
    return isTouchExplorationEnabled
}

@Composable
internal fun HomePendingActionsEmptyState(sectionTitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { paneTitle = sectionTitle },
    ) {
        Text(
            text = sectionTitle,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(VertoSpacing.sm))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeDesignTokens.pendingCardHeight),
            color = BgCard,
            shape = RoundedCornerShape(VertoRadius.lg),
            border = BorderStroke(VertoStroke.thin, BorderColor),
            tonalElevation = VertoElevation.card,
        ) {
            VertoEmptyState(
                title = stringResource(R.string.home_pending_empty_title),
                message = stringResource(R.string.home_pending_empty_message),
                icon = Icons.Filled.TaskAlt,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun PendingActionCard(
    event: PendingAction,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onAction: (HomeAction) -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit = {},
) {
    val priorityLabel = event.priority.label()
    val openDescription = stringResource(R.string.home_pending_open)
    val moreActionsDescription = stringResource(R.string.home_pending_more_actions)
    val snoozeLabel = stringResource(R.string.home_pending_snooze)
    val hideLabel = stringResource(R.string.home_pending_hide)
    val accessibilityLabel = stringResource(
        R.string.home_pending_accessibility,
        priorityLabel,
        event.title,
        event.summary,
    )
    var menuExpanded by remember(event.eventKey) { mutableStateOf(false) }
    val primaryAction = remember(event.actions) {
        event.actions.firstOrNull { action ->
            action.id != "remind_later" && action.id != "remind_customer"
        } ?: event.actions.firstOrNull()
    }
    val secondaryActions = remember(event.actions, primaryAction) {
        event.actions.filterNot { action -> action.id == primaryAction?.id }
    }

    DisposableEffect(event.eventKey) {
        onDispose { onInteractionChanged(false) }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HomeDesignTokens.pendingActionCardHeight)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
            }
            .clickable(
                onClickLabel = openDescription,
                onClick = onOpen,
            ),
        color = BgCard,
        shape = RoundedCornerShape(VertoRadius.lg),
        border = BorderStroke(
            VertoStroke.thin,
            event.priority.color().copy(alpha = VertoAlpha.border),
        ),
        tonalElevation = VertoElevation.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VertoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = priorityLabel,
                        color = event.priority.color(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(VertoRadius.xs))
                            .background(event.priority.color().copy(alpha = VertoAlpha.soft))
                            .padding(horizontal = VertoSpacing.xs, vertical = VertoSpacing.xxs),
                    )
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = event.priority.color(),
                        modifier = Modifier.size(VertoSize.iconSmall),
                    )
                }

                Text(
                    text = event.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = event.summary,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    primaryAction?.let { action ->
                        VertoButton(
                            onClick = { onAction(action) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = HomeDesignTokens.pendingActionButtonHeight),
                            shape = RoundedCornerShape(VertoRadius.md),
                            contentPadding = PaddingValues(
                                horizontal = VertoSpacing.xs,
                                vertical = VertoSpacing.none,
                            ),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = {
                                menuExpanded = true
                                onInteractionChanged(true)
                            },
                            modifier = Modifier.size(HomeDesignTokens.pendingInlineAction),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = moreActionsDescription,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                                onInteractionChanged(false)
                            },
                        ) {
                            secondaryActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        menuExpanded = false
                                        onInteractionChanged(false)
                                        onAction(action)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(snoozeLabel) },
                                onClick = {
                                    menuExpanded = false
                                    onInteractionChanged(false)
                                    onSnooze()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(hideLabel) },
                                onClick = {
                                    menuExpanded = false
                                    onInteractionChanged(false)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(HomeDesignTokens.pendingIllustrationWidth)
                    .heightIn(min = HomeDesignTokens.pendingIllustrationWidth),
                contentAlignment = Alignment.Center,
            ) {
                PendingActionIllustration()
            }
        }
    }
}

@Composable
internal fun PendingActionIllustration() {
    val errorColor = ErrorColor
    Canvas(
        modifier = Modifier
            .width(HomeDesignTokens.pendingIllustrationWidth + VertoSpacing.lg)
            .height(HomeDesignTokens.pendingIllustrationWidth),
    ) {
        val scale = size.minDimension / 96f
        fun s(value: Float) = value * scale

        drawOval(
            brush = Brush.horizontalGradient(
                listOf(
                    AccentLight.copy(alpha = 0.18f),
                    AccentPrimary.copy(alpha = 0.10f),
                ),
            ),
            topLeft = Offset(s(5f), s(78f)),
            size = Size(s(87f), s(13f)),
        )

        // Soft leaves behind the calendar.
        withTransform({ rotate(-46f, Offset(s(15f), s(56f))) }) {
            drawOval(
                color = AccentPrimary.copy(alpha = 0.34f),
                topLeft = Offset(s(4f), s(51f)),
                size = Size(s(23f), s(11f)),
            )
        }
        withTransform({ rotate(-67f, Offset(s(14f), s(44f))) }) {
            drawOval(
                color = AccentPrimary.copy(alpha = 0.23f),
                topLeft = Offset(s(2f), s(39f)),
                size = Size(s(24f), s(11f)),
            )
        }
        withTransform({ rotate(-20f, Offset(s(22f), s(65f))) }) {
            drawOval(
                color = AccentPrimary.copy(alpha = 0.20f),
                topLeft = Offset(s(12f), s(61f)),
                size = Size(s(20f), s(9f)),
            )
        }

        // Calendar body.
        drawRoundRect(
            color = AccentPrimary.copy(alpha = 0.92f),
            topLeft = Offset(s(28f), s(22f)),
            size = Size(s(48f), s(58f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(8f)),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(s(32f), s(28f)),
            size = Size(s(44f), s(48f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(6f)),
        )
        drawRoundRect(
            color = AccentBlue.copy(alpha = 0.70f),
            topLeft = Offset(s(28f), s(22f)),
            size = Size(s(48f), s(14f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(8f)),
        )
        drawRect(
            color = AccentBlue.copy(alpha = 0.70f),
            topLeft = Offset(s(28f), s(29f)),
            size = Size(s(48f), s(7f)),
        )
        listOf(37f, 49f, 61f).forEach { x ->
            drawLine(
                color = AccentPrimary,
                start = Offset(s(x), s(19f)),
                end = Offset(s(x), s(29f)),
                strokeWidth = s(3.2f),
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            color = errorColor,
            start = Offset(s(54f), s(43f)),
            end = Offset(s(54f), s(57f)),
            strokeWidth = s(4f),
            cap = StrokeCap.Round,
        )
        drawCircle(errorColor, radius = s(2.2f), center = Offset(s(54f), s(64f)))

        // Small clock on the lower right.
        drawCircle(
            color = AccentLight,
            radius = s(17f),
            center = Offset(s(82f), s(68f)),
        )
        drawCircle(
            color = AccentPrimary,
            radius = s(14f),
            center = Offset(s(82f), s(68f)),
            style = Stroke(width = s(3f)),
        )
        drawLine(
            color = AccentPrimary,
            start = Offset(s(82f), s(68f)),
            end = Offset(s(82f), s(60f)),
            strokeWidth = s(2.5f),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = AccentPrimary,
            start = Offset(s(82f), s(68f)),
            end = Offset(s(89f), s(72f)),
            strokeWidth = s(2.5f),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun PendingActionSnoozeDialog(
    eventTitle: String,
    nowEpochMillis: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(nowEpochMillis) { pendingActionSnoozeOptions(nowEpochMillis) }
    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    HomeDialogSurface(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(VertoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.home_pending_snooze_title),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(eventTitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            options.forEach { option ->
                VertoSecondaryButton(
                    text = stringResource(
                        R.string.home_pending_snooze_option,
                        option.label,
                        dateFormatter.format(Date(option.untilEpochMillis)),
                    ),
                    onClick = { onSelect(option.untilEpochMillis) },
                )
            }
            VertoSecondaryButton(
                text = stringResource(R.string.home_cancel),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun PendingActionPriority.label(): String = when (this) {
    PendingActionPriority.LOW -> stringResource(R.string.home_priority_low)
    PendingActionPriority.NORMAL -> stringResource(R.string.home_priority_normal)
    PendingActionPriority.HIGH -> stringResource(R.string.home_priority_high)
    PendingActionPriority.CRITICAL -> stringResource(R.string.home_priority_critical)
}

@Composable
internal fun PendingActionPriority.color() = when (this) {
    PendingActionPriority.LOW -> TextMuted
    PendingActionPriority.NORMAL -> AccentBlue
    PendingActionPriority.HIGH -> WarningColor
    PendingActionPriority.CRITICAL -> ErrorColor
}
