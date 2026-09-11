package com.verto.app.feature.integration.optimal.domain.model

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessage

data class OptimalChatSnapshot(
    val organizationId: String,
    val clientId: String,
    val conversationId: String,
    val companyName: String,
    val currentUserId: String,
    val unreadCount: Int,
    val isArchived: Boolean,
    val messages: List<OptimalMessage>,
)

sealed interface OptimalChatLoadResult {
    data class Ready(val snapshot: OptimalChatSnapshot) : OptimalChatLoadResult
    data object NotFound : OptimalChatLoadResult
    data object SessionUnavailable : OptimalChatLoadResult
    data object PermissionDenied : OptimalChatLoadResult
}

sealed interface SendOptimalTextResult {
    data class Saved(val message: OptimalMessage) : SendOptimalTextResult
    data object EmptyText : SendOptimalTextResult
    data object InvalidConversation : SendOptimalTextResult
    data object PermissionDenied : SendOptimalTextResult
    data object BackendContractBlocked : SendOptimalTextResult
    data object PersistenceFailed : SendOptimalTextResult
}

sealed interface MarkOptimalReadResult {
    data class Marked(val changedMessages: Int) : MarkOptimalReadResult
    data object PermissionDenied : MarkOptimalReadResult
    data object ConversationUnavailable : MarkOptimalReadResult
}

sealed interface ArchiveOptimalConversationResult {
    data object Archived : ArchiveOptimalConversationResult
    data object PermissionDenied : ArchiveOptimalConversationResult
    data object BackendContractBlocked : ArchiveOptimalConversationResult
    data object Failed : ArchiveOptimalConversationResult
}
