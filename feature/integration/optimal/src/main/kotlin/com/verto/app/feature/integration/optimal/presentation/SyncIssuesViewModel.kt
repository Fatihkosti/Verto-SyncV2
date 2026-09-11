package com.verto.app.feature.integration.optimal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveOptimalSyncIssuesUseCase
import com.verto.app.feature.integration.optimal.application.RetryOptimalSyncUseCase
import com.verto.app.feature.integration.optimal.domain.model.OptimalSyncIssue
import com.verto.app.feature.integration.optimal.domain.model.RetryOptimalSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class SyncIssuesUiState(
    val isLoading: Boolean = true,
    val issues: List<OptimalSyncIssue> = emptyList(),
    val retryingEventIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val feedback: String? = null,
)

private data class SyncIssuesSource(
    val isLoading: Boolean,
    val issues: List<OptimalSyncIssue>,
    val errorMessage: String? = null,
)

@HiltViewModel
class SyncIssuesViewModel @Inject constructor(
    observeIssues: ObserveOptimalSyncIssuesUseCase,
    private val retrySync: RetryOptimalSyncUseCase,
) : ViewModel() {
    private val retrying = MutableStateFlow<Set<String>>(emptySet())
    private val feedback = MutableStateFlow<String?>(null)

    private val source = observeIssues()
        .map { SyncIssuesSource(isLoading = false, issues = it) }
        .onStart { emit(SyncIssuesSource(isLoading = true, issues = emptyList())) }
        .catch {
            emit(
                SyncIssuesSource(
                    isLoading = false,
                    issues = emptyList(),
                    errorMessage = "تعذّر تحميل أخطاء المزامنة",
                ),
            )
        }

    internal val uiState: StateFlow<SyncIssuesUiState> = combine(
        source,
        retrying,
        feedback,
    ) { current, retryingIds, message ->
        SyncIssuesUiState(
            isLoading = current.isLoading,
            issues = current.issues,
            retryingEventIds = retryingIds,
            errorMessage = current.errorMessage,
            feedback = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncIssuesUiState(),
    )

    fun retry(eventId: String) {
        if (eventId in retrying.value) return
        viewModelScope.launch {
            retrying.value = retrying.value + eventId
            feedback.value = when (val result = retrySync(eventId)) {
                is RetryOptimalSyncResult.Retried -> if (result.syncScheduled) {
                    "بدأت إعادة المحاولة"
                } else {
                    "أُعيدت العملية إلى قائمة الانتظار وستعمل مع المزامنة القادمة"
                }
                RetryOptimalSyncResult.NotFoundOrNotRetryable -> "لم تعد العملية قابلة لإعادة المحاولة"
                RetryOptimalSyncResult.PermissionDenied -> "لا تملك صلاحية إعادة المحاولة"
                RetryOptimalSyncResult.SessionUnavailable -> "الجلسة غير متاحة"
            }
            retrying.value = retrying.value - eventId
        }
    }

    fun consumeFeedback() {
        feedback.value = null
    }
}
