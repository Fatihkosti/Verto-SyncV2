package com.verto.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.core.designsystem.R
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.DisabledColor
import com.verto.app.ui.theme.DisabledContainer
import com.verto.app.ui.theme.InfoColor
import com.verto.app.ui.theme.InfoContainer
import com.verto.app.ui.theme.OfflineColor
import com.verto.app.ui.theme.OfflineContainer
import com.verto.app.ui.theme.PermissionColor
import com.verto.app.ui.theme.PermissionContainer
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.ErrorContainer
import com.verto.app.ui.theme.OnDanger
import com.verto.app.ui.theme.OnPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.SuccessContainer
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.VertoStroke
import com.verto.app.ui.theme.VertoAlpha
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.WaitingColor
import com.verto.app.ui.theme.WaitingContainer
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.WarningContainer
import com.verto.app.ui.theme.VertoComponentSize

enum class VertoStatusTone {
    Info,
    Success,
    Warning,
    Error,
    Waiting,
    Offline,
    Permission,
    Disabled,
}

@Composable
private fun VertoStatusTone.accent(): Color = when (this) {
    VertoStatusTone.Info -> InfoColor
    VertoStatusTone.Success -> SuccessColor
    VertoStatusTone.Warning -> WarningColor
    VertoStatusTone.Error -> ErrorColor
    VertoStatusTone.Waiting -> WaitingColor
    VertoStatusTone.Offline -> OfflineColor
    VertoStatusTone.Permission -> PermissionColor
    VertoStatusTone.Disabled -> DisabledColor
}

@Composable
private fun VertoStatusTone.container(): Color = when (this) {
    VertoStatusTone.Info -> InfoContainer
    VertoStatusTone.Success -> SuccessContainer
    VertoStatusTone.Warning -> WarningContainer
    VertoStatusTone.Error -> ErrorContainer
    VertoStatusTone.Waiting -> WaitingContainer
    VertoStatusTone.Offline -> OfflineContainer
    VertoStatusTone.Permission -> PermissionContainer
    VertoStatusTone.Disabled -> DisabledContainer
}

private fun VertoStatusTone.icon(): ImageVector = when (this) {
    VertoStatusTone.Info -> Icons.Filled.Info
    VertoStatusTone.Success -> Icons.Filled.CheckCircle
    VertoStatusTone.Warning -> Icons.Filled.Warning
    VertoStatusTone.Error -> Icons.Filled.Error
    VertoStatusTone.Waiting -> Icons.Filled.HourglassTop
    VertoStatusTone.Offline -> Icons.Filled.CloudOff
    VertoStatusTone.Permission -> Icons.Filled.Lock
    VertoStatusTone.Disabled -> Icons.Filled.Lock
}

@Composable
fun VertoStatusBanner(
    title: String,
    message: String,
    tone: VertoStatusTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = tone.accent()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$title. $message"
            },
        color = tone.container(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(VertoStroke.thin, accent.copy(alpha = VertoAlpha.statusBorder)),
    ) {
        Row(
            modifier = Modifier.padding(VertoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = VertoAlpha.soft),
            ) {
                Icon(
                    imageVector = tone.icon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(VertoSpacing.xs).size(VertoSize.iconMedium),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.heightIn(min = VertoSize.minTouchTarget),
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                    ) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

enum class VertoEmptyStateVariant { Card, Plain }

@Composable
fun VertoEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = Icons.Filled.Info,
    iconText: String? = null,
    variant: VertoEmptyStateVariant = VertoEmptyStateVariant.Card,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(
                horizontal = if (variant == VertoEmptyStateVariant.Plain) VertoSpacing.xxxl else VertoSpacing.xl,
                vertical = if (variant == VertoEmptyStateVariant.Plain) VertoSpacing.xxxl else VertoSpacing.xxl,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
        ) {
            when {
                iconText != null -> Text(
                    text = iconText,
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                )

                icon != null -> Surface(shape = CircleShape, color = AccentPrimary.copy(alpha = VertoAlpha.subtle)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.padding(VertoSpacing.md).size(VertoSize.iconHero),
                    )
                }
            }
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = message,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(VertoSpacing.xxs))
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = VertoComponentSize.buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                ) {
                    Text(actionLabel, color = TextOnAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (variant == VertoEmptyStateVariant.Card) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = BgCard,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(VertoStroke.thin, BorderColor),
            content = content,
        )
    } else {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun VertoLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BgCard,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(VertoStroke.thin, BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(VertoSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
        ) {
            CircularProgressIndicator(
                color = AccentPrimary,
                strokeWidth = VertoStroke.loading,
                modifier = Modifier.size(VertoSize.iconHero),
            )
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun VertoConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.verto_action_cancel),
) {
    val confirmColor = if (destructive) ErrorColor else AccentPrimary
    val confirmContentColor = if (destructive) OnDanger else OnPrimary
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                imageVector = if (destructive) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = confirmColor,
            )
        },
        title = { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmColor,
                    contentColor = confirmContentColor,
                ),
            ) { Text(confirmLabel, color = confirmContentColor, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = TextSecondary) }
        },
    )
}
