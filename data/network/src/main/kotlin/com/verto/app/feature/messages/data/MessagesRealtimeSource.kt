package com.verto.app.feature.messages.data

import com.verto.app.data.remote.dto.InternalMessageDto
import kotlinx.coroutines.flow.Flow

interface MessagesRealtimeSource {
    fun observeConversationListChanges(orgId: String): Flow<Unit>
    fun observeMessages(orgId: String, conversationId: String): Flow<InternalMessageDto>
}
