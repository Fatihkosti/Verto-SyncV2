package com.verto.app.feature.organization.presentation.team

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEffect
import com.verto.app.core.presentation.UiEffectSource
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.organization.domain.model.CreateOrganizationInviteRequest
import com.verto.app.feature.organization.domain.model.EmployeePermissions
import com.verto.app.feature.organization.domain.model.OrganizationEmployee
import com.verto.app.feature.organization.domain.repository.OrganizationTeamGateway
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmployeesUiState(
    val isLoading: Boolean = false,
    val employees: List<OrganizationEmployee> = emptyList(),
    val error: String? = null
)

data class EmployeePermissionsUiState(
    val isLoading: Boolean = false,
    val employeeName: String = "",
    val joinedAt: String = "",
    val permissions: EmployeePermissions? = null,
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false,
    val error: String? = null
)

data class CreateInviteUiState(
    val employeeName: String = "",
    val jobTitle: String = "",
    val actualJoinDate: String = "",
    val permissions: EmployeePermissions = EmployeePermissions.defaultEmployee(),
    val isGenerating: Boolean = false,
    val generatedCode: String? = null,
    val error: String? = null
)

data class OrganizationTeamUiState(
    val employees: EmployeesUiState = EmployeesUiState(),
    val employeePermissions: EmployeePermissionsUiState = EmployeePermissionsUiState(),
    val createInvite: CreateInviteUiState = CreateInviteUiState()
) : UiState

sealed interface OrganizationTeamEvent : UiEvent {
    data object LoadEmployees : OrganizationTeamEvent
    data class LoadEmployeePermissions(val userId: String) : OrganizationTeamEvent
    data class UpdatePermissionsLocally(val permissions: EmployeePermissions) : OrganizationTeamEvent
    data class SaveEmployeePermissions(val userId: String) : OrganizationTeamEvent
    data class RemoveEmployee(val userId: String) : OrganizationTeamEvent
    data class UpdateInvitePermissions(val permissions: EmployeePermissions) : OrganizationTeamEvent
    data class UpdateInviteEmployeeName(val name: String) : OrganizationTeamEvent
    data class UpdateInviteJobTitle(val jobTitle: String) : OrganizationTeamEvent
    data class UpdateInviteActualJoinDate(val date: String) : OrganizationTeamEvent
    data object GenerateInviteCode : OrganizationTeamEvent
    data object ResetCreateInvite : OrganizationTeamEvent
}

sealed interface OrganizationTeamEffect : UiEffect {
    data class EmployeeRemoved(val userId: String) : OrganizationTeamEffect
}

