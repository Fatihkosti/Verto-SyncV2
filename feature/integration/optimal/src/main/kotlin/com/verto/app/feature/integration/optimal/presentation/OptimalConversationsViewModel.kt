package com.verto.app.feature.integration.optimal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveOptimalConversationsUseCase
import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationListItem
import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal data class OptimalConversationsUiState(
    val isLoading: Boolean = true,
    val conversations: List<OptimalConversationListItem> = emptyList(),
    val searchTerm: String = "",
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OptimalConversationsViewModel @Inject constructor(
    observeConversations: ObserveOptimalConversationsUseCase,
) : ViewModel() {
    private val query = MutableStateFlow(OptimalConversationQuery())

    internal val uiState: StateFlow<OptimalConversationsUiState> = query
        .flatMapLatest { currentQuery ->
            observeConversations(currentQuery)
                .map { conversations ->
                    OptimalConversationsUiState(
                        isLoading = false,
                        conversations = conversations,
                        searchTerm = currentQuery.searchTerm,
                    )
                }
                .onStart {
                    emit(
                        OptimalConversationsUiState(
                            isLoading = true,
                            searchTerm = currentQuery.searchTerm,
                        ),
                    )
                }
                .catch {
                    emit(
                        OptimalConversationsUiState(
                            isLoading = false,
                            searchTerm = currentQuery.searchTerm,
                            errorMessage = "تعذّر تحميل محادثات Optimal",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OptimalConversationsUiState(),
        )

    fun updateSearch(searchTerm: String) {
        query.value = OptimalConversationQuery(searchTerm = searchTerm)
    }
}
