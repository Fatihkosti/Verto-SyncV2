package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.IssueOptimalCompanyJoinCodeUseCase
import com.verto.app.feature.integration.optimal.application.ObserveOptimalCompaniesUseCase
import com.verto.app.feature.integration.optimal.data.OptimalCompaniesRemoteRefresher
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkFilter
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkStatus
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyQuery
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationCode
import com.verto.app.feature.integration.optimal.domain.repository.OptimalRegistrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OptimalCodesUiState(
    val isLoading: Boolean = true,
    val companies: List<OptimalCompany> = emptyList(),
    val searchTerm: String = "",
    val selectedClientId: String? = null,
    val isIssuing: Boolean = false,
    val registrationCode: OptimalRegistrationCode? = null,
    val errorMessage: String? = null,
) {
    val canIssue: Boolean
        get() = !isLoading && !isIssuing && selectedClientId != null &&
            companies.any {
                it.clientId == selectedClientId && it.linkStatus == OptimalCompanyLinkStatus.UNLINKED
            }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OptimalCodesViewModel @Inject constructor(
    private val issueCompanyJoinCode: IssueOptimalCompanyJoinCodeUseCase,
    observeCompanies: ObserveOptimalCompaniesUseCase,
    private val remoteRefresher: OptimalCompaniesRemoteRefresher,
) : ViewModel() {
    private val searchTerm = MutableStateFlow("")
    private val _uiState = MutableStateFlow(OptimalCodesUiState())
    internal val uiState: StateFlow<OptimalCodesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val refresh = remoteRefresher.refresh()
            val snapshot = refresh.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        companies = emptyList(),
                        selectedClientId = null,
                        errorMessage = ErrorHumanizer.humanize(error, "تحميل أكواد الربط")
                            ?: "تعذّر تحديث شركات Verto من السيرفر",
                    )
                }
                return@launch
            }
            val authoritativeClientIds = snapshot.remoteCompanyClientIds

            searchTerm
                .flatMapLatest { term ->
                    observeCompanies(
                        OptimalCompanyQuery(
                            searchTerm = term,
                            linkFilter = OptimalCompanyLinkFilter.ALL,
                        ),
                    )
                }
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            companies = emptyList(),
                            selectedClientId = null,
                            errorMessage = ErrorHumanizer.humanize(error, "تحميل أكواد الربط")
                                ?: "تعذّر تحميل شركات Verto",
                        )
                    }
                }
                .collect { localCompanies ->
                    val companies = localCompanies.filter { it.clientId in authoritativeClientIds }
                    _uiState.update { current ->
                        val validSelection = current.selectedClientId?.takeIf { selectedId ->
                            companies.any {
                                it.clientId == selectedId &&
                                    it.linkStatus == OptimalCompanyLinkStatus.UNLINKED
                            }
                        }
                        current.copy(
                            isLoading = false,
                            companies = companies,
                            selectedClientId = validSelection,
                        )
                    }
                }
        }
    }

    fun updateSearch(value: String) {
        if (value == searchTerm.value) return
        _uiState.update { it.copy(searchTerm = value, isLoading = true) }
        searchTerm.value = value
    }

    fun selectCompany(clientId: String) {
        _uiState.update { current ->
            val selectable = current.companies.any {
                it.clientId == clientId && it.linkStatus == OptimalCompanyLinkStatus.UNLINKED
            }
            if (selectable && !current.isIssuing) {
                current.copy(selectedClientId = clientId, registrationCode = null)
            } else {
                current
            }
        }
    }

    fun issueCode() {
        val snapshot = _uiState.value
        if (!snapshot.canIssue) return
        val clientId = snapshot.selectedClientId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isIssuing = true, errorMessage = null, registrationCode = null) }
            val result = runCatching { issueCompanyJoinCode(clientId) }
                .getOrElse { OptimalRegistrationResult.NetworkError }
            _uiState.update { current ->
                when (result) {
                    is OptimalRegistrationResult.Success -> {
                        val stillSelected = current.selectedClientId == result.code.clientId &&
                            current.companies.any {
                                it.clientId == result.code.clientId &&
                                    it.organizationId == result.code.organizationId &&
                                    it.linkStatus == OptimalCompanyLinkStatus.UNLINKED
                            }
                        if (stillSelected) {
                            current.copy(isIssuing = false, registrationCode = result.code)
                        } else {
                            current.copy(
                                isIssuing = false,
                                registrationCode = null,
                                errorMessage = "تغيّرت المؤسسة أو الشركة المختارة. أعد الاختيار",
                            )
                        }
                    }
                    else -> current.copy(
                        isIssuing = false,
                        errorMessage = result.errorMessage(),
                    )
                }
            }
        }
    }

    fun dismissRegistrationCode() {
        _uiState.update { it.copy(registrationCode = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

private fun OptimalRegistrationResult.errorMessage(): String = when (this) {
    is OptimalRegistrationResult.Success -> ""
    OptimalRegistrationResult.PermissionDenied -> "لا تملك صلاحية إصدار كود انضمام شركة"
    OptimalRegistrationResult.BackendContractBlocked -> "خادم Optimal لم يُفعّل أكواد انضمام الشركات بعد"
    OptimalRegistrationResult.SessionUnavailable -> "تعذّر تحديد مؤسسة Verto الحالية"
    OptimalRegistrationResult.CompanyUnavailable -> "الشركة المختارة لم تعد متاحة أو ليست من نوع شركة"
    OptimalRegistrationResult.CompanyAlreadyLinked -> "الشركة المختارة مرتبطة بـOptimal بالفعل"
    OptimalRegistrationResult.RemoteResponseRejected -> "استجابة الخادم لا تطابق الشركة المختارة"
    OptimalRegistrationResult.BackendNotConfigured -> "اتصال Supabase غير مُعد"
    OptimalRegistrationResult.NetworkError -> "تعذّر إصدار الكود. تحقق من الاتصال"
}
