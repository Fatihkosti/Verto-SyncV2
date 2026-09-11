package com.verto.app.ui.screens.messages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.messages.application.ConversationListItem
import com.verto.app.feature.messages.application.RegisteredMarketerItem
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenChat: (conversationId: String, clientId: String, clientName: String) -> Unit,
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showMarketerPicker by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null) {
            snackbarHost.showSnackbar(err)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost   = { SnackbarHost(snackbarHost) },
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_ed3d29d919f1), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.loadRegisteredMarketers()
                    showMarketerPicker = true
                },
                containerColor = AccentPrimary,
                contentColor   = Color.White,
                shape          = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_015d3289457b))
            }
        }
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPrimary)
            }
            return@Scaffold
        }

        if (state.conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Chat, null, tint = TextMuted, modifier = Modifier.size(MessagesDimensions.dp56))
                    Spacer(Modifier.height(MessagesDimensions.dp12))
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_9bb687f4a9a7), color = TextMuted, fontSize = MessagesTextScale.sp15)
                    Spacer(Modifier.height(MessagesDimensions.dp8))
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_5283562f6da8), color = AccentLight, fontSize = MessagesTextScale.sp13)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding),
            contentPadding  = PaddingValues(vertical = MessagesDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(MessagesDimensions.dp1)
        ) {
            items(state.conversations, key = { it.conversationId }) { conv ->
                SwipeToDeleteRow(
                    onDelete = { pendingDeleteId = conv.conversationId }
                ) {
                    ConversationRow(
                        item    = conv,
                        onClick = { onOpenChat(conv.conversationId, conv.clientId, conv.clientName) }
                    )
                }
            }
        }
    }

    // تأكيد الحذف
    pendingDeleteId?.let { delId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor   = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_e2ed01a92bf1), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_c875f6f6f2e6), color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(delId)
                    pendingDeleteId = null
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete), color = ErrorColor) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                }
            }
        )
    }

    // قائمة اختيار المسوّق
    if (showMarketerPicker) {
        MarketerPickerDialog(
            marketers = state.registeredMarketers,
            onSelect  = { marketer ->
                showMarketerPicker = false
                viewModel.startConversation(marketer.clientId) { convId ->
                    onOpenChat(convId, marketer.clientId, marketer.clientName)
                }
            },
            onDismiss = { showMarketerPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                    ErrorColor else Color.Transparent,
                label = androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_2e35c67ba8ca)
            )
            val scale by animateFloatAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) 1f else 0.75f,
                label = androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_f96a9c56df7e)
            )
            Box(
                Modifier.fillMaxSize().background(color).padding(start = MessagesDimensions.dp20),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.Filled.Delete, null,
                    tint = Color.White,
                    modifier = Modifier.scale(scale)
                )
            }
        }
    ) {
        content()
    }
}

@Composable
private fun ConversationRow(item: ConversationListItem, onClick: () -> Unit) {
    Surface(
        onClick    = onClick,
        color      = BgCard,
        modifier   = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = MessagesDimensions.dp16, vertical = MessagesDimensions.dp12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة الشخص
            Box(
                Modifier.size(MessagesDimensions.dp44).clip(CircleShape).background(AccentBlueDim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.clientName.take(1),
                    color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = MessagesTextScale.sp18
                )
            }
            Spacer(Modifier.width(MessagesDimensions.dp12))
            Column(Modifier.weight(1f)) {
                Text(item.clientName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = MessagesTextScale.sp15)
                Spacer(Modifier.height(MessagesDimensions.dp2))
                Text(
                    item.lastMessage.ifBlank { item.subject },
                    color = if (item.unreadCount > 0) TextPrimary else TextMuted,
                    fontSize = MessagesTextScale.sp13,
                    fontWeight = if (item.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MessagesDimensions.dp8))
            Column(horizontalAlignment = Alignment.End) {
                Text(item.lastAt.take(10), color = TextMuted, fontSize = MessagesTextScale.sp11)
                if (item.unreadCount > 0) {
                    Spacer(Modifier.height(MessagesDimensions.dp4))
                    Box(
                        Modifier.size(MessagesDimensions.dp20).clip(CircleShape).background(AccentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.unreadCount.toString(),
                            color = Color.White, fontSize = MessagesTextScale.sp11, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketerPickerDialog(
    marketers: List<RegisteredMarketerItem>,
    onSelect: (RegisteredMarketerItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_26f08012fb44), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (marketers.isEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_86a01e164614),
                    color = TextMuted, textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = MessagesDimensions.dp360),
                    verticalArrangement = Arrangement.spacedBy(MessagesDimensions.dp4)
                ) {
                    items(marketers) { marketer ->
                        VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
                            onClick = { onSelect(marketer) },
                            colors  = CardDefaults.cardColors(containerColor = BgCard),
                            shape   = RoundedCornerShape(MessagesDimensions.dp8),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(MessagesDimensions.dp12),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(marketer.clientName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(marketer.typeLabel, color = AccentLight, fontSize = MessagesTextScale.sp12)
                                }
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.messages.R.string.messages_ds_60ecbc7f40b3), color = TextMuted, fontSize = MessagesTextScale.sp18)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
        }
    )
}
