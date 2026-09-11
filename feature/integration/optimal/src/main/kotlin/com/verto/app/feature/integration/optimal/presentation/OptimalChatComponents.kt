package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessage
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageDeliveryStatus
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMedia
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.feature.integration.optimal.domain.model.OptimalStoredAttachment
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@Composable
internal fun AudioRecordingBar(
    elapsedMs: Long,
    isStopping: Boolean,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier
            .fillMaxWidth()
            .padding(OptimalDimensions.dp12),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OptimalDimensions.dp8, vertical = OptimalDimensions.dp6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
        ) {
            VertoIconButton(onClick = onCancel, enabled = !isStopping) {
                Icon(Icons.Default.Delete, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_7b7cee7ed9a8))
            }
            Icon(Icons.Default.Mic, contentDescription = null)
            Text(
                text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_16d1d073f4da, formatDuration(elapsedMs)),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
            )
            VertoIconButton(onClick = onStop, enabled = !isStopping) {
                if (isStopping) {
                    CircularProgressIndicator(modifier = Modifier.size(OptimalDimensions.dp22))
                } else {
                    Icon(Icons.Default.Stop, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_b70c5444eb82))
                }
            }
        }
    }
}

@Composable
internal fun AudioPreviewBar(
    preview: OptimalStoredAttachment,
    isSending: Boolean,
    onDiscard: () -> Unit,
    onSend: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier
            .fillMaxWidth()
            .padding(OptimalDimensions.dp12),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OptimalDimensions.dp8, vertical = OptimalDimensions.dp6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
        ) {
            VoicePlaybackButton(preview.privateUri)
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_8994e61f8932), fontWeight = FontWeight.Medium)
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c75f125c6fd3, formatDuration(preview.durationMs ?: 0L), formatBytes(preview.sizeBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VertoIconButton(onClick = onDiscard, enabled = !isSending) {
                Icon(Icons.Default.Delete, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c0b0c29020c9))
            }
            VertoIconButton(onClick = onSend, enabled = !isSending) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(OptimalDimensions.dp22))
                } else {
                    Icon(Icons.Default.Send, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_79080b808c5b))
                }
            }
        }
    }
}

@Composable
private fun VoicePlaybackButton(uri: String?) {
    val context = LocalContext.current
    var isPlaying by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) {
        uri?.takeIf(String::isNotBlank)?.let { source ->
            runCatching { MediaPlayer.create(context, Uri.parse(source)) }.getOrNull()
        }
    }
    DisposableEffect(player) {
        player?.setOnCompletionListener { isPlaying = false }
        onDispose {
            runCatching { player?.stop() }
            player?.release()
        }
    }
    VertoIconButton(
        enabled = player != null,
        onClick = {
            player ?: return@VertoIconButton
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        },
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_15eb510b67b7_2) else androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_15eb510b67b7),
        )
    }
}

@Composable
internal fun ChatMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(OptimalDimensions.dp32),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun OptimalMessageBubble(message: OptimalMessage, outgoing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
            modifier = Modifier.widthIn(max = OptimalDimensions.dp300),
            shape = RoundedCornerShape(OptimalDimensions.dp16),
            colors = CardDefaults.cardColors(
                containerColor = if (outgoing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = OptimalDimensions.dp12, vertical = OptimalDimensions.dp9),
                verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp6),
            ) {
                if (!outgoing) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c75f125c6fd3, message.sender.senderName, message.sender.senderRole),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                when (message.kind) {
                    OptimalMessageKind.TEXT -> Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OptimalMessageKind.VOICE -> VoiceMessageContent(message.media.firstOrNull())
                    OptimalMessageKind.IMAGE,
                    OptimalMessageKind.VIDEO,
                    OptimalMessageKind.DOCUMENT,
                    -> AttachmentMessageContent(message)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp6)) {
                    Text(
                        text = DateUtils.formatDateTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (outgoing) {
                        Text(
                            text = when (message.deliveryStatus) {
                                OptimalMessageDeliveryStatus.PENDING -> androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_e9887e25e92d)
                                OptimalMessageDeliveryStatus.SENT -> androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_f2699018c107)
                                OptimalMessageDeliveryStatus.FAILED -> androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_0fe8087b7208)
                                OptimalMessageDeliveryStatus.RECEIVED -> androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_b4e20891fe09)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceMessageContent(media: OptimalMessageMedia?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VoicePlaybackButton(media?.localUri)
        Column {
            Text(
                text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_83515367e5ee),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(formatDuration(media?.durationMs ?: 0L))
                    if (media != null) append(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_37b662f5ece8, formatBytes(media.sizeBytes)))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentMessageContent(message: OptimalMessage) {
    val icon = when (message.kind) {
        OptimalMessageKind.IMAGE -> Icons.Default.Image
        OptimalMessageKind.VIDEO -> Icons.Default.Videocam
        OptimalMessageKind.DOCUMENT -> Icons.Default.Description
        OptimalMessageKind.VOICE -> Icons.Default.Mic
        OptimalMessageKind.TEXT -> Icons.Default.AttachFile
    }
    val fallback = when (message.kind) {
        OptimalMessageKind.IMAGE -> "صورة"
        OptimalMessageKind.VIDEO -> "فيديو"
        OptimalMessageKind.DOCUMENT -> "مستند"
        OptimalMessageKind.VOICE -> "تسجيل صوتي"
        OptimalMessageKind.TEXT -> "رسالة"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(OptimalDimensions.dp30))
        Spacer(modifier = Modifier.size(OptimalDimensions.dp8))
        Column {
            Text(
                text = message.body.ifBlank { fallback },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            message.media.firstOrNull()?.let { media ->
                Text(
                    text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_c75f125c6fd3, media.mimeType, formatBytes(media.sizeBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDuration(value: Long): String {
    val totalSeconds = (value.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
    value >= 1024L -> "%.1f KB".format(value / 1024.0)
    else -> "$value B"
}
