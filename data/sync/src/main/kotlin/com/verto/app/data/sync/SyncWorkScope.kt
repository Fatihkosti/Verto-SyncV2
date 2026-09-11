package com.verto.app.data.sync

import java.security.MessageDigest

/** Immutable tenant/account identity captured when WorkManager work is enqueued. */
data class SyncWorkScope(
    val organizationId: String,
    val userId: String,
    val sessionEpoch: Long,
) {
    init {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(userId.isNotBlank()) { "userId is required" }
        require(sessionEpoch > 0L) { "sessionEpoch must be positive" }
    }

    internal val stableKey: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest("$organizationId:$userId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
}

class StaleSyncWorkScopeException(message: String) : IllegalStateException(message)
