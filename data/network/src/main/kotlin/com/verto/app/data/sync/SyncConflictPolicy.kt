package com.verto.app.data.sync

enum class SyncConflictResolution {
    KEEP_LOCAL,
    APPLY_REMOTE
}

/**
 * Conflict policy for mutable rows.
 *
 * 1. A dirty local row always wins until its push succeeds.
 * 2. Otherwise the newest timestamp wins when both timestamps exist.
 * 3. Rows without comparable timestamps accept the remote copy.
 */
object SyncConflictPolicy {
    fun resolve(
        localDirty: Boolean,
        localUpdatedAt: Long? = null,
        remoteUpdatedAt: Long? = null
    ): SyncConflictResolution {
        if (localDirty) return SyncConflictResolution.KEEP_LOCAL
        if (localUpdatedAt != null && remoteUpdatedAt != null && localUpdatedAt >= remoteUpdatedAt) {
            return SyncConflictResolution.KEEP_LOCAL
        }
        return SyncConflictResolution.APPLY_REMOTE
    }
}
