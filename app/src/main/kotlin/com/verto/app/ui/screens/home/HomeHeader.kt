package com.verto.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import com.verto.app.R
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
internal fun HomeHeader(
    userName: String,
    unreadNotificationsCount: Int,
    onSearch: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDrawer: () -> Unit,
    onNotifications: () -> Unit,
) {
    val now by rememberHomeHeaderClock()
    val copy = homeHeaderCopyFor(now.toLocalDateTime())
    val notificationsLabel = if (unreadNotificationsCount > 0) {
        stringResource(R.string.home_header_open_notifications_unread, unreadNotificationsCount)
    } else {
        stringResource(R.string.home_header_open_notifications)
    }
    Surface(color = BgDeep, tonalElevation = VertoElevation.none) {
        Column(modifier = Modifier.padding(horizontal = HomeDesignTokens.screenHorizontalPadding)) {
            Spacer(Modifier.height(VertoSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            append(copy.greeting)
                            append("، ")
                            withStyle(SpanStyle(color = AccentPrimary)) {
                                append(homeDisplayName(userName))
                            }
                        },
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = copy.phrase,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    HeaderIconBtn(
                        label = stringResource(R.string.home_header_open_search),
                        onClick = onSearch,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(HomeDesignTokens.headerIcon),
                        )
                    }
                    HeaderIconBtn(
                        label = notificationsLabel,
                        onClick = onNotifications,
                    ) {
                        Box(
                            modifier = Modifier.size(VertoSize.iconLarge),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(HomeDesignTokens.headerIcon),
                            )
                            if (unreadNotificationsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(HomeDesignTokens.notificationDot)
                                        .clip(CircleShape)
                                        .background(AccentPrimary),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(VertoSpacing.md))
        }
    }
}

@Composable
internal fun HeaderIconBtn(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(HomeDesignTokens.headerAction)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        color = BgCard,
        shape = RoundedCornerShape(VertoRadius.md),
        border = BorderStroke(VertoStroke.thin, BorderColor),
        shadowElevation = VertoElevation.raised,
        tonalElevation = VertoElevation.none,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun rememberHomeHeaderClock(): androidx.compose.runtime.State<ZonedDateTime> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = ZonedDateTime.now(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = ZonedDateTime.now()
                value = current
                val nextBoundary = nextHomeHeaderBoundary(current)
                val delayMillis = Duration.between(current, nextBoundary)
                    .toMillis()
                    .coerceAtLeast(1L) + BOUNDARY_SCHEDULER_SAFETY_MILLIS
                delay(delayMillis)
            }
        }
    }
}

private const val BOUNDARY_SCHEDULER_SAFETY_MILLIS: Long = 25L
