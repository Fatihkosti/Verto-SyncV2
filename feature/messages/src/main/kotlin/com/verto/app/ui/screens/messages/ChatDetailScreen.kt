package com.verto.app.ui.screens.messages

import com.verto.app.ui.components.VertoOutlinedTextField

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.verto.app.feature.messages.application.MessageItem
import com.verto.app.feature.messages.application.MessageKind
import com.verto.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    clientId: String,
    clientName: String,
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val state        by viewModel.uiState.collectAsStateWithLifecycle()
    val context       = LocalContext.current
    val snackbarHost  = remember { SnackbarHostState() }
    val listState     = rememberLazyListState()

    LaunchedEffect(conversationId, clientId) {
        viewModel.init(conversationId, clientId)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null) {
            snackbarHost.showSnackbar(err)
            viewModel.clearError()
        }
    }

    var messageToDelete by remember { mutableStateOf<MessageItem?>(null) }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost   = { SnackbarHost(snackbarHost) },
        topBar = {
            VertoTopAppBar(
                title = { Text(clientName, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSurface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (state.isLoading) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPrimary)
                }
            } else {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(MessagesDimensions.dp12),
                    verticalArrangement = Arrangement.spacedBy(MessagesDimensions.dp8)
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            Box(Modifier.fillParentMaxWidth().padding(vertical = MessagesDimensions.dp48)) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_8320b1bcc158),
                                    color    = TextMuted,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            msg         = msg,
                            context     = context,
                            onLongPress = { messageToDelete = msg }
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderColor)

            ChatInputBar(
                isSending   = state.isSending,
                onSendText  = { viewModel.sendText(it) },
                onSendImage = { bytes, mime -> viewModel.sendImage(bytes, mime) },
                onSendVoice = { bytes, ms   -> viewModel.sendVoice(bytes, ms)  }
            )
        }
    }

    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            containerColor   = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_4607329d2e82), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_6375368b96ee), color = TextMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMessage(msg.id); messageToDelete = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete), color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MessageItem,
    context: Context,
    onLongPress: () -> Unit
) {
    val isAdmin = msg.senderType == "ADMIN"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = MessagesDimensions.dp280)
                .clip(
                    RoundedCornerShape(
                        topStart    = MessagesDimensions.dp16, topEnd  = MessagesDimensions.dp16,
                        bottomStart = if (isAdmin) MessagesDimensions.dp16 else MessagesDimensions.dp4,
                        bottomEnd   = if (isAdmin) MessagesDimensions.dp4  else MessagesDimensions.dp16
                    )
                )
                .background(if (isAdmin) AccentPrimary else BgCard)
                .combinedClickable(onLongClick = onLongPress, onClick = {})
                .padding(horizontal = MessagesDimensions.dp12, vertical = MessagesDimensions.dp8)
        ) {
            Column {
                when (msg.kind) {
                    MessageKind.IMAGE -> {
                        if (!msg.mediaUrl.isNullOrBlank()) {
                            AsyncImage(
                                model              = msg.mediaUrl,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = MessagesDimensions.dp220)
                                    .clip(RoundedCornerShape(MessagesDimensions.dp8))
                            )
                        }
                    }
                    MessageKind.VOICE -> {
                        VoicePlayer(
                            url        = msg.mediaUrl ?: "",
                            durationMs = msg.mediaDurationMs ?: 0L,
                            isAdmin    = isAdmin
                        )
                    }
                    else -> {
                        Text(
                            msg.body,
                            color    = if (isAdmin) Color.White else TextPrimary,
                            fontSize = MessagesTextScale.sp14
                        )
                    }
                }
                Spacer(Modifier.height(MessagesDimensions.dp2))
                Text(
                    msg.createdAt.take(16).replace('T', ' '),
                    color    = if (isAdmin) Color.White.copy(alpha = 0.6f) else TextMuted,
                    fontSize = MessagesTextScale.sp10,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VoicePlayer(url: String, durationMs: Long, isAdmin: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress  by remember { mutableStateOf(0f) }
    var player    by remember { mutableStateOf<MediaPlayer?>(null) }
    val scope     = rememberCoroutineScope()
    val textColor = if (isAdmin) Color.White else TextPrimary

    DisposableEffect(url) {
        onDispose { player?.release(); player = null }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MessagesDimensions.dp6)) {
        VertoIconButton(
            onClick = {
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                } else {
                    if (player == null && url.isNotBlank()) {
                        player = MediaPlayer().apply {
                            setDataSource(url)
                            prepare()
                            setOnCompletionListener { isPlaying = false; progress = 0f }
                        }
                    }
                    player?.start()
                    isPlaying = true
                    scope.launch {
                        val p = player ?: return@launch
                        while (isPlaying && p.isPlaying) {
                            val dur = p.duration.takeIf { it > 0 } ?: 1
                            progress = p.currentPosition.toFloat() / dur.toFloat()
                            delay(200)
                        }
                    }
                }
            },
            modifier = Modifier.size(MessagesDimensions.dp36)
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                null, tint = textColor
            )
        }
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.width(MessagesDimensions.dp100).height(MessagesDimensions.dp3),
            color      = textColor,
            trackColor = textColor.copy(alpha = 0.3f)
        )
        val secs = ((if (durationMs > 0) durationMs else 0L) / 1000).toInt()
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_d11d4ef8c977).format(secs / 60, secs % 60), color = textColor, fontSize = MessagesTextScale.sp11)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatInputBar(
    isSending:   Boolean,
    onSendText:  (String) -> Unit,
    onSendImage: (ByteArray, String) -> Unit,
    onSendVoice: (ByteArray, Long) -> Unit
) {
    var text        by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordSecs  by remember { mutableStateOf(0) }
    var recorder    by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile  by remember { mutableStateOf<File?>(null) }
    var recordStart by remember { mutableStateOf(0L) }
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
            recordFile  = file
            recordStart = System.currentTimeMillis()
            recorder = (if (android.os.Build.VERSION.SDK_INT >= 31)
                MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            ).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordSecs  = 0
            scope.launch {
                while (isRecording) { delay(1000); recordSecs++ }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            val mime  = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) onSendImage(bytes, mime)
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri   = cameraUri ?: return@rememberLauncherForActivityResult
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes != null) onSendImage(bytes, "image/jpeg")
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
            val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    fun startRecording() {
        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopRecording() {
        val durationMs = System.currentTimeMillis() - recordStart
        recorder?.apply { stop(); release() }
        recorder    = null
        isRecording = false
        val file  = recordFile ?: return
        val bytes = file.readBytes()
        if (bytes.isNotEmpty()) onSendVoice(bytes, durationMs)
        file.delete()
        recordFile = null
    }

    Surface(color = BgSurface, tonalElevation = MessagesDimensions.dp2) {
        if (isRecording) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MessagesDimensions.dp12, vertical = MessagesDimensions.dp10),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MessagesDimensions.dp12)
            ) {
                Icon(Icons.Filled.Mic, null, tint = ErrorColor, modifier = Modifier.size(MessagesDimensions.dp24))
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_beab66f16492).format(recordSecs / 60, recordSecs % 60),
                    color    = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                VertoIconButton(onClick = { stopRecording() }) {
                    Icon(Icons.Filled.Stop, null, tint = AccentPrimary)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MessagesDimensions.dp8, vertical = MessagesDimensions.dp6),
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MessagesDimensions.dp4)
            ) {
                VertoIconButton(onClick = { launchCamera() }, modifier = Modifier.size(MessagesDimensions.dp48)) {
                    Icon(Icons.Filled.CameraAlt, null, tint = TextMuted)
                }
                VertoIconButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.size(MessagesDimensions.dp48)) {
                    Icon(Icons.Filled.AttachFile, null, tint = TextMuted)
                }
                VertoOutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_aa54ca9529fa), color = TextMuted, fontSize = MessagesTextScale.sp14) },
                    modifier      = Modifier.weight(1f),
                    singleLine    = false,
                    maxLines      = 4,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentPrimary,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    ),
                    shape = RoundedCornerShape(MessagesDimensions.dp20)
                )
                if (text.isNotBlank()) {
                    VertoIconButton(
                        onClick  = { onSendText(text); text = "" },
                        enabled  = !isSending,
                        modifier = Modifier
                            .size(MessagesDimensions.dp44)
                            .background(AccentPrimary, CircleShape)
                            .align(Alignment.Bottom)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color       = Color.White,
                                modifier    = Modifier.size(MessagesDimensions.dp20),
                                strokeWidth = MessagesDimensions.dp2
                            )
                        } else {
                            Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(MessagesDimensions.dp20))
                        }
                    }
                } else {
                    VertoIconButton(
                        onClick  = { startRecording() },
                        modifier = Modifier
                            .size(MessagesDimensions.dp44)
                            .background(AccentBlue, CircleShape)
                            .align(Alignment.Bottom)
                    ) {
                        Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(MessagesDimensions.dp20))
                    }
                }
            }
        }
    }
}
