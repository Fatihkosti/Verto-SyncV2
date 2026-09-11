package com.verto.app.data.sync.push

/** Mirrors the production dependency-state policy in UnifiedSyncPushEngine for focused Session 336 tests. */
internal fun dependencyBlockReason336(parentState: String?): String? = when (parentState) {
    null -> "DEPENDENCY_MISSING"
    "ACKNOWLEDGED" -> null
    "PENDING", "LEASED", "RETRY" -> "DEPENDENCY_NOT_ACKNOWLEDGED"
    "REQUIRES_REVIEW", "REJECTED" -> "DEPENDENCY_FAILED"
    else -> "DEPENDENCY_FAILED"
}
