package com.verto.app.feature.management.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.management.domain.model.BenzineClientError
import com.verto.app.feature.management.domain.model.BenzineJoinCodeCandidate
import com.verto.app.feature.management.domain.model.BenzineUserHealth
import com.verto.app.feature.management.domain.repository.BenzineControlPlaneGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BenzineControlPlaneViewModel @Inject constructor(
    private val gateway: BenzineControlPlaneGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(BenzineControlPlaneState(isLoading = true))
    val state: StateFlow<BenzineControlPlaneState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.userHealth.isEmpty() && _state.value.joinCodeCandidates.isEmpty(),
                isRefreshing = true,
                errorMessage = null,
            )

            val (candidatesResult, healthResult, errorsResult) = coroutineScope {
                val candidates = async { gateway.loadJoinCodeCandidates() }
                val health = async { gateway.loadUserHealth() }
                val errors = async { gateway.loadRecentErrors() }
                Triple(candidates.await(), health.await(), errors.await())
            }

            val failures = listOfNotNull(
                candidatesResult.exceptionOrNull(),
                healthResult.exceptionOrNull(),
                errorsResult.exceptionOrNull(),
            )

            _state.value = _state.value.copy(
                isLoading = false,
                isRefreshing = false,
                joinCodeCandidates = candidatesResult.getOrElse { _state.value.joinCodeCandidates },
                userHealth = healthResult.getOrElse { _state.value.userHealth },
                recentErrors = errorsResult.getOrElse { _state.value.recentErrors },
                errorMessage = failures.firstOrNull()?.toBenzineMessage(),
            )
        }
    }

    fun issueJoinCode(candidate: BenzineJoinCodeCandidate) {
        if (_state.value.isIssuingCode) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isIssuingCode = true,
                issueCodeClientId = candidate.clientId,
                generatedJoinCode = null,
                generatedJoinCodeClientName = "",
                errorMessage = null,
            )
            gateway.issueJoinCode(candidate.clientId, candidate.accountType)
                .onSuccess { code ->
                    _state.value = _state.value.copy(
                        isIssuingCode = false,
                        issueCodeClientId = null,
                        generatedJoinCode = code,
                        generatedJoinCodeClientName = candidate.name,
                        actionMessage = "تم إنشاء كود الانضمام",
                    )
                    refreshCandidatesOnly()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isIssuingCode = false,
                        issueCodeClientId = null,
                        errorMessage = error.toBenzineMessage(),
                    )
                }
        }
    }

    fun clearGeneratedJoinCode() {
        _state.value = _state.value.copy(
            generatedJoinCode = null,
            generatedJoinCodeClientName = "",
        )
    }

    fun consumeActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }

    private fun refreshCandidatesOnly() {
        viewModelScope.launch {
            gateway.loadJoinCodeCandidates().onSuccess { candidates ->
                _state.value = _state.value.copy(joinCodeCandidates = candidates)
            }
        }
    }
}

data class BenzineControlPlaneState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val joinCodeCandidates: List<BenzineJoinCodeCandidate> = emptyList(),
    val userHealth: List<BenzineUserHealth> = emptyList(),
    val recentErrors: List<BenzineClientError> = emptyList(),
    val isIssuingCode: Boolean = false,
    val issueCodeClientId: String? = null,
    val generatedJoinCode: String? = null,
    val generatedJoinCodeClientName: String = "",
    val errorMessage: String? = null,
    val actionMessage: String? = null,
) {
    val healthyCount: Int get() = userHealth.count { it.healthStatus == "HEALTHY" }
    val warningCount: Int get() = userHealth.count { it.healthStatus in setOf("WARNING", "NO_TELEMETRY") }
    val criticalCount: Int get() = userHealth.count { it.healthStatus == "CRITICAL" }
    val offlineCount: Int get() = userHealth.count { it.healthStatus == "OFFLINE" }
}

private fun Throwable.toBenzineMessage(): String {
    val raw = message.orEmpty()
    return when {
        "commission_manage_required" in raw || "AUTODRIVE_MANAGE_REQUIRED" in raw -> "لا تملك صلاحية إدارة AutoDrive"
        "client_already_linked" in raw || "CLIENT_ALREADY_LINKED" in raw -> "هذا العميل مرتبط مسبقًا بحساب AutoDrive"
        "client_account_type_mismatch" in raw || "CLIENT_ACCOUNT_TYPE_MISMATCH" in raw -> "نوع العميل في Verto لا يطابق نوع حساب AutoDrive"
        "client_not_found" in raw || "CLIENT_NOT_FOUND" in raw -> "تعذر العثور على العميل المحدد"
        "AUTODRIVE_NOT_ENABLED" in raw -> "AutoDrive غير مفعّل لهذه المؤسسة"
        raw.isNotBlank() -> "تعذر تنفيذ عملية AutoDrive"
        else -> "تعذر الاتصال بخدمة AutoDrive"
    }
}
