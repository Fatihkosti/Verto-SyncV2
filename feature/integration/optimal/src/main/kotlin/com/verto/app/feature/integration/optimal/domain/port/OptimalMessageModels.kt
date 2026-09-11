package com.verto.app.feature.integration.optimal.domain.port

enum class OptimalMessageKind { TEXT, IMAGE, VOICE, VIDEO, DOCUMENT }
enum class OptimalMessageDeliveryStatus { PENDING, SENT, RECEIVED, FAILED }

data class OptimalSenderSnapshot(val senderId: String, val senderName: String, val senderRole: String)

data class OptimalMessageMediaDraft(
    val mediaId: String,
    val localUri: String? = null,
    val remoteUrl: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
)

data class OptimalMessageMediaLocation(val localUri: String?, val remoteUrl: String?)

data class OptimalMessageMedia(
    val mediaId: String,
    val messageId: String,
    val location: OptimalMessageMediaLocation,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val createdAt: Long
) {
    val localUri: String? get() = location.localUri
    val remoteUrl: String? get() = location.remoteUrl
}

data class OptimalMessageIdentity(
    val organizationId: String,
    val messageId: String,
    val conversationId: String,
    val clientId: String,
    val remoteId: String?,
)

data class OptimalMessageState(
    val sender: OptimalSenderSnapshot,
    val kind: OptimalMessageKind,
    val body: String,
    val deliveryStatus: OptimalMessageDeliveryStatus,
    val isRead: Boolean,
)

data class OptimalMessageTiming(val createdAt: Long, val updatedAt: Long)

data class OptimalMessage(
    val identity: OptimalMessageIdentity,
    val state: OptimalMessageState,
    val timing: OptimalMessageTiming,
    val media: List<OptimalMessageMedia> = emptyList(),
) {
    val organizationId: String get() = identity.organizationId
    val messageId: String get() = identity.messageId
    val conversationId: String get() = identity.conversationId
    val clientId: String get() = identity.clientId
    val remoteId: String? get() = identity.remoteId
    val sender: OptimalSenderSnapshot get() = state.sender
    val kind: OptimalMessageKind get() = state.kind
    val body: String get() = state.body
    val deliveryStatus: OptimalMessageDeliveryStatus get() = state.deliveryStatus
    val isRead: Boolean get() = state.isRead
    val createdAt: Long get() = timing.createdAt
    val updatedAt: Long get() = timing.updatedAt
}
