package com.verto.app.feature.integration.optimal.domain.model

data class OptimalBadgeCounts(
    val unreadMessages: Int = 0,
    val syncIssues: Int = 0,
) {
    init {
        require(unreadMessages >= 0) { "unreadMessages cannot be negative" }
        require(syncIssues >= 0) { "syncIssues cannot be negative" }
    }
}
