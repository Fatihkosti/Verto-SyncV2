package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.ui.components.VertoOutlinedTextField

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationListItem
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimalConversationsScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    viewModel: OptimalConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage

    Scaffold(
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_32631d94e937), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
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
                .padding(horizontal = OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
        ) {
            VertoOutlinedTextField(
                value = state.searchTerm,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_47b19a6e2cde)) },
            )

            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                errorMessage != null -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = OptimalDimensions.dp24),
                )

                state.conversations.isEmpty() -> Text(
                    text = if (state.searchTerm.isBlank()) {
                        androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_e7d8bd5ceda6)
                    } else {
                        androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_600d9190a03c)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = OptimalDimensions.dp24),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = OptimalDimensions.dp20),
                    verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10),
                ) {
                    items(
                        items = state.conversations,
                        key = OptimalConversationListItem::conversationId,
                    ) { conversation ->
                        OptimalConversationCard(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation.clientId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptimalConversationCard(
    conversation: OptimalConversationListItem,
    onClick: () -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OptimalDimensions.dp16),
            horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp5),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.companyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DateUtils.formatDateTime(conversation.lastActivityAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = conversation.lastMessagePreview.ifBlank { androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_8663eff2edf6) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                conversation.lastSenderName?.let { senderName ->
                    Text(
                        text = buildString {
                            append(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_e3816d19fffc))
                            append(senderName)
                            conversation.lastSenderRole?.takeIf(String::isNotBlank)?.let { role ->
                                append(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_6cac93616868))
                                append(role)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (conversation.unreadCount > 0) {
                Badge {
                    Text(conversation.unreadCount.coerceAtMost(99).toString())
                }
            }
        }
    }
}
