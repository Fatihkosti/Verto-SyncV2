package com.verto.app.feature.integration.optimal.domain.port

import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationListItem
import kotlinx.coroutines.flow.Flow

/** Read-only tenant-scoped projection over the current Messages owner. */
fun interface OptimalConversationQueryPort {
    fun observeConversations(
        organizationId: String,
    ): Flow<List<OptimalConversationListItem>>
}
