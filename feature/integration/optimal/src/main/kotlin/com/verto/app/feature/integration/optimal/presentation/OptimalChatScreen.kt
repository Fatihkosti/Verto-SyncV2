package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessage

import com.verto.app.ui.components.VertoOutlinedTextField

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.OptimalStoredAttachment
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

private val DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimalChatScreen(
    onBack: () -> Unit,
    viewModel: OptimalChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success -> viewModel.finishCameraCapture(success) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.requestCameraCapture() else viewModel.cameraPermissionDenied()
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startAudioRecording() else viewModel.audioPermissionDenied()
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            viewModel.sendPickedAttachment(
                sourceUri = it.toString(),
                mimeType = context.contentResolver.getType(it),
            )
        }
    }
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.sendPickedAttachment(
                sourceUri = it.toString(),
                mimeType = context.contentResolver.getType(it),
            )
        }
    }

    DisposableEffect(viewModel) {
        onDispose(viewModel::onChatLeaving)
    }
    LaunchedEffect(state.cameraCaptureRequest?.mediaId) {
        state.cameraCaptureRequest?.let { target ->
            viewModel.consumeCameraCaptureRequest()
            cameraLauncher.launch(Uri.parse(target.captureUri))
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(
                        text = state.companyName.ifBlank { androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_f89a8bcd6ece) },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
                    }
                },
                actions = {
                    VertoIconButton(onClick = viewModel::archive) {
                        Icon(Icons.Default.Archive, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_61abccfe7e3c))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(OptimalDimensions.dp32),
                )
                state.accessDenied -> ChatMessage("لا تملك صلاحية عرض المحادثة")
                state.notFound -> ChatMessage("المحادثة غير موجودة")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(OptimalDimensions.dp12),
                    verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                ) {
                    if (state.messages.isEmpty()) item { ChatMessage("لا توجد رسائل بعد") }
                    items(state.messages, key = OptimalMessage::messageId) { message ->
                        OptimalMessageBubble(
                            message = message,
                            outgoing = message.sender.senderId == state.currentUserId,
                        )
                    }
                }
            }

            if (!state.accessDenied && !state.notFound) {
                val baseInputEnabled = state.canSend && !state.isArchived &&
                    !state.isSending && !state.isSendingAttachment && !state.isSendingAudio
                when {
                    state.isRecordingAudio || state.isStoppingAudio -> AudioRecordingBar(
                        elapsedMs = state.audioRecordingElapsedMs,
                        isStopping = state.isStoppingAudio,
                        onCancel = viewModel::cancelAudioRecording,
                        onStop = viewModel::stopAudioRecording,
                    )
                    state.audioPreview != null -> AudioPreviewBar(
                        preview = requireNotNull(state.audioPreview),
                        isSending = state.isSendingAudio,
                        onDiscard = viewModel::discardAudioPreview,
                        onSend = viewModel::sendAudioPreview,
                    )
                    else -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OptimalDimensions.dp12),
                        horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            VertoIconButton(
                                onClick = { attachmentMenuExpanded = true },
                                enabled = baseInputEnabled && !state.isStartingAudio,
                            ) {
                                if (state.isSendingAttachment) {
                                    CircularProgressIndicator(modifier = Modifier.size(OptimalDimensions.dp22))
                                } else {
                                    Icon(Icons.Default.AttachFile, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_7c5f3902bc70))
                                }
                            }
                            DropdownMenu(
                                expanded = attachmentMenuExpanded,
                                onDismissRequest = { attachmentMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_cbc75aa194b3)) },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                    onClick = {
                                        attachmentMenuExpanded = false
                                        if (
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA,
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            viewModel.requestCameraCapture()
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_f1163732e614)) },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                    onClick = {
                                        attachmentMenuExpanded = false
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_d1da2729994a)) },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                    onClick = {
                                        attachmentMenuExpanded = false
                                        documentLauncher.launch(DOCUMENT_MIME_TYPES)
                                    },
                                )
                            }
                        }
                        VertoOutlinedTextField(
                            value = state.draftText,
                            onValueChange = viewModel::updateDraft,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_b73e88df7170)) },
                            enabled = baseInputEnabled && !state.isStartingAudio,
                            maxLines = 4,
                        )
                        VertoIconButton(
                            onClick = {
                                if (
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.startAudioRecording()
                                } else {
                                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = baseInputEnabled && state.draftText.isBlank(),
                        ) {
                            if (state.isStartingAudio) {
                                CircularProgressIndicator(modifier = Modifier.size(OptimalDimensions.dp22))
                            } else {
                                Icon(Icons.Default.Mic, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_83515367e5ee))
                            }
                        }
                        VertoIconButton(
                            onClick = viewModel::sendText,
                            enabled = baseInputEnabled && !state.isStartingAudio && state.draftText.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Send, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_send))
                        }
                    }
                }
                if (!state.canSend) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_561f709eafd1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OptimalDimensions.dp16, vertical = OptimalDimensions.dp4),
                    )
                }
            }
        }
    }
}
