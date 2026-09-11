package com.verto.app.feature.messages.domain.port

import kotlinx.coroutines.flow.Flow

enum class CompanyMessageTimelineKind {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    DOCUMENT,
}

/** Read-only projection owned by Messages for cross-feature company timelines. */
data class CompanyMessageTimelineItem(
    val organizationId: String,
    val clientId: String,
    val messageId: String,
    val conversationId: String,
    val senderType: String,
    val kind: CompanyMessageTimelineKind,
    val body: String,
    val occurredAt: Long,
)

fun interface CompanyMessageTimelinePort {
    fun observeCompanyMessages(
        organizationId: String,
        clientId: String,
    ): Flow<List<CompanyMessageTimelineItem>>
}
