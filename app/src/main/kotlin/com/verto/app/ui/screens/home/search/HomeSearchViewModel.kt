package com.verto.app.ui.screens.home.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.error.ErrorPresentationPolicy
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.ui.screens.home.observeHomePermissionContext
import com.verto.app.ui.navigation.search.HomeSearchDestinationResolver
import com.verto.app.feature.dashboard.application.search.UnifiedHomeSearchUseCase
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeSearchQuery
import com.verto.feature.dashboard.api.HomeSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class HomeSearchViewModel @Inject constructor(
    private val search: UnifiedHomeSearchUseCase,
    sessionReader: SessionReader,
    permissionProvider: PermissionProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restorationState = savedStateHandle
    private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")
    private val selectedFilterKey = savedStateHandle.getStateFlow(
        SELECTED_FILTER_KEY,
        HomeSearchFilter.ALL.name,
    )
    private val selectedFilter: StateFlow<HomeSearchFilter> = selectedFilterKey
        .map { savedName ->
            HomeSearchFilter.entries.firstOrNull { filter -> filter.name == savedName }
                ?: HomeSearchFilter.ALL
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HomeSearchFilter.ALL,
        )
    private val expandedResultKey = savedStateHandle.getStateFlow<String?>(
        EXPANDED_RESULT_KEY,
        null,
    )

    private val permissionContext: Flow<HomePermissionContext?> =
        observeHomePermissionContext(sessionReader, permissionProvider)

    private val destinationResolver = HomeSearchDestinationResolver()
    private val retryGeneration = MutableStateFlow(0L)
    private val _navigationEvents = MutableSharedFlow<HomeDestination>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<HomeDestination> = _navigationEvents.asSharedFlow()

    private val searchOutcome: Flow<SearchOutcome> = combine(
        query
            .debounce(UnifiedHomeSearchUseCase.DEBOUNCE_MILLIS)
            .distinctUntilChanged(),
        permissionContext,
        retryGeneration,
    ) { text, context, _ -> text.trim() to context }
        .flatMapLatest { (text, context) ->
            when {
                text.length < HomeSearchUiState.MIN_QUERY_LENGTH -> flowOf(SearchOutcome.Idle)
                context == null -> flowOf(SearchOutcome.Loading(text))
                else -> executeSearch(text, context)
            }
        }

    val uiState: StateFlow<HomeSearchUiState> = combine(
        query,
        selectedFilter,
        searchOutcome,
        expandedResultKey,
    ) { text, filter, outcome, expandedKey ->
        val normalizedText = text.trim()
        val content = (outcome as? SearchOutcome.Content)
            ?.takeIf { it.query == normalizedText }
        val error = (outcome as? SearchOutcome.Error)
            ?.takeIf { it.query == normalizedText }
        val outcomeMatchesQuery = when (outcome) {
            SearchOutcome.Idle -> normalizedText.length < HomeSearchUiState.MIN_QUERY_LENGTH
            is SearchOutcome.Loading -> outcome.query == normalizedText
            is SearchOutcome.Content -> outcome.query == normalizedText
            is SearchOutcome.Error -> outcome.query == normalizedText
        }
        val visibleResults = content?.results.orEmpty().filter { it.kind in filter.kinds }
        HomeSearchUiState(
            query = text,
            selectedFilter = filter,
            results = visibleResults,
            expandedResultKey = expandedKey?.takeIf { key ->
                visibleResults.any { result -> result.stableSearchKey() == key }
            },
            isLoading = normalizedText.length >= HomeSearchUiState.MIN_QUERY_LENGTH &&
                (!outcomeMatchesQuery || outcome is SearchOutcome.Loading),
            error = error?.presentation,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeSearchUiState(),
        )

    fun onQueryChange(value: String) {
        val nextQuery = value.take(MAX_QUERY_LENGTH)
        if (nextQuery != query.value) clearExpandedResult()
        restorationState[QUERY_KEY] = nextQuery
    }

    fun clearQuery() {
        clearExpandedResult()
        restorationState[QUERY_KEY] = ""
    }

    fun retrySearch() {
        retryGeneration.value = retryGeneration.value + 1L
    }

    fun selectFilter(filter: HomeSearchFilter) {
        if (filter != selectedFilter.value) clearExpandedResult()
        restorationState[SELECTED_FILTER_KEY] = filter.name
    }

    fun toggleResultExpansion(result: HomeSearchResult) {
        val selectedKey = result.stableSearchKey()
        restorationState[EXPANDED_RESULT_KEY] = nextExpandedResultKey(
            currentKey = expandedResultKey.value,
            selectedKey = selectedKey,
        )
    }

    fun onResultAction(result: HomeSearchResult, action: HomeAction) {
        val resultKey = result.stableSearchKey()
        val actionId = action.id
        viewModelScope.launch {
            val destination = authorizeCurrentDestination(resultKey, actionId) ?: return@launch
            _navigationEvents.emit(destination)
        }
    }

    private suspend fun authorizeCurrentDestination(
        resultKey: String,
        actionId: String,
    ): HomeDestination? {
        val currentText = query.value.trim()
        if (currentText.length < HomeSearchUiState.MIN_QUERY_LENGTH) return null
        val currentContext = permissionContext.first() ?: return null
        val canonicalResults = search(
            query = HomeSearchQuery(text = currentText, limit = SEARCH_RESULT_LIMIT),
            context = currentContext,
        )
        return resolveCanonicalSearchDestination(
            canonicalResults = canonicalResults,
            resultKey = resultKey,
            actionId = actionId,
            context = currentContext,
            isDestinationValid = { destination -> destinationResolver.resolve(destination) != null },
        )
    }

    private fun clearExpandedResult() {
        restorationState[EXPANDED_RESULT_KEY] = null
    }

    private fun executeSearch(
        text: String,
        context: HomePermissionContext,
    ): Flow<SearchOutcome> = flow {
        emit(SearchOutcome.Loading(text))
        try {
            val results = search(
                query = HomeSearchQuery(text = text, limit = SEARCH_RESULT_LIMIT),
                context = context,
            )
            emit(SearchOutcome.Content(text, results))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emit(
                SearchOutcome.Error(
                    query = text,
                    presentation = ErrorPresentationPolicy.from(
                        error,
                        ErrorPresentationContext.SCREEN_LOAD,
                    ),
                ),
            )
        }
    }

    private sealed interface SearchOutcome {
        data object Idle : SearchOutcome
        data class Loading(val query: String) : SearchOutcome
        data class Content(val query: String, val results: List<HomeSearchResult>) : SearchOutcome
        data class Error(val query: String, val presentation: UserErrorPresentation) : SearchOutcome
    }

    private companion object {
        const val MAX_QUERY_LENGTH: Int = 120
        const val SEARCH_RESULT_LIMIT: Int = 24
        const val QUERY_KEY: String = "home_search_query"
        const val SELECTED_FILTER_KEY: String = "home_search_selected_filter"
        const val EXPANDED_RESULT_KEY: String = "home_search_expanded_result_key"
    }
}
