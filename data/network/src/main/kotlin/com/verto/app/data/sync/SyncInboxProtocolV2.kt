package com.verto.app.data.sync

import kotlinx.serialization.Serializable

/** The server manifest is scoped to the authorized projection, never to hidden transaction rows. */
@Serializable
data class SyncTouchedKey(val type: String, val id: String)

/** SHA-256/bytes cover the canonical UTF-8 SyncInboxGroupBodyV2, including keys and dependencies. */
@Serializable
data class SyncInboxGroupManifestV2(
    val transactionId: String,
    val memberCount: Int,
    val firstRevision: Long,
    val lastRevision: Long,
    val contentSha256: String,
    val serializedBytes: Long,
    val touchedKeys: List<SyncTouchedKey>,
    val dependsOnTransactionIds: List<String>,
)

@Serializable
data class SyncInboxGroupBodyV2(
    val transactionId: String,
    val changes: List<SyncChange>,
    val touchedKeys: List<SyncTouchedKey>,
    val dependsOnTransactionIds: List<String>,
)

/** Explicit B08 server binding. No fallback to the older, one-based, manifest-less RPC. */
@Serializable
data class SyncInboxPullRequestV2(
    val scopeId: String,
    val cursorToken: String,
    val softLimit: Int,
    val maxGroupBytes: Long = 2_097_152L,
)
