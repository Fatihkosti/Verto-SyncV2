package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.domain.model.OptimalBadgeCounts
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationQueryPort
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.domain.repository.OptimalOutboxRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOptimalBadgesUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val accessSource: OptimalHomeAccessSource,
    private val conversationQuery: OptimalConversationQueryPort,
    private val outboxRepository: OptimalOutboxRepository,
) {
    operator fun invoke(): Flow<OptimalBadgeCounts> = combine(
        sessionReader.organizationId.map(String::trim).distinctUntilChanged(),
        accessSource.grantedPermissions,
    ) { organizationId, permissions ->
        OptimalBadgeScope(
            organizationId = organizationId,
            canViewMessages = ManagementOptimalPermission.VIEW_OPTIMAL_MESSAGES in permissions,
            canViewSyncIssues = ManagementOptimalPermission.VIEW_OPTIMAL_SYNC_ISSUES in permissions,
        )
    }
        .distinctUntilChanged()
        .flatMapLatest(::observeScope)
        .distinctUntilChanged()

    private fun observeScope(scope: OptimalBadgeScope): Flow<OptimalBadgeCounts> {
        if (scope.organizationId.isBlank()) return flowOf(OptimalBadgeCounts())

        val unreadMessages = if (scope.canViewMessages) {
            conversationQuery.observeConversations(scope.organizationId)
                .map { conversations ->
                    conversations
                        .asSequence()
                        .filter { it.organizationId == scope.organizationId }
                        .sumOf { it.unreadCount.coerceAtLeast(0).toLong() }
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                }
        } else {
            flowOf(0)
        }

        val syncIssues = if (scope.canViewSyncIssues) {
            outboxRepository.observeIssues(scope.organizationId)
                .map { issues ->
                    issues.count { it.organizationId == scope.organizationId }
                }
        } else {
            flowOf(0)
        }

        return combine(unreadMessages, syncIssues) { unread, issues ->
            OptimalBadgeCounts(
                unreadMessages = unread.coerceAtLeast(0),
                syncIssues = issues.coerceAtLeast(0),
            )
        }.onStart {
            // Clear the previous tenant immediately while the new Room flows start.
            emit(OptimalBadgeCounts())
        }
    }
}

private data class OptimalBadgeScope(
    val organizationId: String,
    val canViewMessages: Boolean,
    val canViewSyncIssues: Boolean,
)
