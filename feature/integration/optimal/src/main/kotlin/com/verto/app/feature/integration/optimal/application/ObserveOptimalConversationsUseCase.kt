package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationListItem
import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationQuery
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationQueryPort
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOptimalConversationsUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val queryPort: OptimalConversationQueryPort,
) {
    operator fun invoke(
        query: OptimalConversationQuery = OptimalConversationQuery(),
    ): Flow<List<OptimalConversationListItem>> {
        val normalizedSearch = normalize(query.searchTerm)
        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                if (organizationId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    queryPort.observeConversations(organizationId)
                        .map { conversations ->
                            conversations
                                .asSequence()
                                .filter { it.organizationId == organizationId }
                                .filterNot(OptimalConversationListItem::isArchived)
                                .filter { conversation ->
                                    normalizedSearch.isBlank() || conversation.matches(normalizedSearch)
                                }
                                .sortedWith(
                                    compareByDescending<OptimalConversationListItem> { it.lastActivityAt }
                                        .thenBy { it.conversationId },
                                )
                                .toList()
                        }
                }
            }
    }
}

private fun OptimalConversationListItem.matches(normalizedSearch: String): Boolean = listOfNotNull(
    companyName,
    lastMessagePreview,
    lastSenderName,
    lastSenderRole,
).any { normalize(it).contains(normalizedSearch) }

private fun normalize(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
