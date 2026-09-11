package com.verto.app.feature.dashboard.presentation.observation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.feature.dashboard.api.TeamObservation
import com.verto.feature.dashboard.api.TeamObservationRepository
import com.verto.feature.dashboard.api.TeamObservationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TeamObservationsViewModel @Inject constructor(
    private val repository: TeamObservationRepository,
    private val syncManager: SyncManager,
    sessionReader: SessionReader,
) : ViewModel() {
    val observations: StateFlow<List<TeamObservation>> = repository.observeCurrentOrganization()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isAdmin: StateFlow<Boolean> = sessionReader.role
        .map { it.equals("admin", ignoreCase = true) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { repository.refreshFromRemote() }
                .onFailure { _message.value = "تعذر تحديث ملاحظات الفريق الآن" }
            _isRefreshing.value = false
        }
    }

    fun setStatus(id: String, status: TeamObservationStatus) = mutate {
        repository.setStatus(id, status)
    }

    fun setImportant(id: String, important: Boolean) = mutate {
        repository.setImportant(id, important)
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    // Durable generation: UI does not wait for network before reflecting the local decision.
                    syncManager.request(SyncRequestReason.OUTBOX_WRITE)
                }
                .onFailure { _message.value = "تعذر حفظ التعديل" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