/** ViewModel مملوك لميزة المؤسسة لإدارة الموظفين وصلاحياتهم وأكواد الانضمام. */
@HiltViewModel
class OrganizationTeamViewModel @Inject constructor(
    private val gateway: OrganizationTeamGateway
) : ViewModel(),
    UiStateHolder<OrganizationTeamUiState>,
    UiEventHandler<OrganizationTeamEvent>,
    UiEffectSource<OrganizationTeamEffect> {

    private val _employeesUiState = MutableStateFlow(EmployeesUiState())
    val employeesUiState: StateFlow<EmployeesUiState> = _employeesUiState.asStateFlow()

    private val _employeePermissionsUiState = MutableStateFlow(EmployeePermissionsUiState())
    val employeePermissionsUiState: StateFlow<EmployeePermissionsUiState> =
        _employeePermissionsUiState.asStateFlow()

    private val _createInviteUiState = MutableStateFlow(CreateInviteUiState())
    val createInviteUiState: StateFlow<CreateInviteUiState> = _createInviteUiState.asStateFlow()

    override val uiState: StateFlow<OrganizationTeamUiState> = combine(
        employeesUiState,
        employeePermissionsUiState,
        createInviteUiState
    ) { employees, permissions, invite ->
        OrganizationTeamUiState(
            employees = employees,
            employeePermissions = permissions,
            createInvite = invite
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OrganizationTeamUiState()
    )

    private val _effects = MutableSharedFlow<OrganizationTeamEffect>(extraBufferCapacity = 1)
    override val effects: SharedFlow<OrganizationTeamEffect> = _effects.asSharedFlow()

    override fun onEvent(event: OrganizationTeamEvent) {
        when (event) {
            OrganizationTeamEvent.LoadEmployees -> loadEmployeesInternal()
            is OrganizationTeamEvent.LoadEmployeePermissions -> loadEmployeePermissionsInternal(event.userId)
            is OrganizationTeamEvent.UpdatePermissionsLocally -> updatePermissionsLocallyInternal(event.permissions)
            is OrganizationTeamEvent.SaveEmployeePermissions -> saveEmployeePermissionsInternal(event.userId)
            is OrganizationTeamEvent.RemoveEmployee -> removeEmployeeInternal(event.userId)
            is OrganizationTeamEvent.UpdateInvitePermissions -> updateInvitePermissionsInternal(event.permissions)
            is OrganizationTeamEvent.UpdateInviteEmployeeName -> updateInviteEmployeeNameInternal(event.name)
            is OrganizationTeamEvent.UpdateInviteJobTitle -> updateInviteJobTitleInternal(event.jobTitle)
            is OrganizationTeamEvent.UpdateInviteActualJoinDate -> updateInviteActualJoinDateInternal(event.date)
            OrganizationTeamEvent.GenerateInviteCode -> generateInviteCodeInternal()
            OrganizationTeamEvent.ResetCreateInvite -> resetCreateInviteInternal()
        }
    }

    fun loadEmployees() = onEvent(OrganizationTeamEvent.LoadEmployees)
    fun loadEmployeePermissions(userId: String) =
        onEvent(OrganizationTeamEvent.LoadEmployeePermissions(userId))
    fun updatePermissionsLocally(permissions: EmployeePermissions) =
        onEvent(OrganizationTeamEvent.UpdatePermissionsLocally(permissions))
    fun saveEmployeePermissions(userId: String) =
        onEvent(OrganizationTeamEvent.SaveEmployeePermissions(userId))
    fun removeEmployee(userId: String) = onEvent(OrganizationTeamEvent.RemoveEmployee(userId))
    fun updateInvitePermissions(permissions: EmployeePermissions) =
        onEvent(OrganizationTeamEvent.UpdateInvitePermissions(permissions))
    fun updateInviteEmployeeName(name: String) =
        onEvent(OrganizationTeamEvent.UpdateInviteEmployeeName(name))
    fun updateInviteJobTitle(jobTitle: String) =
        onEvent(OrganizationTeamEvent.UpdateInviteJobTitle(jobTitle))
    fun updateInviteActualJoinDate(date: String) =
        onEvent(OrganizationTeamEvent.UpdateInviteActualJoinDate(date))
    fun generateInviteCode() = onEvent(OrganizationTeamEvent.GenerateInviteCode)
    fun resetCreateInvite() = onEvent(OrganizationTeamEvent.ResetCreateInvite)

    private fun loadEmployeesInternal() = viewModelScope.launch {
        _employeesUiState.update { it.copy(isLoading = true, error = null) }
        gateway.getEmployees()
            .onSuccess { employees ->
                _employeesUiState.update { it.copy(isLoading = false, employees = employees) }
            }
            .onFailure { error ->
                _employeesUiState.update {
                    it.copy(isLoading = false, error = ErrorHumanizer.humanize(error, "جلب الموظفين"))
                }
            }
    }

    private fun loadEmployeePermissionsInternal(userId: String) = viewModelScope.launch {
        val trimmedUserId = userId.trim()
        Log.d("EmployeePermissions", "Employee permissions screen initialized")
        _employeePermissionsUiState.update {
            it.copy(
                isLoading = true,
                employeeName = "",
                joinedAt = "",
                permissions = null,
                error = null,
                savedSuccess = false
            )
        }

        if (trimmedUserId.isBlank()) {
            Log.w("EmployeePermissions", "Employee permissions load failed: empty employeeId")
            _employeePermissionsUiState.update {
                it.copy(isLoading = false, error = "معرف الموظف غير موجود")
            }
            return@launch
        }

        val employee = _employeesUiState.value.employees.find { it.userId == trimmedUserId }
        Log.d("EmployeePermissions", "Cached employee lookup completed; found=${employee != null}")

        gateway.getEmployeePermissions(trimmedUserId)
            .onSuccess { permissions ->
                Log.d("EmployeePermissions", "Employee permissions loaded")
                _employeePermissionsUiState.update {
                    it.copy(
                        isLoading = false,
                        employeeName = employee?.name ?: "",
                        joinedAt = employee?.joinedAt?.substringBefore("T") ?: "",
                        permissions = permissions
                    )
                }
            }
            .onFailure { error ->
                Log.e("EmployeePermissions", "Employee permissions load failed: ${error::class.java.simpleName}")
                _employeePermissionsUiState.update {
                    val message = if (isNetworkError(error)) {
                        "تعذّر الاتصال بالإنترنت، تحقق من الشبكة وأعد المحاولة"
                    } else {
                        ErrorHumanizer.humanize(error, "جلب الصلاحيات")
                    }
                    it.copy(isLoading = false, permissions = null, error = message)
                }
            }
    }

    private fun updatePermissionsLocallyInternal(permissions: EmployeePermissions) {
        _employeePermissionsUiState.update {
            it.copy(permissions = permissions, savedSuccess = false)
        }
    }

    private fun saveEmployeePermissionsInternal(userId: String) = viewModelScope.launch {
        val trimmedUserId = userId.trim()
        val permissions = _employeePermissionsUiState.value.permissions ?: return@launch
        if (trimmedUserId.isBlank()) {
            Log.w("EmployeePermissions", "Save failed: empty employeeId")
            _employeePermissionsUiState.update { it.copy(error = "معرف الموظف غير موجود") }
            return@launch
        }

        _employeePermissionsUiState.update { it.copy(isSaving = true, error = null) }
        Log.d("EmployeePermissions", "Saving employee permissions")
        gateway.saveEmployeePermissions(permissions.copy(userId = trimmedUserId))
            .onSuccess {
                Log.d("EmployeePermissions", "Employee permissions saved")
                _employeePermissionsUiState.update {
                    it.copy(isSaving = false, savedSuccess = true)
                }
                delay(3_000)
                _employeePermissionsUiState.update { it.copy(savedSuccess = false) }
            }
            .onFailure { error ->
                Log.e("EmployeePermissions", "Employee permissions save failed: ${error::class.java.simpleName}")
                _employeePermissionsUiState.update {
                    val message = if (isNetworkError(error)) {
                        "تعذّر حفظ الصلاحيات بسبب مشكلة اتصال"
                    } else {
                        ErrorHumanizer.humanize(error, "حفظ الصلاحيات")
                    }
                    it.copy(isSaving = false, error = message)
                }
            }
    }

    private fun removeEmployeeInternal(userId: String) = viewModelScope.launch {
        gateway.removeEmployee(userId)
            .onSuccess {
                _employeesUiState.update { state ->
                    state.copy(employees = state.employees.filter { it.userId != userId })
                }
                _effects.emit(OrganizationTeamEffect.EmployeeRemoved(userId))
            }
            .onFailure { error ->
                _employeePermissionsUiState.update {
                    it.copy(error = ErrorHumanizer.humanize(error, "حذف الموظف"))
                }
            }
    }

    private fun updateInvitePermissionsInternal(permissions: EmployeePermissions) {
        _createInviteUiState.update { it.copy(permissions = permissions) }
    }

    private fun updateInviteEmployeeNameInternal(name: String) {
        _createInviteUiState.update { it.copy(employeeName = name, error = null) }
    }

    private fun updateInviteJobTitleInternal(jobTitle: String) {
        _createInviteUiState.update { it.copy(jobTitle = jobTitle, error = null) }
    }

    private fun updateInviteActualJoinDateInternal(date: String) {
        _createInviteUiState.update { it.copy(actualJoinDate = date, error = null) }
    }

    private fun generateInviteCodeInternal() = viewModelScope.launch {
        val state = _createInviteUiState.value
        val validationError = when {
            state.employeeName.isBlank() -> "اسم الموظف مطلوب"
            state.jobTitle.isBlank() -> "المسمى الوظيفي مطلوب"
            state.actualJoinDate.isBlank() -> "تاريخ الانضمام مطلوب"
            !state.permissions.hasAnyEnabledPermission() -> "حدد صلاحية واحدة على الأقل"
            else -> null
        }
        if (validationError != null) {
            _createInviteUiState.update { it.copy(error = validationError) }
            return@launch
        }

        _createInviteUiState.update { it.copy(isGenerating = true, error = null) }
        gateway.generateInviteCode(
            CreateOrganizationInviteRequest(
                employeeName = state.employeeName,
                jobTitle = state.jobTitle,
                actualJoinDate = state.actualJoinDate,
                permissions = state.permissions
            )
        )
            .onSuccess { code ->
                _createInviteUiState.update {
                    it.copy(isGenerating = false, generatedCode = code)
                }
            }
            .onFailure { error ->
                _createInviteUiState.update {
                    it.copy(isGenerating = false, error = ErrorHumanizer.humanize(error, "توليد الكود"))
                }
            }
    }

    private fun resetCreateInviteInternal() {
        _createInviteUiState.value = CreateInviteUiState()
    }

    private fun isNetworkError(error: Throwable): Boolean = ErrorHumanizer.isNetworkError(error)
}
