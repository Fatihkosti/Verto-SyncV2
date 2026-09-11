package com.verto.app.feature.integration.optimal.domain.model

data class OptimalConversationListItem(
    val organizationId: String,
    val clientId: String,
    val conversationId: String,
    val companyName: String,
    val lastMessagePreview: String,
    val lastSenderName: String?,
    val lastSenderRole: String?,
    val unreadCount: Int,
    val isArchived: Boolean,
    val lastActivityAt: Long,
)

data class OptimalConversationQuery(
    val searchTerm: String = "",
)
